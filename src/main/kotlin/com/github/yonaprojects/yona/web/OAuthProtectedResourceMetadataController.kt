package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.oauth2server.McpOAuthScopes
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

// yona-wiki P3-07(MCP 서버) Step2 — RFC9728(Protected Resource Metadata) 응답. MCP 리소스 서버가
// 401 응답의 WWW-Authenticate 헤더로 이 위치를 가리켜야 하고(ResourceServerConfig 참고), 이 문서가
// 실제 인가 서버 위치/지원 스코프를 광고한다. `/mcp`가 리소스 경로이므로 RFC9728 §3.1에 따라
// `/.well-known/oauth-protected-resource/mcp`가 정식 경로이지만, 일부 클라이언트가 접두어 없는
// 경로를 먼저 시도하는 경우까지 대응하기 위해 둘 다 동일한 문서를 제공한다.
@RestController
class OAuthProtectedResourceMetadataController(
    @Value("\${yona.base-url:http://localhost:8080}")
    private val baseUrl: String
) {

    @GetMapping("/.well-known/oauth-protected-resource", "/.well-known/oauth-protected-resource/mcp")
    fun metadata(): Map<String, Any> {
        return mapOf(
            "resource" to "$baseUrl/mcp",
            "authorization_servers" to listOf(baseUrl),
            "bearer_methods_supported" to listOf("header"),
            "scopes_supported" to McpOAuthScopes.ALL.toList()
        )
    }
}
