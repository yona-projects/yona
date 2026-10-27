package dev.yona.web.access

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * HTTP Basic 자격을 읽는 [BasicCredentials.parse]를 검증한다.
 *
 * v1.6은 commons-codec `decodeBase64`로 읽어 알파벳 밖의 글자와 넘치는 패딩을 무시했다. 기대값은 옛 라이브러리를 직접 실행해 얻었다.
 */
class BasicCredentialsTest {

    private fun parse(value: String): BasicCredentials? {
        val request = MockHttpServletRequest().also { it.addHeader("Authorization", "Basic $value") }
        return BasicCredentials.parse(request)
    }

    private fun assertUserPass(value: String) {
        val credentials = parse(value)!!
        assertEquals("user", credentials.loginId, value)
        assertEquals("pass", credentials.password, value)
    }

    @Test
    fun `올바른 Base64는 그대로 읽는다`() {
        assertUserPass("dXNlcjpwYXNz")
    }

    @Test
    fun `넘치는 패딩과 알파벳 밖의 글자를 무시하고 읽는다 (v1_6 commons-codec)`() {
        assertUserPass("dXNlcjpwYXNz=")
        assertUserPass("dXNlcjpwYXNz====")
        assertUserPass("dXNl cjpw\nYXNz")
        assertUserPass("dXNl!cjpw*YXNz")
    }

    @Test
    fun `URL 안전 문자도 읽는다`() {
        // "user:pass?>>" 를 URL 안전 알파벳으로 쓴 값
        assertEquals("user:pass?>>", java.util.Base64.getUrlEncoder().encodeToString("user:pass?>>".toByteArray()).let { parse(it)!!.let { c -> "${c.loginId}:${c.password}" } })
    }

    @Test
    fun `콜론이 없으면 형식이 틀린 자격이다`() {
        assertSame(BasicCredentials.MALFORMED, parse(java.util.Base64.getEncoder().encodeToString("nocolon".toByteArray())))
    }
}
