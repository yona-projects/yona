package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 내장 SSH 서버를 켠 사이트의 프로젝트 홈 복제 상자(X-3 6단계, `decisions.md` D-66)를 검증한다. HTTP와 SSH 주소를 고르는 단추가 생기고, 주소 입력란의 기본은 HTTP 주소 그대로이다.
 * 서버를 끈 사이트의 복제 상자는 v1.6과 같다([dev.yona.web.project.ProjectHomeTest]).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-project-home-ssh", "yona.ssh.enabled=true", "yona.ssh.port=0", "yona.ssh.public-port=2222", "yona.hostname=code.example.org"])
class ProjectHomeSshTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var repositories: RepositoryService

    private var sequence = 0

    private fun project(vcs: String = "GIT"): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "hso$n", name = "hsp$n", vcs = vcs, projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        if (vcs == "GIT") repositories.createRepository(p)
        return p
    }

    private fun home(p: Project) = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}")).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `복제 상자에 HTTP와 SSH를 고르는 단추가 있고 입력란은 HTTP 주소이다`() {
        val p = project(); val page = home(p)
        assertEquals("http://code.example.org/${p.owner}/${p.name}", page.selectFirst("#cloneURL")!!.attr("value"))
        val buttons = page.select(".clone-protocol button")
        assertEquals(listOf("HTTP", "SSH"), buttons.map { it.text() })
        assertEquals("http://code.example.org/${p.owner}/${p.name}", buttons[0].attr("data-clone-url"))
        assertEquals("ssh://git@code.example.org:2222/${p.owner}/${p.name}.git", buttons[1].attr("data-clone-url"))
        assertEquals(listOf(true, false), buttons.map { it.hasClass("active") })
    }

    @Test
    fun `Subversion 프로젝트에는 SSH 단추가 없다`() {
        val page = home(project("Subversion"))
        assertNull(page.selectFirst(".clone-protocol"))
        assertNotNull(page.selectFirst("#cloneURL"))
    }
}
