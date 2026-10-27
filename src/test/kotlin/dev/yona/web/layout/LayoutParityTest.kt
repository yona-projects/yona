package dev.yona.web.layout

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 공식 v1.16.0 릴리스(v1.6 기준 코드)와 같은 요청을 보내 비교하다 드러난 레이아웃 차이를 고정한다.
 * 기대값은 실행 중인 v1.16.0의 실제 응답이다.
 */
@AutoConfigureMockMvc
class LayoutParityTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun home(session: MockHttpSession? = null) =
        mockMvc.perform(get("/").apply { session?.let { session(it) } }).andReturn().response

    @Test
    fun `파비콘 링크는 역방향 라우트가 만든 루트 주소이다`() {
        val doc = Jsoup.parse(home().contentAsString)
        assertEquals("/favicon.ico", doc.selectFirst("link[rel=shortcut icon]")?.attr("href"))
    }

    @Test
    fun `넘겨 받은 요청의 og url은 처음 요청한 경로이다 (v1_6 request path는 go 같은 주소를 그대로 보인다)`() {
        val request = get("/").requestAttr(jakarta.servlet.RequestDispatcher.FORWARD_REQUEST_URI, "/someone/proj/go")
        val doc = Jsoup.parse(mockMvc.perform(request).andReturn().response.contentAsString)
        assertEquals("/someone/proj/go", doc.selectFirst("meta[property=og:url]")?.attr("content"))
        assertEquals("/someone/proj/go", doc.selectFirst("meta[name=twitter:url]")?.attr("content"))
    }

    @Test
    fun `익명 방문자의 사용자 메뉴에서 프로필 링크는 루트이다 (로그인 ID가 빈 문자열)`() {
        val doc = Jsoup.parse(home().contentAsString)
        val profile = doc.select(".user-menu a").firstOrNull { it.text().trim() == "Profile" }
        assertNotNull(profile)
        assertEquals("/", profile.attr("href"))
    }

    @Test
    fun `사이드바 자동 열기 조건은 익명이면 !true이고 로그인하면 !false이다`() {
        assertTrue(home().contentAsString.contains("shallWeOpenLeftNavigation === \"true\" && !true)"), "익명")
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "lay1", name = "lay1", email = "lay1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "lay1").param("password", "1234"))
        assertTrue(home(session).contentAsString.contains("shallWeOpenLeftNavigation === \"true\" && !false)"), "로그인")
    }

    @Test
    fun `화면 응답에는 Cache-Control no-cache가 붙는다 (v1_6 Global의 기본 동작)`() {
        assertEquals("no-cache", home().getHeader("Cache-Control"))
        assertEquals("no-cache", mockMvc.perform(get("/projects")).andReturn().response.getHeader("Cache-Control"))
    }

    @Test
    fun `접근 검사가 막은 응답에는 Cache-Control이 붙지 않는다 (로그인 이동, 403)`() {
        val redirect = mockMvc.perform(get("/projectform")).andReturn().response
        assertEquals(303, redirect.status)
        assertEquals(null, redirect.getHeader("Cache-Control"))
        assertEquals(null, mockMvc.perform(get("/sites/userList")).andReturn().response.getHeader("Cache-Control"))
    }

    @Test
    fun `JSON 응답의 내용 형식에는 문자 집합이 붙는다 (v1_6 Play의 ok(json))`() {
        val type = mockMvc.perform(get("/user/isUsed").param("name", "nobody")).andReturn().response.contentType.orEmpty()
        assertEquals("application/json;charset=utf-8", type.lowercase().replace(" ", ""))
    }

    @Test
    fun `리소스를 해석하지 못한 구독 요청은 빈 본문이 아니라 오류 화면이다 (v1_6 Global_onBadRequest)`() {
        val response = mockMvc.perform(post("/watch").queryParam("resource.type", "nothing").queryParam("resource.id", "1")).andReturn().response
        assertEquals(400, response.status)
        assertTrue(response.contentAsString.contains("<title>"), "본문이 비어 있다")
    }
}
