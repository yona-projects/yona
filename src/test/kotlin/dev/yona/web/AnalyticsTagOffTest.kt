package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertFalse

/**
 * GA4 태그(X-21)는 기본 설정에서 화면에 나오지 않는다. 기본값은 꺼짐이다 (`decisions.md` A-186).
 */
@AutoConfigureMockMvc
class AnalyticsTagOffTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login() = mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString

    @Test
    fun `기본 설정에서는 GA4 태그가 없다`() {
        assertFalse(login().contains("googletagmanager.com"))
    }
}
