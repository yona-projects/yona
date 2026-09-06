package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import java.security.interfaces.RSAPublicKey

// yona-wiki P3-07(MCP 서버) Step2 — yona 자신이 MCP 리소스 서버(Resource Server) 역할을 하는 설정.
// `/mcp/**`(Step3에서 실제 MCP 도구 엔드포인트가 붙는다)만 이 체인이 담당하고, 그 외 모든 경로는
// 기존 SecurityConfig(@Order 낮음, catch-all)로 그대로 위임한다.
//
// 이중 인증 지원(계획 문서 요구사항): OAuth2 JWT(대화형 클라이언트)와 [[p3-02]] Fine-grained PAT
// (헤드리스 클라이언트) 둘 다 허용 — ApiTokenAuthenticationFilter를 BearerTokenAuthenticationFilter
// 앞에 추가해, PAT 헤더(Authorization: token .../Yona-Token)가 있으면 그쪽으로 먼저 인증하고, 없으면
// JWT 처리로 넘어간다(같은 순서 원칙이 `/api/v1/projects/**`에도 이미 적용돼 있다 —
// ApiTokenAuthenticationFilter의 KDoc 참고).
@Configuration
class ResourceServerConfig(
    private val jwkKeyPairProvider: JwkKeyPairProvider,
    private val apiTokenAuthenticationFilter: ApiTokenAuthenticationFilter,
    @Value("\${yona.base-url:http://localhost:8080}")
    private val baseUrl: String
) {

    private val mcpResourceUri get() = "$baseUrl/mcp"
    private val resourceMetadataUri get() = "$baseUrl/.well-known/oauth-protected-resource/mcp"

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(jwkKeyPairProvider.keyPair.public as RSAPublicKey).build()
        decoder.setJwtValidator(
            JwtValidators.createDefaultWithValidators(AudienceValidator(mcpResourceUri))
        )
        return decoder
    }

    @Bean
    @Order(2)
    fun mcpResourceServerSecurityFilterChain(http: HttpSecurity, jwtDecoder: JwtDecoder): SecurityFilterChain {
        http
            .securityMatcher("/mcp/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { rs ->
                rs.jwt { it.decoder(jwtDecoder) }
                rs.authenticationEntryPoint(McpAuthenticationEntryPoint(resourceMetadataUri))
            }
            .addFilterBefore(apiTokenAuthenticationFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }
}
