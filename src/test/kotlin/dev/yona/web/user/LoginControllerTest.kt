package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6 `UserApp.login`, `UserApp.logout`과 현재 사용자 식별(세션, remember-me 쿠키, API 토큰)을 검증한다.
 *
 * 원본: `app/controllers/UserApp.java:154-345`, `:625-710`, `:1227-1290`.
 */
@AutoConfigureMockMvc
@Transactional
class LoginControllerTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var users: UserRepository

    @Autowired
    private lateinit var sessionUsers: dev.yona.security.SessionUserCache

    @Autowired
    private lateinit var siteAdmins: dev.yona.domain.user.SiteAdminRepository

    private fun newUser(
        loginId: String, password: String = "1234", state: UserState = UserState.ACTIVE, token: String? = null
    ): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                name = loginId, loginId = loginId, email = "$loginId@example.com", state = state, token = token,
                passwordSalt = salt, password = LegacyPasswordHasher.hash(password, salt)
            )
        )
    }

    private fun whoAmI(session: MockHttpSession? = null, vararg cookies: Cookie, headers: Map<String, String> = emptyMap()): String {
        val request = get("/__test/whoami")
        session?.let { request.session(it) }
        if (cookies.isNotEmpty()) request.cookie(*cookies)
        headers.forEach { (k, v) -> request.header(k, v) }
        return mockMvc.perform(request).andReturn().response.contentAsString
    }

    // --- 폼 로그인 -------------------------------------------------------------------------

    @Test
    fun `Referer가 없는 로그아웃은 Location null로 이동한다 (v1_6 결함 재현)`() {
        mockMvc.perform(get("/users/logout")).andExpect(status().isSeeOther).andExpect(header().string("Location", "null"))
    }

    @Test
    fun `로그인에 성공하면 홈으로 이동하고 이후 요청에서 사용자로 식별된다`() {
        newUser("alice")
        val session = MockHttpSession()
        mockMvc.perform(
            post("/users/login").session(session).param("loginIdOrEmail", "alice").param("password", "1234")
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("/"))

        assertEquals("alice", whoAmI(session))
    }

    @Test
    fun `로그인하지 않은 요청은 익명이다`() {
        assertEquals("anonymous", whoAmI())
    }

    @Test
    fun `redirectUrl이 있으면 그 경로로 이동한다`() {
        newUser("bob")
        mockMvc.perform(
            post("/users/login").param("loginIdOrEmail", "bob").param("password", "1234")
                .param("redirectUrl", "/bob/project one")
        ).andExpect(redirectedUrl("/bob/project%20one"))
    }

    @Test
    fun `redirectUrl이 외부 주소이면 첫 화면으로 보낸다 (R-06)`() {
        // v1.6은 redirectUrl을 검사하지 않아 `//example.com`으로 이동했다
        newUser("bobx")
        for (bad in listOf("//example.com/x", "https://example.com/x", "/\\example.com")) {
            mockMvc.perform(post("/users/login").param("loginIdOrEmail", "bobx").param("password", "1234").param("redirectUrl", bad))
                .andExpect(status().isSeeOther).andExpect(header().string("Location", "/"))
        }
    }

    @Test
    fun `redirectUrl 끝의 슬래시는 버린다 (v1_6 encodedPath의 Java split)`() {
        newUser("bob2")
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "bob2").param("password", "1234").param("redirectUrl", "/bob/p/"))
            .andExpect(redirectedUrl("/bob/p"))
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "bob2").param("password", "1234").param("redirectUrl", "/"))
            .andExpect(redirectedUrl("/"))
    }

    @Test
    fun `패스워드가 틀리면 redirectUrl을 유지한 채 로그인 폼으로 돌아간다`() {
        newUser("carol")
        mockMvc.perform(
            post("/users/login").param("loginIdOrEmail", "carol").param("password", "bad").param("redirectUrl", "/x")
        ).andExpect(redirectedUrl("/users/loginform?redirectUrl=/x"))
            .andExpect(flash().attribute("warning", "user.login.invalid"))
    }

    @Test
    fun `삭제된 계정은 안내 문구와 함께 로그인 폼으로 돌아간다`() {
        newUser("dave", state = UserState.DELETED)
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "dave").param("password", "1234"))
            .andExpect(redirectedUrl("/users/loginform?redirectUrl="))
            .andExpect(flash().attribute("warning", "user.deleted"))
    }

    @Test
    fun `잠긴 계정은 패스워드가 맞아도 로그인되지 않는다`() {
        newUser("erin", state = UserState.LOCKED)
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "erin").param("password", "1234"))
            .andExpect(flash().attribute("warning", "user.locked"))
        assertEquals("anonymous", whoAmI(session))
    }

    @Test
    fun `잠긴 계정으로 로그인하면 로그아웃과 같이 Referer로 보내고 없으면 null 문자열로 보낸다`() {
        // v1.6은 `flash(...)` 뒤 `logout()`을 부르고 logout()은 `redirect(request().getHeader("Referer"))`라 Referer가 없으면 `Location: null`이다
        newUser("ellen", state = UserState.LOCKED)
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "ellen").param("password", "1234")).andExpect(redirectedUrl("null"))
        mockMvc.perform(post("/users/login").header("Referer", "/projects").param("loginIdOrEmail", "ellen").param("password", "1234")).andExpect(redirectedUrl("/projects"))
    }

    @Test
    fun `로그인한 사용자의 언어는 요청마다 Accept-Language에 맞춰 바뀐다`() {
        val alice = newUser("langer")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "langer").param("password", "1234").header("Accept-Language", "ko-KR"))
        assertEquals("ko-KR", users.findById(alice.id!!).get().lang)
        // v1.6 Global.getDefaultAction이 모든 요청에서 UserApp.updatePreferredLanguage()를 부른다
        whoAmI(session, headers = mapOf("Accept-Language" to "ja-JP"))
        assertEquals("ja-JP", users.findById(alice.id!!).get().lang)
        // 헤더가 없으면 건드리지 않는다
        whoAmI(session)
        assertEquals("ja-JP", users.findById(alice.id!!).get().lang)
    }

    @Test
    fun `익명 요청은 어떤 사용자의 언어도 바꾸지 않는다`() {
        val bob = newUser("langer2")
        whoAmI(headers = mapOf("Accept-Language" to "ru-RU"))
        assertNull(users.findById(bob.id!!).get().lang)
    }

    @Test
    fun `관리자가 지운 사용자의 세션은 다음 요청에서 무효가 된다`() {
        val carol = newUser("carol2")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "carol2").param("password", "1234"))
        assertEquals("carol2", whoAmI(session))
        // v1.6 User.isLocked() = LOCKED || DELETED. getUserFromSession이 이 값으로 세션을 비운다.
        carol.state = UserState.DELETED; users.save(carol)
        assertEquals("anonymous", whoAmI(session))
    }

    @Test
    fun `이메일로도 로그인된다`() {
        newUser("frank")
        val session = MockHttpSession()
        mockMvc.perform(
            post("/users/login").session(session).param("loginIdOrEmail", "frank@example.com").param("password", "1234")
        ).andExpect(redirectedUrl("/"))
        assertEquals("frank", whoAmI(session))
    }

    // --- remember me -----------------------------------------------------------------------

    @Test
    fun `로그인 유지를 선택하면 30일짜리 yobi_token 쿠키를 만든다`() {
        val user = newUser("gina")
        mockMvc.perform(
            post("/users/login").param("loginIdOrEmail", "gina").param("password", "1234").param("rememberMe", "true")
        ).andExpect(cookie().maxAge("yobi.token", 30 * 24 * 60 * 60)).andExpect(cookie().httpOnly("yobi.token", true)).andReturn().response.getCookie("yobi.token")!!.also {
            // v1.6은 값이 `로그인ID:해시된 비밀번호`라 쿠키가 새면 해시가 그대로 드러났다(R-05)
            assertTrue(it.value.startsWith("gina:") && !it.value.contains(user.password!!), it.value)
        }
    }

    @Test
    fun `체크박스가 보내는 on도 로그인 유지로 읽는다 (Play 폼 바인딩)`() {
        newUser("gina2")
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "gina2").param("password", "1234").param("rememberMe", "on"))
            .andExpect(cookie().exists("yobi.token"))
    }

    @Test
    fun `로그인 유지를 선택하지 않으면 쿠키를 만들지 않는다`() {
        newUser("hank")
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "hank").param("password", "1234"))
            .andExpect(cookie().doesNotExist("yobi.token"))
    }

    @Test
    fun `yobi_token 쿠키로 사용자가 복원된다`() {
        newUser("ivan")
        assertEquals("ivan", whoAmI(null, issued("ivan")))
    }

    /** 로그인 유지를 골라 로그인하고 받은 쿠키를 돌려준다. */
    private fun issued(loginId: String): Cookie =
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", loginId).param("password", "1234").param("rememberMe", "true")).andReturn().response.getCookie("yobi.token")!!

    @Test
    fun `옛 형식이나 바뀐 쿠키는 통하지 않고 지워진다 (R-05)`() {
        val user = newUser("olga")
        // v1.6 형식(로그인ID:해시된 비밀번호)과 값을 바꾼 쿠키
        for (bad in listOf(Cookie("yobi.token", "olga:${user.password}"), Cookie("yobi.token", issued("olga").value.dropLast(3) + "abc"))) {
            val result = mockMvc.perform(get("/__test/whoami").cookie(bad)).andReturn()
            assertEquals("anonymous", result.response.contentAsString)
            assertEquals(0, result.response.getCookie("yobi.token")?.maxAge)
        }
    }

    @Test
    fun `비밀번호를 바꾸면 이전 쿠키는 통하지 않는다 (R-05)`() {
        val user = newUser("pam")
        val cookie = issued("pam")
        assertEquals("pam", whoAmI(null, cookie))
        val salt = dev.yona.security.LegacyPasswordHasher.generateSalt()
        user.passwordSalt = salt; user.password = dev.yona.security.LegacyPasswordHasher.hash("new-password", salt); users.save(user)
        assertEquals("anonymous", whoAmI(null, cookie))
    }

    @Test
    fun `로그인 동안 사이트 관리자가 되면 바로 반영된다 (R-106, v1_6은 처음 판정한 값을 기억했다)`() {
        val user = newUser("sam")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "sam").param("password", "1234"))
        fun hasAdminLink() = mockMvc.perform(get("/projects").session(session)).andReturn().response.contentAsString.contains("/sites/userList")
        assertEquals(false, hasAdminLink())
        siteAdmins.save(dev.yona.domain.user.SiteAdmin(admin = user))
        assertEquals(true, hasAdminLink(), "권한이 바뀌면 다시 로그인하지 않아도 바로 반영된다")
    }

    @Test
    fun `서버 캐시에서 밀려난 사용자의 세션도 계정이 정상이면 유효하다 (R-106, v1_6은 무효가 되어 다시 로그인해야 했다)`() {
        val user = newUser("cora")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "cora").param("password", "1234"))
        assertEquals("cora", whoAmI(session))
        sessionUsers.invalidate(requireNotNull(user.id))
        assertEquals("cora", whoAmI(session))
    }

    @Test
    fun `로그인하면 서버 캐시에 사용자가 기억된다`() {
        val user = newUser("cora2")
        // 다른 시험이 같은 번호의 사용자를 기억시켰을 수 있으므로 먼저 비운다
        sessionUsers.invalidate(requireNotNull(user.id))
        assertEquals(false, sessionUsers.contains(user.id!!))
        mockMvc.perform(post("/users/login").session(MockHttpSession()).param("loginIdOrEmail", "cora2").param("password", "1234"))
        assertEquals(true, sessionUsers.contains(user.id!!))
    }

    @Test
    fun `로그인 유지 쿠키로 복원되면 서버 캐시에도 기억된다 (v1_6 initTokenUser의 addUserInfoToSession)`() {
        val user = newUser("cora3")
        whoAmI(null, issued("cora3"))
        assertEquals(true, sessionUsers.contains(requireNotNull(user.id)))
    }

    @Test
    fun `잠기거나 지워진 계정은 yobi_token 쿠키로 로그인되지 않고 쿠키가 지워진다 (R-107)`() {
        // v1.6은 요청마다 쿠키 사용자를 캐시에 넣고 잠금 검사 없이 돌려주어, 잠기거나 지워진 계정이 쿠키로 계속 로그인 상태였다
        val locked = newUser("lara"); val deleted = newUser("dora")
        val lockedCookie = issued("lara"); val deletedCookie = issued("dora")
        locked.state = UserState.LOCKED; users.save(locked)
        deleted.state = UserState.DELETED; users.save(deleted)
        for (cookie in listOf(lockedCookie, deletedCookie)) {
            val result = mockMvc.perform(get("/__test/whoami").cookie(cookie)).andReturn()
            assertEquals("anonymous", result.response.contentAsString)
            assertEquals(0, result.response.getCookie("yobi.token")?.maxAge)
        }
    }

    @Test
    fun `잠긴 계정의 쿠키 로그인은 세션에 기록되지 않는다 (v1_6 addUserInfoToSession은 잠긴 계정을 건너뛴다)`() {
        val locked = newUser("lena")
        val cookie = issued("lena")
        locked.state = UserState.LOCKED; users.save(locked)
        // 잠긴 계정은 쿠키로 로그인되지 않는다(R-107). v1.6은 요청에서는 로그인 상태로 보았다
        val result = mockMvc.perform(get("/__test/whoami").cookie(cookie)).andReturn()
        assertEquals("anonymous", result.response.contentAsString)
        assertEquals("anonymous", whoAmI(result.request.getSession(false) as MockHttpSession?))
    }

    @Test
    fun `잠긴 사용자의 세션은 무효이고 쿠키로도 다시 식별되지 않는다 (R-107)`() {
        val user = newUser("sara")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "sara").param("password", "1234").param("rememberMe", "true"))
        val cookie = issued("sara")
        user.state = UserState.LOCKED; users.save(user)
        assertEquals("anonymous", whoAmI(session, cookie))
    }

    @Test
    fun `잘못된 yobi_token 쿠키는 버려진다`() {
        newUser("judy")
        val result = mockMvc.perform(get("/__test/whoami").cookie(Cookie("yobi.token", "judy:wrong")))
            .andExpect(cookie().maxAge("yobi.token", 0)).andReturn()
        assertEquals("anonymous", result.response.contentAsString)
    }

    // --- API 토큰 -------------------------------------------------------------------------

    @Test
    fun `웹 경로는 Yona-Token 헤더도 인정하지 않는다 (X-5, D-36)`() {
        newUser("karl", token = "tok-123")
        assertEquals("anonymous", whoAmI(headers = mapOf("Yona-Token" to "tok-123")))
    }

    @Test
    fun `웹 경로는 Authorization token 헤더를 인정하지 않는다 (X-5, D-36, 토큰은 외부 API에서만 쓴다)`() {
        newUser("lena", token = "tok-456")
        assertEquals("anonymous", whoAmI(headers = mapOf("Authorization" to "token tok-456")))
    }

    @Test
    fun `알 수 없는 토큰은 익명이다`() {
        assertEquals("anonymous", whoAmI(headers = mapOf("Yona-Token" to "nope")))
    }

    // --- 로그아웃 -------------------------------------------------------------------------

    @Test
    fun `로그아웃하면 세션과 쿠키가 지워지고 이전 화면으로 돌아간다`() {
        val user = newUser("mia")
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", "mia").param("password", "1234"))
        mockMvc.perform(
            get("/users/logout").session(session).header("Referer", "http://localhost/mia/proj")
                .cookie(Cookie("yobi.token", "mia:${user.password}"))
        ).andExpect(redirectedUrl("http://localhost/mia/proj"))
            .andExpect(flash().attribute("success", "user.logout.success"))
            .andExpect(cookie().maxAge("yobi.token", 0))
        assertEquals("anonymous", whoAmI(session))
    }

    // --- AJAX 로그인 ----------------------------------------------------------------------

    @Test
    fun `AJAX 로그인 성공은 빈 JSON을 돌려준다`() {
        newUser("nora")
        mockMvc.perform(
            post("/users/login").accept("application/json").param("loginIdOrEmail", "nora").param("password", "1234")
        ).andExpect(status().isOk).andExpect(content().string("{}"))
    }

    @Test
    fun `AJAX 로그인 실패는 403과 메시지 키를 돌려준다`() {
        newUser("omar")
        mockMvc.perform(
            post("/users/login").accept("application/json").param("loginIdOrEmail", "omar").param("password", "x")
        ).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.login.invalid"))
    }

    @Test
    fun `AJAX 로그인에서 삭제된 계정은 404이다`() {
        newUser("pete", state = UserState.DELETED)
        mockMvc.perform(
            post("/users/login").accept("application/json").param("loginIdOrEmail", "pete").param("password", "1234")
        ).andExpect(status().isNotFound).andExpect(jsonPath("$.message").value("user.deleted"))
    }

    @Test
    fun `AJAX 로그인에서 잠긴 계정은 403이다`() {
        newUser("quin", state = UserState.LOCKED)
        mockMvc.perform(
            post("/users/login").accept("application/json").param("loginIdOrEmail", "quin").param("password", "1234")
        ).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.locked"))
    }

    @Test
    fun `AJAX 로그인에서 입력이 빠지면 400이다`() {
        mockMvc.perform(post("/users/login").accept("application/json").param("loginIdOrEmail", "x"))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("user.login.required"))
    }

    @Test
    fun `로그인 요청은 캐시되지 않는다`() {
        newUser("rosa")
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", "rosa").param("password", "1234"))
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(header().string("Expires", "0"))
    }
}
