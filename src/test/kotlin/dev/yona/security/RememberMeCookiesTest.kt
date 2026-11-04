package dev.yona.security

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그인 유지 쿠키(`yobi.token`)를 검증한다. v1.6은 값이 `로그인ID:해시된 비밀번호`라 쿠키가 새면 해시가 그대로 드러났고(R-05), 잠기거나 지워진 계정도 쿠키로 통했다(R-107).
 * 지금은 서명한 값이며 비밀번호 해시를 담지 않고 비밀번호가 바뀌면 무효이다.
 */
class RememberMeCookiesTest {

    private val cookies = RememberMeCookies(YonaProperties(secret = "unit-test-secret"))
    private val now = Instant.parse("2026-11-04T00:00:00Z")

    private fun user(password: String = "hashed-password", state: UserState = UserState.ACTIVE) =
        User(loginId = "alice", name = "앨리스", email = "alice@example.com", state = state, password = password)

    @Test
    fun `쿠키 값에 비밀번호 해시가 들어 있지 않다 (R-05)`() {
        val value = cookies.valueFor(user(), now)
        assertFalse(value.contains("hashed-password"), value)
        assertTrue(Regex("alice:\\d+:[A-Za-z0-9_-]+").matches(value), value)
    }

    @Test
    fun `만든 쿠키는 같은 사용자에게 통한다`() {
        val value = cookies.valueFor(user(), now)
        assertTrue(cookies.verify(value, user(), now.plusSeconds(60)))
    }

    @Test
    fun `비밀번호가 바뀌면 무효이다`() {
        val value = cookies.valueFor(user(), now)
        assertFalse(cookies.verify(value, user(password = "changed"), now.plusSeconds(60)))
    }

    @Test
    fun `기간이 지났거나 값이 바뀌었거나 옛 형식이면 무효이다`() {
        val value = cookies.valueFor(user(), now)
        assertFalse(cookies.verify(value, user(), now.plusSeconds(YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE + 1L)))
        assertFalse(cookies.verify(value.dropLast(2) + "xx", user(), now.plusSeconds(60)))
        assertFalse(cookies.verify(value.replace("alice:", "alice:9"), user(), now.plusSeconds(60)))
        // v1.6 형식: 로그인ID:해시된 비밀번호
        assertFalse(cookies.verify("alice:hashed-password", user(), now.plusSeconds(60)))
        assertFalse(cookies.verify("", user(), now))
        assertNull(RememberMeCookies.loginIdOf("alice:hashed-password-only-two-parts"))
        assertEquals("alice", RememberMeCookies.loginIdOf(value))
        assertNotNull(cookies.valueFor(user(), now))
    }

    @Test
    fun `다른 비밀 키로 서명한 쿠키는 무효이다`() {
        val other = RememberMeCookies(YonaProperties(secret = "another-secret"))
        assertFalse(cookies.verify(other.valueFor(user(), now), user(), now.plusSeconds(60)))
    }
}
