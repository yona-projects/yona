package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
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
 * 풀 리퀘스트의 담당자 지정을 검증한다(로드맵 X-14, `decisions.md` D-34): 1명까지, 후보 규칙(v1.6 이슈 담당자 후보), 타임라인 이벤트,
 * 알림 수신자(새 담당자, 이전 담당자, 작성자, 관찰자에서 본인을 뺌).
 */
@Transactional
class PullRequestAssigneeServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: PullRequestAssigneeService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var eventRepository: PullRequestEventRepository
    @Autowired private lateinit var notifications: NotificationEventRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "asg${++sequence}", name = "asg$sequence", email = "asg$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = Project(owner = "asgo${++sequence}", name = "asgp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC).also { em.persist(it) }

    private fun member(project: Project, user: User = user()): User = user.also { em.persist(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }

    private fun pullRequest(to: Project, contributor: User) =
        PullRequest(toProject = to, fromProject = to, number = (++sequence).toLong(), title = "요청", contributor = contributor, state = State.OPEN, toBranch = "refs/heads/master", fromBranch = "refs/heads/topic").also { em.persist(it) }

    private fun events(pr: PullRequest) = eventRepository.findByPullRequestIdOrderByCreatedAscIdAsc(pr.id!!).filter { it.eventType == EventType.PULL_REQUEST_ASSIGNEE_CHANGED }

    private fun notes() = notifications.findAll().filter { it.eventType == EventType.PULL_REQUEST_ASSIGNEE_CHANGED }

    @Test
    fun `후보인 구성원을 담당자로 지정하면 저장하고 타임라인에 남긴다`() {
        val p = project(); val actor = member(p); val target = member(p); val pr = pullRequest(p, member(p))
        assertEquals(AssignResult.CHANGED, service.set(pr, target, actor))
        assertEquals(setOf(target.id), pr.assignees.map { it.id }.toSet())
        val event = events(pr).single()
        assertEquals(actor.loginId, event.senderLoginId); assertEquals(null, event.oldValue); assertEquals(target.loginId, event.newValue)
    }

    @Test
    fun `담당자는 1명까지라 다시 지정하면 바뀌고 이전 담당자는 이벤트의 이전 값이다`() {
        val p = project(); val actor = member(p); val first = member(p); val second = member(p); val pr = pullRequest(p, member(p))
        service.set(pr, first, actor)
        assertEquals(AssignResult.CHANGED, service.set(pr, second, actor))
        assertEquals(setOf(second.id), pr.assignees.map { it.id }.toSet())
        val last = events(pr).last()
        assertEquals(first.loginId, last.oldValue); assertEquals(second.loginId, last.newValue)
        assertEquals(1, PullRequestAssigneeService.MAX_ASSIGNEES)
    }

    @Test
    fun `담당자를 비우면 해제되고 이벤트의 새 값이 비며 같은 사람을 다시 지정해도 아무 일도 없다`() {
        val p = project(); val actor = member(p); val target = member(p); val pr = pullRequest(p, member(p))
        service.set(pr, target, actor)
        assertEquals(AssignResult.UNCHANGED, service.set(pr, target, actor))
        assertEquals(1, events(pr).size, "같은 담당자는 이벤트를 더하지 않는다")
        assertEquals(AssignResult.CHANGED, service.set(pr, null, actor))
        assertTrue(pr.assignees.isEmpty())
        assertEquals(target.loginId, events(pr).last().oldValue); assertEquals(null, events(pr).last().newValue)
        assertEquals(AssignResult.UNCHANGED, service.set(pr, null, actor))
    }

    @Test
    fun `담당자 후보가 아닌 사람은 지정할 수 없다`() {
        val p = project(); val actor = member(p); val outsider = user(); val pr = pullRequest(p, member(p))
        assertEquals(AssignResult.NOT_CANDIDATE, service.set(pr, outsider, actor))
        assertTrue(pr.assignees.isEmpty()); assertTrue(events(pr).isEmpty()); assertTrue(notes().isEmpty())
    }

    @Test
    fun `새 담당자와 작성자가 알림을 받고 바꾼 본인은 받지 않는다`() {
        val p = project(); val actor = member(p); val target = member(p); val author = member(p); val pr = pullRequest(p, author)
        service.set(pr, target, actor)
        val receivers = notes().single().receivers.map { it.loginId }.toSet()
        assertEquals(setOf(target.loginId, author.loginId), receivers)
    }

    @Test
    fun `이전 담당자도 알림을 받는다 (바꾼 본인이면 제외)`() {
        val p = project(); val actor = member(p); val first = member(p); val second = member(p); val author = member(p); val pr = pullRequest(p, author)
        service.set(pr, first, actor)
        service.set(pr, second, actor)
        assertEquals(setOf(first.loginId, second.loginId, author.loginId), notes().last().receivers.map { it.loginId }.toSet())
        // 이전 담당자가 스스로 다른 사람으로 바꾸면 본인에게는 가지 않는다
        service.set(pr, first, second)
        assertTrue(second.loginId !in notes().last().receivers.map { it.loginId })
    }

    @Test
    fun `알림 이벤트에 이전 값과 새 값이 담긴다`() {
        val p = project(); val actor = member(p); val target = member(p); val pr = pullRequest(p, member(p))
        service.set(pr, target, actor)
        val note = notes().single()
        assertEquals(null, note.oldValue); assertEquals(target.loginId, note.newValue)
    }
}
