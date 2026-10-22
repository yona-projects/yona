package dev.yona.domain.notification

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 프로젝트와 조직의 가입 신청, 취소, 수락이 알림 이벤트로 바뀌는 규칙을 검증한다.
 *
 * 원본: `app/models/NotificationEvent.java:1226-1286`(`afterMemberRequest`, `afterOrganizationMemberRequest`).
 */
@Transactional
class NotificationMemberRequestTest : AbstractIntegrationTest() {

    @Autowired private lateinit var publisher: ApplicationEventPublisher
    @Autowired private lateinit var eventRepository: NotificationEventRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var messages: org.springframework.context.MessageSource

    /** 요청 언어로 만든 메시지이다. 테스트 스레드에는 요청이 없어 서버 기본 언어를 쓴다. */
    private fun message(key: String, vararg args: Any) = messages.getMessage(key, args, org.springframework.context.i18n.LocaleContextHolder.getLocale())

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mr$n", name = "mr$n", email = "mr$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(manager: User, watching: Boolean): Project {
        val p = projects.save(Project(owner = "mro${++sequence}", name = "mrp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        projectUsers.save(ProjectUser(user = manager, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        if (watching) watches.save(Watch(user = manager, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        return p
    }

    private fun only(): NotificationEvent { entityManager.flush(); return eventRepository.findAll().single() }

    @Test
    fun `프로젝트 가입 신청은 프로젝트를 구독 중인 관리자가 받는다`() {
        val manager = user(); val applicant = user(); val p = project(manager, true)
        project(user(), false)
        publisher.publishEvent(ProjectMemberRequestEvent(p, applicant, RequestState.REQUEST, applicant))
        val e = only()
        assertEquals(EventType.MEMBER_ENROLL_REQUEST, e.eventType); assertEquals(ResourceType.PROJECT, e.resourceType); assertEquals("REQUEST", e.newValue); assertEquals("CANCEL", e.oldValue)
        assertEquals(message("notification.member.request.title", p.name!!, applicant.loginId!!), e.title); assertEquals(setOf(manager.loginId), e.receivers.map { it.loginId }.toSet())
    }

    @Test
    fun `구독하지 않은 관리자에게는 알리지 않고 취소는 신청과 합쳐 사라진다`() {
        val manager = user(); val applicant = user(); val p = project(manager, false)
        publisher.publishEvent(ProjectMemberRequestEvent(p, applicant, RequestState.REQUEST, applicant))
        assertEquals(0, eventRepository.count())
        val watching = project(manager, true)
        publisher.publishEvent(ProjectMemberRequestEvent(watching, applicant, RequestState.REQUEST, applicant))
        publisher.publishEvent(ProjectMemberRequestEvent(watching, applicant, RequestState.CANCEL, applicant))
        entityManager.flush()
        assertEquals(0, eventRepository.count())
    }

    @Test
    fun `수락은 신청한 사용자가 받고 수락한 관리자는 받지 않는다`() {
        val manager = user(); val applicant = user(); val p = project(manager, true)
        publisher.publishEvent(ProjectMemberRequestEvent(p, applicant, RequestState.ACCEPT, manager))
        val e = only()
        assertEquals(EventType.MEMBER_ENROLL_ACCEPT, e.eventType); assertEquals("ACCEPT", e.newValue); assertEquals("REQUEST", e.oldValue)
        assertEquals(message("notification.member.request.accept.title", p.name!!, applicant.loginId!!), e.title); assertEquals(setOf(applicant.loginId), e.receivers.map { it.loginId }.toSet())
    }

    @Test
    fun `조직 가입 신청은 조직 관리자 모두가 받는다`() {
        val admin = user(); val other = user(); val applicant = user()
        val org = organizations.save(Organization(name = "mrorg${++sequence}", descr = "d", created = Date()))
        organizationUsers.save(OrganizationUser(user = admin, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get()))
        organizationUsers.save(OrganizationUser(user = other, organization = org, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        publisher.publishEvent(OrganizationMemberRequestEvent(org, applicant, RequestState.REQUEST, applicant))
        val e = only()
        assertEquals(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, e.eventType); assertEquals(ResourceType.ORGANIZATION, e.resourceType)
        assertEquals(message("notification.organization.member.request.title", org.name!!, applicant.loginId!!), e.title); assertEquals(setOf(admin.loginId), e.receivers.map { it.loginId }.toSet())
        publisher.publishEvent(OrganizationMemberRequestEvent(org, applicant, RequestState.ACCEPT, admin))
        entityManager.flush()
        val accepted = eventRepository.findAll().single { it.eventType == EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT }
        assertEquals(setOf(applicant.loginId), accepted.receivers.map { it.loginId }.toSet()); assertNull(null)
    }
}
