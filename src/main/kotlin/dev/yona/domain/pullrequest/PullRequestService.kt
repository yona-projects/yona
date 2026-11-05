package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 풀 리퀘스트 폼에서 받은 값이다.
 *
 * @property fromBranch 보내는 브랜치의 전체 이름(`refs/heads/...`)
 */
data class PullRequestForm(
    val title: String, val body: String, val fromProject: Project, val toProject: Project, val fromBranch: String, val toBranch: String
)

/** 풀 리퀘스트를 만든 결과이다. [created]이면 새로 만든 것이고, 아니면 같은 조건의 열린 요청이 이미 있어 그것을 돌려준다. */
data class PullRequestCreation(val pullRequest: PullRequest, val created: Boolean)

/**
 * 풀 리퀘스트의 번호 매기기, 만들기, 조회를 담당한다. v1.6 `PullRequestApp.createPullRequest`와 `PullRequest.save`, `nextPullRequestNumber`,
 * `findOne`, `findDuplicatedPullRequest`, `addNewIssueEvents`(`app/controllers/PullRequestApp.java:262-312`, `app/models/PullRequest.java`)에 해당한다.
 * 권한 확인과 검증은 호출하는 쪽이 한다.
 */
@Service
class PullRequestService(
    private val pullRequests: PullRequestRepository,
    private val issues: IssueRepository,
    private val issueEvents: IssueEventRepository,
    private val pushedBranches: PushedBranchRepository,
    private val events: PullRequestEventService,
    private val pullRequestCommits: PullRequestCommitRepository,
    private val threadRepository: dev.yona.domain.comment.CommentThreadRepository,
    private val publisher: ApplicationEventPublisher,
    private val watchService: dev.yona.domain.misc.WatchService,
    private val projects: dev.yona.domain.project.ProjectRepository
) {

    /** 받는 프로젝트 안에서 번호로 찾는다. 번호가 1보다 작으면 `null`이다 (v1.6 `findOne`). */
    @Transactional(readOnly = true)
    fun findOne(toProject: Project, number: Long): PullRequest? =
        if (number <= 0) null else pullRequests.findByToProjectIdAndNumber(requireNotNull(toProject.id), number)

    /** 풀 리퀘스트에 달린 스레드를 만든 시각 순서로 돌려준다. */
    @Transactional(readOnly = true)
    fun threads(pullRequest: PullRequest): List<dev.yona.domain.comment.CommentThread> =
        threadRepository.findByPullRequestId(requireNotNull(pullRequest.id)).sortedBy { it.createdDate }

    /** 받는 프로젝트의 다음 풀 리퀘스트 번호이다. 가장 큰 번호에 1을 더하고 없으면 1이다 (v1.6 `nextPullRequestNumber`). */
    @Transactional(readOnly = true)
    fun nextNumber(toProject: Project): Long {
        val max = pullRequests.findAll(
            { root, _, cb -> cb.equal(root.get<Project>("toProject").get<Long>("id"), toProject.id) },
            PageRequest.of(0, 1, Sort.by(Sort.Order.desc("number")))
        ).content.firstOrNull()?.number
        return if (max == null) 1 else max + 1
    }

    /** 같은 두 프로젝트의 같은 두 브랜치 사이에 열려 있는 요청이다 (v1.6 `findDuplicatedPullRequest`). */
    @Transactional(readOnly = true)
    fun findDuplicate(form: PullRequestForm): PullRequest? = pullRequests.findFirstByFromProjectIdAndFromBranchAndToProjectIdAndToBranchAndState(
        requireNotNull(form.fromProject.id), form.fromBranch, requireNotNull(form.toProject.id), form.toBranch, State.OPEN
    )

    /**
     * 풀 리퀘스트를 만든다. 같은 조건의 열린 요청이 이미 있으면 새로 만들지 않고 그것을 돌려준다.
     *
     * 번호를 받아 저장하고 제목과 본문이 가리키는 이슈에 이벤트를 남기며, 보내는 브랜치의 최근 푸시 기록을 지운다. 새 풀 리퀘스트 알림을 내보내고
     * 타임라인에 새 풀 리퀘스트 이벤트를 쌓는다. 병합 검사는 호출하는 쪽이 이어서 시작한다.
     */
    @Transactional
    fun create(form: PullRequestForm, contributor: User): PullRequestCreation {
        findDuplicate(form)?.let { return PullRequestCreation(it, false) }

        // 받는 프로젝트 행을 잠가 번호를 매기는 순서를 맞춘다. v1.6은 잠그지 않아 동시에 만들면 번호가 겹칠 수 있었다(C081)
        projects.lockById(requireNotNull(form.toProject.id))
        val pullRequest = pullRequests.save(
            PullRequest(
                title = form.title, body = form.body, toProject = form.toProject, fromProject = form.fromProject, toBranch = form.toBranch, fromBranch = form.fromBranch,
                contributor = contributor, created = Date(), state = State.OPEN, isMerging = false, isConflict = false, number = nextNumber(form.toProject)
            )
        )
        addIssueEvents(pullRequest)
        pushedBranches.findFirstByProjectIdAndName(requireNotNull(form.fromProject.id), form.fromBranch)?.let { pushedBranches.delete(it) }

        publisher.publishEvent(PullRequestCreatedActivity(pullRequest, contributor))
        events.addFromNotification(pullRequest, contributor, EventType.NEW_PULL_REQUEST, null, pullRequest.body)
        return PullRequestCreation(pullRequest, true)
    }

    /** 제목과 본문의 `#번호`가 가리키는 이슈마다 "풀 리퀘스트에서 언급됨" 이벤트를 남긴다 (v1.6 `addNewIssueEvents`). */
    @Transactional
    fun addIssueEvents(pullRequest: PullRequest) {
        val project = requireNotNull(pullRequest.toProject)
        val text = pullRequest.title.orEmpty() + pullRequest.body.orEmpty()
        ISSUE_PATTERN.findAll(text).map { it.value.substring(1).toLongOrNull() }.filterNotNull().distinct()
            .mapNotNull { issues.findByProjectIdAndNumber(requireNotNull(project.id), it) }.distinctBy { it.id }
            .forEach {
                issueEvents.save(
                    IssueEvent(
                        issue = it, senderLoginId = pullRequest.contributor?.loginId, newValue = pullRequest.id.toString(), created = Date(),
                        eventType = EventType.ISSUE_REFERRED_FROM_PULL_REQUEST
                    )
                )
            }
    }

    /**
     * 풀 리퀘스트를 지운다. 이슈 언급 이벤트, 커밋, 타임라인 이벤트, 리뷰어 연결을 함께 지운다 (v1.6 `PullRequest.delete`와 연쇄 삭제).
     * 리뷰 스레드가 달려 있으면 외래 키 때문에 지워지지 않고 예외가 난다(v1.6도 같다).
     */
    @Transactional
    fun delete(pullRequest: PullRequest) {
        val id = requireNotNull(pullRequest.id)
        deleteIssueEvents(pullRequest)
        pullRequestCommits.deleteAll(pullRequestCommits.findByPullRequestId(id))
        events.deleteAll(id)
        pullRequest.reviewers.clear()
        pullRequests.save(pullRequest)
        pullRequests.delete(pullRequest)
        pullRequests.flush()
        watchService.removeAll(dev.yona.domain.enumeration.ResourceType.PULL_REQUEST, id.toString())
    }

    /** 이 풀 리퀘스트가 남긴 "풀 리퀘스트에서 언급됨" 이벤트를 지운다 (v1.6 `deleteIssueEvents`). */
    @Transactional
    fun deleteIssueEvents(pullRequest: PullRequest) {
        val sender = pullRequest.contributor?.loginId ?: return
        issueEvents.deleteAll(issueEvents.findByNewValueAndSenderLoginIdAndEventType(pullRequest.id.toString(), sender, EventType.ISSUE_REFERRED_FROM_PULL_REQUEST))
    }

    private companion object {
        val ISSUE_PATTERN = Regex("#\\d+")
    }
}
