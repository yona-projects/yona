package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

/** v1.6 `HttpUtil.isRequestedWithXHR`(값을 소문자로 바꿔 비교)와 htmx 요청 판별(`HX-Request`, `Boolean.parseBoolean`, D-98)을 검증한다. */
class AjaxRequestsTest {

    private fun xhr(value: String?) = AjaxRequests.isXhr(MockHttpServletRequest().also { r -> value?.let { r.addHeader("X-Requested-With", it) } })

    private fun htmx(value: String?) = AjaxRequests.isHtmx(MockHttpServletRequest().also { r -> value?.let { r.addHeader("HX-Request", it) } })

    @Test
    fun `X-Requested-With 값은 대소문자를 가리지 않는다`() {
        for (value in listOf("XMLHttpRequest", "xmlhttprequest", "XMLHTTPREQUEST", "xMlHtTpReQuEsT")) assertEquals(true, xhr(value), value)
        for (value in listOf("", "fetch", "XMLHttpRequest2")) assertEquals(false, xhr(value), value)
        assertEquals(false, xhr(null))
    }

    @Test
    fun `HX-Request는 true만 받고 대소문자를 가리지 않는다`() {
        for (value in listOf("true", "TRUE", "True")) assertEquals(true, htmx(value), value)
        for (value in listOf("1", "yes", "", "false")) assertEquals(false, htmx(value), value)
        assertEquals(false, htmx(null))
    }

    @Test
    fun `옛 X-PJAX 헤더는 더 이상 조각 요청으로 보지 않는다`() {
        val request = MockHttpServletRequest().also { it.addHeader("X-PJAX", "true"); it.addHeader("X-Requested-With", "XMLHttpRequest") }
        assertEquals(false, AjaxRequests.isHtmx(request))
    }
}
