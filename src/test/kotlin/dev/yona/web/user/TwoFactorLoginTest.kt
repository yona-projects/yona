package dev.yona.web.user

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.twofactor.Base32
import dev.yona.domain.twofactor.TotpConfirmation
import dev.yona.domain.twofactor.Totp
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2단계 인증을 켠 사용자의 로그인 흐름(X-2 3단계, `decisions.md` D-69)을 검증한다. 비밀번호가 맞아도 2단계를 마치기 전에는 로그인되지 않고(세션에 인증 정보가 없다), 2단계 화면에서
 * 인증 앱 코드나 백업 코드를 입력해야 로그인된다. 웹 폼과 AJAX 로그인(팝업)이 같은 판정을 따른다. 켜지 않은 사용자의 로그인은 v1.6 그대로이다([LoginControllerTest]).
 */
@AutoConfigureMockMvc
@Transactional
class TwoFactorLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var twoFactor: TwoFactorService
    @Autowired private lateinit var attempts: dev.yona.security.TwoFactorAttempts

    private var sequence = 0

    /** 2단계 인증을 켠 사용자이다. 비밀 값과 백업 코드를 함께 돌려준다. */
    private class Enrolled(val user: User, val secret: ByteArray, val backupCodes: List<String>)

    private fun enrolled(state: UserState = UserState.ACTIVE): Enrolled {
        val n = ++sequence; val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "tl$n-${System.nanoTime()}", name = "tl$n", email = "tl$n-${System.nanoTime()}@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val secret = Base32.decode(twoFactor.beginTotp(user, "Yona", user.loginId!!)!!.secret)
        val confirmed = twoFactor.confirmTotp(user, Totp.code(secret, Instant.now().epochSecond / 30 - 5), Instant.now().minusSeconds(150)) as TotpConfirmation.Confirmed
        return Enrolled(user, secret, confirmed.backupCodes)
    }

    private fun currentCode(e: Enrolled) = Totp.code(e.secret, Instant.now().epochSecond / 30)

    private fun passwordLogin(e: Enrolled, session: MockHttpSession, rememberMe: Boolean = false, redirectUrl: String? = null, json: Boolean = false): ResultActions =
        mockMvc.perform(
            post("/users/login").session(session).param("loginIdOrEmail", e.user.loginId!!).param("password", "1234").apply {
                if (rememberMe) param("rememberMe", "true"); redirectUrl?.let { param("redirectUrl", it) }; if (json) accept(MediaType.APPLICATION_JSON)
            }
        )

    private fun submit(code: String, session: MockHttpSession, json: Boolean = false, rememberDevice: Boolean = false): ResultActions =
        mockMvc.perform(post("/users/login/2fa").session(session).param("code", code).apply { if (rememberDevice) param("rememberDevice", "true"); if (json) accept(MediaType.APPLICATION_JSON) })

    private fun loggedIn(session: MockHttpSession) = mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status == 200

    @Test
    fun `비밀번호가 맞아도 2단계 인증을 켠 사용자는 2단계 화면으로 가고 로그인되지 않는다`() {
        val e = enrolled(); val session = MockHttpSession()
        passwordLogin(e, session).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/users/login/2fa"))
        assertTrue(!loggedIn(session), "2단계를 마치기 전에는 인증된 화면을 열 수 없다")
    }

    @Test
    fun `2단계 화면은 진행 중인 로그인이 있을 때만 열리고 코드 입력란과 캐시 금지 머리글을 가진다`() {
        val e = enrolled()
        mockMvc.perform(get("/users/login/2fa").session(MockHttpSession())).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/users/loginform?redirectUrl="))
        val session = MockHttpSession(); passwordLogin(e, session)
        val page = mockMvc.perform(get("/users/login/2fa").session(session)).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andReturn().response.contentAsString
        val doc = Jsoup.parse(page)
        val form = doc.selectFirst("form[action$=/users/login/2fa]")!!
        assertNotNull(form.selectFirst("input[name=code]")); assertEquals("post", form.attr("method").lowercase())
    }

    @Test
    fun `인증 앱 코드를 입력하면 로그인되고 원래 가려던 주소로 간다`() {
        val e = enrolled(); val session = MockHttpSession()
        passwordLogin(e, session, redirectUrl = "/user/editform")
        submit(currentCode(e), session).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/user/editform"))
        assertTrue(loggedIn(session))
    }

    @Test
    fun `틀린 코드는 로그인하지 못하고 2단계 화면으로 되돌아가며 안내를 남기고 실패를 기록한다`() {
        val e = enrolled(); val session = MockHttpSession(); passwordLogin(e, session)
        submit("000000".takeIf { it != currentCode(e) } ?: "111111", session).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/users/login/2fa"))
        assertTrue(!loggedIn(session))
        mockMvc.perform(get("/users/login/2fa").session(session)).andExpect(status().isOk)
    }

    @Test
    fun `백업 코드로도 로그인하고 한 번 쓴 코드는 다시 쓰지 못한다`() {
        val e = enrolled(); val first = MockHttpSession(); passwordLogin(e, first)
        submit(e.backupCodes[0], first).andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/"))
        assertTrue(loggedIn(first))
        val second = MockHttpSession(); passwordLogin(e, second)
        submit(e.backupCodes[0], second).andExpect(redirectedUrl("/users/login/2fa"))
        assertTrue(!loggedIn(second))
    }

    @Test
    fun `로그인 유지를 고른 로그인은 2단계를 마친 뒤에 쿠키를 만든다`() {
        val e = enrolled(); val session = MockHttpSession()
        val password = passwordLogin(e, session, rememberMe = true).andReturn()
        assertNull(password.response.getCookie(dev.yona.security.YonaAuthenticationFilter.REMEMBER_ME_COOKIE), "2단계 전에는 쿠키를 만들지 않는다")
        val done = submit(currentCode(e), session).andReturn()
        assertNotNull(done.response.getCookie(dev.yona.security.YonaAuthenticationFilter.REMEMBER_ME_COOKIE))
        val other = enrolled(); val plain = MockHttpSession(); passwordLogin(other, plain)
        assertNull(submit(currentCode(other), plain).andReturn().response.getCookie(dev.yona.security.YonaAuthenticationFilter.REMEMBER_ME_COOKIE), "고르지 않았으면 쿠키가 없다")
    }

    @Test
    fun `다섯 번 틀리면 잠기고 잠긴 동안은 맞는 코드도 받지 않으며 진행 중이던 로그인은 사라진다`() {
        val e = enrolled(); val session = MockHttpSession(); passwordLogin(e, session)
        repeat(5) { submit("00000$it", session).andExpect(status().is3xxRedirection) }
        assertTrue(!loggedIn(session))
        val again = MockHttpSession(); passwordLogin(e, again)
        submit(currentCode(e), again).andExpect(redirectedUrl("/users/loginform?redirectUrl="))
        assertTrue(!loggedIn(again), "잠긴 동안은 맞는 코드도 받지 않는다")
        attempts.clear(e.user.id!!)
        val fresh = MockHttpSession(); passwordLogin(e, fresh); submit(currentCode(e), fresh)
        assertTrue(loggedIn(fresh), "잠금을 풀면 다시 로그인된다")
    }

    @Test
    fun `진행 중인 로그인은 10분이 지나면 쓸 수 없다`() {
        val e = enrolled(); val session = MockHttpSession(); passwordLogin(e, session)
        attempts.advanceClock(java.time.Duration.ofMinutes(11))
        try {
            submit(currentCode(e), session).andExpect(redirectedUrl("/users/loginform?redirectUrl="))
            assertTrue(!loggedIn(session))
        } finally {
            attempts.resetClock()
        }
    }

    @Test
    fun `2단계 사이에 사용자가 잠기면 로그인하지 못한다`() {
        val e = enrolled(); val session = MockHttpSession(); passwordLogin(e, session)
        e.user.state = UserState.LOCKED; users.save(e.user)
        submit(currentCode(e), session).andExpect(status().is3xxRedirection)
        assertTrue(!loggedIn(session))
    }

    @Test
    fun `AJAX 로그인은 2단계가 필요하다고 JSON으로 알리고 코드를 JSON으로 확인한다`() {
        val e = enrolled(); val session = MockHttpSession()
        passwordLogin(e, session, json = true).andExpect(status().isOk).andExpect(jsonPath("$.twoFactor").value(true))
        assertTrue(!loggedIn(session))
        submit("000000".takeIf { it != currentCode(e) } ?: "111111", session, json = true).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.twoFactor.invalid"))
        submit(currentCode(e), session, json = true).andExpect(status().isOk)
        assertTrue(loggedIn(session))
    }

    @Test
    fun `AJAX에서 진행 중인 로그인이 없으면 코드 확인은 거부한다`() {
        submit("123456", MockHttpSession(), json = true).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("user.twoFactor.expired"))
    }

    @Test
    fun `2단계 인증을 켜지 않은 사용자는 지금처럼 곧바로 로그인한다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val plain = users.save(User(loginId = "tlp${++sequence}-${System.nanoTime()}", name = "plain", email = "tlp$sequence@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", plain.loginId!!).param("password", "1234")).andExpect(redirectedUrl("/"))
        assertTrue(loggedIn(session))
    }

    @Test
    fun `이 기기에서 묻지 않기를 고르면 기기 쿠키가 생기고 다음 로그인은 2단계를 건너뛴다`() {
        val e = enrolled(); val first = MockHttpSession(); passwordLogin(e, first)
        val done = submit(currentCode(e), first, rememberDevice = true).andReturn()
        val device = assertNotNull(done.response.getCookie(dev.yona.domain.twofactor.KnownDeviceService.COOKIE))
        assertTrue(device.isHttpOnly && device.maxAge == 30 * 24 * 60 * 60)
        val second = MockHttpSession()
        mockMvc.perform(post("/users/login").session(second).cookie(device).param("loginIdOrEmail", e.user.loginId!!).param("password", "1234")).andExpect(redirectedUrl("/"))
        assertTrue(loggedIn(second), "알려진 기기에서는 코드를 묻지 않는다")
        val other = enrolled(); val third = MockHttpSession()
        mockMvc.perform(post("/users/login").session(third).cookie(device).param("loginIdOrEmail", other.user.loginId!!).param("password", "1234")).andExpect(redirectedUrl("/users/login/2fa"))
    }

    @Test
    fun `고르지 않으면 기기 쿠키를 만들지 않는다`() {
        val e = enrolled(); val session = MockHttpSession(); passwordLogin(e, session)
        assertNull(submit(currentCode(e), session).andReturn().response.getCookie(dev.yona.domain.twofactor.KnownDeviceService.COOKIE))
    }

    @Test
    fun `로그인 유지 쿠키는 2단계 인증 사용자에게 알려진 기기 쿠키와 함께일 때만 통한다`() {
        val e = enrolled()
        val remember = jakarta.servlet.http.Cookie(dev.yona.security.YonaAuthenticationFilter.REMEMBER_ME_COOKIE, "${e.user.loginId}:${e.user.password}")
        val bare = mockMvc.perform(get("/user/editform").cookie(remember)).andReturn().response
        assertTrue(bare.status != 200, "기기 쿠키 없이는 로그인 유지 쿠키만으로 들어갈 수 없다")
        val session = MockHttpSession(); passwordLogin(e, session)
        val device = submit(currentCode(e), session, rememberDevice = true).andReturn().response.getCookie(dev.yona.domain.twofactor.KnownDeviceService.COOKIE)!!
        assertEquals(200, mockMvc.perform(get("/user/editform").cookie(remember, device)).andReturn().response.status)
    }
}
