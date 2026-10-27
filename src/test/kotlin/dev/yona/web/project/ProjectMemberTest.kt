package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.notification.ProjectMemberRequestEvent
import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 구성원 관리를 검증한다. 목록(`GET members`), 추가(`POST members`), 역할 변경(`POST member/:id/edit`),
 * 삭제 또는 탈퇴(`DELETE member/:id/delete`).
 *
 * 원본: `app/controllers/ProjectApp.java:358-366, 937-1052`, `app/views/project/members.scala.html`,
 * `app/models/ProjectUser.java`, `app/models/Project.java:642-656`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
class ProjectMemberTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun newUser(name: String? = null, lang: String? = null, guest: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "pm$n", name = name ?: "pm$n", email = "pm$n@example.com", state = UserState.ACTIVE, lang = lang, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, owner: String? = null): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = owner ?: "mo$n", name = "mp$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun membersPage(project: Project, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/members").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun roleOf(user: User, project: Project) = projectUsers.findFirstByUserIdAndProjectId(user.id!!, project.id!!)?.role?.id

    // --- 목록 ---

    @Test
    fun `구성원 화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        mockMvc.perform(get("/${project.owner}/${project.name}/members").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/members")).andExpect(status().isForbidden)
        membersPage(project, login(join(newUser(), project, RoleType.MANAGER)))
    }

    @Test
    fun `제목과 추가 폼`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val page = membersPage(project, login(manager))
        assertEquals("Member list - ${project.owner}/${project.name}", page.title())
        val form = page.selectFirst("form#addNewMember")!!
        assertEquals("/${project.owner}/${project.name}/members", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        val input = form.selectFirst("input#loginId[name=loginId]")!!
        assertTrue(input.hasAttr("required"))
        assertEquals("Add new member ID.", input.attr("placeholder"))
        assertEquals("^[a-zA-Z0-9-]+([_.][a-zA-Z0-9-]+)*$", input.attr("pattern"))
        assertEquals("Enter Valid ID", input.attr("title"))
        assertEquals("Add", form.selectFirst("button")?.text()?.trim())
    }

    @Test
    fun `구성원은 이름순이고 사이트 관리자는 빠진다`() {
        val project = newProject()
        join(users.findById(1L).get(), project, RoleType.SITEMANAGER)
        val beta = join(newUser("나나"), project, RoleType.MEMBER)
        val alpha = join(newUser("가가"), project, RoleType.MANAGER)
        val page = membersPage(project, login(alpha))
        assertEquals(listOf("가가", "나나"), page.select("ul.members li.member .member-name").map { it.text() })
        assertEquals(listOf("@${alpha.loginId}", "@${beta.loginId}"), page.select("ul.members li.member .member-id").map { it.text() })
        assertEquals("/${alpha.loginId}", page.selectFirst("ul.members li.member a.avatar-wrap")?.attr("href"))
    }

    @Test
    fun `게스트 사용자에게는 게스트 표시가 붙는다`() {
        val project = newProject()
        val guest = join(newUser(guest = true), project, RoleType.MEMBER)
        val manager = join(newUser(), project, RoleType.MANAGER)
        val page = membersPage(project, login(manager))
        assertEquals(1, page.select("ul.members .guest").size)
        assertEquals("GUEST", page.selectFirst("ul.members .guest")?.text())
        assertNotNull(guest.id)
    }

    @Test
    fun `소유자가 아닌 구성원에게는 역할 선택과 삭제 버튼이 있다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val member = join(newUser(), project, RoleType.MEMBER)
        val row = membersPage(project, login(manager)).select("ul.members li.member").first { it.selectFirst(".member-id")!!.text() == "@${member.loginId}" }
        assertEquals("roleof-${member.loginId}", row.selectFirst(".btn-group")?.attr("data-name"))
        assertEquals("Member", row.selectFirst(".d-label")?.text())
        val items = row.select(".dropdown-menu li")
        assertEquals(listOf("Manager", "Member"), items.map { it.text() })
        assertEquals(listOf(RoleType.MANAGER.id.toString(), RoleType.MEMBER.id.toString()), items.map { it.attr("data-value") })
        assertEquals("true", items[1].attr("data-selected"))
        assertTrue(items[1].hasClass("active"))
        val apply = items[0].selectFirst("a")!!
        assertEquals("apply", apply.attr("data-action"))
        assertEquals("/${project.owner}/${project.name}/member/${member.id}/edit", apply.attr("data-href"))
        assertEquals(member.loginId, apply.attr("data-loginId"))
        val remove = row.selectFirst("a[data-action=delete]")!!
        assertEquals("/${project.owner}/${project.name}/member/${member.id}/delete", remove.attr("data-href"))
        assertEquals("Delete", remove.text().trim())
    }

    @Test
    fun `소유자에게는 역할 선택 대신 소유자 표시가 있다`() {
        val owner = newUser()
        val project = newProject(owner = owner.loginId!!)
        join(owner, project, RoleType.MANAGER)
        val row = membersPage(project, login(owner)).selectFirst("ul.members li.member")!!
        assertEquals("Project owner", row.selectFirst("span.label.owner")?.text())
        assertNull(row.selectFirst(".btn-group"))
        assertNull(row.selectFirst("a[data-action=delete]"))
    }

    @Test
    fun `가입 신청 목록을 보여 준다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val applicant = newUser("신청자")
        applicant.enrolledProjects.add(project)
        users.save(applicant)
        val page = membersPage(project, login(manager))
        assertEquals("Sign-up request (1)", page.selectFirst("legend h3")?.text())
        assertEquals("신청자", page.selectFirst(".span2 strong")?.text())
        assertEquals(applicant.loginId, page.selectFirst("button.enrollAcceptBtn")?.attr("data-loginId"))
    }

    @Test
    fun `가입 신청이 없으면 신청 목록이 없다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        assertNull(membersPage(project, login(manager)).selectFirst("legend"))
    }

    @Test
    fun `이미 구성원인 사람의 가입 신청은 화면을 열 때 정리하고 승인 알림을 보낸다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val already = join(newUser(), project, RoleType.MEMBER)
        already.enrolledProjects.add(project)
        users.save(already)
        val page = membersPage(project, login(manager))
        assertNull(page.selectFirst("legend"))
        assertFalse(users.hasEnrolled(already.id!!, project.id!!))
        val accepted = events.stream(ProjectMemberRequestEvent::class.java).toList()
        assertEquals(listOf(RequestState.ACCEPT), accepted.map { it.state })
        assertEquals(already.id, accepted.single().user.id)
    }

    @Test
    fun `스크립트는 구성원 모듈과 사용자 검색 주소를 가진다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val script = membersPage(project, login(manager)).select("script").map { it.data().replace("\\/", "/") }.first { "project.Member" in it }
        assertTrue("\"sActionURL\"" in script && "/-_-api/v1/users" in script, script)
    }

    // --- 추가 ---

    private fun addMember(project: Project, session: MockHttpSession?, loginId: String?, json: Boolean = false) =
        mockMvc.perform(
            post("/${project.owner}/${project.name}/members").apply { session?.let { session(it) } }
                .apply { loginId?.let { param("loginId", it) } }
                .apply { if (json) accept(MediaType.APPLICATION_JSON) }
        )

    @Test
    fun `관리자는 사용자를 구성원으로 추가하고 구독시킨다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val newcomer = newUser()
        addMember(project, login(manager), newcomer.loginId)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}/members"))
        assertEquals(RoleType.MEMBER.id, roleOf(newcomer, project))
        assertTrue(watches.existsByUserIdAndResourceTypeAndResourceId(newcomer.id!!, ResourceType.PROJECT, project.id.toString()))
        assertEquals(listOf(RequestState.ACCEPT), events.stream(ProjectMemberRequestEvent::class.java).map { it.state }.toList())
    }

    @Test
    fun `추가하면 그 사용자의 가입 신청이 정리된다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val applicant = newUser()
        applicant.enrolledProjects.add(project)
        users.save(applicant)
        addMember(project, login(manager), applicant.loginId)
        assertFalse(users.hasEnrolled(applicant.id!!, project.id!!))
    }

    @Test
    fun `JSON을 원하면 빈 객체로 답하고 구독은 만들지 않는다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val newcomer = newUser()
        addMember(project, login(manager), newcomer.loginId, json = true)
            .andExpect(status().isOk)
            .andExpect(content().string("{}"))
        assertEquals(RoleType.MEMBER.id, roleOf(newcomer, project))
        assertFalse(watches.existsByUserIdAndResourceTypeAndResourceId(newcomer.id!!, ResourceType.PROJECT, project.id.toString()))
    }

    @Test
    fun `없는 사용자를 추가하면 안내와 함께 구성원 화면으로 돌아간다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        addMember(project, login(manager), "nobody-here")
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}/members"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("warning", "project.member.notExist"))
    }

    @Test
    fun `이미 구성원인 사용자를 추가하면 안내한다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val member = join(newUser(), project, RoleType.MEMBER)
        addMember(project, login(manager), member.loginId)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("warning", "project.member.alreadyMember"))
    }

    @Test
    fun `사이트 관리자 행도 구성원으로 센다 (v1_6 isMemberOf는 역할을 가리지 않는다)`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val admin = join(newUser(), project, RoleType.SITEMANAGER)
        addMember(project, login(manager), admin.loginId)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("warning", "project.member.alreadyMember"))
    }

    @Test
    fun `JSON을 원하면 오류를 400과 JSON으로 알린다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        addMember(project, login(manager), "nobody-here", json = true)
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.loginId[0]").value("User does not exist."))
    }

    @Test
    fun `로그인 ID를 보내지 않으면 없는 사용자로 본다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        addMember(project, login(manager), null)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("warning", "project.member.notExist"))
    }

    @Test
    fun `추가는 관리자만 할 수 있다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        val target = newUser()
        addMember(project, login(member), target.loginId).andExpect(status().isForbidden)
        assertNull(roleOf(target, project))
    }

    // --- 역할 변경 ---

    private fun editMember(project: Project, session: MockHttpSession?, userId: Long?, roleId: Long?) =
        mockMvc.perform(
            post("/${project.owner}/${project.name}/member/$userId/edit").apply { session?.let { session(it) } }
                .apply { roleId?.let { param("id", it.toString()) } }
        )

    @Test
    fun `관리자는 구성원의 역할을 바꾼다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val member = join(newUser(), project, RoleType.MEMBER)
        editMember(project, login(manager), member.id, RoleType.MANAGER.id).andExpect(status().isNoContent)
        assertEquals(RoleType.MANAGER.id, roleOf(member, project))
    }

    @Test
    fun `프로젝트 역할이 아닌 번호는 거부한다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val member = join(newUser(), project, RoleType.MEMBER)
        editMember(project, login(manager), member.id, RoleType.SITEMANAGER.id).andExpect(status().isBadRequest)
        editMember(project, login(manager), member.id, null).andExpect(status().isBadRequest)
        assertEquals(RoleType.MEMBER.id, roleOf(member, project))
    }

    @Test
    fun `소유자의 역할은 바꿀 수 없다`() {
        val owner = newUser()
        val project = newProject(owner = owner.loginId!!)
        join(owner, project, RoleType.MANAGER)
        val manager = join(newUser(), project, RoleType.MANAGER)
        editMember(project, login(manager), owner.id, RoleType.MEMBER.id)
            .andExpect(status().isBadRequest)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Project owner must have a manager-level authority.")))
        assertEquals(RoleType.MANAGER.id, roleOf(owner, project))
    }

    @Test
    fun `역할 변경은 관리자만 할 수 있다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        val other = join(newUser(), project, RoleType.MEMBER)
        editMember(project, login(member), other.id, RoleType.MANAGER.id).andExpect(status().isForbidden)
        assertEquals(RoleType.MEMBER.id, roleOf(other, project))
    }

    // --- 삭제와 탈퇴 ---

    private fun removeMember(project: Project, session: MockHttpSession?, userId: Long?) =
        mockMvc.perform(delete("/${project.owner}/${project.name}/member/$userId/delete").apply { session?.let { session(it) } })

    @Test
    fun `관리자는 구성원을 내보내고 구성원 화면 주소를 받는다`() {
        val project = newProject()
        val manager = join(newUser(), project, RoleType.MANAGER)
        val member = join(newUser(), project, RoleType.MEMBER)
        removeMember(project, login(manager), member.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.location").value("/${project.owner}/${project.name}/members"))
        assertNull(roleOf(member, project))
    }

    @Test
    fun `구성원은 스스로 탈퇴하고 프로젝트를 읽을 수 있으면 프로젝트 홈 주소를 받는다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        removeMember(project, login(member), member.id)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.location").value("/${project.owner}/${project.name}"))
        assertNull(roleOf(member, project))
    }

    @Test
    fun `비공개 프로젝트를 탈퇴하면 첫 화면 주소를 받는다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val member = join(newUser(), project, RoleType.MEMBER)
        removeMember(project, login(member), member.id)
            .andExpect(jsonPath("$.location").value("/"))
    }

    @Test
    fun `소유자는 탈퇴할 수 없다`() {
        val owner = newUser()
        val project = newProject(owner = owner.loginId!!)
        join(owner, project, RoleType.MANAGER)
        removeMember(project, login(owner), owner.id)
            .andExpect(status().isForbidden)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Project owner cannot leave his own project.")))
        assertEquals(RoleType.MANAGER.id, roleOf(owner, project))
    }

    @Test
    fun `관리자라도 소유자를 내보낼 수 없다`() {
        val owner = newUser()
        val project = newProject(owner = owner.loginId!!)
        join(owner, project, RoleType.MANAGER)
        val manager = join(newUser(), project, RoleType.MANAGER)
        removeMember(project, login(manager), owner.id).andExpect(status().isForbidden)
        assertEquals(RoleType.MANAGER.id, roleOf(owner, project))
    }

    @Test
    fun `관리자가 아닌 구성원은 다른 사람을 내보낼 수 없다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        val other = join(newUser(), project, RoleType.MEMBER)
        removeMember(project, login(member), other.id).andExpect(status().isForbidden)
        assertEquals(RoleType.MEMBER.id, roleOf(other, project))
    }

    @Test
    fun `익명은 내보낼 수 없다`() {
        val project = newProject()
        val member = join(newUser(), project, RoleType.MEMBER)
        removeMember(project, null, member.id).andExpect(status().isForbidden)
        assertEquals(RoleType.MEMBER.id, roleOf(member, project))
    }
}
