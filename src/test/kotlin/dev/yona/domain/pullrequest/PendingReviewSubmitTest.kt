package dev.yona.domain.pullrequest

import dev.yona.domain.comment.CodeRange
import dev.yona.domain.comment.PendingReviewReader
import dev.yona.domain.comment.ReviewCommentService
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 리뷰를 제출할 때 임시 저장 리뷰의 줄 댓글이 함께 공개되는지 검증한다(로드맵 X-26, `decisions.md` D-38): 임시 댓글이 있으면 본문 없이도 제출할 수 있고,
 * 알림은 줄 댓글마다가 아니라 리뷰 하나로 한 번만 나가며 줄 댓글에서 멘션된 사용자도 그 알림을 받는다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-pending-submit"])
class PendingReviewSubmitTest : AbstractIntegrationTest() {

    @Autowired private lateinit var reviews: PullRequestReviewService
    @Autowired private lateinit var comments: ReviewCommentService
    @Autowired private lateinit var reader: PendingReviewReader
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @Autowired private lateinit var events: PullRequestEventRepository
    @Autowired private lateinit var repositories: RepositoryService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(name: String? = null) = users.save(User(loginId = name ?: "psb${++sequence}", name = "psb$sequence", email = "psb$sequence@example.com", state = UserState.ACTIVE))

    private fun project(): Project = Project(owner = "psbo${++sequence}", name = "psbp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also {
        em.persist(it); repositories.createRepository(it)
        GitTestSupport.pushCommits(repositories, it, GitTestSupport.TestCommit("기반", mapOf("a.txt" to "1\n2\n3\n4\n".toByteArray())))
    }

    private fun pullRequest(p: Project, contributor: User): PullRequest {
        val head = repositories.openRepository(p).use { it.resolve("refs/heads/master").name }
        return PullRequest(
            toProject = p, fromProject = p, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature",
            mergedCommitIdFrom = head, mergedCommitIdTo = head
        ).also { em.persist(it) }
    }

    private val range get() = CodeRange("a.txt", Side.B, 2, 0, Side.B, 2, 3)

    private fun notes(type: EventType) = notifications.findAll().filter { it.eventType == type }

    @Test
    fun `임시 줄 댓글이 있으면 본문 없이도 댓글과 변경 요청을 제출할 수 있다`() {
        val p = project(); val reviewer = user(); val pr = pullRequest(p, user())
        assertEquals(ReviewResult.BODY_REQUIRED, reviews.submit(pr, reviewer, ReviewVerdict.COMMENTED, null), "임시 댓글이 없으면 본문이 필요하다")
        comments.commentOnPullRequest(p, pr, null, reviewer, "이 줄을 보세요", null, range, pending = true)
        assertEquals(ReviewResult.SUBMITTED, reviews.submit(pr, reviewer, ReviewVerdict.CHANGES_REQUESTED, null))
    }

    @Test
    fun `제출하면 임시 줄 댓글이 공개되고 개수가 0이 되며 타임라인에 남는다`() {
        val p = project(); val reviewer = user(); val pr = pullRequest(p, user())
        comments.commentOnPullRequest(p, pr, null, reviewer, "첫째", null, range, pending = true)
        comments.commentOnPullRequest(p, pr, null, reviewer, "둘째", null, range, pending = true)
        em.flush(); em.clear()
        assertEquals(ReviewResult.SUBMITTED, reviews.submit(em.find(PullRequest::class.java, pr.id), reviewer, ReviewVerdict.COMMENTED, "요약"))
        assertEquals(0L, reader.count(pr, reviewer))
        em.flush(); em.clear()
        assertEquals(2, em.createQuery("select count(c) from ReviewComment c where c.thread.pullRequest.id = :id", java.lang.Long::class.java).setParameter("id", pr.id).singleResult.toLong())
        assertEquals(1, events.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).count { it.eventType == EventType.PULL_REQUEST_REVIEW_SUBMITTED })
    }

    @Test
    fun `알림은 줄 댓글마다가 아니라 리뷰 하나로 한 번만 나가고 줄 댓글을 요약한다`() {
        val p = project(); val reviewer = user(); val author = user(); val pr = pullRequest(p, author)
        comments.commentOnPullRequest(p, pr, null, reviewer, "첫째 줄 설명", null, range, pending = true)
        comments.commentOnPullRequest(p, pr, null, reviewer, "둘째 줄 설명", null, range, pending = true)
        assertTrue(notes(EventType.NEW_REVIEW_COMMENT).isEmpty(), "임시로 달 때는 알림이 없다")
        reviews.submit(pr, reviewer, ReviewVerdict.COMMENTED, "요약")
        assertTrue(notes(EventType.NEW_REVIEW_COMMENT).isEmpty(), "공개되어도 줄 댓글마다 알림을 보내지 않는다")
        val submitted = notes(EventType.PULL_REQUEST_REVIEW_SUBMITTED).single()
        assertTrue(submitted.receivers.any { it.id == author.id })
        val body = submitted.newValue.orEmpty()
        assertTrue(body.contains("요약") && body.contains("a.txt:2") && body.contains("첫째 줄 설명") && body.contains("둘째 줄 설명"), body)
    }

    @Test
    fun `줄 댓글에서 멘션된 사용자도 리뷰 알림을 받고 제출한 본인은 받지 않는다`() {
        val p = project(); val reviewer = user(); val author = user(); val mentioned = user("mentionee"); val pr = pullRequest(p, author)
        comments.commentOnPullRequest(p, pr, null, reviewer, "@mentionee 확인해 주세요", null, range, pending = true)
        reviews.submit(pr, reviewer, ReviewVerdict.COMMENTED, null)
        val receivers = notes(EventType.PULL_REQUEST_REVIEW_SUBMITTED).single().receivers.map { it.loginId }.toSet()
        assertTrue(mentioned.loginId in receivers && author.loginId in receivers && reviewer.loginId !in receivers, receivers.toString())
    }

    @Test
    fun `임시 댓글이 없는 제출은 지금까지와 같다`() {
        val p = project(); val reviewer = user(); val author = user(); val pr = pullRequest(p, author)
        assertEquals(ReviewResult.SUBMITTED, reviews.submit(pr, reviewer, ReviewVerdict.APPROVED, null))
        assertEquals("", notes(EventType.PULL_REQUEST_REVIEW_SUBMITTED).single().newValue.orEmpty())
    }
}
