package dev.yona.web.error

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional

/**
 * 끝 슬래시 제거 이동을 검증한다. v1.6 `conf/routes`의 마지막 줄(`GET`, 경로 `paths` 포괄 매개변수) -> `Application.removeTrailer`(`app/controllers/Application.java:94-104`).
 */
@AutoConfigureMockMvc
@Transactional
class TrailingSlashRedirectTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `일치하는 처리기가 없는 GET이 슬래시로 끝나면 슬래시를 뗀 주소로 303 이동한다`() {
        mockMvc.perform(get("/a/b/c/d/")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/a/b/c/d"))
        mockMvc.perform(get("/organizations/yona/")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/organizations/yona"))
    }

    @Test
    fun `쿼리 문자열은 이동 주소에 넣지 않는다`() {
        mockMvc.perform(get("/some/path/").param("x", "1")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/some/path"))
    }

    @Test
    fun `슬래시로 끝나지 않으면 404이다`() {
        mockMvc.perform(get("/no/such/path/here")).andExpect(status().isNotFound)
    }

    @Test
    fun `슬래시 하나만 있는 주소는 홈이다`() {
        mockMvc.perform(get("/")).andExpect(status().isOk)
    }

    @Test
    fun `GET이 아니면 이동하지 않고 404이다`() {
        mockMvc.perform(post("/a/b/")).andExpect(status().isNotFound)
    }

    @Test
    fun `이동한 주소가 맞는 처리기가 있으면 그대로 처리한다`() {
        // `/users/loginform/`은 맞는 처리기가 없어 `/users/loginform`으로 이동하고, 그 주소는 로그인 화면이다.
        mockMvc.perform(get("/users/loginform/")).andExpect(status().isSeeOther).andExpect(header().string("Location", "/users/loginform"))
        mockMvc.perform(get("/users/loginform")).andExpect(status().isOk)
    }
}
