package dev.yona.repository

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.CommitsPushedActivity
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.pullrequest.BackgroundJobRunner
import dev.yona.domain.pullrequest.PullRequestMergeProcessor
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.ReceiveCommand
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import java.util.Date

/**
 * 푸시 직후에 도는 일들이다. v1.6 `playRepository.hooks.IssueReferredFromCommitEvent`, `PullRequestCheck`, `NotifyPushedCommits`와 그 액터들
 * (`IssueReferredFromCommitEventActor`, `RelatedPullRequestMergingActor`, `CommitsNotificationActor`)에 해당한다.
 *
 * - 커밋 메시지의 `#번호`가 가리키는 이슈에 "커밋에서 언급됨" 이벤트를 남긴다.
 * - 푸시한 브랜치와 관련된 열린 풀 리퀘스트의 병합 검사를 다시 하고, 지운 브랜치와 관련된 풀 리퀘스트는 지운다.
 * - 새 커밋 알림을 낸다.
 *
 * 모두 [BackgroundJobRunner]로 요청과 별개로 실행한다. 푸시로 새로 생기거나 바뀐 커밋 목록은 명령마다 `새 id`까지의 이력에서 `옛 id`까지를 뺀 것이다
 * (옛 id가 없으면 새 id 하나뿐이다).
 */
@Component
class PostReceiveJobs(
    private val repositories: RepositoryService,
    private val projects: ProjectRepository,
    private val users: UserRepository,
    private val issues: IssueRepository,
    private val issueEvents: IssueEventRepository,
    private val pullRequests: PullRequestRepository,
    private val pullRequestService: PullRequestService,
    private val processor: PullRequestMergeProcessor,
    private val runner: BackgroundJobRunner,
    private val publisher: ApplicationEventPublisher
) {

    /** 푸시로 새로 생기거나 바뀐 ref에 들어온 커밋과 그 ref 이름이다. */
    class PushedCommits(val commits: List<CommitDetail>, val refNames: List<String>)

    /** 커밋 하나의 값이다. */
    data class CommitDetail(val id: String, val shortId: String, val shortMessage: String, val message: String)

    private fun parse(repository: org.eclipse.jgit.lib.Repository, start: ObjectId, end: ObjectId): List<CommitDetail> = RevWalk(repository).use { walk ->
        walk.markStart(walk.parseCommit(end))
        if (start == ObjectId.zeroId()) return listOf(detail(walk.parseCommit(end)))
        walk.markUninteresting(walk.parseCommit(start))
        walk.map { detail(it) }
    }

    private fun detail(commit: RevCommit) = CommitDetail(commit.name, commit.abbreviate(SHORT_ID).name(), commit.shortMessage, commit.fullMessage)

    /** 푸시 뒤 일을 시작한다. 명령 정보는 푸시가 끝나면 쓸 수 없으므로 필요한 값만 복사해 넘긴다. */
    fun afterPush(project: Project, sender: User?, commands: Collection<ReceiveCommand>) {
        val projectId = requireNotNull(project.id)
        val senderId = sender?.id
        val pushed = commands.map { Command(it.type, it.refName, it.oldId, it.newId) }
        runner.run("after pushing to ${project.owner}/${project.name}", { referToIssues(projectId, senderId, pushed) }, { checkPullRequests(projectId, senderId, pushed) }, { notifyCommits(projectId, senderId, pushed) })
    }

    private class Command(val type: ReceiveCommand.Type, val refName: String, val oldId: ObjectId, val newId: ObjectId)

    private fun pushedCommits(projectId: Long, pushed: List<Command>): PushedCommits {
        val project = projects.findById(projectId).orElse(null) ?: return PushedCommits(emptyList(), emptyList())
        val details = mutableListOf<CommitDetail>()
        val refNames = mutableListOf<String>()
        repositories.openRepository(project).use { repository ->
            for (command in pushed) {
                if (command.type == ReceiveCommand.Type.CREATE || command.type == ReceiveCommand.Type.UPDATE || command.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD) {
                    details += parse(repository, command.oldId, command.newId)
                    refNames += command.refName
                }
            }
        }
        return PushedCommits(details, refNames)
    }

    /** 커밋 메시지의 `#번호`가 가리키는 이슈마다 "커밋에서 언급됨" 이벤트를 남긴다 (v1.6 `IssueReferredFromCommitEventActor`). */
    private fun referToIssues(projectId: Long, senderId: Long?, pushed: List<Command>) {
        val sender = senderId?.let { users.findById(it).orElse(null) } ?: return
        for (commit in pushedCommits(projectId, pushed).commits) {
            ISSUE_PATTERN.findAll(commit.message).mapNotNull { it.value.substring(1).toLongOrNull() }.distinct()
                .mapNotNull { issues.findByProjectIdAndNumber(projectId, it) }.distinctBy { it.id }.forEach { issue ->
                    issueEvents.save(
                        IssueEvent(
                            issue = issue, senderLoginId = sender.loginId, senderEmail = sender.email, newValue = commit.id, created = Date(),
                            eventType = EventType.ISSUE_REFERRED_FROM_COMMIT
                        )
                    )
                }
        }
    }

    /**
     * 갱신한 브랜치와 관련된 열린 풀 리퀘스트의 병합 검사를 다시 하고, 지운 브랜치와 관련된 풀 리퀘스트는 지운다 (v1.6 `PullRequestCheck`).
     * 풀 리퀘스트를 지우다 실패하면(리뷰 스레드가 달려 있는 경우 등) 로그만 남기고 다음 풀 리퀘스트를 계속 처리한다.
     */
    private fun checkPullRequests(projectId: Long, senderId: Long?, pushed: List<Command>) {
        val sender = senderId ?: return
        val updated = pushed.filter { it.type == ReceiveCommand.Type.UPDATE || it.type == ReceiveCommand.Type.UPDATE_NONFASTFORWARD }.map { it.refName }.toSet()
        val ids = updated.flatMap { branch -> pullRequests.findRelated(projectId, branch).mapNotNull { it.id } }
        if (ids.isNotEmpty()) {
            runner.run("merging of pull requests related to the pushed branches", { ids.forEach { processor.start(it) } }, { ids.forEach { processor.process(it, sender, false) } })
        }
        val deleted = pushed.filter { it.type == ReceiveCommand.Type.DELETE }.map { it.refName }.toSet()
        for (branch in deleted) {
            // 요청마다 따로 지운다. 하나가 실패해도(리뷰 스레드가 달려 있는 경우 등) 로그만 남고 나머지는 계속 지운다.
            for (id in pullRequests.findRelated(projectId, branch).mapNotNull { it.id }) {
                runner.run("deleting pull request $id of the deleted branch $branch", { pullRequests.findById(id).ifPresent { pullRequestService.delete(it) } })
            }
        }
    }

    /** 새 커밋 알림을 낸다 (v1.6 `CommitsNotificationActor`). */
    private fun notifyCommits(projectId: Long, senderId: Long?, pushed: List<Command>) {
        val project = projects.findById(projectId).orElse(null) ?: return
        val sender = senderId?.let { users.findById(it).orElse(null) } ?: return
        val result = pushedCommits(projectId, pushed)
        publisher.publishEvent(CommitsPushedActivity(project, sender, result.commits.map { Triple(it.shortId, it.shortMessage, it.id) }, result.refNames))
    }

    private companion object {
        const val SHORT_ID = 7
        val ISSUE_PATTERN = Regex("#\\d+")
    }
}
