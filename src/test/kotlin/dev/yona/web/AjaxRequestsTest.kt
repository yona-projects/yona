package dev.yona.web

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals

/** v1.6 `HttpUtil.isRequestedWithXHR`(값을 소문자로 바꿔 비교)와 `isPJAXRequest`(`Boolean.parseBoolean`)를 검증한다. */
class AjaxRequestsTest {

    private fun xhr(value: String?) = AjaxRequests.isXhr(MockHttpServletRequest().also { r -> value?.let { r.addHeader("X-Requested-With", it) } })

    private fun pjax(value: String?) = AjaxRequests.isPjax(MockHttpServletRequest().also { r -> value?.let { r.addHeader("X-PJAX", it) } })

    @Test
    fun `X-Requested-With 값은 대소문자를 가리지 않는다`() {
        for (value in listOf("XMLHttpRequest", "xmlhttprequest", "XMLHTTPREQUEST", "xMlHtTpReQuEsT")) assertEquals(true, xhr(value), value)
        for (value in listOf("", "fetch", "XMLHttpRequest2")) assertEquals(false, xhr(value), value)
        assertEquals(false, xhr(null))
    }

    @Test
    fun `X-PJAX는 true만 받고 대소문자를 가리지 않는다`() {
        for (value in listOf("true", "TRUE", "True")) assertEquals(true, pjax(value), value)
        for (value in listOf("1", "yes", "", "false")) assertEquals(false, pjax(value), value)
        assertEquals(false, pjax(null))
    }
}
