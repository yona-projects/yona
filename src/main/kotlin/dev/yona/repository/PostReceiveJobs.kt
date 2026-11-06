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
import dev.yona.domain.vcs.RefUpdate
import dev.yona.domain.vcs.RefUpdateType
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
    private val backends: VcsBackends,
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
    data class CommitDetail(
        val id: String, val shortId: String, val shortMessage: String, val message: String, val authorName: String, val authorEmail: String,
        val committerName: String, val committerEmail: String, val commitTime: Date
    )

    private fun detail(commit: CommitInfo) = CommitDetail(
        commit.id, commit.shortId, commit.shortMessage, commit.message, commit.authorName, commit.authorEmail,
        commit.committerName, commit.committerEmail, commit.commitTime
    )

    /** 푸시 뒤 일을 시작한다. 푸시가 끝나면 쓸 수 없는 정보가 없도록 [updates]는 값 복사본이다. */
    fun afterPush(project: Project, sender: User?, updates: List<RefUpdate>) {
        val projectId = requireNotNull(project.id)
        val senderId = sender?.id
        val pushed = updates.toList()
        runner.run("after pushing to ${project.owner}/${project.name}", { referToIssues(projectId, senderId, pushed) }, { checkPullRequests(projectId, senderId, pushed) }, { notifyCommits(projectId, senderId, pushed) })
    }

    private fun pushedCommits(projectId: Long, pushed: List<RefUpdate>): PushedCommits {
        val project = projects.findById(projectId).orElse(null) ?: return PushedCommits(emptyList(), emptyList())
        val details = mutableListOf<CommitDetail>()
        val refNames = mutableListOf<String>()
        val reader = backends.commits(project.vcs)
        for (update in pushed) {
            if (update.type == RefUpdateType.CREATE || update.type == RefUpdateType.UPDATE || update.type == RefUpdateType.FORCE_UPDATE) {
                details += reader.pushedCommits(project, update.old, update.new).map { detail(it) }
                refNames += update.ref
            }
        }
        return PushedCommits(details, refNames)
    }

    /** 커밋 메시지의 `#번호`가 가리키는 이슈마다 "커밋에서 언급됨" 이벤트를 남긴다 (v1.6 `IssueReferredFromCommitEventActor`). */
    private fun referToIssues(projectId: Long, senderId: Long?, pushed: List<RefUpdate>) {
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
    private fun checkPullRequests(projectId: Long, senderId: Long?, pushed: List<RefUpdate>) {
        val sender = senderId ?: return
        val updated = dev.yona.domain.vcs.RefUpdates.updated(pushed)
        val ids = updated.flatMap { branch -> pullRequests.findRelated(projectId, branch).mapNotNull { it.id } }
        if (ids.isNotEmpty()) {
            runner.run("merging of pull requests related to the pushed branches", { ids.forEach { processor.start(it) } }, { ids.forEach { processor.process(it, sender, false) } })
        }
        val deleted = dev.yona.domain.vcs.RefUpdates.deleted(pushed)
        for (branch in deleted) {
            // 요청마다 따로 지운다. 하나가 실패해도(리뷰 스레드가 달려 있는 경우 등) 로그만 남고 나머지는 계속 지운다.
            for (id in pullRequests.findRelated(projectId, branch).mapNotNull { it.id }) {
                runner.run("deleting pull request $id of the deleted branch $branch", { pullRequests.findById(id).ifPresent { pullRequestService.delete(it) } })
            }
        }
    }

    /** 새 커밋 알림을 낸다 (v1.6 `CommitsNotificationActor`). */
    private fun notifyCommits(projectId: Long, senderId: Long?, pushed: List<RefUpdate>) {
        val project = projects.findById(projectId).orElse(null) ?: return
        val sender = senderId?.let { users.findById(it).orElse(null) } ?: return
        val result = pushedCommits(projectId, pushed)
        publisher.publishEvent(CommitsPushedActivity(project, sender, result.commits, result.refNames))
    }

    private companion object {
        val ISSUE_PATTERN = Regex("#\\d+")
    }
}
