package com.github.yonaprojects.yona.config.oauth2server

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.stereotype.Component

// RFC8707 §3의 공식 오류 코드. Spring의 OAuth2ErrorCodes 상수 목록에는 아직 없어 리터럴로 둔다.
private const val INVALID_TARGET = "invalid_target"

// yona-wiki P3-07(MCP 서버) Step2 — RFC8707(Resource Indicators) 오디언스 스탬핑. 계획 문서
// "완료 로그 — Step 1"에서 결정한 대로, Spring Authorization Server가 기본 제공하지 않는 이 부분만
// 직접 구현한다(PKCE/DCR은 Spring이 기본 제공).
//
// MCP 클라이언트(Claude 등)는 `/oauth2/token` 요청에 `resource` 파라미터로 이 MCP 리소스 서버의
// URI를 명시해야 한다(MCP 인가 스펙 MUST). 이 값을 그대로 액세스 토큰의 `aud` 클레임에 스탬핑해,
// 리소스 서버(ResourceServerConfig)가 자신 앞으로 발급된 토큰인지 검증할 수 있게 한다 — 다른
// 리소스 서버용으로 발급된 토큰을 그대로 받아주는 "토큰 패스스루" 취약점을 막는 핵심 지점이다
// (계획 문서의 보안 검증 항목).
@Component
class ResourceIndicatorTokenCustomizer(
    @Value("\${yona.base-url:http://localhost:8080}")
    private val baseUrl: String
) : OAuth2TokenCustomizer<JwtEncodingContext> {

    override fun customize(context: JwtEncodingContext) {
        if (context.tokenType != OAuth2TokenType.ACCESS_TOKEN) return

        val grant = context.getAuthorizationGrant<org.springframework.security.core.Authentication>()
        val resource = (grant as? OAuth2AuthorizationGrantAuthenticationToken)
            ?.additionalParameters
            ?.get(OAuth2ParameterNames.RESOURCE) as? String

        if (resource.isNullOrBlank() || resource != mcpResourceUri) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    INVALID_TARGET,
                    "resource 파라미터가 없거나 이 MCP 리소스 서버($mcpResourceUri)를 가리키지 않습니다.",
                    null
                )
            )
        }

        context.claims.audience(listOf(resource))
    }

    val mcpResourceUri: String
        get() = "$baseUrl/mcp"
}
