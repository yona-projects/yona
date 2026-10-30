package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ruleset
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 리뷰 판정의 알림과 규칙 안내를 검증한다(로드맵 X-12, `decisions.md` D-35). 제출과 해제는 구독자와 작성자(해제는 해제된 리뷰어도)에게 알리고 행위자는 뺀다.
 * 새 커밋으로 시스템이 해제한 것은 알리지 않는다. 작성자 승인을 세지 않는 새 규칙 때문에 병합 요건을 잃은 열린 요청은 작성자와 프로젝트 관리자에게 한 번만 알린다.
 */
@Transactional
class PullRequestReviewNotificationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var service: PullRequestReviewService
    @Autowired private lateinit var backfill: PullRequestReviewBackfill
    @Autowired private lateinit var reviews: PullRequestReviewRepository
    @Autowired private lateinit var eventRepository: PullRequestEventRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "rn${++sequence}", name = "rn$sequence", email = "rn$sequence@example.com", state = UserState.ACTIVE))

    private fun project(usingCount: Boolean = true) =
        Project(owner = "rno${++sequence}", name = "rnp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it); if (usingCount) em.persist(ruleset(it)) }

    private fun manager(project: Project) = user().also { em.persist(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun pullRequest(project: Project, state: State = State.OPEN) = PullRequest(
        toProject = project, fromProject = project, number = (++sequence).toLong(), title = "요청", contributor = user(), state = state, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature"
    ).also { em.persist(it) }

    private fun notes(type: EventType) = notifications.findAll().filter { it.eventType == type }

    @Test
    fun `판정을 제출하면 작성자에게 알리고 제출한 사람은 받지 않는다`() {
        val pr = pullRequest(project()); val reviewer = user()
        service.submit(pr, reviewer, ReviewVerdict.COMMENTED, "의견입니다")
        val note = notes(EventType.PULL_REQUEST_REVIEW_SUBMITTED).single()
        assertEquals(reviewer.id, note.senderId); assertEquals("COMMENTED", note.oldValue); assertEquals("의견입니다", note.newValue)
        assertTrue(note.title!!.startsWith("Re: [") && note.title!!.contains("(#${pr.number})"), note.title)
        assertEquals(setOf(pr.contributor!!.id), note.receivers.map { it.id }.toSet())
    }

    @Test
    fun `해제하면 작성자와 해제된 리뷰어에게 알리고 해제한 사람은 받지 않는다`() {
        val project = project(); val pr = pullRequest(project); val reviewer = user(); val admin = manager(project)
        service.submit(pr, reviewer, ReviewVerdict.APPROVED, null)
        service.dismiss(pr, reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(pr.id!!).single(), admin, "철회")
        val note = notes(EventType.PULL_REQUEST_REVIEW_DISMISSED).single()
        assertEquals(admin.id, note.senderId); assertEquals(reviewer.loginId, note.oldValue); assertEquals("철회", note.newValue)
        assertEquals(setOf(pr.contributor!!.id, reviewer.id), note.receivers.map { it.id }.toSet())
    }

    @Test
    fun `새 커밋으로 시스템이 해제한 것은 알리지 않는다`() {
        val pr = pullRequest(project()); service.submit(pr, user(), ReviewVerdict.APPROVED, null)
        service.dismissStaleApprovals(pr)
        assertTrue(notes(EventType.PULL_REQUEST_REVIEW_DISMISSED).isEmpty())
    }

    // --- 작성자 승인을 세지 않는 새 규칙의 영향 점검 ---

    @Test
    fun `작성자 승인만으로 요건을 채웠던 열린 요청은 작성자와 관리자에게 한 번만 알린다`() {
        val project = project(); val admin = manager(project); val pr = pullRequest(project)
        pr.reviewers.add(pr.contributor!!); em.flush()
        assertEquals(1, backfill.noticeIgnoredAuthorApprovals())
        assertEquals(1, eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).count { it.eventType == EventType.PULL_REQUEST_REVIEW_RULE_NOTICE })
        val note = notes(EventType.PULL_REQUEST_REVIEW_RULE_NOTICE).single()
        assertEquals(setOf(pr.contributor!!.id, admin.id), note.receivers.map { it.id }.toSet())
        assertEquals(0, backfill.noticeIgnoredAuthorApprovals(), "이미 알린 요청에는 다시 알리지 않는다")
        assertEquals(1, notes(EventType.PULL_REQUEST_REVIEW_RULE_NOTICE).size)
    }

    @Test
    fun `타인의 승인이 함께 있거나 닫혔거나 리뷰어 수를 쓰지 않는 요청은 알리지 않는다`() {
        val project = project(); val withOther = pullRequest(project).also { it.reviewers.addAll(listOf(it.contributor!!, user())) }
        val closed = pullRequest(project, State.CLOSED).also { it.reviewers.add(it.contributor!!) }
        val notUsing = pullRequest(project(usingCount = false)).also { it.reviewers.add(it.contributor!!) }
        em.flush()
        assertEquals(0, backfill.noticeIgnoredAuthorApprovals())
        assertTrue(listOf(withOther, closed, notUsing).all { pr -> eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).none { it.eventType == EventType.PULL_REQUEST_REVIEW_RULE_NOTICE } })
    }
}
