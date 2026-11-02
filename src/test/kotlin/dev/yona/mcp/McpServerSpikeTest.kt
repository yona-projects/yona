package dev.yona.mcp

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * MCP 서버의 뼈대(X-6 단계 0, `decisions.md` D-72)를 확인한다: Spring AI 스타터가 세션 없는(stateless) Streamable HTTP로 `/mcp`를 열고,
 * 초기화와 도구 목록 요청에 일반 JSON으로 답한다.
 */
@AutoConfigureMockMvc
class McpServerSpikeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    private fun rpc(body: String) = post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(body)

    @Test
    fun `초기화 요청에 서버 정보와 도구 기능을 JSON으로 답한다`() {
        mockMvc.perform(rpc("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.serverInfo.name").value("yona-mcp-server")).andExpect(jsonPath("$.result.capabilities.tools").exists())
    }

    @Test
    fun `도구 목록 요청에 JSON으로 답한다`() {
        mockMvc.perform(rpc("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")).andExpect(status().isOk).andExpect(jsonPath("$.result.tools").isArray)
    }

    @Test
    fun `GET은 SSE를 제공하지 않으므로 405이다`() {
        mockMvc.perform(get("/mcp").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isMethodNotAllowed)
    }
}
