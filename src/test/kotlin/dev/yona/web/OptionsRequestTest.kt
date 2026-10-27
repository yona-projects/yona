package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * `OPTIONS` 요청은 어느 주소든 `404`이다. Play 2.3은 `OPTIONS`를 자동으로 처리하지 않고 라우트에도 없으므로 "Page not found" 화면으로 답한다.
 * 스프링은 기본으로 `200`과 `Allow` 헤더로 답하므로 막아야 같다.
 */
@AutoConfigureMockMvc
class OptionsRequestTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `라우트가 있는 주소의 OPTIONS도 404이고 Allow 헤더가 없다`() {
        mockMvc.perform(options("/files/1")).andExpect(status().isNotFound).andExpect(header().doesNotExist("Allow"))
        mockMvc.perform(options("/")).andExpect(status().isNotFound)
    }

    @Test
    fun `없는 주소의 OPTIONS도 404이다`() {
        mockMvc.perform(options("/nonexistent/x")).andExpect(status().isNotFound)
    }

    @Test
    fun `라우트가 없는 GET의 404에는 Cache-Control no-cache가 붙고 다른 방식에는 붙지 않는다`() {
        // v1.6은 맨 아래 포괄 라우트(GET)의 액션이 기본 헤더를 붙인다. 다른 방식의 라우트 없음은 액션 밖의 404이다
        mockMvc.perform(get("/files/5/x")).andExpect(status().isNotFound).andExpect(header().string("Cache-Control", "no-cache"))
        mockMvc.perform(post("/files/5/x")).andExpect(status().isNotFound).andExpect(header().doesNotExist("Cache-Control"))
        mockMvc.perform(post("/files/5/")).andExpect(status().isNotFound).andExpect(header().doesNotExist("Cache-Control"))
    }

    @Test
    fun `파일 주소의 끝 슬래시 GET은 슬래시를 떼어 이동하고 POST는 404이다`() {
        mockMvc.perform(get("/files/5/")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/files/5"))
    }
}
