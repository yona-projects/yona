package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 로그인 뒤 이동할 주소가 같은 사이트 안의 경로인지 가리는 규칙을 검증한다(R-06, C013, R-59-10). v1.6은 값을 검사하지 않아 외부 주소로 보낼 수 있었다. */
class RedirectTargetTest {

    private fun request(host: String = "yona.test:9000") = MockHttpServletRequest().apply { addHeader("Host", host) }

    @Test
    fun `같은 사이트 안의 경로는 그대로 쓴다`() {
        assertEquals("/bob/project", RedirectSupport.localTarget("/bob/project", request()))
        assertEquals("/a/b?x=1#h", RedirectSupport.localTarget("/a/b?x=1#h", request()))
        assertEquals("/", RedirectSupport.localTarget("/", request()))
    }

    @Test
    fun `외부로 가는 주소는 거절한다`() {
        for (bad in listOf("//evil.example.com/x", "/\\evil.example.com", "\\\\evil.example.com", "https://evil.example.com/x", "javascript:alert(1)", "evil.example.com", "/ok\r\nSet-Cookie: a=b", "")) {
            assertNull(RedirectSupport.localTarget(bad, request()), bad)
        }
        assertNull(RedirectSupport.localTarget(null, request()))
    }

    @Test
    fun `이 서버 주소의 절대 주소는 경로로 바꿔 쓴다 (Referer)`() {
        assertEquals("/users/x?a=1", RedirectSupport.localTarget("http://yona.test:9000/users/x?a=1", request()))
        assertNull(RedirectSupport.localTarget("http://other.test/users/x", request()))
    }

    @Test
    fun `기본 로그인 페이지는 앞의 슬래시를 뺀 상대 경로만 받는다`() {
        assertEquals(true, RedirectSupport.isSafeRelativePath("notifications"))
        assertEquals(true, RedirectSupport.isSafeRelativePath("user/issues"))
        for (bad in listOf("//evil.example.com", "/abs", "https://evil.example.com", "javascript:alert(1)", "a\\b", "a\nb", "")) {
            assertEquals(false, RedirectSupport.isSafeRelativePath(bad), bad)
        }
    }
}
