package dev.yona.web.shell

import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리 화면이 새 셸의 사이드바(`ShellNav`, D-141)로 그려지는지 검증한다.
 *
 * 항목은 `ShellNav.siteAdmin`이 정하고, 템플릿은 그대로 그리기만 한다. 좁은 폭에서는 `offcanvas-lg`가 서랍으로 바꾼다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteAdminShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository

    private fun admin(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "shelladmin", name = "shelladmin", email = "shelladmin@example.com", createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        siteAdmins.save(SiteAdmin(admin = user))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun page(path: String): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `사이트 관리 사이드바는 열네 항목을 평평한 목록으로 그린다(데이터가 추가되었다)`() {
        val nav = page("/sites/userList").selectFirst("nav.shell-sidebar")
        assertNotNull(nav)
        val links = nav.select("a.nav-link")
        assertEquals(14, links.size)
        assertEquals(
            listOf("/sites/userList", "/sites/projectList", "/sites/issueList", "/sites/postList", "/sites/mail", "/sites/massmail", "/sites/update", "/sites/diagnostic", "/sites/data", "/sites/audit", "/sites/security", "/sites/sso", "/sites/oauth-apps", "/sites/lfs"),
            links.map { it.attr("href") }
        )
        assertTrue(nav.select(".shell-sidebar-group, .accordion").isEmpty(), "묶음 제목이나 접는 묶음이 없어야 한다(D-141)")
    }

    @Test
    fun `현재 화면의 항목에만 active와 aria-current가 붙는다`() {
        val nav = page("/sites/userList").selectFirst("nav.shell-sidebar")!!
        val active = nav.select("a.nav-link.active")
        assertEquals(1, active.size)
        assertEquals("/sites/userList", active.first()!!.attr("href"))
        assertEquals("page", active.first()!!.attr("aria-current"))
        assertEquals(0, nav.select("a.nav-link:not(.active)[aria-current]").size)
    }

    @Test
    fun `사이드바는 offcanvas-lg이고 좁은 폭용 서랍 단추가 사이드바를 가리킨다`() {
        val doc = page("/sites/sso")
        val nav = doc.selectFirst("nav.shell-sidebar")!!
        assertTrue(nav.hasClass("offcanvas-lg"))
        val id = nav.id()
        assertTrue(id.isNotEmpty())
        val open = doc.selectFirst("button[data-bs-toggle=offcanvas][data-bs-target=#$id]")
        assertNotNull(open, "좁은 폭에서 서랍을 여는 단추가 사이드바를 가리킨다")
        assertTrue(open.attr("aria-label").isNotBlank())
        assertEquals(id, open.attr("aria-controls"))
        assertEquals("/sites/sso", nav.selectFirst("a.nav-link.active")!!.attr("href"))
    }

    @Test
    fun `큰 제목 띠는 옛 화면 그대로 있고 옛 사이드바 마크업은 없다`() {
        val doc = page("/sites/userList")
        assertTrue(doc.selectFirst(".site-breadcrumb-inner h3")!!.text().isNotBlank(), "옛 요나의 큰 제목 띠를 그대로 둔다")
        assertTrue(doc.selectFirst("nav.shell-sidebar .offcanvas-title")!!.text().isNotBlank(), "서랍 머리에는 맥락 이름이 있다")
        assertNull(doc.selectFirst("ul.site-setting-nav"))
    }

    @Test
    fun `본문은 사이드바 옆의 shell-main 안에 그려진다`() {
        val doc = page("/sites/userList")
        assertNotNull(doc.selectFirst(".shell .shell-main ul.user-list-wrap, .shell .shell-main .user-list-wrap"))
    }

    @Test
    fun `넓은 폭에서 사이드바를 숨기고 다시 보이는 단추는 상단 바에 있고 모듈이 켠다`() {
        val doc = page("/sites/userList")
        val toggle = doc.selectFirst("nav.shell-sidebar button[data-shell-sidebar-toggle]")
        assertNotNull(toggle, "넓은 화면에서 접고 펴는 단추는 사이드바 맨 아래에 있다(A-488)")
        assertEquals("true", toggle.attr("aria-expanded"))
        assertNotNull(doc.selectFirst("nav.shell-sidebar .offcanvas-body > button.shell-sidebar-fold[data-shell-sidebar-toggle]"), "넓은 화면에서 접고 펴는 단추는 사이드바 맨 아래에 있다(A-488)")
        assertTrue(doc.select("script[type=module]").any { it.data().contains("yona/shell/sidebar.js") && !it.data().contains("/assets/") }, "import map 이름으로 불러 컨텍스트 경로에 안전해야 한다")
        assertTrue(doc.select("script").any { it.data().contains("shellSidebarCollapsed") }, "첫 그림 전에 기억한 상태를 적용하는 짧은 스크립트")
    }

    @Test
    fun `스크립트가 없는 화면만 swap-safe 표지를 갖고 사이드바는 본문 교체를 선언한다(D-136)`() {
        val safe = listOf("/sites/security", "/sites/sso", "/sites/diagnostic", "/sites/update", "/sites/audit", "/sites/lfs", "/sites/mail")
        val session = admin()
        safe.forEach { path ->
            val doc = Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
            assertNotNull(doc.selectFirst(".shell-main [data-swap-safe]"), "$path 는 swap-safe 표지가 있어야 한다")
        }
        listOf("/sites/userList", "/sites/projectList", "/sites/postList", "/sites/issueList", "/sites/data", "/sites/oauth-apps").forEach { path ->
            val d = Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
            assertNotNull(d.selectFirst(".shell-main [data-swap-safe]"), "$path 는 전역 처리기가 없어 swap-safe 이다")
        }
        val unsafe = Jsoup.parse(mockMvc.perform(get("/sites/massmail").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNull(unsafe.selectFirst("[data-swap-safe]"), "옛 스크립트가 있는 화면은 일반 이동으로 둔다")
        val nav = unsafe.selectFirst("nav.shell-sidebar")!!
        assertEquals(".shell-main", nav.attr("hx-target"))
        assertEquals(".shell-main", nav.attr("hx-select"))
        assertEquals("#shellSidebar", nav.attr("hx-select-oob").substringBefore(",").trim())
        assertTrue(unsafe.select("script[type=module]").any { it.data().contains("yona/shell/navigation.js") })
    }
}
