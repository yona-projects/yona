package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [JsonCharsetFilter]가 응답 헤더를 v1.6과 같게 다듬는지 검증한다. */
class JsonCharsetFilterTest {

    private fun run(attribute: Boolean = false, body: (jakarta.servlet.http.HttpServletResponse) -> Unit): MockHttpServletResponse {
        val request = MockHttpServletRequest("GET", "/x")
        if (attribute) request.setAttribute(JsonCharsetFilter.RAW_ATTRIBUTE, true)
        val response = MockHttpServletResponse()
        val chain = MockFilterChain(object : jakarta.servlet.http.HttpServlet() {
            override fun doGet(req: jakarta.servlet.http.HttpServletRequest, res: jakarta.servlet.http.HttpServletResponse) = body(res)
        })
        JsonCharsetFilter().doFilter(request, response, chain)
        return response
    }

    @Test
    fun `JSON 내용 형식에 문자 집합을 붙인다`() {
        assertEquals("application/json;charset=utf-8", run { it.contentType = "application/json" }.contentType)
        assertEquals("application/json", run(attribute = true) { it.contentType = "application/json" }.contentType)
    }

    @Test
    fun `스프링의 파일 내려받기 방지 헤더는 내보내지 않는다`() {
        assertNull(run { it.setHeader("Content-Disposition", "inline;filename=f.txt") }.getHeader("Content-Disposition"))
        assertEquals("attachment; filename=a.zip", run { it.setHeader("Content-Disposition", "attachment; filename=a.zip") }.getHeader("Content-Disposition"))
    }
}
