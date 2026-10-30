package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.ProjectTransferRequestedEvent
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectTransfer
import dev.yona.domain.project.ProjectTransferRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 이전을 검증한다. 화면(`GET transfer`), 요청(`PUT transfer`), 수락(`GET /project/transfer/:id/:key`).
 *
 * 원본: `app/controllers/ProjectApp.java:596-690`, `app/views/project/transfer.scala.html`, `app/models/ProjectTransfer.java`,
 * `app/models/Project.java:786-799`.
 */
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-transfer"])
class ProjectTransferTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var transfers: ProjectTransferRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-project-transfer").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "tr$n", name = "tr$n", email = "tr$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(owner: String? = null, name: String? = null): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = owner ?: "tro$n", name = name ?: "tp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun manager(project: Project): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun newOrganization(admin: User? = null): Organization {
        val org = organizations.save(Organization(name = "trorg${++sequence}"))
        admin?.let { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get())) }
        return org
    }

    private fun page(project: Project, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/transfer").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun request(project: Project, session: MockHttpSession?, owner: String?, xhr: Boolean = false) =
        mockMvc.perform(
            put("/${project.owner}/${project.name}/transfer").apply { session?.let { session(it) } }
                .apply { owner?.let { param("owner", it) } }
                .apply { if (xhr) header("X-Requested-With", "XMLHttpRequest") }
        )

    // --- 화면 ---

    @Test
    fun `이전 화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/transfer").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/transfer")).andExpect(status().isForbidden)
    }

    @Test
    fun `이전 화면의 구성`() {
        val project = newProject()
        val page = page(project, login(manager(project)))
        assertEquals("Project Transfer - ${project.owner}/${project.name}", page.title())
        assertNotNull(page.selectFirst("input#owner[name=owner]"))
        assertEquals(5, page.select("li.notice").size)
        assertNotNull(page.selectFirst("input#accept[type=checkbox]"))
        assertEquals("I agree with the transfer of this project.", page.selectFirst("label[for=accept]")?.text())
        assertEquals("Transfer this project", page.selectFirst("a#btnTransfer")?.text()?.trim())
        assertEquals("Yes", page.selectFirst("#alertTransfer #btnTransferExec")?.text())
        assertTrue(page.selectFirst("#subMenuProjectTransfer")!!.selectFirst("a")!!.hasClass("active"))
        val script = page.select("script").map { it.data().replace("\\/", "/") }.first { "project.Transfer" in it }
        assertTrue("/${project.owner}/${project.name}/transfer" in script, script)
    }

    // --- 요청 ---

    @Test
    fun `사용자에게 이전을 요청하면 요청을 저장하고 이벤트를 알린다`() {
        val project = newProject()
        val sender = manager(project)
        val dest = newUser()
        request(project, login(sender), dest.loginId)
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}"))
            .andExpect(flash().attribute("info", "project.transfer.is.requested"))
        val saved = transfers.findAll().single()
        assertEquals(project.id, saved.project?.id)
        assertEquals(sender.id, saved.sender?.id)
        assertEquals(dest.loginId, saved.destination)
        assertEquals(project.name, saved.newProjectName)
        assertEquals(50, saved.confirmKey!!.length)
        assertTrue(saved.confirmKey!!.all { it.isLetterOrDigit() })
        assertEquals(false, saved.accepted)
        assertEquals(1, events.stream(ProjectTransferRequestedEvent::class.java).count())
    }

    @Test
    fun `조직에게 요청하면 조직 이름이 대상이 된다`() {
        val project = newProject()
        val org = newOrganization()
        request(project, login(manager(project)), org.name).andExpect(status().isSeeOther)
        assertEquals(org.name, transfers.findAll().single().destination)
    }

    @Test
    fun `받는 곳에 같은 이름의 프로젝트가 있으면 새 이름에 번호를 붙인다`() {
        val project = newProject()
        val dest = newUser()
        newProject(owner = dest.loginId, name = project.name)
        newProject(owner = dest.loginId, name = "${project.name}-1")
        request(project, login(manager(project)), dest.loginId)
        assertEquals("${project.name}-2", transfers.findAll().single().newProjectName)
    }

    @Test
    fun `같은 요청을 다시 보내면 새로 만들지 않고 열쇠와 시각을 갱신한다`() {
        val project = newProject()
        val sender = manager(project)
        val dest = newUser()
        val session = login(sender)
        request(project, session, dest.loginId)
        val first = transfers.findAll().single()
        val firstKey = first.confirmKey
        request(project, session, dest.loginId)
        val again = transfers.findAll().single()
        assertEquals(first.id, again.id)
        assertTrue(again.confirmKey != firstKey)
    }

    @Test
    fun `Ajax 요청이면 204와 프로젝트 주소를 돌려준다`() {
        val project = newProject()
        val dest = newUser()
        request(project, login(manager(project)), dest.loginId, xhr = true)
            .andExpect(status().isNoContent)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}"))
    }

    @Test
    fun `대상이 사용자도 조직도 아니면 400이다`() {
        val project = newProject()
        request(project, login(manager(project)), "nobody-here").andExpect(status().isBadRequest)
        request(project, login(manager(project)), null).andExpect(status().isBadRequest)
        assertTrue(transfers.findAll().isEmpty())
    }

    @Test
    fun `이미 그 사용자의 프로젝트이면 안내와 함께 화면을 다시 보여 준다`() {
        val owner = newUser()
        val project = newProject(owner = owner.loginId)
        projectUsers.save(ProjectUser(user = owner, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        val body = request(project, login(owner), owner.loginId).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("This project is already owned by the user or group."))
        assertTrue(transfers.findAll().isEmpty())
    }

    @Test
    fun `이미 그 조직의 프로젝트이면 거부한다`() {
        val org = newOrganization()
        val project = projects.save(Project(owner = org.name, name = "orgp${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, organization = org))
        menuSettings.save(ProjectMenuSetting(project = project, code = true))
        val admin = newUser().also { organizationUsers.save(OrganizationUser(user = it, organization = org, role = roles.findById(RoleType.ORG_ADMIN.id).get())) }
        request(project, login(admin), org.name).andExpect(status().isOk)
        assertTrue(transfers.findAll().isEmpty())
    }

    @Test
    fun `이전 요청은 관리자만 할 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        request(project, login(member), newUser().loginId).andExpect(status().isForbidden)
        assertTrue(transfers.findAll().isEmpty())
    }

    // --- 수락 ---

    private fun requested(project: Project, sender: User, destination: String, requested: Date = Date(), accepted: Boolean = false): ProjectTransfer =
        transfers.save(
            ProjectTransfer(sender = sender, destination = destination, project = project, requested = requested, confirmKey = "key123", accepted = accepted, newProjectName = project.name)
        )

    private fun accept(transfer: ProjectTransfer, session: MockHttpSession?, key: String = "key123") =
        mockMvc.perform(get("/project/transfer/${transfer.id}/$key").apply { session?.let { session(it) } })

    private fun reload(project: Project): Project {
        projects.flush()
        return projects.findById(project.id!!).get()
    }

    @Test
    fun `받는 사용자가 수락하면 소유자와 이름이 바뀌고 저장소가 옮겨진다`() {
        val project = newProject()
        val oldName = project.name!!
        val oldOwner = project.owner!!
        val sender = manager(project)
        val dest = newUser()
        val transfer = requested(project, sender, dest.loginId!!)
        accept(transfer, login(dest))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/${dest.loginId}/$oldName"))
        val saved = reload(project)
        assertEquals(dest.loginId, saved.owner)
        assertEquals(oldName, saved.name)
        assertNull(saved.organization)
        assertEquals(oldOwner, saved.previousOwnerLoginId)
        assertEquals(oldName, saved.previousName)
        assertTrue(File("build/tmp/it-project-transfer/repo/git/${dest.loginId}/$oldName.git").isDirectory)
        assertFalse(File("build/tmp/it-project-transfer/repo/git/$oldOwner/$oldName.git").exists())
    }

    @Test
    fun `수락하면 받는 사용자는 관리자가 되고 보낸 관리자는 멤버가 된다`() {
        val project = newProject()
        val sender = manager(project)
        val dest = newUser()
        accept(requested(project, sender, dest.loginId!!), login(dest))
        assertEquals(RoleType.MANAGER.id, projectUsers.findFirstByUserIdAndProjectId(dest.id!!, project.id!!)?.role?.id)
        assertEquals(RoleType.MEMBER.id, projectUsers.findFirstByUserIdAndProjectId(sender.id!!, project.id!!)?.role?.id)
    }

    @Test
    fun `받는 곳에 같은 이름이 있으면 번호가 붙은 이름이 된다`() {
        val project = newProject()
        val oldName = project.name!!
        val dest = newUser()
        newProject(owner = dest.loginId, name = oldName)
        accept(requested(project, manager(project), dest.loginId!!), login(dest))
        assertEquals("$oldName-1", reload(project).name)
    }

    @Test
    fun `조직 관리자가 수락하면 프로젝트가 조직에 속하고 아무도 관리자가 되지 않는다`() {
        val project = newProject()
        val sender = manager(project)
        val admin = newUser()
        val org = newOrganization(admin)
        accept(requested(project, sender, org.name!!), login(admin)).andExpect(status().isSeeOther)
        val saved = reload(project)
        assertEquals(org.name, saved.owner)
        assertEquals(org.id, saved.organization?.id)
        assertEquals(RoleType.MEMBER.id, projectUsers.findFirstByUserIdAndProjectId(sender.id!!, project.id!!)?.role?.id)
        assertNull(projectUsers.findFirstByUserIdAndProjectId(admin.id!!, project.id!!))
    }

    @Test
    fun `수락한 요청은 다시 쓸 수 없다`() {
        val project = newProject()
        val dest = newUser()
        val transfer = requested(project, manager(project), dest.loginId!!)
        accept(transfer, login(dest))
        accept(transfer, login(dest)).andExpect(status().isNotFound)
    }

    @Test
    fun `하루가 지난 요청은 수락할 수 없다`() {
        val project = newProject()
        val dest = newUser()
        val old = requested(project, manager(project), dest.loginId!!, requested = Date(System.currentTimeMillis() - 25L * 3_600_000))
        accept(old, login(dest)).andExpect(status().isNotFound)
    }

    @Test
    fun `열쇠가 다르면 400이다`() {
        val project = newProject()
        val dest = newUser()
        accept(requested(project, manager(project), dest.loginId!!), login(dest), key = "wrong").andExpect(status().isBadRequest)
    }

    @Test
    fun `받는 사람이 아니면 수락할 수 없다`() {
        val project = newProject()
        val dest = newUser()
        val transfer = requested(project, manager(project), dest.loginId!!)
        accept(transfer, login(newUser())).andExpect(status().isForbidden)
        assertEquals(project.owner, reload(project).owner)
    }

    @Test
    fun `수락은 로그인해야 한다`() {
        val project = newProject()
        val dest = newUser()
        accept(requested(project, manager(project), dest.loginId!!), null).andExpect(status().isSeeOther)
    }
}
