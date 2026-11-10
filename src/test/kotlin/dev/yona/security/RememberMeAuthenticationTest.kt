package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그인 유지 쿠키(`yona.token`)로 인증하는 동작을 필터 수준에서 고정하는 특성화 시험이다(로드맵 X-43 2단계 작업 2-1, `decisions.md` D-129).
 * 쿠키 읽기를 `RememberMeServices`로 옮기는 동안 이 시험이 수정 없이 통과해야 한다. 2단계 인증 사용자와 알려진 기기 쿠키의 결합은
 * `TwoFactorLoginTest`가, 쿠키 값의 서명과 만료는 `RememberMeCookiesTest`가 고정한다.
 *
 * 로그인 여부는 로그인해야 열리는 `/user/editform`이 200인지로 본다.
 */
@AutoConfigureMockMvc
@Transactional
class RememberMeAuthenticationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var cookies: RememberMeCookies
    @Autowired private lateinit var passwords: UserPasswordService

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val credentials = passwords.newCredentials("1234")
        return users.save(User(loginId = "rm$n-$stamp", name = "rm$n", email = "rm$n-$stamp@example.com", state = state, passwordSalt = credentials.salt, password = credentials.hash))
    }

    private fun open(cookie: Cookie?, session: MockHttpSession = MockHttpSession()) =
        mockMvc.perform(get("/user/editform").session(session).apply { cookie?.let { cookie(it) } }).andReturn().response

    private fun cleared(response: org.springframework.mock.web.MockHttpServletResponse) = response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE)?.maxAge == 0

    @Test
    fun `유효한 쿠키만으로 로그인되고 세션에 기록되어 다음 요청은 쿠키 없이도 통한다`() {
        val u = user(); val session = MockHttpSession()
        assertEquals(200, open(cookies.create(u, secure = false), session).status)
        assertEquals(200, open(null, session).status, "쿠키로 인증하면 세션에도 기록한다")
    }

    @Test
    fun `쿠키가 없으면 로그인되지 않고 지울 쿠키도 없다`() {
        val response = open(null)
        assertTrue(response.status != 200)
        assertNull(response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE))
    }

    @Test
    fun `서명이 맞지 않는 쿠키는 로그인되지 않고 쿠키를 지운다`() {
        val u = user(); val valid = cookies.create(u, secure = false)
        val tampered = Cookie(valid.name, valid.value.dropLast(3) + "xxx")
        val response = open(tampered)
        assertTrue(response.status != 200)
        assertTrue(cleared(response), "잘못된 쿠키는 지운다")
    }

    @Test
    fun `비밀번호를 바꾸면 이전 쿠키는 무효이고 지운다`() {
        val u = user(); val old = cookies.create(u, secure = false)
        passwords.set(u, "changed"); users.saveAndFlush(u)
        val response = open(old)
        assertTrue(response.status != 200)
        assertTrue(cleared(response))
    }

    @Test
    fun `기간이 지난 쿠키는 무효이고 지운다`() {
        val u = user()
        val expired = Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, cookies.valueFor(u, Instant.now().minusSeconds(31L * 24 * 3600)))
        val response = open(expired)
        assertTrue(response.status != 200)
        assertTrue(cleared(response))
    }

    @Test
    fun `잠긴 계정과 삭제된 계정은 쿠키가 맞아도 로그인되지 않고 쿠키를 지운다`() {
        listOf(UserState.LOCKED, UserState.DELETED).forEach { state ->
            val u = user(state)
            val response = open(cookies.create(u, secure = false))
            assertTrue(response.status != 200, "상태 $state")
            assertTrue(cleared(response), "상태 $state")
        }
    }

    @Test
    fun `없는 사용자의 쿠키는 로그인되지 않고 쿠키를 지운다`() {
        val u = user(); val cookie = cookies.create(u, secure = false)
        users.delete(u); users.flush()
        val response = open(cookie)
        assertTrue(response.status != 200)
        assertTrue(cleared(response))
        assertNotNull(response)
    }

    @Test
    fun `이미 로그인한 세션이 있으면 쿠키는 보지 않는다`() {
        val u = user(); val session = MockHttpSession()
        assertEquals(200, open(cookies.create(u, secure = false), session).status)
        val response = open(Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, "garbage"), session)
        assertEquals(200, response.status, "세션이 유효하면 쿠키를 읽지 않는다")
        assertNull(response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE), "읽지 않으므로 지우지도 않는다")
    }
}
