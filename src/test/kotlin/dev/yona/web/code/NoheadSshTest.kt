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
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
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
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 빈 저장소 안내 화면(`code/nohead`)의 SSH 복제 주소(`decisions.md` D-68)를 검증한다. SSH를 켠 사이트에서는 HTTP와 SSH를 고르는 단추가 생기고 안내의 복제 주소가 그 선택을 따른다.
 * 켜지 않은 사이트의 안내는 v1.6과 같다([dev.yona.web.code.CodeBrowserWebTest]).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-nohead-ssh", "yona.ssh.enabled=true", "yona.ssh.port=0", "yona.ssh.public-port=2222", "yona.hostname=code.example.org"])
class NoheadSshTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun manager(): Pair<Project, MockHttpSession> {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "nh$n", name = "nh$n", email = "nh$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val p = projects.save(Project(owner = "nho$n", name = "nhp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = user, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
        return p to session
    }

    private fun page(p: Project, session: MockHttpSession) = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/code").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `SSH를 켠 사이트의 안내에 HTTP와 SSH 단추가 있고 복제 주소는 HTTP로 시작한다`() {
        val (p, session) = manager(); val doc = page(p, session)
        val buttons = doc.select(".clone-protocol button")
        assertEquals(listOf("HTTP", "SSH"), buttons.map { it.text() })
        assertEquals("ssh://git@code.example.org:2222/${p.owner}/${p.name}.git", buttons[1].attr("data-clone-url"))
        assertEquals(listOf(true, false), buttons.map { it.hasClass("selected") })
        val urls = doc.select("pre code .clone-url").map { it.text() }
        assertEquals(3, urls.size, "git clone, git remote add 두 곳, 세 군데에 주소가 있다")
        assertTrue(urls.all { it == buttons[0].attr("data-clone-url") }, urls.toString())
    }

    @Test
    fun `안내의 명령 본문은 HTTP만 있을 때와 같은 글이다`() {
        val (p, session) = manager(); val doc = page(p, session)
        val first = doc.select("pre code").first()!!.text()
        assertTrue(first.startsWith("git clone http://"), first)
        assertTrue(first.contains("cd ${p.name}/") && first.contains("echo \"# ${p.name}\" > README.md") && first.contains("git push origin master"), first)
    }
}
