package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.ReviewVerdict.APPROVED
import dev.yona.domain.enumeration.ReviewVerdict.CHANGES_REQUESTED
import dev.yona.domain.enumeration.ReviewVerdict.COMMENTED
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 리뷰 판정의 규칙을 검증한다(로드맵 X-12, `decisions.md` D-35). 권한 확인은 호출하는 쪽이 하고 이 서비스는 작성자 제한, 본문 규칙, 열린 요청 규칙,
 * 리뷰어별 최신 판정, 승인 집합 동기화, 해제를 맡는다.
 */
@Transactional
class PullRequestReviewServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var service: PullRequestReviewService
    @Autowired private lateinit var reviews: PullRequestReviewRepository
    @Autowired private lateinit var events: PullRequestEventRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "rv${++sequence}", name = "rv$sequence", email = "rv$sequence@example.com", state = UserState.ACTIVE))

    private fun pullRequest(usingCount: Boolean = true, required: Int = 1, state: State = State.OPEN): PullRequest {
        val project = Project(owner = "rvo${++sequence}", name = "rvp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC, isUsingReviewerCount = usingCount, defaultReviewerCount = required).also { em.persist(it) }
        return PullRequest(
            toProject = project, fromProject = project, number = (++sequence).toLong(), title = "요청", contributor = user(), state = state,
            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature"
        ).also { em.persist(it) }
    }

    private fun pullRequestEvents(pr: PullRequest) = events.findByPullRequestIdOrderByCreatedAscIdAsc(requireNotNull(pr.id))

    // --- 제출 ---

    @Test
    fun `구성원의 승인은 리뷰와 승인 집합과 타임라인 이벤트를 남긴다`() {
        val pr = pullRequest(); val reviewer = user()
        assertEquals(ReviewResult.SUBMITTED, service.submit(pr, reviewer, APPROVED, null))
        val saved = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).single()
        assertEquals(APPROVED, saved.verdict); assertEquals(reviewer.id, saved.authorId); assertEquals(reviewer.loginId, saved.authorLoginId); assertNotNull(saved.createdDate)
        assertEquals(listOf(reviewer.id), pr.reviewers.map { it.id })
        val event = pullRequestEvents(pr).single { it.eventType == EventType.PULL_REQUEST_REVIEW_SUBMITTED }
        assertEquals(reviewer.loginId, event.senderLoginId); assertEquals("APPROVED", event.oldValue); assertEquals(saved.id.toString(), event.newValue)
    }

    @Test
    fun `작성자는 승인과 변경 요청을 할 수 없고 댓글은 남길 수 있다`() {
        val pr = pullRequest(); val author = requireNotNull(pr.contributor)
        assertEquals(ReviewResult.AUTHOR_NOT_ALLOWED, service.submit(pr, author, APPROVED, null))
        assertEquals(ReviewResult.AUTHOR_NOT_ALLOWED, service.submit(pr, author, CHANGES_REQUESTED, "고칩니다"))
        assertEquals(ReviewResult.SUBMITTED, service.submit(pr, author, COMMENTED, "설명 덧붙임"))
        assertTrue(pr.reviewers.isEmpty())
        assertEquals(1, reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).size)
    }

    @Test
    fun `댓글과 변경 요청은 본문이 필수이고 승인은 선택이다`() {
        val pr = pullRequest(); val r = user()
        assertEquals(ReviewResult.BODY_REQUIRED, service.submit(pr, r, COMMENTED, "  "))
        assertEquals(ReviewResult.BODY_REQUIRED, service.submit(pr, r, CHANGES_REQUESTED, null))
        assertEquals(ReviewResult.SUBMITTED, service.submit(pr, r, APPROVED, ""))
        assertEquals(1, reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).size)
    }

    @Test
    fun `승인과 변경 요청은 열린 요청에만 하고 댓글은 상태와 상관없다`() {
        val merged = pullRequest(state = State.MERGED); val r = user()
        assertEquals(ReviewResult.NOT_OPEN, service.submit(merged, r, APPROVED, null))
        assertEquals(ReviewResult.NOT_OPEN, service.submit(merged, r, CHANGES_REQUESTED, "늦었다"))
        assertEquals(ReviewResult.SUBMITTED, service.submit(merged, r, COMMENTED, "나중에 본 의견"))
    }

    // --- 리뷰어별 최신 판정 ---

    @Test
    fun `같은 리뷰어의 최신 판정만 쓰며 승인 집합과 변경 요청 목록이 따라간다`() {
        val pr = pullRequest(); val r = user()
        service.submit(pr, r, APPROVED, null)
        assertEquals(setOf(r.id), pr.reviewers.map { it.id }.toSet()); assertTrue(pr.changesRequestedBy.isEmpty())
        service.submit(pr, r, CHANGES_REQUESTED, "고쳐 주세요")
        assertTrue(pr.reviewers.isEmpty()); assertEquals(listOf(r.loginId), pr.changesRequestedBy)
        service.submit(pr, r, APPROVED, "고쳤네요")
        assertEquals(setOf(r.id), pr.reviewers.map { it.id }.toSet()); assertTrue(pr.changesRequestedBy.isEmpty())
        assertEquals(3, reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).size, "이력은 모두 쌓는다")
    }

    @Test
    fun `승인한 사람의 뒤이은 댓글 판정은 최신 판정이 되어 승인을 거둔다`() {
        // GitHub 공식 문서는 댓글 리뷰가 앞선 승인을 덮는지 적지 않는다(자료가 엇갈린다). D-35 설계는 리뷰어별 최신 판정이므로 댓글이 승인을 거둔다
        val pr = pullRequest(); val r = user()
        service.submit(pr, r, APPROVED, null)
        service.submit(pr, r, COMMENTED, "한 가지만 더")
        assertTrue(pr.reviewers.isEmpty())
    }

    // --- 해제 ---

    @Test
    fun `변경 요청을 해제하면 막힘이 풀리고 승인을 해제하면 승인 집합에서 빠진다`() {
        val pr = pullRequest(); val a = user(); val b = user(); val admin = user()
        service.submit(pr, a, APPROVED, null); service.submit(pr, b, CHANGES_REQUESTED, "문제")
        val rc = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).last()
        assertEquals(DismissResult.DISMISSED, service.dismiss(pr, rc, admin, "검토 끝"))
        assertTrue(pr.changesRequestedBy.isEmpty())
        assertEquals("검토 끝", rc.dismissedReason); assertEquals(admin.loginId, rc.dismissedByLoginId); assertNotNull(rc.dismissedDate)
        val approval = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).first()
        assertEquals(DismissResult.DISMISSED, service.dismiss(pr, approval, admin, "철회"))
        assertTrue(pr.reviewers.isEmpty())
        assertEquals(2, pullRequestEvents(pr).count { it.eventType == EventType.PULL_REQUEST_REVIEW_DISMISSED })
    }

    @Test
    fun `해제 사유는 필수이고 이미 해제했거나 다른 요청의 리뷰는 해제할 수 없다`() {
        val pr = pullRequest(); val other = pullRequest(); val r = user(); val admin = user()
        service.submit(pr, r, APPROVED, null); service.submit(other, r, APPROVED, null)
        val mine = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).single()
        val foreign = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(other.id)).single()
        assertEquals(DismissResult.MESSAGE_REQUIRED, service.dismiss(pr, mine, admin, " "))
        assertEquals(DismissResult.NOT_FOUND, service.dismiss(pr, foreign, admin, "사유"))
        assertEquals(DismissResult.DISMISSED, service.dismiss(pr, mine, admin, "사유"))
        assertEquals(DismissResult.ALREADY_DISMISSED, service.dismiss(pr, mine, admin, "또"))
    }

    @Test
    fun `새 커밋으로 현재 승인을 해제하면 사유가 남고 변경 요청과 댓글은 그대로이다`() {
        val pr = pullRequest(); val a = user(); val b = user()
        service.submit(pr, a, APPROVED, null); service.submit(pr, b, CHANGES_REQUESTED, "문제")
        assertEquals(1, service.dismissStaleApprovals(pr))
        val all = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id))
        val approval = all.first { it.verdict == APPROVED }
        assertNotNull(approval.dismissedDate); assertNull(approval.dismissedByLoginId); assertEquals(PullRequestReviewService.STALE_REASON, approval.dismissedReason)
        assertNull(all.first { it.verdict == CHANGES_REQUESTED }.dismissedDate)
        assertTrue(pr.reviewers.isEmpty()); assertEquals(listOf(b.loginId), pr.changesRequestedBy)
        assertEquals(0, service.dismissStaleApprovals(pr), "해제할 승인이 없으면 0이다")
    }

    // --- 병합 조건 ---

    @Test
    fun `작성자의 승인은 개수에 세지 않는다`() {
        val pr = pullRequest(required = 1); val author = requireNotNull(pr.contributor)
        pr.reviewers.add(author) // v1.6 시절 작성자가 직접 승인해 둔 기록
        assertEquals(false, pr.reviewed); assertEquals(1, pr.lackingReviewerCount)
        service.submit(pr, user(), APPROVED, null)
        assertEquals(true, pr.reviewed)
    }

    @Test
    fun `리뷰어 수를 켠 프로젝트는 변경 요청이 남아 있으면 병합할 수 없다`() {
        val pr = pullRequest(usingCount = true, required = 1); val a = user(); val b = user()
        service.submit(pr, a, APPROVED, null)
        assertTrue(pr.acceptable)
        service.submit(pr, b, CHANGES_REQUESTED, "문제")
        assertEquals(false, pr.acceptable)
        assertEquals("pullRequest.not.acceptable.because.changes.requested", pr.disabledAcceptMessage().first)
        service.submit(pr, b, APPROVED, null)
        assertTrue(pr.acceptable)
    }

    @Test
    fun `리뷰어 수를 켜지 않은 프로젝트는 변경 요청이 있어도 표시일 뿐이다`() {
        val pr = pullRequest(usingCount = false); service.submit(pr, user(), CHANGES_REQUESTED, "문제")
        assertEquals(listOf(pr.reviewHistoryLoginIdOf(CHANGES_REQUESTED)), pr.changesRequestedBy)
        assertTrue(pr.acceptable)
    }

    private fun PullRequest.reviewHistoryLoginIdOf(verdict: ReviewVerdict) = reviewHistory.first { it.verdict == verdict }.authorLoginId
}
