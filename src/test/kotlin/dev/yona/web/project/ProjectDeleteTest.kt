package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.FavoriteProject
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 프로젝트 삭제 화면(`GET deleteform`)과 삭제(`DELETE delete`)를 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:331-356`, `app/views/project/delete.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-delete"])
class ProjectDeleteTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var favorites: FavoriteProjectRepository
    @Autowired private lateinit var attachments: dev.yona.domain.misc.AttachmentRepository

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-project-delete").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "dl$n", name = "dl$n", email = "dl$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "dlo$n", name = "dp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        return project
    }

    private fun manager(project: Project): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun deleteForm(project: Project, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/setting/danger").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `삭제 화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/setting/danger").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/setting/danger")).andExpect(status().isForbidden)
    }

    @Test
    fun `삭제 화면의 구성`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertEquals("Danger Zone - ${project.owner}/${project.name}", page.title())
        assertEquals("Delete project", page.selectFirst("#danger-delete .cu-label")?.text())
        assertTrue(page.selectFirst("#danger-delete .cu-desc .notice")!!.text().startsWith("Once you delete the project"))
        val button = page.selectFirst("a#btnDelete")!!
        assertEquals("#alertDeletion", button.attr("href"))
        assertEquals("modal", button.attr("data-bs-toggle"), "확인 창이 곧 확인 절차라 단추가 바로 연다(체크박스 없음, A-537)")
        assertEquals("Delete this project", button.text().trim())
        val dialog = page.selectFirst("#alertDeletion")!!
        assertEquals("Do you want to delete this project?", dialog.selectFirst(".modal-header h3")?.text())
        assertEquals(1, dialog.select(".modal-body p").count { !it.hasClass("mb-0") }, "설명 문단 하나와 입력 안내만 둔다")
        assertEquals("Delete this project", dialog.selectFirst("#btnDeleteExec")?.text())
    }

    @Test
    fun `위험 구역의 세 확인 창은 모두 owner와 name을 다시 입력받고 입력 전에는 예 단추가 비활성이다(A-536, A-538)`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        val full = "${project.owner}/${project.name}"
        // GitHub처럼 실행 단추 하나만 둔다(아니요 단추 없음, 닫기는 ×, A-540)
        listOf("alertTransfer", "alertChangeVCS", "alertDeletion").forEach { assertEquals(1, page.select("#$it .modal-footer button").size, "$it 푸터 단추 수") }
        // 입력이 곧 확인이라 "정말로 ...하시겠습니까?" 같은 재확인 문구는 없다(A-539)
        listOf("alertTransfer", "alertChangeVCS", "alertDeletion").forEach { assertFalse(page.selectFirst("#$it .modal-body")!!.text().contains("Are you sure"), it) }
        listOf(Triple("alertTransfer", "transferConfirmName", "btnTransferExec"), Triple("alertChangeVCS", "vcsConfirmName", "btnChangeVCSExec"), Triple("alertDeletion", "deleteConfirmName", "btnDeleteExec")).forEach { (modal, input, exec) ->
            val box = page.selectFirst("#$modal input#$input")!!
            assertEquals("off", box.attr("autocomplete"), modal)
            assertTrue(page.selectFirst("#$modal label[for=$input]")!!.text().contains(full), "$modal 안내에 $full")
            assertTrue(page.selectFirst("#$modal #$exec")!!.hasAttr("disabled"), "$modal 예 단추")
        }
        // JS 인라인은 슬래시를 `\/`로 내보낸다
        val scripts = page.select("script").joinToString("\n") { it.data().replace("\\/", "/") }
        assertEquals(3, Regex("\"sProjectName\": \"${Regex.escape(full)}\"").findAll(scripts).count(), "세 모듈에 프로젝트 이름을 넘긴다")
    }

    @Test
    fun `위험 구역은 이관, 저장소 타입 변경, 삭제 구역이 이 순서로 한 화면에 있고 구역마다 따로 동의와 단추를 가진다(A-535)`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertEquals(listOf("danger-transfer", "danger-vcs", "danger-delete"), page.select("section.danger-section").map { it.id() })
        assertTrue(page.select("section.danger-section input[type=checkbox]").isEmpty(), "동의 체크박스는 없다(A-537)")
        assertEquals(listOf("modal", "modal", "modal"), page.select("section.danger-section a.ybtn-danger").map { it.attr("data-bs-toggle") })
        assertEquals(listOf("btnTransfer", "btnChangeVCS", "btnDelete"), page.select("section.danger-section a.ybtn-danger").map { it.id() })
        assertEquals(3, page.select("section.danger-section").map { it.id() }.toSet().size)
    }

    @Test
    fun `위험 구역 맨 위 안내는 동의가 아니라 프로젝트 이름 입력을 말한다(A-542)`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertEquals("These actions are hard to undo. To run one, you must type the project name.", page.selectFirst(".shell-main > p.form-text")?.text())
    }

    @Test
    fun `옛 이관, 삭제, 타입 변경 주소는 위험 구역으로 이동시키고 구성원은 막는다(A-535)`() {
        val project = newProject(); val session = login(manager(project)); val base = "/${project.owner}/${project.name}"
        listOf("/transfer", "/deleteform", "/changeVCS").forEach { old ->
            val r = mockMvc.perform(get(base + old).session(session)).andReturn().response
            assertTrue(r.status in 301..303, "$old 상태 ${r.status}")
            assertTrue(r.getHeader("Location")!!.endsWith("$base/setting/danger"), "$old 이동 주소 ${r.getHeader("Location")}")
        }
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        listOf("/transfer", "/deleteform", "/changeVCS").forEach { old -> mockMvc.perform(get(base + old).session(login(member))).andExpect(status().isForbidden) }
    }

    @Test
    fun `삭제 화면은 설정 하위 메뉴에서 삭제를 활성으로 보여 준다`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertTrue(page.selectFirst("nav.shell-sidebar a.nav-link[href\$=/setting/danger]")!!.hasClass("active"))
        assertFalse(page.selectFirst("nav.shell-sidebar a.nav-link[href\$=/settingform]")!!.hasClass("active"))
    }

    @Test
    fun `삭제 스크립트는 삭제 주소를 가진다`() {
        val project = newProject()
        val script = deleteForm(project, login(manager(project))).select("script").map { it.data().replace("\\/", "/") }.first { "project.Delete" in it }
        assertTrue("\"sDeleteURL\"" in script && "/${project.owner}/${project.name}/delete" in script, script)
    }

    @Test
    fun `Ajax로 삭제하면 204와 첫 화면 주소를 돌려주고 프로젝트와 저장소를 지운다`() {
        val project = newProject()
        val manager = manager(project)
        val repositoryDir = File("build/tmp/it-project-delete/repo/git/${project.owner}/${project.name}.git")
        assertTrue(repositoryDir.isDirectory)
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete").session(login(manager)).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Location", "/"))
        projects.flush()
        assertFalse(projects.findById(project.id!!).isPresent)
        assertFalse(repositoryDir.exists())
    }

    @Test
    fun `일반 요청으로 삭제하면 첫 화면으로 보낸다`() {
        val project = newProject()
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete").session(login(manager(project))))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/"))
    }

    @Test
    fun `프로젝트 자체의 첨부(로고)도 지운다 (R-18, v1_6은 고아 데이터로 남겼다)`() {
        val project = newProject()
        val manager = manager(project)
        val logo = attachments.save(dev.yona.domain.misc.Attachment(name = "logo.png", hash = "h-r18-${System.nanoTime()}", containerType = dev.yona.domain.enumeration.ResourceType.PROJECT, containerId = project.id!!, mimeType = "image/png", size = 1L, createdDate = java.util.Date(), ownerLoginId = manager.loginId))
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete").session(login(manager)))
        attachments.flush()
        assertFalse(attachments.findById(logo.id!!).isPresent)
    }

    @Test
    fun `삭제하는 사용자의 즐겨찾기도 정리한다`() {
        val project = newProject()
        val manager = manager(project)
        favorites.save(FavoriteProject(user = manager, project = project, owner = project.owner, projectName = project.name))
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete").session(login(manager)))
        favorites.flush()
        assertTrue(favorites.findAll().isEmpty())
    }

    @Test
    fun `관리자가 아니면 삭제할 수 없다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(delete("/${project.owner}/${project.name}/delete")).andExpect(status().isForbidden)
        assertTrue(projects.findById(project.id!!).isPresent)
    }
}
