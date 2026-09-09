package com.github.yonaprojects.yona.mcp

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// spring-ai-starter-mcp-server-webmvc 자동설정이 이 ToolCallbackProvider 빈을 찾아 등록된 모든
// @Tool 메서드를 /mcp(Streamable HTTP, application.yml의 spring.ai.mcp.server.protocol=STREAMABLE)
// 아래에 자동으로 노출한다 — MCP 프로토콜 자체(JSON-RPC 메시지, 세션 관리, 도구 스키마 생성 등)를
// 직접 구현하지 않는다.
@Configuration
class McpToolsConfig(
    private val issueMcpTools: IssueMcpTools,
    private val pullRequestMcpTools: PullRequestMcpTools
) {
    @Bean
    fun mcpToolCallbackProvider(): ToolCallbackProvider =
        MethodToolCallbackProvider.builder()
            .toolObjects(issueMcpTools, pullRequestMcpTools)
            .build()
}
