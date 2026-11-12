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
    fun `상단 바 맨 왼쪽의 햄버거는 v1_6의 사이드바(즐겨찾기, 프로젝트, 최근 이슈 패널)를 연다(A-489)`() {
        val doc = page("/user/issues", login())
        val items = doc.select("ul.gnb-nav > li")
        val li = items.first()!!
        assertTrue(li.hasClass("gnb-switcher"), "첫 항목이 햄버거이다: ${li.className()}")
        val button = li.selectFirst("button.shell-switcher-btn[data-shell-switcher]")
        assertNotNull(button)
        assertNotNull(button.selectFirst("svg"), "햄버거 아이콘이다")
        assertTrue(button.text().isBlank(), "글자 없이 아이콘만 그린다")
        assertEquals("mySidenav", button.attr("aria-controls"))
        assertTrue(button.attr("aria-label").isNotBlank())
        assertTrue(button.attr("data-toggle-label").isNotBlank(), "사이드바가 있는 화면에서는 접고 펴는 단추가 되므로 그때 쓸 이름표를 미리 담는다(sidebar 브랜치)")
        assertTrue(items[1].selectFirst("a.logo") != null, "두 번째 항목은 로고이다")
        assertEquals(1, doc.select("header.gnb-outer button").filter { it.hasAttr("data-shell-switcher") || it.hasAttr("data-shell-sidebar-toggle") }.size, "상단 바의 사이드바류 단추는 햄버거 하나뿐이다")
        assertNotNull(doc.selectFirst("#mySidenav #usermenu-tab-content-list"), "패널의 목록 자리는 그대로이다")
    }

    @Test
    fun `사이드바에는 접기 단추가 없고(상단 바 햄버거가 맡는다) 좁은 폭의 서랍 단추만 있다(sidebar 브랜치)`() {
        val doc = page("/user/issues", login())
        assertNull(doc.selectFirst(".shell-sidebar-fold"), "사이드바 아래의 접기 단추는 없앴다")
        assertNull(doc.selectFirst("nav.shell-sidebar [data-shell-sidebar-toggle]"), "사이드바 안에 접기 요소가 없다")
        val id = doc.selectFirst("nav.shell-sidebar")!!.id()
        val drawer = doc.selectFirst("button.shell-sidebar-toggle[data-bs-toggle=offcanvas][data-bs-target=#$id]")
        assertNotNull(drawer, "좁은 폭에서 서랍을 여는 단추가 본문 위에 있다")
        assertTrue(drawer.hasClass("d-lg-none"), "넓은 화면에서는 숨긴다")
        assertTrue(drawer.attr("aria-label").isNotBlank())
        assertNull(doc.selectFirst(".gnb-sidebar-toggle, .shell-sidebar-btn"), "상단 바에는 햄버거 하나만 있다")
    }

    @Test
    fun `햄버거 패널은 새로고침과 하단 문구를 가지지만 사용자 정보 줄과 닫기 화살표는 없다(A-492)`() {
        val doc = page("/user/issues", login())
        val panel = doc.selectFirst("#mySidenav")!!
        assertNull(panel.selectFirst(".user-menu-wrap"), "사용자 줄은 아바타 메뉴가 맡는다")
        assertNull(panel.selectFirst(".pin-in-sidebar"), "닫기 화살표도 사용자 줄과 함께 없앤다")
        assertNotNull(panel.selectFirst(".nav-tabs .refresh-button"), "탭 줄 끝의 새로고침")
        assertTrue(panel.selectFirst(".sidebar-bottom")!!.text().isNotBlank(), "하단 문구")
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
    fun `사이드바 맨 위에 홈, 즐겨찾기, 프로젝트, 최근 읽은 이슈를 고르는 작은 단추 줄이 있고 홈이 선택되어 있다(sidebar 브랜치)`() {
        val doc = page("/user/issues", login())
        val body = doc.selectFirst("nav.shell-sidebar .offcanvas-body")!!
        val row = body.children().first()!!
        assertTrue(row.hasClass("shell-sidebar-switch"), "본문의 첫 요소가 선택 단추 줄이다: ${row.className()}")
        assertEquals("tablist", row.attr("role"))
        assertTrue(row.attr("aria-label").isNotBlank())
        val keys = row.select("button[data-shell-switch]").map { it.attr("data-shell-switch") }
        assertEquals(listOf("home", "myOrganizationList", "myProjectList", "myRecentIssueList"), keys)
        assertEquals(listOf("true", "false", "false", "false"), row.select("button").map { it.attr("aria-selected") })
        assertTrue(row.select("button").all { it.attr("role") == "tab" && it.text().isNotBlank() }, "각 단추는 탭이고 글자가 있다")
        assertEquals("home", doc.selectFirst("nav.shell-sidebar")!!.attr("data-shell-view"), "처음 보기는 홈이다")
    }

    @Test
    fun `맥락 메뉴와 바로가기는 각각 하나의 구역이고, 바로가기 구역은 항목 목록과 접기 단추 사이에 있다(sidebar 브랜치)`() {
        val doc = page("/user/issues", login())
        val body = doc.selectFirst("nav.shell-sidebar .offcanvas-body")!!
        val home = body.selectFirst("[data-shell-pane=home]")
        val host = body.selectFirst("[data-shell-pane=shortcuts][data-shell-shortcuts]")
        assertNotNull(home, "맥락 메뉴 구역"); assertNotNull(host, "바로가기 구역")
        assertNotNull(home.selectFirst("ul.shell-sidebar-nav"), "맥락 메뉴 구역 안에 항목 목록")
        val kids = body.children()
        val hostAt = kids.indexOf(host)
        assertTrue(kids.indexOf(home) < hostAt, "맥락 메뉴 구역 다음에 바로가기 구역이 있다: $hostAt")
        assertEquals(kids.size - 1, hostAt, "바로가기 구역이 사이드바 본문의 마지막이다(접기 단추가 없다)")
        assertTrue(host.attr("aria-label").isNotBlank(), "구역의 이름표")
        assertNotNull(doc.selectFirst("#mySidenav .right-menu"), "옮겨 올 원본 패널은 서버가 그대로 그린다(사이드바가 없는 화면에서는 덮는 패널로 쓰인다)")
    }

    @Test
    fun `예전 핀 화살표(상단 바)와 iframe 사이드바 스크립트는 없고 아바타는 패널을 열지 않는다(A-491)`() {
        val s = login()
        val doc = page("/user/issues", s)
        assertNull(doc.selectFirst("header .pin"), "상단 바에 핀 화살표는 없다(햄버거가 대신한다)")
        assertNull(doc.selectFirst("#sidebar-open-btn"), "아바타가 더 이상 패널을 열지 않는다")
        assertFalse(doc.html().contains("mainFrame"), "iframe 사이드바(mainFrame)로 들어가는 흔적이 없어야 한다")
        assertNull(doc.selectFirst("#mySidenav .pin-in-sidebar"), "패널 안에 닫기 화살표는 없다(A-492)")
    }

    @Test
    fun `익명에게는 전환 단추가 없다`() {
        val doc = page("/projects")
        assertNull(doc.selectFirst("[data-shell-switcher]"))
        assertNull(doc.selectFirst("li.shell-user-dropdown"))
    }
}
