package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
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
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 리뷰 판정의 저장을 검증한다(로드맵 X-12, `decisions.md` D-35). 테이블 `pull_request_review`에 이력을 쌓고,
 * 프로젝트가 지워질 때 함께 지워지며, 사용자를 삭제 처리해도 이력은 로그인 ID 사본과 함께 남아야 한다.
 */
@Transactional
class PullRequestReviewStorageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var reviews: PullRequestReviewRepository
    @Autowired private lateinit var projectDeletion: ProjectDeletionService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "prr${++sequence}", name = "prr$sequence", email = "prr$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = Project(owner = "prro${++sequence}", name = "prrp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }

    private fun pullRequest(to: Project, contributor: User = user()) = PullRequest(
        toProject = to, fromProject = to, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature"
    ).also { em.persist(it) }

    private fun count(table: String, where: String = "1 = 1"): Long {
        em.flush()
        return (em.createNativeQuery("select count(*) from $table where $where").singleResult as Number).toLong()
    }

    @Test
    fun `리뷰를 저장하고 다시 읽는다`() {
        val p = project(); val reviewer = user(); val pr = pullRequest(p)
        val saved = reviews.save(PullRequestReview(pullRequest = pr, authorId = reviewer.id, authorLoginId = reviewer.loginId, verdict = ReviewVerdict.CHANGES_REQUESTED, contents = "고쳐 주세요", createdDate = Date()))
        em.flush(); em.clear()
        val loaded = reviews.findById(requireNotNull(saved.id)).get()
        assertEquals(ReviewVerdict.CHANGES_REQUESTED, loaded.verdict); assertEquals("고쳐 주세요", loaded.contents)
        assertEquals(reviewer.id, loaded.authorId); assertEquals(reviewer.loginId, loaded.authorLoginId); assertEquals(pr.id, loaded.pullRequest?.id)
        assertNull(loaded.dismissedDate); assertNull(loaded.dismissedByLoginId); assertNull(loaded.dismissedReason)
        assertEquals("CHANGES_REQUESTED", em.createNativeQuery("select verdict from pull_request_review where id = ${saved.id}").singleResult, "판정은 열거형 이름으로 저장한다")
    }

    @Test
    fun `한 풀 리퀘스트의 리뷰는 제출 순서로 읽는다`() {
        val pr = pullRequest(project()); val a = user(); val b = user()
        val base = System.currentTimeMillis()
        reviews.save(PullRequestReview(pullRequest = pr, authorId = b.id, authorLoginId = b.loginId, verdict = ReviewVerdict.COMMENTED, contents = "둘째", createdDate = Date(base + 2000)))
        reviews.save(PullRequestReview(pullRequest = pr, authorId = a.id, authorLoginId = a.loginId, verdict = ReviewVerdict.APPROVED, createdDate = Date(base + 1000)))
        em.flush(); em.clear()
        assertEquals(listOf(ReviewVerdict.APPROVED, ReviewVerdict.COMMENTED), reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(requireNotNull(pr.id)).map { it.verdict })
    }

    @Test
    fun `프로젝트를 지우면 그 프로젝트가 받는 풀 리퀘스트의 리뷰도 지워진다`() {
        val p = project(); val other = project(); val reviewer = user()
        val mine = pullRequest(p); val others = pullRequest(other)
        reviews.save(PullRequestReview(pullRequest = mine, authorId = reviewer.id, authorLoginId = reviewer.loginId, verdict = ReviewVerdict.APPROVED, createdDate = Date()))
        reviews.save(PullRequestReview(pullRequest = others, authorId = reviewer.id, authorLoginId = reviewer.loginId, verdict = ReviewVerdict.APPROVED, createdDate = Date()))
        em.flush()
        projectDeletion.delete(p)
        em.flush(); em.clear()
        assertEquals(0, count("pull_request_review", "pull_request_id = ${mine.id}"))
        assertEquals(1, count("pull_request_review", "pull_request_id = ${others.id}"), "다른 프로젝트의 풀 리퀘스트는 그대로이다")
    }

    @Test
    fun `프로젝트의 리뷰 해제 설정은 기본으로 켜져 있다`() {
        assertEquals(true, Project().isDismissingStaleApprovals)
        val p = project(); em.flush(); em.clear()
        assertTrue(em.find(Project::class.java, p.id).isDismissingStaleApprovals != false)
    }
}
