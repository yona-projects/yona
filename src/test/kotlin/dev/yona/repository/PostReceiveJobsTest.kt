package dev.yona.repository

import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.NonRangedCodeCommentThread
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.PullRequestCommitState
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.WatchService
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.ForkService
import dev.yona.domain.pullrequest.PullRequestCommitRepository
import dev.yona.domain.pullrequest.PullRequestEventRepository
import dev.yona.domain.pullrequest.PullRequestForm
import dev.yona.domain.pullrequest.PullRequestReviewRepository
import dev.yona.domain.pullrequest.PullRequestReviewService
import dev.yona.domain.pullrequest.PullRequestMergeProcessor
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.transport.ReceiveCommand
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 푸시 뒤에 도는 일(이슈 언급, 풀 리퀘스트 확인, 새 커밋 알림)을 검증한다.
 *
 * 원본: `app/playRepository/hooks/IssueReferredFromCommitEvent.java`, `PullRequestCheck.java`, `NotifyPushedCommits.java`와
 * `app/actors/IssueReferredFromCommitEventActor.java`, `CommitsNotificationActor.java`, `RelatedPullRequestMergingActor.java`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class PostReceiveJobsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var jobs: PostReceiveJobs
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var forks: ForkService
    @Autowired private lateinit var pullRequestService: PullRequestService
    @Autowired private lateinit var processor: PullRequestMergeProcessor
    @Autowired private lateinit var reviewService: PullRequestReviewService
    @Autowired private lateinit var reviewRepository: PullRequestReviewRepository
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var pullRequestCommits: PullRequestCommitRepository
    @Autowired private lateinit var pullRequestEvents: PullRequestEventRepository
    @Autowired private lateinit var notificationEvents: NotificationEventRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository
    @Autowired private lateinit var threads: CommentThreadRepository
    @Autowired private lateinit var watchService: WatchService
    @Autowired private lateinit var messages: org.springframework.context.MessageSource

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pj$n", name = "이름pj$n", email = "pj$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private class Setup(val origin: Project, val fork: Project, val contributor: User, val manager: User)

    private fun setup(): Setup {
        val n = ++sequence
        val origin = projects.save(Project(owner = "pjo$n", name = "pjp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = origin, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(origin)
        GitTestSupport.pushCommits(repositories, origin, TestCommit("기반", mapOf("a.txt" to "line1\nline2\nline3\n".toByteArray())))
        val manager = user()
        projectUsers.save(ProjectUser(user = manager, project = origin, role = roles.findById(RoleType.MANAGER.id).get()))
        val contributor = user()
        return Setup(origin, forks.fork(origin, contributor.loginId!!, "pjf$n", ProjectScope.PUBLIC, contributor), contributor, manager)
    }

    private fun head(project: Project, branch: String = "master"): ObjectId? = repositories.openRepository(project).use { it.resolve("refs/heads/$branch") }

    /** 포크의 [branch]에 커밋을 더한 뒤 푸시 명령을 만든다. */
    private fun push(s: Setup, message: String, file: String, content: String, branch: String = "master"): ReceiveCommand {
        val before = head(s.fork, branch)
        GitTestSupport.appendCommits(repositories, s.fork, TestCommit(message, mapOf(file to content.toByteArray())), branch = branch)
        return ReceiveCommand(before ?: ObjectId.zeroId(), head(s.fork, branch), "refs/heads/$branch")
    }

    private fun openPullRequest(s: Setup, fromBranch: String = "refs/heads/master") =
        pullRequestService.create(PullRequestForm("요청", "본문", s.fork, s.origin, fromBranch, "refs/heads/master"), s.contributor).pullRequest.also {
            processor.start(it.id!!); processor.process(it.id!!, s.contributor.id!!, true)
        }

    @Test
    fun `커밋 메시지의 번호가 가리키는 이슈에 커밋 언급 이벤트가 남는다`() {
        val s = setup()
        val issue = issues.save(Issue(project = s.fork, number = 7, title = "이슈"))
        val command = push(s, "고침 #7 그리고 #99", "b.txt", "b")
        jobs.afterPush(s.fork, s.contributor, listOf(command))
        val events = issueEvents.findByIssueIdOrderByCreatedAscIdAsc(issue.id!!)
        assertEquals(listOf(EventType.ISSUE_REFERRED_FROM_COMMIT), events.map { it.eventType })
        assertEquals(head(s.fork)!!.name, events.single().newValue); assertEquals(s.contributor.loginId, events.single().senderLoginId); assertEquals(s.contributor.email, events.single().senderEmail)
    }

    @Test
    fun `프로젝트를 구독한 구성원에게 새 커밋 알림이 간다`() {
        val s = setup()
        watchService.watch(s.manager, ResourceType.PROJECT, s.origin.id.toString())
        watchService.watch(s.contributor, ResourceType.PROJECT, s.origin.id.toString())
        val outsider = user(); watchService.watch(outsider, ResourceType.PROJECT, s.origin.id.toString())
        GitTestSupport.appendCommits(repositories, s.origin, TestCommit("첫 푸시", mapOf("b.txt" to "b".toByteArray())), TestCommit("둘째 푸시", mapOf("c.txt" to "c".toByteArray())))
        val old = ObjectId.fromString(head(s.origin)!!.name).let { _ -> repositories.openRepository(s.origin).use { it.resolve("refs/heads/master~2") } }
        val command = ReceiveCommand(old, head(s.origin), "refs/heads/master")
        jobs.afterPush(s.origin, s.contributor, listOf(command))
        val note = notificationEvents.findAll().single { it.eventType == EventType.NEW_COMMIT }
        assertEquals(messages.getMessage("notification.pushed.commits.to", arrayOf<Any>(s.origin.name!!, 2, "refs/heads/master"), java.util.Locale.getDefault()), note.title)
        assertEquals(setOf(s.manager.id), note.receivers.map { it.id }.toSet())
        assertTrue(note.newValue!!.startsWith("### " + messages.getMessage("notification.pushed.newcommits", null, java.util.Locale.getDefault()) + "\n```\n"), note.newValue)
        assertTrue(note.newValue!!.contains("둘째 푸시\n") && note.newValue!!.contains("첫 푸시\n"))
        assertTrue(note.newValue!!.contains("[refs/heads/master](/${s.origin.owner}/${s.origin.name}/commits/refs%252Fheads%252Fmaster/)"), note.newValue)
    }

    @Test
    fun `푸시한 브랜치와 관련된 열린 풀 리퀘스트는 병합 검사를 다시 하고 새 커밋 이벤트가 남는다`() {
        val s = setup()
        push(s, "첫 변경", "b.txt", "b")
        val pr = openPullRequest(s)
        pr.reviewers.add(s.manager); pullRequests.save(pr)
        val command = push(s, "추가 변경", "c.txt", "c")
        jobs.afterPush(s.fork, s.contributor, listOf(command))
        val updated = pullRequests.findById(pr.id!!).get()
        assertEquals(false, updated.isMerging); assertTrue(updated.reviewers.isEmpty())
        val commits = pullRequestCommits.findByPullRequestId(pr.id!!)
        assertEquals(setOf("첫 변경", "추가 변경"), commits.map { it.commitMessage!!.trim() }.toSet())
        assertEquals(2, commits.count { it.state == PullRequestCommitState.CURRENT })
        val changed = pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).filter { it.eventType == EventType.PULL_REQUEST_COMMIT_CHANGED }
        assertEquals(2, changed.size); assertNotNull(changed.last().oldValue)
        assertTrue(notificationEvents.findAll().none { it.eventType == EventType.PULL_REQUEST_COMMIT_CHANGED && it.receivers.isEmpty() })
    }

    @Test
    fun `새 커밋이 올라오면 승인을 해제하고 사유를 남기며 변경 요청은 그대로 둔다 (X-12, D-35 5번)`() {
        val s = setup()
        push(s, "첫 변경", "b.txt", "b")
        val pr = openPullRequest(s)
        val other = users.save(dev.yona.domain.user.User(loginId = "rvx${System.nanoTime()}", name = "다른 리뷰어", email = "rvx${System.nanoTime()}@example.com", state = dev.yona.domain.enumeration.UserState.ACTIVE))
        reviewService.submit(pr, s.manager, dev.yona.domain.enumeration.ReviewVerdict.APPROVED, null)
        reviewService.submit(pr, other, dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED, "문제")
        jobs.afterPush(s.fork, s.contributor, listOf(push(s, "추가 변경", "c.txt", "c")))
        val updated = pullRequests.findById(pr.id!!).get()
        assertTrue(updated.reviewers.isEmpty())
        val all = reviewRepository.findByPullRequestIdOrderByCreatedDateAscIdAsc(pr.id!!)
        val approval = all.first { it.verdict == dev.yona.domain.enumeration.ReviewVerdict.APPROVED }
        assertNotNull(approval.dismissedDate); assertEquals(PullRequestReviewService.STALE_REASON, approval.dismissedReason); assertNull(approval.dismissedByLoginId)
        assertNull(all.first { it.verdict == dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED }.dismissedDate)
    }

    @Test
    fun `프로젝트가 승인 해제를 끄면 새 커밋이 올라와도 승인이 남는다 (X-12, D-35 5번)`() {
        val s = setup()
        push(s, "첫 변경", "b.txt", "b")
        val pr = openPullRequest(s)
        s.origin.isDismissingStaleApprovals = false; projects.save(s.origin)
        reviewService.submit(pr, s.manager, dev.yona.domain.enumeration.ReviewVerdict.APPROVED, null)
        jobs.afterPush(s.fork, s.contributor, listOf(push(s, "추가 변경", "c.txt", "c")))
        val updated = pullRequests.findById(pr.id!!).get()
        assertEquals(listOf(s.manager.id), updated.reviewers.map { it.id })
        assertNull(reviewRepository.findByPullRequestIdOrderByCreatedDateAscIdAsc(pr.id!!).single().dismissedDate)
    }

    @Test
    fun `지운 브랜치와 관련된 풀 리퀘스트는 지워지고 리뷰 스레드가 있으면 남는다`() {
        val s = setup()
        push(s, "다른 변경", "d.txt", "d", branch = "other"); push(s, "또 다른 변경", "e.txt", "e", branch = "third")
        val plain = openPullRequest(s, "refs/heads/other"); val withThread = openPullRequest(s, "refs/heads/third")
        threads.save(NonRangedCodeCommentThread(commitId = "c").also { it.project = s.origin; it.pullRequest = withThread; it.state = ThreadState.OPEN; it.createdDate = Date() })
        threads.flush()
        val id = ObjectId.zeroId()
        jobs.afterPush(s.fork, s.contributor, listOf(ReceiveCommand(head(s.fork, "other"), id, "refs/heads/other", ReceiveCommand.Type.DELETE)))
        assertNull(pullRequests.findById(plain.id!!).orElse(null)); assertEquals(0, pullRequestCommits.findByPullRequestId(plain.id!!).size)
        assertEquals(0, pullRequestEvents.findByPullRequestIdOrderByCreatedAscIdAsc(plain.id!!).size)
        assertEquals(State.OPEN, pullRequests.findById(withThread.id!!).get().state)
    }
}
