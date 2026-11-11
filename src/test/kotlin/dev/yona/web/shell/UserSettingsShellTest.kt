package dev.yona.web.shell

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
 * 내 설정 화면(탭 11개)이 새 셸의 사이드바로 그려지는지 검증한다(D-141). 항목은 `ShellNav.userSettings`가 정하고,
 * 탭 줄이 있던 20개 화면이 모두 같은 사이드바를 쓴다.
 */
@AutoConfigureMockMvc
@Transactional
class UserSettingsShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun login(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "shelluser", name = "shelluser", email = "shelluser@example.com", createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun page(path: String, session: MockHttpSession = login()): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `내 설정 사이드바는 탭 대신 항목을 평평하게 그린다`() {
        val session = login()
        val nav = page("/user/editform", session).selectFirst("nav.shell-sidebar")
        assertNotNull(nav)
        val hrefs = nav.select("a.nav-link").map { it.attr("href") }
        assertTrue(hrefs.containsAll(listOf("/user/editform", "/user/editform/password", "/user/editform/emails", "/user/editform/linked-accounts", "/user/editform/notifications", "/user/editform/token", "/user/editform/ssh", "/user/editform/gpg", "/user/editform/two-factor", "/user/editform/audit")), hrefs.toString())
        assertTrue(nav.select(".accordion, .shell-sidebar-group").isEmpty())
        assertNull(page("/user/editform", session).selectFirst("ul.nav-tabs.mt20"), "설정 탭 줄이 없어야 한다")
    }

    @Test
    fun `현재 화면의 항목만 활성이고 하위 화면에서도 켜진다`() {
        val session = login()
        listOf("/user/editform" to "profile", "/user/editform/password" to "password", "/user/editform/token/new" to "token", "/user/editform/ssh/new" to "ssh", "/user/editform/gpg/new" to "gpg", "/user/editform/audit" to "audit").forEach { (path, key) ->
            val active = page(path, session).select("nav.shell-sidebar a.nav-link.active")
            assertEquals(1, active.size, path)
            assertTrue(active.first()!!.attr("href").endsWith(if (key == "profile") "/user/editform" else "/user/editform/${if (key == "token") "token" else key}"), "$path -> ${active.first()!!.attr("href")}")
            assertEquals("page", active.first()!!.attr("aria-current"))
        }
    }

    @Test
    fun `사이드바와 본문 래퍼가 나란히 놓인다`() {
        val doc = page("/user/editform/ssh")
        val shell = doc.selectFirst(".page-wrap.shell")
        assertNotNull(shell)
        assertNotNull(shell.selectFirst("> nav.shell-sidebar"))
        assertNotNull(shell.selectFirst("> .shell-main"), "본문은 래퍼 안에 있어 옛 float 배치가 그대로 동작해야 한다")
    }

    @Test
    fun `사용자 앱 사용 설정이 꺼지면 OAuth 앱 항목이 없다`() {
        val hrefs = page("/user/editform").select("nav.shell-sidebar a.nav-link").map { it.attr("href") }
        assertEquals(false, "/user/editform/oauth-apps" in hrefs && !appsEnabled())
    }

    @Autowired private lateinit var settings: dev.yona.domain.site.SiteSettingService
    private fun appsEnabled() = settings.oauthUserAppsEnabled()

    @Test
    fun `스크립트가 없는 내 설정 화면만 swap-safe 표지를 갖고 제목 띠는 교체 대상이다(D-136)`() {
        val s = login()
        listOf("/user/editform/audit", "/user/editform/ssh/new", "/user/editform/gpg/new", "/user/editform/token/new").forEach { path ->
            assertNotNull(page(path, s).selectFirst(".shell-main [data-swap-safe]"), "$path 는 swap-safe 표지가 있어야 한다")
        }
        listOf("/user/editform", "/user/editform/password", "/user/editform/ssh", "/user/editform/token").forEach { path ->
            assertNull(page(path, s).selectFirst("[data-swap-safe]"), "$path 는 옛 스크립트가 있어 일반 이동이다")
        }
        val doc = page("/user/editform/audit", s)
        assertNotNull(doc.selectFirst("#pageTitle.site-breadcrumb-outer"), "제목 띠에 id가 있어야 사이드바와 함께 교체된다")
        assertTrue(doc.selectFirst("nav.shell-sidebar")!!.attr("hx-select-oob").contains("#pageTitle"))
    }
}
