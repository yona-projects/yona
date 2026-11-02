package dev.yona.mcp

import org.springframework.ai.tool.ToolCallbackProvider
import org.springframework.ai.tool.method.MethodToolCallbackProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * MCP 서버에 도구를 등록한다(추가 개발 항목 X-6, `decisions.md` D-72). Spring AI 스타터가 이 [ToolCallbackProvider]를 찾아 `/mcp`로 알린다.
 */
@Configuration
class McpToolsConfig {

    @Bean
    fun mcpToolCallbackProvider(read: McpReadTools, write: McpWriteTools): ToolCallbackProvider = MethodToolCallbackProvider.builder().toolObjects(read, write).build()
}
