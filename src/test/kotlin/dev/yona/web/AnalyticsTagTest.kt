package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertTrue

/**
 * GA4 태그(X-21)가 켜졌을 때 측정 ID와 설치 ID가 들어간 태그가 나오고 경로를 가려서 보내는지 검증한다. v1.6의 UA 태그(`yona.send-yona-usage`)와는 따로 동작한다.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["yona.analytics.send-usage=true", "yona.analytics.measurement-id=G-TESTID", "yona.analytics.install-id-path=build/tmp/analytics-test/install-id"])
class AnalyticsTagTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login() = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString

    @Test
    fun `켜면 측정 ID와 설치 ID가 들어간 태그가 나온다`() {
        val html = login()
        assertTrue(html.contains("googletagmanager.com/gtag/js"))
        assertTrue(html.contains("G-TESTID"))
        assertTrue(html.contains("maskPath"))
        assertTrue(html.contains("install_id"))
    }
}
