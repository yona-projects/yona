package dev.yona.web

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import org.springframework.http.HttpMethod
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals

/**
 * 변칙 요청에서 v1.6(Play)의 응답을 따르는지 검증한다. 공식 v1.16.0에 소켓으로 직접 보내 확인한 값이다(`tools/v16-diff/weird.py`).
 */
@AutoConfigureMockMvc
class MalformedRequestTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `error 주소는 스프링의 오류 화면이 아니라 사용자 error의 화면이다`() {
        // v1.6에서 `/error`는 `/:user` 라우트로 가서 없는 사용자의 404이다
        val html = mockMvc.perform(get("/error")).andExpect(status().isNotFound).andReturn().response.contentAsString
        assertEquals(true, html.contains("User exists not"), html)
    }

    @Test
    fun `WebDAV 메서드는 어느 주소로 와도 svn 서비스가 받는다 (주소가 짧으면 403)`() {
        // v1.6 Global.onRouteRequest: 메서드가 WebDAV이면 경로와 상관없이 SvnApp.service. 구간이 3개 미만이면 403
        listOf("PROPFIND", "MKCOL", "REPORT", "LOCK", "propfind").forEach { method ->
            listOf("/", "/tester", "/tester/proj1", "/svn", "/svn/tester").forEach { path ->
                mockMvc.perform(request(HttpMethod.valueOf(method.uppercase()), path)).andExpect(status().isForbidden)
            }
        }
        // 구간이 3개 이상이면 두 번째 구간을 소유자로 읽어 프로젝트를 찾는다. 없으면 404
        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/tester/proj1/issues")).andExpect(status().isNotFound)
    }
}
