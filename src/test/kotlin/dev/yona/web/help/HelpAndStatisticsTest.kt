package dev.yona.web.help

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 도움말(`GET /_help`)과 통계(`GET /:user/:project/statistics`)를 검증한다.
 *
 * 원본: `app/controllers/HelpApp.java`, `app/views/help/toc.scala.html`, `app/controllers/StatisticsApp.java`,
 * `app/views/project/statistics.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class HelpAndStatisticsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "hs$n", name = "hs$n", email = "hs$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(owner: User, name: String, scope: ProjectScope = ProjectScope.PUBLIC): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = scope, createdDate = Date()))
            .also { menuSettings.save(ProjectMenuSetting(project = it, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true)) }

    @Test
    fun `도움말은 질문 여섯 개를 한국어로 보여 준다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/_help")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Help", doc.selectFirst(".site-breadcrumb-inner h3")!!.text())
        val questions = doc.select("ul.qas > li.qa a.question").map { it.text() }
        assertEquals(
            listOf(
                "Yona를 설치하고 싶어요.", "프로젝트를 새로 생성하고 싶어요.", "내가 참여하는 프로젝트들은 어디서 볼수 있나요?",
                "프로젝트 탈퇴는 어떻게 하나요.", "게시판에서는 어떠한 것들을 할수 있나요?", "Yona의 버그를 발견했어요."
            ),
            questions
        )
        assertEquals("#!/toggle", doc.selectFirst("a.question")!!.attr("href"))
    }

    @Test
    fun `도움말의 답변에는 링크와 게시판 기능 목록이 있다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/_help")).andExpect(status().isOk).andReturn().response.contentAsString)
        val answers = doc.select("ul.qas > li.qa .answer")
        assertEquals("https://github.com/doortts/yona#korean", answers[0].selectFirst("a")!!.attr("href"))
        assertEquals(listOf("/", "/info"), answers[2].select("a").map { it.attr("href") })
        assertEquals(listOf("/info"), answers[3].select("a").map { it.attr("href") })
        assertEquals(6, answers[4].select("ul > li").size)
        assertEquals("https://github.com/nforge/yobi/issues", answers[5].selectFirst("a")!!.attr("href"))
        assertEquals("Yona 이슈트래커에 등록", answers[5].selectFirst("a")!!.text())
    }

    @Test
    fun `도움말은 질문을 누르면 펼치는 스크립트를 싣는다`() {
        val html = mockMvc.perform(get("/_help")).andReturn().response.contentAsString
        assertTrue(html.contains("toggleClass(\"open\")"))
    }

    @Test
    fun `통계 화면은 공사 중 문구와 프로젝트 레이아웃이다`() {
        val owner = newUser(); project(owner, "stat")
        val doc = Jsoup.parse(mockMvc.perform(get("/${owner.loginId}/stat/statistics")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Under Construction", doc.selectFirst(".project-page-wrap h1")!!.text())
        assertEquals("statistics - ${owner.loginId}/stat", doc.title())
    }

    @Test
    fun `읽을 수 없는 프로젝트와 없는 프로젝트의 통계는 403이다`() {
        val owner = newUser(); project(owner, "statpriv", ProjectScope.PRIVATE)
        mockMvc.perform(get("/${owner.loginId}/statpriv/statistics")).andExpect(status().isForbidden)
        mockMvc.perform(get("/${owner.loginId}/nosuch/statistics")).andExpect(status().isForbidden)
    }

    @Test
    fun `UI 키트는 독립 문서로 일곱 개 견본 구역을 보여 준다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/_UIKit")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Yobi UI", doc.title())
        assertEquals(listOf("Buttons", "Select", "Search Form", "Labels", "Avatar", "Tabs", "Switches"), doc.select("h3").map { it.text().trim() }.filter { it.isNotEmpty() }.take(7))
        assertTrue(doc.select("script[src=/assets/javascripts/common/yobi.Common.js]").isNotEmpty())
    }
}
