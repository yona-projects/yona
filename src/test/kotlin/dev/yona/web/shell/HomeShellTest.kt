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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트 밖(홈, 내 이슈, 내 파일, 프로젝트 목록, 그룹 목록) 화면이 새 셸의 사이드바로 그려지는지 검증한다(D-141).
 * 항목은 프로젝트, 그룹, 내 이슈, 알림, 내 파일이고, 본문 위의 탭 줄은 사이드바와 중복이므로 없어졌다.
 * 알림과 내 이슈 화면의 "기본 페이지로 지정" 단추는 탭이 아니라 기능이므로 남는다.
 */
@AutoConfigureMockMvc
@Transactional
class HomeShellTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository

    private fun login(): MockHttpSession {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "homeuser", name = "homeuser", email = "homeuser@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        return MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }
    }

    private fun page(path: String, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get(path).apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `로그인하면 프로젝트, 그룹, 내 이슈, 알림, 내 파일 다섯 항목이 나온다`() {
        val nav = page("/", login()).selectFirst("nav.shell-sidebar")
        assertNotNull(nav)
        assertEquals(listOf("/projects", "/orgs", "/user/issues", "/", "/user/files"), nav.select("a.nav-link").map { it.attr("href") })
    }

    @Test
    fun `현재 화면의 항목만 활성이다`() {
        val s = login()
        listOf("/" to "/", "/notifications" to "/", "/user/issues" to "/user/issues", "/user/files" to "/user/files", "/projects" to "/projects", "/orgs" to "/orgs").forEach { (path, href) ->
            val active = page(path, s).select("nav.shell-sidebar a.nav-link.active")
            assertEquals(1, active.size, path)
            assertEquals(href, active.first()!!.attr("href"), path)
        }
    }

    @Test
    fun `비로그인은 프로젝트와 그룹 목록 항목만 보인다`() {
        assertEquals(listOf("/projects", "/orgs"), page("/projects").select("nav.shell-sidebar a.nav-link").map { it.attr("href") })
    }

    @Test
    fun `본문 위의 옛 탭 줄은 없고 기본 페이지로 지정 단추는 남는다`() {
        val s = login()
        listOf("/user/issues", "/user/files").forEach { path ->
            assertTrue(page(path, s).select(".shell-main ul.nav-tabs a[href\$=/user/files]").isEmpty(), path)
        }
        assertNotNull(page("/user/issues", s).selectFirst("#setDefaultLoginPage"), "내 이슈 화면의 기본 페이지 단추")
    }

    @Test
    fun `사이드바와 본문 래퍼가 나란히 놓인다`() {
        val doc = page("/user/issues", login())
        assertNotNull(doc.selectFirst(".page-wrap.shell > nav.shell-sidebar"))
        assertNotNull(doc.selectFirst(".page-wrap.shell > .shell-main"))
        assertNull(doc.selectFirst(".shell-main > ul.nav-tabs.mb-3 a[href=\"/notifications\"]"))
    }
}
