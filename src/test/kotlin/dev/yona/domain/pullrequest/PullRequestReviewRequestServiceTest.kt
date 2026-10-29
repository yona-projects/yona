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
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 풀 리퀘스트 리뷰 요청을 검증한다(로드맵 X-27, `decisions.md` D-37): 요청과 취소, 후보 규칙(읽을 수 있는 활성 사용자, 작성자 제외), 타임라인 이벤트,
 * 알림 수신자(요청받은 사람), 리뷰를 제출하면 요청이 말없이 지워지는 것, 이미 리뷰한 사람에게 다시 요청하는 것.
 */
@Transactional
class PullRequestReviewRequestServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PullRequestReviewRequestService
    @Autowired private lateinit var reviewService: PullRequestReviewService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var eventRepository: PullRequestEventRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE) = users.save(User(loginId = "rrq${++sequence}", name = "rrq$sequence", email = "rrq$sequence@example.com", state = state))

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC) = Project(owner = "rrqo${++sequence}", name = "rrqp$sequence", vcs = "GIT", projectScope = scope).also { em.persist(it) }

    private fun member(project: Project, user: User = user()): User = user.also { em.persist(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun pullRequest(to: Project, contributor: User, state: State = State.OPEN) =
        PullRequest(toProject = to, fromProject = to, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = state, toBranch = "refs/heads/master", fromBranch = "refs/heads/feature").also { em.persist(it) }

    private fun events(pr: PullRequest, type: EventType = EventType.PULL_REQUEST_REVIEW_REQUESTED) =
        eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).filter { it.eventType == type }

    private fun notes() = notifications.findAll().filter { it.eventType == EventType.PULL_REQUEST_REVIEW_REQUESTED }

    @Test
    fun `리뷰를 요청하면 대기 목록에 들어가고 타임라인에 남긴다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val pr = pullRequest(p, member(p))
        assertEquals(ReviewRequestResult.CHANGED, service.request(pr, reviewer, actor))
        assertEquals(setOf(reviewer.id), pr.reviewRequests.map { it.id }.toSet())
        val event = events(pr).single()
        assertEquals(actor.loginId, event.senderLoginId); assertEquals(null, event.oldValue); assertEquals(reviewer.loginId, event.newValue)
    }

    @Test
    fun `같은 사람을 또 요청하면 아무 일도 없고 여러 명을 요청할 수 있다`() {
        val p = project(); val actor = member(p); val a = member(p); val b = member(p); val pr = pullRequest(p, member(p))
        service.request(pr, a, actor)
        assertEquals(ReviewRequestResult.UNCHANGED, service.request(pr, a, actor))
        assertEquals(1, events(pr).size, "이미 대기 중이면 이벤트를 더하지 않는다")
        assertEquals(ReviewRequestResult.CHANGED, service.request(pr, b, actor))
        assertEquals(setOf(a.id, b.id), pr.reviewRequests.map { it.id }.toSet())
    }

    @Test
    fun `작성자와 읽을 수 없는 사람과 활성이 아닌 사용자는 요청할 수 없다`() {
        val p = project(ProjectScope.PRIVATE); val actor = member(p); val author = member(p); val pr = pullRequest(p, author)
        val outsider = user(); val locked = member(p, user(UserState.LOCKED))
        assertEquals(ReviewRequestResult.NOT_CANDIDATE, service.request(pr, author, actor), "작성자는 요청 대상이 아니다")
        assertEquals(ReviewRequestResult.NOT_CANDIDATE, service.request(pr, outsider, actor), "비공개 프로젝트를 읽을 수 없다")
        assertEquals(ReviewRequestResult.NOT_CANDIDATE, service.request(pr, locked, actor), "잠긴 사용자")
        assertTrue(pr.reviewRequests.isEmpty()); assertTrue(events(pr).isEmpty()); assertTrue(notes().isEmpty())
    }

    @Test
    fun `공개 프로젝트는 구성원이 아니어도 읽을 수 있는 사용자를 요청할 수 있다`() {
        val p = project(ProjectScope.PUBLIC); val actor = member(p); val outsider = user(); val pr = pullRequest(p, member(p))
        assertEquals(ReviewRequestResult.CHANGED, service.request(pr, outsider, actor))
    }

    @Test
    fun `닫히거나 병합된 풀 리퀘스트에는 요청하지 못한다`() {
        val p = project(); val actor = member(p); val reviewer = member(p)
        assertEquals(ReviewRequestResult.NOT_OPEN, service.request(pullRequest(p, member(p), State.CLOSED), reviewer, actor))
        assertEquals(ReviewRequestResult.NOT_OPEN, service.request(pullRequest(p, member(p), State.MERGED), reviewer, actor))
    }

    @Test
    fun `요청을 취소하면 대기 목록에서 빠지고 타임라인에 남기며 없는 요청은 아무 일도 없다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val pr = pullRequest(p, member(p))
        assertEquals(ReviewRequestResult.UNCHANGED, service.cancel(pr, reviewer, actor))
        service.request(pr, reviewer, actor)
        assertEquals(ReviewRequestResult.CHANGED, service.cancel(pr, reviewer, actor))
        assertTrue(pr.reviewRequests.isEmpty())
        val removed = events(pr, EventType.PULL_REQUEST_REVIEW_UNREQUESTED).single()
        assertEquals(reviewer.loginId, removed.oldValue); assertEquals(null, removed.newValue)
    }

    @Test
    fun `요청받은 사람만 알림을 받고 요청한 본인과 작성자는 받지 않는다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val author = member(p); val pr = pullRequest(p, author)
        service.request(pr, reviewer, actor)
        val note = notes().single()
        assertEquals(setOf(reviewer.loginId), note.receivers.map { it.loginId }.toSet())
        assertEquals(reviewer.loginId, note.newValue)
        // 자기 자신에게 요청하면 알림이 없다
        service.request(pr, actor, actor)
        assertTrue(notes().all { n -> actor.loginId !in n.receivers.map { it.loginId } })
    }

    @Test
    fun `취소는 알림을 보내지 않는다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val pr = pullRequest(p, member(p))
        service.request(pr, reviewer, actor)
        val before = notifications.findAll().size
        service.cancel(pr, reviewer, actor)
        assertEquals(before, notifications.findAll().size)
    }

    @Test
    fun `요청받은 사람이 리뷰를 제출하면 요청이 말없이 지워진다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val other = member(p); val pr = pullRequest(p, member(p))
        service.request(pr, reviewer, actor); service.request(pr, other, actor)
        reviewService.submit(pr, reviewer, ReviewVerdict.APPROVED, null)
        assertEquals(setOf(other.id), pr.reviewRequests.map { it.id }.toSet())
        assertTrue(events(pr, EventType.PULL_REQUEST_REVIEW_UNREQUESTED).isEmpty(), "리뷰 이벤트가 이미 타임라인에 있으므로 취소 이벤트를 남기지 않는다")
    }

    @Test
    fun `이미 리뷰한 사람에게 다시 요청할 수 있다`() {
        val p = project(); val actor = member(p); val reviewer = member(p); val pr = pullRequest(p, member(p))
        reviewService.submit(pr, reviewer, ReviewVerdict.CHANGES_REQUESTED, "고쳐 주세요")
        assertEquals(ReviewRequestResult.CHANGED, service.request(pr, reviewer, actor))
        assertEquals(setOf(reviewer.id), pr.reviewRequests.map { it.id }.toSet())
    }

    @Test
    fun `후보는 이름이나 로그인 ID로 찾고 작성자와 이미 요청된 사람을 뺀 읽을 수 있는 활성 사용자 최대 20명이다`() {
        val p = project(); val author = member(p); val pr = pullRequest(p, author)
        val named = (1..25).map { member(p, users.save(User(loginId = "zzcand$it", name = "후보 $it", email = "zzcand$it@example.com", state = UserState.ACTIVE))) }
        val locked = user(UserState.LOCKED).also { it.loginId = "zzcand-locked"; users.save(it) }
        val result = service.candidates(pr, "zzcand", author)
        assertEquals(20, result.size)
        assertTrue(result.none { it.id == author.id || it.id == locked.id })
        assertEquals(1, service.candidates(pr, "후보 25", author).size)
        assertTrue(service.candidates(pr, "", author).isEmpty(), "빈 검색어는 목록을 보이지 않는다")
        assertTrue(named.isNotEmpty())
    }
}
