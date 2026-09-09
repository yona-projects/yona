package com.github.yonaprojects.yona.config.oauth2server

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

// MCP 인가 스펙이 요구하는 401 응답: 토큰 없이(또는 잘못된
// 토큰으로) /mcp 엔드포인트를 호출하면 RFC9728 Protected Resource Metadata 위치를 가리키는
// `resource_metadata` 파라미터가 포함된 WWW-Authenticate 헤더를 내려줘야 한다(Claude 등 클라이언트가
// 이 헤더를 보고 인가 서버 위치를 자동 탐지). Spring 기본 BearerTokenAuthenticationEntryPoint는
// RFC9728보다 이전 코드라 resource_metadata 파라미터를 지원하지 않아 직접 구현한다.
class McpAuthenticationEntryPoint(private val resourceMetadataUri: String) : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader(
            "WWW-Authenticate",
            "Bearer resource_metadata=\"$resourceMetadataUri\""
        )
    }
}
