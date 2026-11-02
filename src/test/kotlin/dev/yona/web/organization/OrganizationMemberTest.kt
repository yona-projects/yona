package dev.yona.web.organization

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.OrganizationMemberRequestEvent
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 구성원 관리, 탈퇴, 삭제를 검증한다.
 *
 * 원본: `app/controllers/OrganizationApp.java:126-295, 313-336, 432-465`, `app/views/organization/members.scala.html`,
 * `deleteForm.scala.html`, `app/models/OrganizationUser.java`, `app/models/Organization.java:76-100`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class OrganizationMemberTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun newUser(name: String? = null, guest: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "om$n", name = name ?: "om$n", email = "om$n@example.com", state = UserState.ACTIVE, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newOrg(): Organization = organizations.save(Organization(name = "morg${++sequence}", descr = "설명", created = Date()))

    private fun join(org: Organization, user: User, role: RoleType): User {
        organizationUsers.save(OrganizationUser(user = user, organization = org, role = roles.findById(role.id).get()))
        return user
    }

    private fun roleOf(org: Organization, user: User) = organizationUsers.findFirstByOrganizationIdAndUserId(org.id!!, user.id!!)?.role?.id

    private fun members(org: Organization, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/organizations/${org.name}/members").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun locationOf(org: Organization) = "/organizations/${org.name}/members"

    // --- 목록 ---

    @Test
    fun `구성원 화면은 조직 관리자만 볼 수 있다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        mockMvc.perform(get("/organizations/${org.name}/members").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/nothing/members").session(login(newUser()))).andExpect(status().isNotFound)
        members(org, login(join(org, newUser(), RoleType.ORG_ADMIN)))
    }

    @Test
    fun `구성원 화면의 구성`() {
        val org = newOrg()
        val admin = join(org, newUser("가가"), RoleType.ORG_ADMIN)
        val member = join(org, newUser("나나"), RoleType.ORG_MEMBER)
        val page = members(org, login(admin))
        assertEquals(org.name, page.title())
        val form = page.selectFirst("form#addNewMember")!!
        assertEquals("/organizations/${org.name}/members", form.attr("action"))
        assertTrue(form.selectFirst("input#loginId[required]") != null)
        assertEquals("Add new member ID.", form.selectFirst("input#loginId")?.attr("placeholder"))
        val rows = page.select("ul.members li.member")
        assertEquals(listOf("가가", "나나"), rows.map { it.selectFirst(".member-name")!!.text() })
        val row = rows[1]
        assertEquals("@${member.loginId}", row.selectFirst(".member-id")?.text())
        assertEquals("roleof-${member.loginId}", row.selectFirst(".btn-group")?.attr("data-name"))
        assertEquals("Group Member", row.selectFirst(".d-label")?.text())
        assertEquals(listOf("Group Manager", "Group Member"), row.select(".dropdown-menu li").map { it.text() })
        assertEquals("/organizations/${org.name}/member/${member.id}/edit", row.selectFirst(".dropdown-menu a")?.attr("data-href"))
        assertEquals("/organizations/${org.name}/member/${member.id}/delete", row.selectFirst("a[data-action=delete]")?.attr("data-href"))
        assertEquals("Are you sure this user should leave this group?", page.selectFirst("#alertDeletion .modal-body p")?.text())
        assertTrue(page.select("script").any { "organization.Member" in it.data() && "/users" in it.data().replace("\\/", "/") })
    }

    @Test
    fun `가입 신청 목록을 보여 준다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val applicant = newUser("신청자")
        applicant.enrolledOrganizations.add(org)
        users.save(applicant)
        val page = members(org, login(admin))
        assertEquals("Sign-up request (1)", page.selectFirst("legend h3")?.text())
        assertEquals(applicant.loginId, page.selectFirst("button.enrollAcceptBtn")?.attr("data-loginId"))
    }

    @Test
    fun `가입 신청이 없으면 신청 목록이 없다`() {
        val org = newOrg()
        assertNull(members(org, login(join(org, newUser(), RoleType.ORG_ADMIN))).selectFirst("legend"))
    }

    // --- 추가 ---

    private fun addMember(org: Organization, session: MockHttpSession?, loginId: String?): ResultActions =
        mockMvc.perform(post("/organizations/${org.name}/members").apply { session?.let { session(it) } }.apply { loginId?.let { param("loginId", it) } })

    @Test
    fun `조직 관리자는 사용자를 구성원으로 추가한다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val target = newUser()
        addMember(org, login(admin), target.loginId)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", locationOf(org)))
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, target))
        assertEquals(listOf(RequestState.ACCEPT), events.stream(OrganizationMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `추가하면 그 사용자의 가입 신청이 정리된다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val target = newUser()
        target.enrolledOrganizations.add(org)
        users.save(target)
        addMember(org, login(admin), target.loginId)
        assertFalse(users.hasEnrolledOrganization(target.id!!, org.id!!))
    }

    private fun assertWarning(result: ResultActions, key: String) {
        result.andExpect(status().isSeeOther).andExpect(flash().attribute("warning", key))
    }

    @Test
    fun `없는 사용자, 게스트, 이미 구성원인 사용자는 안내와 함께 거부한다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val session = login(admin)
        assertWarning(addMember(org, session, "nobody-here"), "organization.member.unknownUser")
        assertWarning(addMember(org, session, null), "organization.member.unknownUser")
        assertWarning(addMember(org, session, newUser(guest = true).loginId), "error.forbidden.to.guest.user")
        assertWarning(addMember(org, session, admin.loginId), "organization.member.alreadyMember")
    }

    @Test
    fun `조직 관리자가 아니면 추가할 수 없다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val target = newUser()
        assertWarning(addMember(org, login(member), target.loginId), "organization.member.needManagerRole")
        assertNull(roleOf(org, target))
    }

    // --- 역할 변경 ---

    private fun editMember(org: Organization, session: MockHttpSession?, userId: Long?, roleId: String?): ResultActions =
        mockMvc.perform(
            post("/organizations/${org.name}/member/$userId/edit").apply { session?.let { session(it) } }.apply { roleId?.let { param("id", it) } }
        )

    @Test
    fun `조직 관리자는 구성원의 역할을 바꾼다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        editMember(org, login(admin), member.id, RoleType.ORG_ADMIN.id.toString()).andExpect(status().isNoContent)
        assertEquals(RoleType.ORG_ADMIN.id, roleOf(org, member))
    }

    @Test
    fun `마지막 관리자를 멤버로 낮출 수 없다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        editMember(org, login(admin), admin.id, RoleType.ORG_MEMBER.id.toString())
            .andExpect(status().isOk).andExpect(jsonPath("$.location").value(locationOf(org)))
            .andExpect(flash().attribute("warning", "organization.member.atLeastOneAdmin"))
        assertEquals(RoleType.ORG_ADMIN.id, roleOf(org, admin))
    }

    @Test
    fun `역할 변경의 거부 이유는 안내와 함께 구성원 화면 주소로 알린다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val outsider = newUser()
        val session = login(admin)
        editMember(org, session, member.id, "abc").andExpect(jsonPath("$.location").value(locationOf(org))).andExpect(flash().attribute("warning", "organization.member.unknownRole"))
        editMember(org, session, outsider.id, RoleType.ORG_ADMIN.id.toString()).andExpect(flash().attribute("warning", "organization.member.isNotAMember"))
        editMember(org, login(member), member.id, RoleType.ORG_ADMIN.id.toString()).andExpect(flash().attribute("warning", "organization.member.needManagerRole"))
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, member))
    }

    @Test
    fun `없는 역할 번호는 아무것도 바꾸지 않고 성공이다 (v1_6 assignRole)`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        editMember(org, login(admin), member.id, "99999").andExpect(status().isNoContent)
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, member))
    }

    @Test
    fun `존재하는 역할이면 조직 역할이 아니어도 받는다 (v1_6은 종류를 가리지 않는다)`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        editMember(org, login(admin), member.id, RoleType.GUEST.id.toString()).andExpect(status().isNoContent)
        assertEquals(RoleType.GUEST.id, roleOf(org, member))
    }

    @Test
    fun `역할 번호가 없으면 500이다 (v1_6 널 참조)`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        editMember(org, login(admin), member.id, null).andExpect(status().isInternalServerError)
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, member))
    }

    // --- 삭제 ---

    private fun removeMember(org: Organization, session: MockHttpSession?, userId: Long?): ResultActions =
        mockMvc.perform(delete("/organizations/${org.name}/member/$userId/delete").apply { session?.let { session(it) } })

    @Test
    fun `조직 관리자는 구성원을 내보내고 구성원 화면 주소를 받는다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        removeMember(org, login(admin), member.id).andExpect(status().isOk).andExpect(jsonPath("$.location").value(locationOf(org)))
        assertNull(roleOf(org, member))
    }

    @Test
    fun `스스로 나가면 조직 화면 주소를 받는다`() {
        val org = newOrg()
        join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        removeMember(org, login(member), member.id).andExpect(jsonPath("$.location").value("/organizations/${org.name}"))
        assertNull(roleOf(org, member))
    }

    @Test
    fun `삭제의 거부 이유는 안내와 함께 구성원 화면 주소로 알린다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        val other = join(org, newUser(), RoleType.ORG_MEMBER)
        removeMember(org, login(member), other.id).andExpect(jsonPath("$.location").value(locationOf(org))).andExpect(flash().attribute("warning", "organization.member.needManagerRole"))
        removeMember(org, login(admin), newUser().id).andExpect(flash().attribute("warning", "organization.member.isNotAMember"))
        removeMember(org, login(admin), admin.id).andExpect(flash().attribute("warning", "organization.member.atLeastOneAdmin"))
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, other))
        assertEquals(RoleType.ORG_ADMIN.id, roleOf(org, admin))
    }

    @Test
    fun `없는 조직의 구성원 삭제는 404이다`() {
        mockMvc.perform(delete("/organizations/nothing/member/1/delete").session(login(newUser()))).andExpect(status().isNotFound)
    }

    // --- 탈퇴 ---

    private fun leave(org: Organization, session: MockHttpSession?): ResultActions =
        mockMvc.perform(delete("/organizations/${org.name}/member/leave").apply { session?.let { session(it) } })

    @Test
    fun `일반 구성원은 관리자가 둘 이상일 때 탈퇴한다`() {
        val org = newOrg()
        join(org, newUser(), RoleType.ORG_ADMIN)
        join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        leave(org, login(member)).andExpect(status().isOk).andExpect(jsonPath("$.location").value("/organizations/${org.name}"))
        assertNull(roleOf(org, member))
    }

    @Test
    fun `일반 구성원은 관리자가 한 명뿐이면 탈퇴할 수 없다 (v1 6 규칙)`() {
        val org = newOrg()
        join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        leave(org, login(member)).andExpect(status().isForbidden).andExpect(jsonPath("$.errorMsg").value("organization.member.atLeastOneAdmin"))
        assertEquals(RoleType.ORG_MEMBER.id, roleOf(org, member))
    }

    @Test
    fun `관리자는 한 명뿐이어도 탈퇴할 수 있다 (v1 6 결함 재현)`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        leave(org, login(admin)).andExpect(status().isOk)
        assertNull(roleOf(org, admin))
    }

    @Test
    fun `탈퇴는 로그인해야 하고 없는 조직은 404 JSON이다`() {
        val org = newOrg()
        leave(org, null).andExpect(status().isSeeOther)
        mockMvc.perform(delete("/organizations/nothing/member/leave").session(login(newUser())))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.errorMsg").value("organization.member.unknownOrganization"))
    }

    // --- 조직 삭제 ---

    private fun deleteOrg(org: Organization, session: MockHttpSession?): ResultActions =
        mockMvc.perform(delete("/organizations/${org.name}").apply { session?.let { session(it) } })

    @Test
    fun `삭제 화면은 조직 관리자만 볼 수 있다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        mockMvc.perform(get("/organizations/${org.name}/deleteForm").session(login(member))).andExpect(status().isForbidden)
        val page = Jsoup.parse(mockMvc.perform(get("/organizations/${org.name}/deleteForm").session(login(admin))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Delete This Group", page.selectFirst("a#btnDelete")?.text()?.trim())
        assertEquals("Do you want to delete this group?", page.selectFirst("#alertDeletion h3")?.text())
        assertEquals("Are you sure you want to delete this group?", page.selectFirst("#alertDeletion .modal-body p")?.text())
        assertTrue(page.select(".project-page-wrap > ul.nav-tabs li")[5].selectFirst("a")!!.hasClass("active"))
        assertTrue(page.select("script").any { "/organizations/${org.name}" in it.data().replace("\\/", "/") })
    }

    @Test
    fun `프로젝트가 없는 조직을 지운다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        join(org, newUser(), RoleType.ORG_MEMBER)
        deleteOrg(org, login(admin)).andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        organizations.flush()
        assertFalse(organizations.findById(org.id!!).isPresent)
        assertEquals(0, organizationUsers.findMembers(org.id!!).size)
    }

    @Test
    fun `가입 신청이 남은 조직도 지운다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        val applicant = newUser()
        applicant.enrolledOrganizations.add(org)
        users.save(applicant)
        deleteOrg(org, login(admin)).andExpect(status().isSeeOther)
        organizations.flush()
        assertFalse(organizations.findById(org.id!!).isPresent)
    }

    @Test
    fun `프로젝트가 있으면 지울 수 없다`() {
        val org = newOrg()
        val admin = join(org, newUser(), RoleType.ORG_ADMIN)
        projects.save(Project(owner = org.name, name = "keep", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        deleteOrg(org, login(admin)).andExpect(status().isNotFound).andExpect(jsonPath("$.errorMsg").value("organization.delete.impossible.project.exist"))
        assertNotNull(organizations.findById(org.id!!).orElse(null))
    }

    @Test
    fun `삭제 권한이 없거나 없는 조직은 404 JSON이다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        deleteOrg(org, login(member)).andExpect(status().isNotFound).andExpect(jsonPath("$.errorMsg").value("organization.member.needManagerRole"))
        mockMvc.perform(delete("/organizations/nothing").session(login(newUser()))).andExpect(status().isNotFound).andExpect(jsonPath("$.errorMsg").value("organization.member.unknownOrganization"))
    }

    @Test
    fun `사이트 관리자는 조직 관리자가 아니어도 지울 수 있다`() {
        val org = newOrg()
        val site = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }
        deleteOrg(org, login(site)).andExpect(status().isSeeOther)
    }

    @Test
    fun `구성원과 삭제 화면의 403은 조직 머리말과 메뉴가 있는 화면이다`() {
        val org = newOrg()
        val member = join(org, newUser(), RoleType.ORG_MEMBER)
        listOf("members", "deleteForm").forEach { path ->
            val doc = Jsoup.parse(mockMvc.perform(get("/organizations/${org.name}/$path").session(login(member))).andExpect(status().isForbidden).andReturn().response.contentAsString)
            assertEquals(org.name, doc.selectFirst(".project-header-wrap .project-author a")!!.text(), path)
            assertTrue(doc.select(".project-menu-nav").isNotEmpty(), path)
            assertTrue(doc.select(".page-wrap-outer .error-wrap p").isNotEmpty(), path)
        }
    }
}
