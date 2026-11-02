package dev.yona.web.wiki

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
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
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

/**
 * 위키 화면의 "이 위키를 로컬에 복제" 상자의 SSH 주소(`decisions.md` D-68)를 검증한다. SSH를 켠 사이트에서는 HTTP와 SSH를 고르는 단추가 생기고 입력란은 HTTP 주소로 시작한다.
 * 켜지 않은 사이트의 상자는 이전과 같다([WikiViewTest]).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-wiki-ssh", "yona.ssh.enabled=true", "yona.ssh.port=0", "yona.ssh.public-port=2222", "yona.hostname=code.example.org"])
class WikiSshCloneTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository

    private val stamp = System.nanoTime()

    @Test
    fun `위키 복제 상자에 HTTP와 SSH 단추가 있고 입력란은 HTTP 주소이다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "ws-$stamp", name = "ws", email = "ws-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val p = projects.save(Project(owner = "wso-$stamp", name = "wsp", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true))
        projectUsers.save(ProjectUser(user = user, project = p, role = roles.findById(RoleType.MEMBER.id).get()))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
        mockMvc.perform(post("/${p.owner}/${p.name}/wiki").session(session).param("title", "Home").param("content", "# 홈").param("message", ""))
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/wiki").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        val buttons = doc.select(".wiki-clone-protocol button")
        assertEquals(listOf("HTTP", "SSH"), buttons.map { it.text() })
        assertEquals("ssh://git@code.example.org:2222/${p.owner}/${p.name}.wiki.git", buttons[1].attr("data-clone-url"))
        assertEquals(buttons[0].attr("data-clone-url"), doc.selectFirst(".wiki-clone-url")!!.attr("value"))
        assertEquals(listOf(true, false), buttons.map { it.hasClass("selected") })
    }
}
