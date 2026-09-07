package com.github.yonaprojects.yona.config.oauth2server

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import java.security.interfaces.RSAPublicKey

// yona-wiki P3-07(MCP 서버) Step2 — yona 자신이 리소스 서버(Resource Server) 역할을 하는 설정.
// yona-wiki P3-14 1라운드 — 원래 `/mcp/**` 하나만 담당했으나, 제3자 앱의 REST API 위임 접근을
// 지원하기 위해 `/api/v1/**`용 체인을 하나 더 추가했다(ProtectedResource 레지스트리 참고). 두 체인
// 모두 그 외 모든 경로는 기존 SecurityConfig(@Order 낮음, catch-all)로 그대로 위임한다.
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

    private val mcpResourceUri get() = ProtectedResource.MCP.uri(baseUrl)
    private val apiResourceUri get() = ProtectedResource.API.uri(baseUrl)
    private val resourceMetadataUri get() = "$baseUrl/.well-known/oauth-protected-resource/mcp"

    private fun jwtDecoderFor(resourceUri: String): JwtDecoder {
        val decoder = NimbusJwtDecoder.withPublicKey(jwkKeyPairProvider.keyPair.public as RSAPublicKey).build()
        decoder.setJwtValidator(
            JwtValidators.createDefaultWithValidators(AudienceValidator(resourceUri))
        )
        return decoder
    }

    // yona-wiki P3-14 1라운드 — 리소스가 mcp 하나뿐일 때는 JwtDecoder 빈이 하나라 타입만으로 주입이
    // 됐지만, 두 번째(api) 빈이 생기면서 모호해졌다. Kotlin은 기본적으로 `-java-parameters`
    // 컴파일 옵션을 켜지 않아 @Bean 팩토리 메서드의 파라미터 이름이 리플렉션에서 지워지므로(Spring이
    // 이름 기반으로 자동 구분하는 폴백이 동작하지 않음), 각 리소스 서버 체인의 `.decoder(...)`
    // 호출부는 @Qualifier로 명시적으로 구분한다.
    //
    // @Primary가 필요한 이유(실측 확인, 2026-09-07): Spring Authorization Server의
    // OAuth2AuthorizationServerConfigurer가 초기화 중 내부적으로(리소스 서버 체인과 무관하게)
    // `applicationContext.getBean(JwtDecoder.class)`를 타입으로만 조회하는 지점이 있어(정확한
    // 용도는 프레임워크 내부 구현이라 불명 — revocation/introspection류 엔드포인트가 토큰 종류를
    // 판별할 때 쓰는 것으로 추정), 리소스 무관하게 "대표" 디코더가 하나 있어야 한다. 이 조회는
    // 오디언스 검증이 목적이 아니라 서명 검증 가능 여부만 보는 것으로 보여 어느 쪽을 대표로 둬도
    // 무방하다 — MCP가 원래 있던 유일한 리소스였으므로 그대로 대표로 지정한다.
    @Bean
    @Primary
    @Qualifier("mcpJwtDecoder")
    fun mcpJwtDecoder(): JwtDecoder = jwtDecoderFor(mcpResourceUri)

    @Bean
    @Qualifier("apiJwtDecoder")
    fun apiJwtDecoder(): JwtDecoder = jwtDecoderFor(apiResourceUri)

    @Bean
    @Order(2)
    fun mcpResourceServerSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("mcpJwtDecoder") jwtDecoder: JwtDecoder
    ): SecurityFilterChain {
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

    // yona-wiki P3-14 1라운드 — `/api/v1/**`(P3-02가 만든 REST API 네임스페이스)에 OAuth2 JWT
    // 인증을 추가한다. GET은 기존 SecurityConfig 캐치올 체인과 동일한 관례(공개 프로젝트 익명 조회
    // 허용, 컨트롤러가 나머지 인가를 처리)를 그대로 유지하고, 그 외 메서드만 인증을 요구한다 — 실제
    // 스코프 단위 인가(issues:write 등)는 OAuthApiScopeAuthorizationFilter가 판정한다.
    @Bean
    @Order(3)
    fun apiResourceServerSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("apiJwtDecoder") apiJwtDecoder: JwtDecoder,
        oAuthApiScopeAuthorizationFilter: OAuthApiScopeAuthorizationFilter
    ): SecurityFilterChain {
        http
            .securityMatcher("/api/v1/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, "/api/v1/projects/**").permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { rs -> rs.jwt { it.decoder(apiJwtDecoder) } }
            .addFilterBefore(apiTokenAuthenticationFilter, BearerTokenAuthenticationFilter::class.java)
            .addFilterAfter(oAuthApiScopeAuthorizationFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }
}
