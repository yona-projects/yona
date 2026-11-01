package dev.yona.domain.audit

import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.OrganizationManagementService
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.site.SiteUserService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 조직과 사이트 관리 이벤트(로드맵 X-9 A5, `decisions.md` D-62)를 검증한다: 조직 만들기와 지우기와 이름 바꾸기, 조직 구성원과 역할, 사이트 관리자 권한,
 * 사용자 상태(잠금, 삭제), 게스트 모드. 조직 이벤트는 소속 조직 번호를 담아 조직 소유자가 자기 조직의 기록만 볼 수 있게 한다.
 */
@Transactional
class AuditOrganizationSiteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizationService: OrganizationService
    @Autowired private lateinit var management: OrganizationManagementService
    @Autowired private lateinit var siteUsers: SiteUserService
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ao$n-$stamp", name = "조직$n", email = "ao$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), createdDate = Date()))
    }

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    // --- 조직 ---

    @Test
    fun `조직을 만들면 조직 번호를 소속으로 남긴다`() {
        val creator = user()
        val org = organizationService.create("org$stamp", "설명", creator)
        val e = eventsOf("org.created").single()
        assertEquals("organization", e.targetType); assertEquals(org.id.toString(), e.targetId); assertEquals("org$stamp", e.targetLabel); assertEquals(org.id, e.organizationId)
    }

    @Test
    fun `조직 구성원을 추가하고 역할을 바꾸고 내보내는 일을 남긴다`() {
        val admin = user(); val member = user()
        val org = organizationService.create("om$stamp", null, admin)
        assertNull(management.addMember(org, admin, member.loginId))
        val added = eventsOf("org.member_added").single()
        assertEquals(member.id.toString(), added.targetId); assertEquals(org.id, added.organizationId)
        assertNull(management.editMember(org, admin, member.id!!, RoleType.ORG_ADMIN.id.toString()))
        assertTrue(eventsOf("org.member_role_changed").single().details!!.contains("\"to\":\"ORG_ADMIN\""))
        assertNull(management.removeMember(org, admin, member.id!!))
        assertEquals(member.id.toString(), eventsOf("org.member_removed").single().targetId)
    }

    @Test
    fun `거부된 조직 구성원 변경은 남기지 않는다`() {
        val admin = user(); val outsider = user()
        val org = organizationService.create("od$stamp", null, admin)
        management.addMember(org, outsider, "nobody-$stamp")
        management.removeMember(org, outsider, admin.id!!)
        assertTrue(logs.findAll().none { it.action.startsWith("org.member_") })
    }

    @Test
    fun `조직 이름과 설명을 바꾸면 전후를 남긴다`() {
        val admin = user(); val org = organizationService.create("oldorg$stamp", "옛 설명", admin)
        management.update(org, "oldorg$stamp", "옛 설명", null, null, null)
        assertTrue(eventsOf("org.settings_changed").isEmpty(), "바뀐 것이 없으면 남기지 않는다")
        management.update(org, "neworg$stamp", "새 설명", null, null, null)
        val d = eventsOf("org.settings_changed").single().details!!
        assertTrue(d.contains("\"name\":{\"from\":\"oldorg$stamp\",\"to\":\"neworg$stamp\"}") && d.contains("\"descr\""), d)
    }

    @Test
    fun `조직을 지우면 남긴다`() {
        val admin = user(); val org = organizationService.create("odel$stamp", null, admin); val id = org.id
        management.delete(org)
        val e = eventsOf("org.deleted").single()
        assertEquals("odel$stamp", e.targetLabel); assertEquals(id, e.organizationId)
    }

    // --- 사이트 관리 ---

    @Test
    fun `사이트 관리자 권한을 주고 거두는 일을 남긴다`() {
        val u = user()
        siteUsers.toggleSiteAdmin(u)
        assertEquals(u.id.toString(), eventsOf("site.admin_granted").single().targetId)
        siteUsers.toggleSiteAdmin(u)
        assertEquals(u.id.toString(), eventsOf("site.admin_revoked").single().targetId)
    }

    @Test
    fun `사용자 상태를 바꾸면 전후를 남기고 같은 상태이면 남기지 않는다`() {
        val u = user()
        siteUsers.changeState(u, UserState.ACTIVE)
        assertTrue(eventsOf("site.user_state_changed").isEmpty())
        siteUsers.changeState(u, UserState.LOCKED)
        assertTrue(eventsOf("site.user_state_changed").single().details!!.contains("\"from\":\"ACTIVE\"") && eventsOf("site.user_state_changed").single().details!!.contains("\"to\":\"LOCKED\""))
    }

    @Test
    fun `사용자를 지우면 남기고 지운 사용자의 이름과 이메일은 남기지 않는다`() {
        val u = user(); val login = u.loginId; val mail = u.email!!
        siteUsers.deleteUser(u)
        val e = eventsOf("site.user_deleted").single()
        assertEquals(login, e.targetLabel)
        assertTrue(logs.findAll().none { (it.details ?: "").contains(mail) })
    }

    @Test
    fun `게스트 모드를 켜고 끄는 일을 남긴다`() {
        val u = user()
        siteUsers.toggleGuest(u)
        assertTrue(eventsOf("site.guest_mode_changed").single().details!!.contains("\"guest\":true"))
        siteUsers.toggleGuest(u)
        assertEquals(2, eventsOf("site.guest_mode_changed").size)
    }
}
