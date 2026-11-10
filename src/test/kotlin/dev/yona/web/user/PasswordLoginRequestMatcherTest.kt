package dev.yona.web.user

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 비밀번호 로그인 필터가 맡을 요청을 가리는 [PasswordLoginRequestMatcher]를 검증한다(로드맵 X-43 3단계 작업 3-3, `decisions.md` D-129).
 * `POST /users/login`이고 로그인 ID와 비밀번호가 모두 비어 있지 않을 때만 맡는다. 입력이 빠진 요청은 v1.6처럼 로그인 화면을 400으로 다시 그려야 하므로
 * 화면을 그리는 컨트롤러가 맡는다.
 */
class PasswordLoginRequestMatcherTest {

    private val matcher = PasswordLoginRequestMatcher()

    private fun request(method: String = "POST", path: String = "/users/login", contextPath: String = "", vararg params: Pair<String, String>) =
        MockHttpServletRequest(method, contextPath + path).apply { this.contextPath = contextPath; params.forEach { (k, v) -> setParameter(k, v) } }

    private val both = arrayOf("loginIdOrEmail" to "tester", "password" to "1234")

    @Test
    fun `로그인 ID와 비밀번호가 있는 POST만 맡는다`() {
        assertTrue(matcher.matches(request(params = both)))
    }

    @Test
    fun `GET이나 다른 경로는 맡지 않는다`() {
        assertFalse(matcher.matches(request(method = "GET", params = both)))
        assertFalse(matcher.matches(request(path = "/users/loginform", params = both)))
        assertFalse(matcher.matches(request(path = "/users/login/2fa", params = both)))
    }

    @Test
    fun `입력이 빠졌거나 비어 있으면 맡지 않아 컨트롤러가 400으로 다시 그린다`() {
        assertFalse(matcher.matches(request(params = arrayOf("loginIdOrEmail" to "tester"))))
        assertFalse(matcher.matches(request(params = arrayOf("password" to "1234"))))
        assertFalse(matcher.matches(request(params = arrayOf("loginIdOrEmail" to "", "password" to "1234"))))
        assertFalse(matcher.matches(request(params = arrayOf("loginIdOrEmail" to "tester", "password" to ""))))
        assertFalse(matcher.matches(request()))
    }

    @Test
    fun `컨텍스트 경로가 있어도 같다`() {
        assertTrue(matcher.matches(request(contextPath = "/yona", params = both)))
    }
}
