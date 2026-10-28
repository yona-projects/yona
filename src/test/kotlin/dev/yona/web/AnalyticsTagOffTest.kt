package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertFalse

/**
 * `yona.send-yona-usage=false`이면 사용 현황 태그(X-21)가 화면에 나오지 않는다 (`decisions.md` A-186).
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.send-yona-usage=false"])
class AnalyticsTagOffTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login() = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString

    @Test
    fun `send-yona-usage를 끄면 GA4 태그도 UA 태그도 없다`() {
        val html = login()
        assertFalse(html.contains("googletagmanager.com"))
        assertFalse(html.contains("google-analytics.com"))
    }
}
