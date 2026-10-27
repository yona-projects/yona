package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

/**
 * 리다이렉트의 `Location` 헤더는 ASCII만 담아야 한다.
 *
 * v1.6(Play 2.3)의 역방향 라우트는 경로 구간을 퍼센트 인코딩한다. 한글이 든 소유자, 프로젝트, 조직 이름을 그대로 헤더에 넣으면 Tomcat이
 * 헤더를 통째로 지워 브라우저가 이동하지 못한다.
 */
class RedirectLocationEncodingTest {

    private fun location(url: String): String? =
        RedirectSupport.see(MockHttpServletRequest(), MockHttpServletResponse(), url).headers.getFirst("Location")

    @Test
    fun `한글 경로는 퍼센트 인코딩한다`() {
        assertEquals("/owner/%ED%95%9C%EA%B8%80/issue/1", location("/owner/한글/issue/1"))
    }

    @Test
    fun `이미 인코딩된 부분과 ASCII 구분자는 그대로 둔다`() {
        assertEquals("/a/%ED%95%9C/b?x=1&y=%EA%B0%80#top", location("/a/%ED%95%9C/b?x=1&y=가#top"))
        assertEquals("/users/loginform?redirectUrl=/o/p", location("/users/loginform?redirectUrl=/o/p"))
    }

    @Test
    fun `공백과 허용되지 않는 글자도 인코딩한다`() {
        assertEquals("/a%20b/c%22d", location("/a b/c\"d"))
    }

    @Test
    fun `절대 주소의 호스트와 스킴은 건드리지 않는다`() {
        assertEquals("http://example.com/%ED%95%9C", location("http://example.com/한"))
    }
}
