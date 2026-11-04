package dev.yona.web.migration

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.migration.GithubTokenClient
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이전(Migration) 화면(`GET /migration`)을 검증한다. 화면은 설정 `yona.github.allow-migration`이 켜져 있어야 열린다.
 *
 * 원본: `app/controllers/MigrationApp.java:50-91`, `app/views/migration/home.scala.html`.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.github.allow-migration=true"])
@AutoConfigureMockMvc
@Transactional
class MigrationPageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @MockitoBean private lateinit var tokens: GithubTokenClient

    private fun login(): Pair<User, MockHttpSession> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "mgpage", name = "mgpage", email = "mgpage@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", "mgpage").param("password", "1234")) }
        return user to session
    }

    @Test
    fun `원본 프로젝트 목록의 링크는 사라진 옛 서버가 아니라 yona 사이트를 가리킨다 (R-58-3)`() {
        val (_, session) = login()
        val html = mockMvc.perform(get("/migration").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        // v1.6은 `yobi.navercorp.com`으로 고정된 링크를 두었다
        assertTrue(!html.contains("yobi.navercorp.com"), "옛 서버 링크가 남아 있다")
        assertTrue(html.contains("https://yona.dev/{{repo.full_name}}"), "yona 사이트 링크가 없다")
    }

    @Test
    fun `로그인하지 않으면 로그인 폼으로 보낸다`() {
        mockMvc.perform(get("/migration")).andExpect(status().isSeeOther)
    }

    @Test
    fun `전용 문서 껍데기를 쓴다 (v1_6 migrationPageLayout)`() {
        val (_, session) = login()
        val doc = Jsoup.parse(mockMvc.perform(get("/migration").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("width=device-width, initial-scale=1.0", doc.selectFirst("meta[name=viewport]")!!.attr("content"))
        assertEquals("no-cache", doc.selectFirst("meta[http-equiv=cache-control]")!!.attr("content"))
        assertEquals(listOf("Montserrat", "Indie+Flower", "Muli"), doc.select("link[href^=https://fonts.googleapis.com]").map { it.attr("href").substringAfter("family=") })
        // head: jquery 1.9.0, vendor.js, yona.Migration.js (본문 안이 아니다)
        val headScripts = doc.head().select("script[src]").map { it.attr("src") }
        assertTrue("/assets/javascripts/lib/jquery/jquery-1.9.0.js" in headScripts && "/assets/javascripts/lib/vendor.js" in headScripts && "/assets/javascripts/service/yona.Migration.js" in headScripts, headScripts.toString())
        // CSS 순서: yobi.css 다음에 usermenu.css
        val css = doc.head().select("link[rel=stylesheet]").map { it.attr("href") }
        assertTrue(css.indexOf("/assets/stylesheets/yobi.css") < css.indexOf("/assets/stylesheets/usermenu.css"), css.toString())
        // 푸터가 없다
        assertTrue(doc.select("footer, #footer, .footer").isEmpty())
    }

    @Test
    fun `코드가 없으면 토큰 없이 화면을 연다`() {
        val (_, session) = login()
        val doc = Jsoup.parse(mockMvc.perform(get("/migration").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        val root = doc.selectFirst("div[ng-app=yona.migration]")!!
        assertEquals("MigrationController as vm", root.attr("ng-controller"))
        assertEquals("vm.setUserToken('');vm.yobiUser = 'mgpage';vm.yobiUserEmail = 'mgpage@example.com'", root.attr("ng-init"))
        assertEquals("''", root.selectFirst(".header-pannel")!!.attr("ng-if"))
        assertTrue(doc.select("script[src=/assets/javascripts/service/yona.Migration.js]").isNotEmpty())
        Mockito.verifyNoInteractions(tokens)
    }

    @Test
    fun `코드가 있으면 토큰으로 바꿔 화면에 넘긴다`() {
        Mockito.`when`(tokens.exchange("abc")).thenReturn("tok123")
        val (_, session) = login()
        val doc = Jsoup.parse(mockMvc.perform(get("/migration").session(session).param("code", "abc")).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.selectFirst("div[ng-app]")!!.attr("ng-init").startsWith("vm.setUserToken('tok123');"))
        assertEquals("'tok123'", doc.selectFirst(".header-pannel")!!.attr("ng-if"))
        assertEquals("vm.getDestinationProjects();vm.getCurrentGithubUser()", doc.selectFirst(".destination-project")!!.attr("ng-init"))
    }

    @Test
    fun `공백 코드는 코드가 없는 것과 같다`() {
        val (_, session) = login()
        mockMvc.perform(get("/migration").session(session).param("code", "  ")).andExpect(status().isOk)
        Mockito.verifyNoInteractions(tokens)
    }

    @Test
    fun `화면은 원본의 구성을 담는다`() {
        val (_, session) = login()
        val html = mockMvc.perform(get("/migration").session(session)).andReturn().response.contentAsString
        listOf("Source 프로젝트를 선택해 주세요", "마일스톤 옮기기", "이슈 옮기기", "게시글 옮기기", "기존 이슈 담당자", "Yona to Github", "{{vm.sourceProjects.length}}").forEach { assertTrue(html.contains(it), it) }
    }

    @Test
    fun `즐겨찾기 호출 주소는 v1_6처럼 문자열 뒤에 번호를 이어 붙인다`() {
        val (_, session) = login()
        val html = mockMvc.perform(get("/migration").session(session)).andReturn().response.contentAsString
        assertTrue(html.contains("""$.post("/user/favoriteProjects/" + that.data("projectId"))"""), "프로젝트 즐겨찾기")
        assertTrue(html.contains("""$.post("/user/favoriteOrganizations/" + that.data("organizationId"))"""), "조직 즐겨찾기")
        assertTrue(html.contains("""$.get("/user/favoriteProjects")"""), "즐겨찾기 목록")
        assertEquals("/favicon.ico", Jsoup.parse(html).selectFirst("link[rel=shortcut icon]")!!.attr("href"))
    }
}
