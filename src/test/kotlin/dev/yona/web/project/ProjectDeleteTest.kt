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
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/deleteform").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `삭제 화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/deleteform").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(get("/${project.owner}/${project.name}/deleteform")).andExpect(status().isForbidden)
    }

    @Test
    fun `삭제 화면의 구성`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertEquals("Delete project - ${project.owner}/${project.name}", page.title())
        assertEquals("Delete project", page.selectFirst(".cu-label")?.text())
        assertTrue(page.selectFirst(".cu-desc .notice")!!.text().startsWith("Once you delete the project"))
        assertNotNull(page.selectFirst("input#accept[type=checkbox]"))
        assertEquals("I agree to delete this project.", page.selectFirst("label[for=accept]")?.text())
        val button = page.selectFirst("a#btnDelete")!!
        assertEquals("#alertDeletion", button.attr("href"))
        assertEquals("modal", button.attr("data-bs-toggle"))
        assertEquals("Delete this project", button.text().trim())
        val dialog = page.selectFirst("#alertDeletion")!!
        assertEquals("Do you want to delete this project?", dialog.selectFirst(".modal-header h3")?.text())
        assertEquals("Are you sure you want to delete this project?", dialog.select(".modal-body p")[1].text())
        assertEquals("Yes", dialog.selectFirst("#btnDeleteExec")?.text())
    }

    @Test
    fun `삭제 화면은 설정 하위 메뉴에서 삭제를 활성으로 보여 준다`() {
        val project = newProject()
        val page = deleteForm(project, login(manager(project)))
        assertTrue(page.selectFirst("#subMenuProjectDelete")!!.selectFirst("a")!!.hasClass("active"))
        assertFalse(page.selectFirst("#subMenuProjectSetting")!!.selectFirst("a")!!.hasClass("active"))
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
