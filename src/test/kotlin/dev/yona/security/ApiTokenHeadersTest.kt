package dev.yona.security

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 토큰 헤더 분석을 검증한다(X-5, `architecture/api-token.md` 구현 점검 1번). 형식을 엄격하게 읽는다. */
class ApiTokenHeadersTest {

    private fun request(authorization: String? = null, yonaToken: String? = null) = MockHttpServletRequest().apply {
        authorization?.let { addHeader("Authorization", it) }
        yonaToken?.let { addHeader("Yona-Token", it) }
    }

    @Test
    fun `token 방식과 Bearer 방식의 값을 읽고 방식 이름은 대소문자를 구분하지 않는다`() {
        assertEquals("abc123", ApiTokenHeaders.extract(request("token abc123")))
        assertEquals("abc123", ApiTokenHeaders.extract(request("Token abc123")))
        assertEquals("abc123", ApiTokenHeaders.extract(request("Bearer abc123")))
        assertEquals("abc123", ApiTokenHeaders.extract(request("bearer   abc123  ")))
    }

    @Test
    fun `Yona-Token 헤더를 읽고 Authorization이 있으면 그것이 먼저이다`() {
        assertEquals("zzz", ApiTokenHeaders.extract(request(yonaToken = " zzz ")))
        assertEquals("first", ApiTokenHeaders.extract(request("token first", "second")))
    }

    @Test
    fun `형식이 틀리면 토큰이 없는 것으로 본다`() {
        assertNull(ApiTokenHeaders.extract(request()))
        assertNull(ApiTokenHeaders.extract(request("tokenabc")), "공백 없이 붙은 값")
        assertNull(ApiTokenHeaders.extract(request("token ")))
        assertNull(ApiTokenHeaders.extract(request("token a b")), "값에 공백이 있으면 안 된다")
        assertNull(ApiTokenHeaders.extract(request("Basic dXNlcjpwYXNz")), "Basic 자격은 토큰이 아니다")
        assertNull(ApiTokenHeaders.extract(request("my token abc")), "방식 이름은 맨 앞이어야 한다")
        assertNull(ApiTokenHeaders.extract(request(yonaToken = "  ")))
    }
}
