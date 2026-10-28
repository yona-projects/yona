package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 기본 설정(`yona.send-yona-usage=true`)에서 UA 태그 대신 GA4 태그(X-21)가 나오고, 측정 ID가 들어가고(설치 ID는 보내지 않는다) 경로를 가려서 보내는지 검증한다.
 */
@AutoConfigureMockMvc
class AnalyticsTagTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login() = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString

    @Test
    fun `기본 설정에서 UA 태그 대신 GA4 태그가 나온다`() {
        val html = login()
        assertTrue(html.contains("googletagmanager.com/gtag/js"))
        assertTrue(html.contains("G-CKTN17HLPP"))
        assertTrue(html.contains("maskPath"))
        assertFalse(html.contains("install_id"))
        assertFalse(html.contains("google-analytics.com"))
        assertFalse(html.contains("UA-102735758-1"))
    }
}
