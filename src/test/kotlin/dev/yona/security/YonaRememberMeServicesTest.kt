package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그인 유지 쿠키를 스프링 `RememberMeServices`로 다루는 [YonaRememberMeServices]를 검증한다(로드맵 X-43 2단계 작업 2-1, `decisions.md` D-129).
 * 쿠키 이름과 값 형식은 그대로이고(`RememberMeCookiesTest`), 쿠키만으로 로그인하는 필터 수준의 동작은 `RememberMeAuthenticationTest`가 고정한다.
 */
@Transactional
class YonaRememberMeServicesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var services: YonaRememberMeServices
    @Autowired private lateinit var cookies: RememberMeCookies
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var passwords: UserPasswordService

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val credentials = passwords.newCredentials("1234")
        return users.save(User(loginId = "ys$n-$stamp", name = "ys$n", email = "ys$n-$stamp@example.com", state = state, passwordSalt = credentials.salt, password = credentials.hash))
    }

    private fun request(cookie: Cookie? = null) = MockHttpServletRequest().apply { cookie?.let { setCookies(it) } }

    @Test
    fun `쿠키가 없으면 자동 로그인하지 않고 응답을 건드리지 않는다`() {
        val response = MockHttpServletResponse()
        assertNull(services.autoLogin(request(), response))
        assertNull(response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE))
    }

    @Test
    fun `유효한 쿠키는 쿠키로 인증한 YonaAuthentication을 돌려준다`() {
        val u = user()
        val authentication = assertIs<YonaAuthentication>(services.autoLogin(request(cookies.create(u, secure = false)), MockHttpServletResponse()))
        assertEquals(u.id, authentication.principal.userId)
        assertTrue(authentication.viaCookie)
        assertEquals(false, authentication.viaToken)
        assertEquals(null, authentication.scopes)
    }

    @Test
    fun `잘못된 쿠키와 잠긴 계정과 비밀번호를 바꾼 계정은 자동 로그인하지 않고 쿠키를 지운다`() {
        val valid = user(); val tampered = Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, cookies.valueFor(valid).dropLast(3) + "xxx")
        val locked = user(UserState.LOCKED)
        val changed = user(); val old = cookies.create(changed, secure = false); passwords.set(changed, "other"); users.saveAndFlush(changed)
        listOf(tampered, cookies.create(locked, secure = false), old).forEach { cookie ->
            val response = MockHttpServletResponse()
            assertNull(services.autoLogin(request(cookie), response))
            assertEquals(0, response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE)?.maxAge, "지워야 한다")
        }
    }

    @Test
    fun `로그인 성공을 알리면 그 사용자의 로그인 유지 쿠키를 응답에 싣는다`() {
        val u = user(); val response = MockHttpServletResponse()
        services.loginSuccess(request(), response, YonaAuthentication.of(u))
        val cookie = assertNotNull(response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE))
        assertTrue(cookies.verify(cookie.value, u))
        assertTrue(cookie.isHttpOnly)
        assertEquals(YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE, cookie.maxAge)
    }

    @Test
    fun `https 요청이면 쿠키를 보안 쿠키로 만든다`() {
        val response = MockHttpServletResponse()
        services.loginSuccess(MockHttpServletRequest().apply { isSecure = true }, response, YonaAuthentication.of(user()))
        assertTrue(response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE)!!.secure)
    }

    @Test
    fun `로그인 실패나 로그아웃을 알리면 쿠키를 지운다`() {
        val response = MockHttpServletResponse()
        services.loginFail(request(), response)
        assertEquals(0, response.getCookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE)?.maxAge)
    }
}
