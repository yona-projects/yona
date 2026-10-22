package dev.yona.web.home

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.user.UserSetting
import dev.yona.domain.user.UserSettingRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 홈 화면(`GET /`, `GET /notifications`)을 검증한다.
 *
 * 원본: `app/controllers/Application.java:45-60`, `app/views/index/{index,notifications,partial_intro}.scala.html`,
 * `app/views/common/mySeriesMenuTab.scala.html`, `app/controllers/UserApp.java`의 `setDefaultLoginPage`.
 * 알림 항목 자체(`partial_notifications`)는 알림 서브시스템을 옮길 때 검증한다.
 */
@AutoConfigureMockMvc
@Transactional
class HomePageTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var settings: UserSettingRepository

    private fun login(loginId: String = "home1"): Pair<MockHttpSession, User> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(
            User(
                loginId = loginId, name = loginId, email = "$loginId@example.com", state = UserState.ACTIVE,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", loginId).param("password", "1234"))
        return session to user
    }

    private fun page(path: String = "/", session: MockHttpSession? = null): Document {
        val request = get(path)
        session?.let { request.session(it) }
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().isOk).andReturn().response.contentAsString)
    }

    // --- 익명 사용자: 소개 ---

    @Test
    fun `익명 사용자에게는 사이트 이름을 제목으로 한 소개 화면을 보여 준다`() {
        val doc = page()
        assertEquals("Yona", doc.title())
        assertEquals("21st Century Software Development Platform", doc.selectFirst(".siteintro h1.site-heading")?.text())
        assertEquals("Just focus on what you have to do", doc.selectFirst(".siteintro ul.site-features li")?.text())
    }

    @Test
    fun `소개 화면에 가입 버튼과 기능 여섯 가지가 있다`() {
        val doc = page()
        val signup = doc.selectFirst(".siteintro .signup-btn a.ybtn-success")
        assertEquals("/users/signupform", signup?.attr("href"))
        assertEquals("Sign up for Yona", signup?.text())

        val features = doc.select(".feature ul.feature-wrap > li")
        assertEquals(6, features.size)
        assertEquals(
            listOf("yobicon-cgicenter", "yobicon-code", "yobicon-articles", "yobicon-lock", "yobicon-preview", "yobicon-friends"),
            features.map { it.selectFirst(".feature-image i")!!.className() }
        )
        assertEquals("Project / Organization", features.first()!!.selectFirst(".feature-title")?.text())
        assertEquals("Key features", doc.selectFirst(".feature h2 span")?.text())
    }

    @Test
    fun `익명 사용자 화면에는 알림 영역이 없다`() {
        val doc = page()
        assertNull(doc.selectFirst("ul.notification-wrap"))
        assertNull(doc.selectFirst(".site-guide-outer"))
    }

    @Test
    fun `홈 화면도 공통 레이아웃과 푸터를 가진다`() {
        val doc = page()
        assertNotNull(doc.selectFirst("header.gnb-outer"))
        assertNotNull(doc.selectFirst("footer.page-footer-outer"))
    }

    // --- 로그인 사용자: 알림 ---

    @Test
    fun `로그인 사용자에게는 환영 표와 알림 영역을 보여 준다`() {
        val (session, _) = login()
        val doc = page("/", session)
        assertNull(doc.selectFirst(".siteintro"))

        val guide = doc.selectFirst(".site-guide-outer.hide")
        assertNotNull(guide)
        assertEquals("Tada! Welcome to Yona! - Web-based platform for collaborative software development", guide.selectFirst("h3 span")?.text())
        val links = guide.select("table.welcome-table tr").map { it.selectFirst("a")!!.attr("href") }
        assertEquals(listOf("/projectform", "/organizations/new", "/projects"), links)
        assertEquals(
            listOf("Create your own project", "If you want to make a group and work with other members, then create a group", "Find a project in which you are interested"),
            guide.select("table.welcome-table tr td:nth-child(2)").map { it.text() }
        )
        assertNotNull(doc.selectFirst(".guide-toggle button#toggleIntro"))
    }

    @Test
    fun `알림이 없으면 없다는 안내를 보여 준다`() {
        val (session, _) = login()
        val doc = page("/", session)
        val list = doc.selectFirst("ul.activity-streams.notification-wrap")
        assertNotNull(list)
        assertNotNull(list.selectFirst(".warning-none i.yobicon-danger"))
        assertEquals(0, list.select("li.notification-stream").size)
        assertNull(doc.selectFirst("#notification-more"), "알림이 없으면 More 버튼도 없다")
    }

    @Test
    fun `내 활동 탭 메뉴에서 알림 탭이 활성이다`() {
        val (session, _) = login()
        val tabs = page("/", session).select(".main-stream ul.nav-tabs > li")
        assertEquals(listOf("/notifications", "/user/issues", "/user/files"), tabs.take(3).map { it.selectFirst("a")!!.attr("href") })
        assertEquals(listOf("Notification", "My Issues", "My Files"), tabs.take(3).map { it.text() })
        assertTrue(tabs[0].hasClass("active"))
        assertFalse(tabs[1].hasClass("active"))
    }

    @Test
    fun `알림 주소로도 같은 화면을 볼 수 있다`() {
        val (session, _) = login("home2")
        assertNotNull(page("/notifications", session).selectFirst("ul.notification-wrap"))
        assertNotNull(page("/notifications").selectFirst(".siteintro"))
    }

    // --- 기본 로그인 페이지 ---

    @Test
    fun `기본 로그인 페이지가 있으면 홈에서 그곳으로 이동한다`() {
        val (session, user) = login("home3")
        settings.save(UserSetting(user = user, loginDefaultPage = "notifications"))
        mockMvc.perform(get("/").session(session))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "notifications"))
    }

    @Test
    fun `기본 로그인 페이지가 비어 있으면 이동하지 않는다`() {
        val (session, user) = login("home4")
        settings.save(UserSetting(user = user, loginDefaultPage = " "))
        mockMvc.perform(get("/").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `기본 로그인 페이지 설정 버튼은 홈이 아닌 알림 화면에서만 나온다`() {
        val (session, _) = login("home5")
        assertNull(page("/", session).selectFirst("#setDefaultLoginPage"))
        val button = page("/notifications", session).selectFirst("#setDefaultLoginPage")
        assertNotNull(button)
        assertEquals("notifications", button.attr("data-url"))
        assertEquals("Set to default page", button.text())
        assertEquals("Make current page the index page when logged in", button.attr("data-content"))
    }

    @Test
    fun `이미 기본 페이지로 정한 화면에서는 설정 버튼이 사라진다`() {
        val (session, user) = login("home6")
        settings.save(UserSetting(user = user, loginDefaultPage = "notifications"))
        // 홈은 기본 페이지로 이동시키지만 알림 화면 자체는 그대로 열린다.
        assertNull(page("/notifications", session).selectFirst("#setDefaultLoginPage"))
    }

    @Test
    fun `기본 로그인 페이지를 저장하면 JSON으로 알려 준다`() {
        val (session, user) = login("home7")
        mockMvc.perform(post("/-_-api/v1/user/defultLoginPage").session(session).param("path", "user/issues"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.defaultLoginPage").value("user/issues"))
        assertEquals("user/issues", settings.findByUserId(user.id!!)?.loginDefaultPage)
    }

    @Test
    fun `이미 설정이 있으면 값을 갱신한다`() {
        val (session, user) = login("home8")
        settings.save(UserSetting(user = user, loginDefaultPage = "old"))
        mockMvc.perform(post("/-_-api/v1/user/defultLoginPage").session(session).param("path", "new"))
        assertEquals("new", settings.findByUserId(user.id!!)?.loginDefaultPage)
        assertEquals(1, settings.findAll().count { it.user?.id == user.id })
    }
}
