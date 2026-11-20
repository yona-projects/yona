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
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.UnexpectedRollbackException
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.util.UUID
import kotlin.test.assertFailsWith
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
    @Autowired private lateinit var jdbc: JdbcTemplate

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
        Jsoup.parse(mockMvc.perform(get("/${project.owner}/${project.name}/setting/danger").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `화면은 관리자만 볼 수 있다`() {
        val project = newProject()
        val member = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        mockMvc.perform(get("/${project.owner}/${project.name}/setting/danger").session(login(member))).andExpect(status().isForbidden)
    }

    @Test
    fun `Git 프로젝트의 화면은 Subversion으로 바꾸는 안내를 보여 준다`() {
        val project = newProject()
        val page = page(project, login(manager(project)))
        assertEquals("Danger Zone - ${project.owner}/${project.name}", page.title())
        assertEquals("Repository Type Change", page.selectFirst("#danger-vcs h3.danger-title")?.text())
        assertEquals("Changing the repository to Subversion.", page.select("#danger-vcs li.notice")[0].text())
        assertEquals("If the repository is changed, all code and history will be deleted.", page.select("#danger-vcs li.notice")[1].text())
        assertNull(page.selectFirst("#acceptChangeVCS"), "동의 체크박스는 없다(A-537)")
        assertNotNull(page.selectFirst("a#btnChangeVCS"))
        assertEquals("Do you want to change the repository to Subversion?", page.selectFirst("#alertChangeVCS h3")?.text())
        assertEquals("Change Repository Type.", page.selectFirst("#btnChangeVCSExec")?.text())
        assertTrue(page.selectFirst("nav.shell-sidebar a.nav-link[href\$=/setting/danger]")!!.hasClass("active"))
        val script = page.select("script").map { it.data().replace("\\/", "/") }.first { "project.ChangeVCS" in it }
        assertTrue("/${project.owner}/${project.name}/changeVCS" in script, script)
    }

    @Test
    fun `코드 메뉴를 끈 프로젝트의 위험 구역에는 저장소 종류 구역이 없고 이관과 삭제만 있다(A-535)`() {
        val project = newProject()
        menuSettings.findAll().filter { it.project?.id == project.id }.forEach { menuSettings.save(it.apply { code = false }) }
        val page = page(project, login(manager(project)))
        assertEquals(listOf("danger-transfer", "danger-delete"), page.select("section.danger-section").map { it.id() })
        assertNull(page.selectFirst("#alertChangeVCS"), "종류 변경 확인 창도 없다")
        assertTrue(page.select("script").none { "project.ChangeVCS" in it.data() && "loadModule(\"project.ChangeVCS\"" in it.data() }, "종류 변경 스크립트를 초기화하지 않는다")
    }

    @Test
    fun `Subversion 프로젝트의 화면은 Git으로 바꾸는 안내를 보여 준다`() {
        val project = newProject("Subversion")
        assertEquals("Changing the repository to GIT.", page(project, login(manager(project))).select("#danger-vcs li.notice")[0].text())
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
    fun `대상을 정해 요청하면 그 종류로 바꾸고 지금 종류나 모르는 종류는 400이며 아무것도 바꾸지 않는다`() {
        val project = newProject("Subversion")
        val session = login(manager(project))
        val url = "/${project.owner}/${project.name}/changeVCS"
        mockMvc.perform(post(url).session(session).param("targetVcs", "Subversion")).andExpect(status().isBadRequest)
        mockMvc.perform(post(url).session(session).param("targetVcs", "Bazaar")).andExpect(status().isBadRequest)
        mockMvc.perform(post(url).session(session).param("targetVcs", "")).andExpect(status().isBadRequest)
        projects.flush()
        assertEquals("Subversion", projects.findById(project.id!!).get().vcs)
        mockMvc.perform(post(url).session(session).param("targetVcs", "GIT")).andExpect(status().isNoContent)
        projects.flush()
        assertEquals("GIT", projects.findById(project.id!!).get().vcs)
    }

    @Test
    fun `저장소 종류 전부를 라디오로 보여 주고 지금 종류는 현재 표시와 함께 비활성이며 기본은 v1_6처럼 번갈아 바꾸는 종류이다`() {
        val git = newProject()
        val gitPage = page(git, login(manager(git)))
        assertEquals(listOf("GIT", "Subversion", "Mercurial"), gitPage.select("#danger-vcs input[name=targetVcs]").map { it.attr("value") })
        assertEquals(listOf("GIT"), gitPage.select("#danger-vcs input[name=targetVcs][disabled]").map { it.attr("value") })
        assertEquals("Current", gitPage.selectFirst("#danger-vcs label.vcs-type.is-current .vcs-current")?.text())
        assertEquals(listOf("Subversion"), gitPage.select("#danger-vcs input[name=targetVcs][checked]").map { it.attr("value") })
        val svn = newProject("Subversion")
        val svnPage = page(svn, login(manager(svn)))
        assertEquals(listOf("Subversion"), svnPage.select("#danger-vcs input[name=targetVcs][disabled]").map { it.attr("value") })
        assertEquals(listOf("GIT"), svnPage.select("#danger-vcs input[name=targetVcs][checked]").map { it.attr("value") })
    }

    @Test
    fun `종류마다 안내와 확인 문구를 스크립트에 실어 선택을 바꾸면 갈아 끼운다`() {
        val project = newProject()
        val script = page(project, login(manager(project))).select("script").map { it.data().replace("\\/", "/") }.first { "project.ChangeVCS" in it }
        assertTrue("\"oTexts\"" in script && "Changing the repository to Mercurial." in script && "Do you want to change the repository to Mercurial?" in script, script)
    }

    @Test
    fun `Mercurial 프로젝트의 화면은 Git과 Subversion을 고르게 하고 Mercurial은 현재로 막는다`() {
        val project = newProject("Mercurial")
        val page = page(project, login(manager(project)))
        assertEquals(listOf("Mercurial"), page.select("#danger-vcs input[name=targetVcs][disabled]").map { it.attr("value") })
        assertEquals(listOf("GIT"), page.select("#danger-vcs input[name=targetVcs][checked]").map { it.attr("value") })
    }

    @Test
    fun `Git 프로젝트를 Mercurial로 바꾸면 커밋 전에는 두 저장소가 남는다`() {
        val project = newProject()
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project))).param("targetVcs", "Mercurial")).andExpect(status().isNoContent)
        projects.flush()
        assertEquals("Mercurial", projects.findById(project.id!!).get().vcs)
        assertTrue(File("build/tmp/it-project-vcs/repo/hg/${project.owner}/${project.name}/.hg").isDirectory)
        assertTrue(File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git").exists())
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
    fun `Git 프로젝트를 Subversion으로 바꾸면 커밋 전에는 두 저장소가 남는다`() {
        val project = newProject()
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(manager(project))))
            .andExpect(status().isNoContent)
            .andExpect(header().string("Location", "/${project.owner}/${project.name}"))
        projects.flush()
        assertEquals("Subversion", projects.findById(project.id!!).get().vcs)
        assertTrue(File("build/tmp/it-project-vcs/repo/svn/${project.owner}/${project.name}/format").isFile)
        assertTrue(File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git").exists())
    }

    @Test
    fun `Git 프로젝트를 Subversion으로 바꾸고 커밋하면 기존 저장소를 지운다`() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val auditHead = jdbc.query("select last_id, last_hash from audit_chain_head where id = 1") { rs, _ -> rs.getLong(1) to rs.getString(2) }.firstOrNull()
        val project = projects.save(Project(owner = "cvo$suffix", name = "cp$suffix", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "cv$suffix", name = "cv$suffix", email = "cv$suffix@example.com", state = UserState.ACTIVE,
            passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val git = File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git")
        val svn = File("build/tmp/it-project-vcs/repo/svn/${project.owner}/${project.name}")
        try {
            menuSettings.save(ProjectMenuSetting(project = project, code = true))
            projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
            repositories.createRepository(project)
            mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(user))).andExpect(status().isNoContent)
            TestTransaction.flagForCommit()
            TestTransaction.end()
            assertTrue(File(svn, "format").isFile)
            assertTrue(!git.exists())
        } finally {
            if (TestTransaction.isActive()) {
                TestTransaction.flagForRollback()
                TestTransaction.end()
            }
            TestTransaction.start()
            jdbc.update("delete from project_user where project_id = ?", project.id)
            jdbc.update("delete from project_menu_setting where project_id = ?", project.id)
            jdbc.update("delete from project where id = ?", project.id)
            jdbc.update("delete from n4user where id = ?", user.id)
            val lastId = auditHead?.first ?: 0L
            jdbc.update("delete from audit_log where id > ?", lastId)
            if (auditHead == null) jdbc.update("delete from audit_chain_head where id = 1")
            else jdbc.update("update audit_chain_head set last_id = ?, last_hash = ? where id = 1", auditHead.first, auditHead.second)
            TestTransaction.flagForCommit()
            TestTransaction.end()
            TestTransaction.start()
            git.deleteRecursively()
            svn.deleteRecursively()
        }
    }

    @Test
    fun `Subversion 생성의 체크 예외가 발생하면 포크 연결을 되돌린다`() {
        val suffix = UUID.randomUUID().toString().replace("-", "")
        val project = projects.save(Project(owner = "cvo$suffix", name = "cp$suffix", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val fork = projects.save(Project(owner = "fork$suffix", name = "cp$suffix", vcs = "GIT", projectScope = ProjectScope.PUBLIC, originalProject = project))
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "cv$suffix", name = "cv$suffix", email = "cv$suffix@example.com", state = UserState.ACTIVE,
            passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val git = File("build/tmp/it-project-vcs/repo/git/${project.owner}/${project.name}.git")
        val blocker = File("build/tmp/it-project-vcs/repo/svn/${project.owner}")
        try {
            menuSettings.save(ProjectMenuSetting(project = project, code = true))
            projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
            repositories.createRepository(project)
            val session = login(user)
            TestTransaction.flagForCommit()
            TestTransaction.end()
            assertTrue(blocker.parentFile.mkdirs() || blocker.parentFile.isDirectory)
            assertTrue(blocker.createNewFile())
            assertFailsWith<org.tmatesoft.svn.core.SVNException> {
                repositories.createRepository(Project(owner = project.owner, name = project.name, vcs = "Subversion"))
            }
            TestTransaction.start()
            mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(session)).andExpect(status().isInternalServerError)
            TestTransaction.flagForCommit()
            try { TestTransaction.end() } catch (_: UnexpectedRollbackException) { }
            TestTransaction.start()
            assertEquals("GIT", projects.findById(project.id!!).get().vcs)
            assertEquals(project.id, projects.findById(fork.id!!).get().originalProject?.id, "포크의 원본 연결이 유지되어야 한다")
            assertTrue(git.isDirectory)
        } finally {
            if (TestTransaction.isActive()) {
                TestTransaction.flagForRollback()
                TestTransaction.end()
            }
            TestTransaction.start()
            jdbc.update("delete from project_user where project_id = ?", project.id)
            jdbc.update("delete from project_menu_setting where project_id = ?", project.id)
            jdbc.update("delete from project where id = ?", fork.id)
            jdbc.update("delete from project where id = ?", project.id)
            jdbc.update("delete from n4user where id = ?", user.id)
            TestTransaction.flagForCommit()
            TestTransaction.end()
            TestTransaction.start()
            blocker.delete()
            git.deleteRecursively()
        }
    }

    @Test
    fun `변경은 관리자만 할 수 있다`() {
        val project = newProject("Subversion")
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/${project.owner}/${project.name}/changeVCS")).andExpect(status().isForbidden)
    }
}
