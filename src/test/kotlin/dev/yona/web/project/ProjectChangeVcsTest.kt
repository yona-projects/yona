package dev.yona.web.project

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.TitleHeadRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 저장소 종류 변경을 검증한다. 화면(`GET changeVCS`)과 변경(`POST changeVCS`).
 *
 * 원본: `app/controllers/ProjectApp.java:714-740`, `app/views/project/change_vcs.scala.html`, `app/models/Project.java:658-670, 891-897`.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-vcs"])
class ProjectChangeVcsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var postings: dev.yona.domain.board.PostingRepository
    @Autowired private lateinit var titleHeads: TitleHeadRepository
    @Autowired private lateinit var mentionRepository: dev.yona.domain.notification.MentionRepository

    private var sequence = 0

    @AfterEach
    fun clean() {
        File("build/tmp/it-project-vcs").deleteRecursively()
    }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "cv$n", name = "cv$n", email = "cv$n@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(vcs: String = "GIT", original: Project? = null): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "cvo$n", name = "cp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC, originalProject = original))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(project)
        return project
    }

    private fun manager(project: Project): User = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }

    private fun page(project: Project, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/changeVCS").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/changeVCS").session(login(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `Git 프로젝트의 화면은 Subversion으로 바꾸는 안내를 보여 준다`() {
        val project = newProject()
        val page = page(project, login(manager(project)))
        assertEquals("Repository Change - ${project.owner}/${project.name}", page.title())
        assertEquals("GIT Subversion", page.selectFirst("h3")?.text())
        assertEquals("Changing the repository to Subversion.", page.select("li.notice")[0].text())
        assertEquals("If the repository is changed, all code and history will be deleted.", page.select("li.notice")[1].text())
        assertEquals("I agree with changing the repository type.", page.selectFirst("label[for=acceptChangeVCS]")?.text())
        assertNotNull(page.selectFirst("a#btnChangeVCS"))
        assertEquals("Do you want to change the repository to Subversion?", page.selectFirst("#alertChangeVCS h3")?.text())
        assertEquals("Yes", page.selectFirst("#btnChangeVCSExec")?.text())
        assertTrue(page.selectFirst("#subMenuProjectChangeVCS")!!.hasClass("active"))
        val script = page.select("script").map { it.data().replace("\\/", "/") }.first { "project.ChangeVCS" in it }
        assertTrue("/${project.owner}/${project.name}/changeVCS" in script, script)
    }

    @Test
    fun `Subversion 프로젝트의 화면은 Git으로 바꾸는 안내를 보여 준다`() {
        val project = newProject("Subversion")
        assertEquals("Changing the repository to GIT.", page(project, login(manager(project))).select("li.notice")[0].text())
    }

    @Test
    fun `Subversion 프로젝트를 Git으로 바꾸면 Git 저장소를 만든다`() {
        val project = newProject("Subversion")
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project))))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}"))
        projects.flush()
        assertEquals("GIT", projects.findById(project.id!!).get().vcs)
        assertTrue(File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git/HEAD").isFile)
    }

    @Test
    fun `README 글의 표시를 풀 때 v1_6 posting_save처럼 제목 머리말을 더하고 멘션을 맞춘다`() {
        val project = newProject()
        dev.yona.support.GitTestSupport.pushCommits(repositories, project, dev.yona.support.GitTestSupport.TestCommit("readme", mapOf("README.md" to "# hi".toByteArray())))
        val mentioned = newUser()
        val posting = postings.save(dev.yona.domain.board.Posting(project = project, number = 1, title = "[문서] 소개", body = "연락 @${mentioned.loginId}", readme = true, createdDate = java.util.Date()))
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project)))).andExpect(status().isNoContent)
        projects.flush()
        assertEquals(false, postings.findById(posting.id!!).get().readme)
        assertEquals(1, titleHeads.findAll().single { it.project?.id == project.id && it.headKeyword == "문서" }.frequency)
        assertEquals(listOf(mentioned.loginId), mentionRepository.findAll().filter { it.resourceType == dev.yona.domain.enumeration.ResourceType.BOARD_POST && it.resourceId == posting.id.toString() }.mapNotNull { it.user?.loginId })
    }

    @Test
    fun `바꾸면 포크의 원본 연결이 끊긴다`() {
        val project = newProject("Subversion")
        val fork = newProject(original = project)
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project)))).andExpect(status().isNoContent)
        projects.flush()
        assertNull(projects.findById(fork.id!!).get().originalProject)
    }

    @Test
    fun `Git 프로젝트를 Subversion으로 바꾸면 Git 저장소를 지우고 Subversion 저장소를 만든다`() {
        val project = newProject()
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project))))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}"))
        projects.flush()
        assertEquals("Subversion", projects.findById(project.id!!).get().vcs)
        assertTrue(File("build/tmp/it-project-vcs/repo/svn/${project.owner}/${project.name}/format").isFile)
        assertTrue(!File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git").exists())
    }

    @Test
    fun `변경은 관리자만 할 수 있다`() {
        val project = newProject("Subversion")
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS")).andExpect(status().isForbidden)
    }
}
