package dev.yona.web.shell

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 새 상단 바를 검증한다(D-134 결정 7, D-141). 핀 사이드바(iframe)와 사용자 오른쪽 패널은 상단 바의 "전환" 단추 하나로 합치고,
 * 아바타는 프로필, 설정, 로그아웃만 가진 짧은 드롭다운이다. 전환 패널(`#mySidenav`)의 내용과 `F` 키, `/user/sidebar` 주소는 그대로이다.
 */
@AutoConfigureMockMvc
@Transactional
class TopBarShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun login(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "topbaruser", name = "topbaruser", email = "topbaruser@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `로그인하면 상단 바에 전환 단추가 있고 전환 패널을 가리킨다`() {
        val doc = page("/user/issues", login())
        val button = doc.selectFirst("button.shell-switcher-btn[data-shell-switcher]")
        assertNotNull(button)
        assertEquals("mySidenav", button.attr("aria-controls"))
        assertTrue(button.attr("aria-label").isNotBlank())
        assertNotNull(doc.selectFirst("#mySidenav #usermenu-tab-content-list"), "전환 패널의 목록 자리는 그대로이다")
    }

    @Test
    fun `아바타는 프로필, 설정, 로그아웃만 가진 짧은 드롭다운이다`() {
        val doc = page("/user/issues", login())
        val menu = doc.selectFirst("li.shell-user-dropdown")
        assertNotNull(menu)
        assertEquals("dropdown", menu.selectFirst("[data-bs-toggle]")!!.attr("data-bs-toggle"))
        assertEquals(listOf("/topbaruser", "/user/editform", "/users/logout"), menu.select("ul.dropdown-menu a.dropdown-item").map { it.attr("href") })
    }

    @Test
    fun `핀 사이드바와 사용자 패널 머리는 없어졌다`() {
        val s = login()
        val doc = page("/user/issues", s)
        assertNull(doc.selectFirst(".pin"), "핀 화살표가 없어야 한다")
        assertNull(doc.selectFirst("#sidebar-open-btn"), "아바타가 더 이상 패널을 열지 않는다")
        assertNull(doc.selectFirst("#mySidenav .user-menu-wrap"), "프로필, 설정, 로그아웃은 아바타 드롭다운으로 합쳤다")
        assertFalse(doc.html().contains("shallWeOpenLeftNavigation"), "iframe 사이드바로 들어가는 스크립트가 없어야 한다")
    }

    @Test
    fun `익명에게는 전환 단추가 없다`() {
        val doc = page("/projects")
        assertNull(doc.selectFirst("[data-shell-switcher]"))
        assertNull(doc.selectFirst("li.shell-user-dropdown"))
    }
}
