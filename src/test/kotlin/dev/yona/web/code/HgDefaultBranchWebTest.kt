package dev.yona.web.code

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
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
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.jsoup.Jsoup
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 브랜치 화면에서 "기본으로 설정"이 실제로 일하는지 확인한다(UI 계약 재조사 code-F7의 남은 부분, 선생님 결정 2026-11-11).
 * v1.6에서 옮긴 단추는 요청이 성공하는데 아무 일도 하지 않았다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-default-branch"])
class HgDefaultBranchWebTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0
    private lateinit var project: Project
    private lateinit var dir: File
    private lateinit var manager: MockHttpSession

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        val n = ++sequence
        project = projects.save(Project(owner = "hdo$n", name = "hdp$n", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        dir = hgRepositories.directoryOf(project.owner!!, project.name!!)
        HgTestSupport.commit(dir, "첫 커밋", mapOf("README.md" to "# 안녕\n"), "2026-11-01 10:00 +0900", "Alice <alice@example.com>")
        HgTestSupport.bookmark(dir, "main")
        HgTestSupport.bookmark(dir, "release")
        val user = newUser("hdm$n")
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MANAGER.id).get()))
        manager = login(user)
    }

    private fun newUser(loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = loginId, email = "$loginId@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun base() = "/${project.owner}/${project.name}"

    /** 브랜치 화면에서 "기본으로 설정" 단추가 요청하는 주소들이다(화면이 만든 주소 그대로). */
    private fun setDefaultUris(session: MockHttpSession): List<String> {
        val html = mockMvc.perform(get(java.net.URI.create(base() + "/branches")).session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        return Jsoup.parse(html).select("button[data-request-uri$=/setAsDefault]").map { it.attr("data-request-uri") }
    }

    @Test
    fun `기본이 아닌 브랜치에만 기본으로 설정 단추가 있고 누르면 기본이 바뀐다`() {
        val before = setDefaultUris(manager)
        assertEquals(1, before.size, before.toString()); assertTrue("release" in before[0], before[0])
        mockMvc.perform(post(java.net.URI.create(before[0])).session(manager).header("X-Requested-With", "XMLHttpRequest")).andExpect(status().isOk)
        val after = setDefaultUris(manager)
        assertEquals(1, after.size, after.toString()); assertTrue("main" in after[0], "기본이 release로 바뀌어 main에 단추가 생겨야 한다: $after")
        assertEquals("refs/heads/release", hgRepositories.defaultBranch(project.owner!!, project.name!!))
    }

    @Test
    fun `없는 브랜치를 기본으로 정하려 하면 400이고 기본은 그대로이다`() {
        mockMvc.perform(post("${base()}/code/nothing/setAsDefault").session(manager)).andExpect(status().isBadRequest)
        assertEquals("refs/heads/main", hgRepositories.defaultBranch(project.owner!!, project.name!!))
    }

    @Test
    fun `수정 권한이 없으면 기본으로 정할 수 없다`() {
        val stranger = login(newUser("hds${++sequence}"))
        mockMvc.perform(post("${base()}/code/release/setAsDefault").session(stranger)).andExpect(status().isForbidden)
        assertTrue(setDefaultUris(stranger).isEmpty(), "권한이 없으면 단추도 없다")
        assertEquals("refs/heads/main", hgRepositories.defaultBranch(project.owner!!, project.name!!))
    }
}
