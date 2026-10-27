package dev.yona.web

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals

/** 컨트롤러가 `Location` 헤더를 직접 설정해도 ASCII로 나가는지 검증한다 (v1.6 역방향 라우트는 경로 구간을 퍼센트 인코딩한다). */
class LocationEncodingFilterTest {

    private fun run(contextPath: String = "", block: (jakarta.servlet.http.HttpServletResponse) -> Unit): MockHttpServletResponse {
        val response = MockHttpServletResponse()
        val request = MockHttpServletRequest().also { it.contextPath = contextPath }
        LocationEncodingFilter().doFilter(request, response, FilterChain { _, res -> block(res as jakarta.servlet.http.HttpServletResponse) })
        return response
    }

    @Test
    fun `setHeader로 넣은 Location의 한글을 인코딩한다`() {
        assertEquals("/o/%ED%95%9C", run { it.setHeader("Location", "/o/한") }.getHeader("Location"))
        assertEquals("/o/%ED%95%9C", run { it.setHeader("location", "/o/한") }.getHeader("Location"))
    }

    @Test
    fun `addHeader도 같다`() {
        assertEquals("/o/%ED%95%9C", run { it.addHeader("Location", "/o/한") }.getHeader("Location"))
    }

    @Test
    fun `다른 헤더는 건드리지 않는다`() {
        assertEquals("한글", run { it.setHeader("X-Title", "한글") }.getHeader("X-Title"))
    }

    @Test
    fun `컨텍스트 경로가 있으면 사이트 안의 절대 경로에 붙인다 (v1_6 역방향 라우트는 application_context를 붙인다)`() {
        assertEquals("/myroot/o/p", run("/myroot") { it.setHeader("Location", "/o/p") }.getHeader("Location"))
        assertEquals("/myroot/o/%ED%95%9C", run("/myroot") { it.setHeader("Location", "/o/한") }.getHeader("Location"))
    }

    @Test
    fun `이미 컨텍스트 경로로 시작하거나 절대 주소이면 붙이지 않는다`() {
        assertEquals("/myroot/o/p", run("/myroot") { it.setHeader("Location", "/myroot/o/p") }.getHeader("Location"))
        assertEquals("/myroot", run("/myroot") { it.setHeader("Location", "/myroot") }.getHeader("Location"))
        assertEquals("http://example.com/o", run("/myroot") { it.setHeader("Location", "http://example.com/o") }.getHeader("Location"))
        assertEquals("o/p", run("/myroot") { it.setHeader("Location", "o/p") }.getHeader("Location"))
        // 컨텍스트 경로와 앞부분만 같은 경로는 사이트 안의 다른 경로이므로 접두어를 붙인다
        assertEquals("/myroot/myrooted/x", run("/myroot") { it.setHeader("Location", "/myrooted/x") }.getHeader("Location"))
    }

    @Test
    fun `이미 인코딩된 Location은 그대로이다`() {
        assertEquals("/o/%ED%95%9C?x=1", run { it.setHeader("Location", "/o/%ED%95%9C?x=1") }.getHeader("Location"))
    }
}
