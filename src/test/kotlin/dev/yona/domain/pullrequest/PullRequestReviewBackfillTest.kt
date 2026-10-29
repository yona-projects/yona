package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
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
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 기존 데이터를 새 리뷰 구조로 이어 주는 보정을 검증한다(로드맵 X-12, `decisions.md` D-35 13번). 승인 집합(`pull_request_reviewers`)에 있으나 승인 리뷰가 없는
 * 행마다 승인 리뷰를 만들고, 몇 번을 돌려도 결과가 같아야 한다.
 */
@Transactional
class PullRequestReviewBackfillTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var backfill: PullRequestReviewBackfill
    @Autowired private lateinit var reviews: PullRequestReviewRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "bf${++sequence}", name = "bf$sequence", email = "bf$sequence@example.com", state = UserState.ACTIVE))

    private fun pullRequest(created: Date = Date(1_700_000_000_000L)): PullRequest {
        val project = Project(owner = "bfo${++sequence}", name = "bfp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }
        return PullRequest(
            toProject = project, fromProject = project, number = (++sequence).toLong(), title = "요청", contributor = user(), state = State.OPEN,
            toBranch = "refs/heads/master", fromBranch = "refs/heads/feature", created = created
        ).also { em.persist(it) }
    }

    private fun approvals(pr: PullRequest) = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).filter { it.verdict == ReviewVerdict.APPROVED }

    @Test
    fun `승인 집합의 사용자마다 승인 리뷰를 만들고 시각은 옛 리뷰 완료 이벤트에서 가져온다`() {
        val pr = pullRequest(); val a = user(); val b = user()
        pr.reviewers.addAll(listOf(a, b))
        val done = Date(1_700_000_500_000L)
        em.persist(PullRequestEvent(created = done, senderLoginId = a.loginId, pullRequest = pr, eventType = EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, oldValue = "CANCEL", newValue = "DONE"))
        em.flush()

        assertEquals(2, backfill.backfill())

        val byAuthor = approvals(pr).associateBy { it.authorLoginId }
        assertEquals(setOf(a.loginId, b.loginId), byAuthor.keys)
        assertEquals(done, byAuthor.getValue(a.loginId!!).createdDate, "이벤트가 있으면 그 시각")
        assertEquals(pr.created, byAuthor.getValue(b.loginId!!).createdDate, "이벤트가 없으면 풀 리퀘스트 생성 시각")
        assertEquals(a.id, byAuthor.getValue(a.loginId!!).authorId)
        assertNull(byAuthor.getValue(a.loginId!!).contents); assertNull(byAuthor.getValue(a.loginId!!).dismissedDate)
    }

    @Test
    fun `몇 번을 돌려도 같은 결과이고 이미 승인 리뷰가 있으면 건너뛴다`() {
        val pr = pullRequest(); val a = user(); val b = user()
        pr.reviewers.addAll(listOf(a, b))
        reviews.save(PullRequestReview(pullRequest = pr, authorId = a.id, authorLoginId = a.loginId, verdict = ReviewVerdict.APPROVED, contents = "원래 있던 승인", createdDate = Date()))
        em.flush()

        assertEquals(1, backfill.backfill(), "b만 새로 만든다")
        assertEquals(0, backfill.backfill())
        assertEquals(0, backfill.backfill())
        assertEquals(setOf("원래 있던 승인", null), approvals(pr).map { it.contents }.toSet())
        assertEquals(2, approvals(pr).size)
    }

    @Test
    fun `해제된 승인만 있는 사용자가 집합에 남아 있으면 새 승인을 만든다`() {
        val pr = pullRequest(); val a = user()
        pr.reviewers.add(a)
        reviews.save(PullRequestReview(pullRequest = pr, authorId = a.id, authorLoginId = a.loginId, verdict = ReviewVerdict.APPROVED, createdDate = Date(), dismissedDate = Date(), dismissedReason = "새 커밋"))
        em.flush()
        assertEquals(1, backfill.backfill())
        assertEquals(1, approvals(pr).count { it.dismissedDate == null })
    }

    @Test
    fun `승인 집합이 비어 있으면 아무것도 만들지 않는다`() {
        pullRequest(); em.flush()
        assertEquals(0, backfill.backfill())
    }
}
