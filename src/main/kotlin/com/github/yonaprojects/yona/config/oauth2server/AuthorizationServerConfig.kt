package com.github.yonaprojects.yona.config.oauth2server

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step2 — yona 자신이 OAuth2 인가 서버(Authorization Server) 역할을 하는
// 핵심 설정. 설계 결정 근거는 docs/yona-wiki/plans/p3-07-mcp-server.md의 "완료 로그 — Step 1" 참고.
//
// 로그인 화면 재사용: /oauth2/authorize에 비로그인 상태로 접근하면 아래 LoginUrlAuthenticationEntryPoint가
// 기존 SecurityConfig의 "/users/loginform"(폼 로그인 + LDAP, P3-06 완료 후에는 SAML2/OIDC까지)으로
// 그대로 리다이렉트한다 — 별도의 로그인 화면을 새로 만들지 않는다(계획 문서의 핵심 요구사항).
@Configuration
class AuthorizationServerConfig(
    private val jwkKeyPairProvider: JwkKeyPairProvider,
    private val dcrRateLimitFilter: DcrRateLimitFilter,
    @Value("\${yona.base-url:http://localhost:8080}")
    private val baseUrl: String
) {

    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity,
        @Qualifier("userInfoJwtDecoder") userInfoJwtDecoder: JwtDecoder
    ): SecurityFilterChain {
        val authorizationServerConfigurer = OAuth2AuthorizationServerConfigurer()

        // 공식 "Getting Started" 예제는 앱 전체가 인가 서버뿐인 경우를 전제로 securityMatcher를
        // 생략하지만(anyRequest), yona는 git 호스팅·이슈 트래커 등 다른 용도가 대부분이라 이 체인을
        // authorizationServerConfigurer.getEndpointsMatcher()(/oauth2/**, /.well-known/oauth-
        // authorization-server 등)로 명시적으로 좁혀야 한다 — 생략하면 이 체인이 "모든 요청"에
        // 매치돼(anyRequest) 아래 ResourceServerConfig(/mcp/**)/SecurityConfig(catch-all) 체인이
        // 전부 도달 불가능해지는 것을 실제로 확인했다(UnreachableFilterChainException).
        http
            .securityMatcher(authorizationServerConfigurer.endpointsMatcher)
            .with(authorizationServerConfigurer) { configurer ->
                configurer
                    // RFC7591 Dynamic Client Registration — Claude 등 대화형 MCP 클라이언트가 별도
                    // 사전등록 없이 자동으로 자신을 등록하는 플로우(계획 문서 핵심 요구사항)를 위해
                    // 열어둔다. 스펙 특성상 원래 인증 없이 여는 게 정상이며, 스팸 등록 방어는
                    // DcrRateLimitFilter(아래 addFilterBefore)가 담당한다.
                    .clientRegistrationEndpoint { it.openRegistrationAllowed(true) }
                    // GitHub 방식 동의 화면(web/OAuthConsentController.kt) 재사용.
                    .authorizationEndpoint { it.consentPage(CONSENT_PAGE_URI) }
                    // yona-wiki P3-14 2라운드 — OIDC("Sign in with yona") 활성화. 이 한 줄로
                    // /.well-known/openid-configuration, /userinfo, `openid` 스코프 요청 시 ID
                    // 토큰 발급이 전부 켜진다(공식 기본 동작, providerConfigurationEndpoint 커스텀
                    // 부분만 아래에서 추가로 손봤다). ID 토큰의 identity 클레임(name/email/
                    // email_verified/picture) 매핑은 ResourceIndicatorTokenCustomizer가 담당한다
                    // (그 클래스 상단 주석 참고 — OAuth2TokenCustomizer<JwtEncodingContext> 빈은
                    // 이 프로젝트 전체에서 하나여야만 해서 별도 클래스로 분리하지 않았다).
                    .oidc { oidc ->
                        oidc.providerConfigurationEndpoint { endpoint ->
                            endpoint.providerConfigurationCustomizer { builder ->
                                // 기본값은 scopes_supported=[openid] 하나뿐이라(공식 소스 확인),
                                // 이 서버가 실제로 지원하는 profile/email 스코프와 그에 따른
                                // 클레임을 discovery 문서에 명시적으로 광고한다 — claims_supported는
                                // 프레임워크가 아예 채워주지 않는 필드라 직접 넣어야 한다.
                                builder.scope(OidcScopes.PROFILE)
                                builder.scope(OidcScopes.EMAIL)
                                builder.claim(
                                    "claims_supported",
                                    listOf(
                                        "sub",
                                        StandardClaimNames.NAME,
                                        StandardClaimNames.EMAIL,
                                        StandardClaimNames.EMAIL_VERIFIED,
                                        StandardClaimNames.PICTURE
                                    )
                                )
                            }
                        }
                    }
            }
            .authorizeHttpRequests { authorize ->
                authorize
                    // RFC9728/RFC8414 메타데이터·JWKS 엔드포인트는 AuthorizationFilter보다 앞선
                    // 필터가 자체 처리해 이 설정과 무관하게 항상 공개되지만(공식 소스로 확인),
                    // DCR 엔드포인트(OAuth2ClientRegistrationEndpointFilter)는 반대로
                    // AuthorizationFilter *뒤*에 붙는다(공식 소스로 확인) — anyRequest().authenticated()만
                    // 두면 openRegistrationAllowed(true)로 열어둔 익명 등록조차 이 authorizeHttpRequests
                    // 단계에서 먼저 401/리다이렉트로 막혀버린다(실제로 이렇게 재현됨, McpOAuth2SecurityIntegrationSpec
                    // 참고). 그래서 DCR 경로만 명시적으로 permitAll — 실제 스팸 방지는 DcrRateLimitFilter가 담당.
                    .requestMatchers(org.springframework.http.HttpMethod.POST, "/oauth2/register").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — 이전에는 이 체인이 담당하는 모든
                // 경로(/oauth2/authorize, /oauth2/token, /oauth2/register, /oauth2/consent, /oauth2/jwks,
                // /.well-known/** 등)에 로그인 리다이렉트를 무조건 적용했다. 이 때문에 PKCE
                // code_verifier를 생략한 /oauth2/token 요청처럼 "브라우저가 아니라 기계가 호출하는"
                // 엔드포인트에서 클라이언트 인증(PublicClientAuthenticationProvider)이 실패하면,
                // 표준 OAuth2 JSON 오류 응답(400 + invalid_client 등) 대신 302로 HTML 로그인
                // 페이지("/users/loginform")로 리다이렉트해버려 MCP 클라이언트가 오류를 해석할 수
                // 없게 되는 문제가 실제 통합테스트로 발견됐다. 사람이 브라우저로 접근하는 두 엔드포인트
                // (/oauth2/authorize, /oauth2/consent)만 로그인 화면으로 리다이렉트하고, 나머지
                // 기계 대 기계 엔드포인트는 Spring의 기본 처리(403)로 남겨 OAuth2 관련 예외 변환
                // 필터가 정상적으로 JSON 오류를 만들 수 있게 한다.
                val browserFacingEntryPoint = LoginUrlAuthenticationEntryPoint("/users/loginform")
                val entryPoints = linkedMapOf<org.springframework.security.web.util.matcher.RequestMatcher, org.springframework.security.web.AuthenticationEntryPoint>(
                    PathPatternRequestMatcher.pathPattern("/oauth2/authorize") to browserFacingEntryPoint,
                    PathPatternRequestMatcher.pathPattern(CONSENT_PAGE_URI) to browserFacingEntryPoint
                )
                val delegatingEntryPoint = DelegatingAuthenticationEntryPoint(entryPoints)
                delegatingEntryPoint.setDefaultEntryPoint(Http403ForbiddenEntryPoint())
                exceptions.authenticationEntryPoint(delegatingEntryPoint)
            }
            // yona-wiki P3-14 2라운드 — `/userinfo`는 이 체인이 담당하는 엔드포인트 중 유일하게
            // 세션 쿠키가 아니라 Bearer 액세스 토큰으로 인증한다. OidcUserInfoEndpointConfigurer의
            // 기본 AuthenticationConverter는 요청을 직접 파싱하지 않고
            // SecurityContextHolder.getContext().getAuthentication()을 그대로 읽어가므로(공식 소스
            // 확인), 이 체인에도 리소스 서버(JWT Bearer) 인증 필터가 있어야 그 시점에 이미
            // 인증돼 있다. 오디언스 무관 디코더를 쓰는 이유는 ResourceServerConfig.userInfoJwtDecoder()
            // 주석 참고.
            .oauth2ResourceServer { rs -> rs.jwt { it.decoder(userInfoJwtDecoder) } }
            // OAuth2ClientRegistrationEndpointFilter는 Spring Security의 필터 순서 레지스트리에
            // 등록돼 있지 않아 addFilterBefore(..., OAuth2ClientRegistrationEndpointFilter::class.java)를
            // 직접 쓸 수 없다(IllegalArgumentException) — 대신 공식 소스로 확인한 사실
            // (OAuth2ClientRegistrationEndpointConfigurer.configure()가 이 필터를 항상
            // AuthorizationFilter 뒤에 붙인다는 것)을 이용해, AuthorizationFilter보다 앞에 두면
            // 결과적으로 DCR 필터보다 항상 먼저 실행됨을 보장한다.
            .addFilterBefore(dcrRateLimitFilter, AuthorizationFilter::class.java)

        return http.build()
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings =
        AuthorizationServerSettings.builder()
            .issuer(baseUrl)
            .build()

    @Bean
    fun jwkSource(): JWKSource<SecurityContext> {
        val keyPair = jwkKeyPairProvider.keyPair
        val rsaKey = RSAKey.Builder(keyPair.public as RSAPublicKey)
            .privateKey(keyPair.private as RSAPrivateKey)
            .keyID(UUID.nameUUIDFromBytes(keyPair.public.encoded).toString())
            .build()
        val jwkSet = JWKSet(rsaKey)
        return ImmutableJWKSet(jwkSet)
    }

    // RegisteredClientRepository/OAuth2AuthorizationService/OAuth2AuthorizationConsentService는
    // domain/oauth2server/의 @Component(JpaRegisteredClientRepository 등)가 이미 해당 인터페이스를
    // 구현하고 있어 Spring이 자동으로 찾아 쓴다 — 이 설정 클래스에서 별도로 @Bean을 다시 선언하지
    // 않는다.

    // yona-wiki P3-14 1라운드 — DCR로 등록되는 클라이언트는 전부 공개(PKCE 전용, clientSecret=null)
    // 클라이언트라 지금까지 PasswordEncoder가 전혀 필요 없었다(이 코드베이스 전체가 사용자 비밀번호도
    // 포함해 자체 SHA-256+Base64 해시를 쓰지 PasswordEncoder를 쓰지 않는다 — 유일한 예외). confidential
    // 클라이언트(OAuthAppsAdminController가 관리자용으로 등록)의 client_secret은 Spring Authorization
    // Server 자체의 client_secret_basic/client_secret_post 인증(OAuth2ClientAuthenticationProvider)이
    // 검증하는데, 그 컴포넌트가 PasswordEncoder 빈을 직접 사용하는 계약이라 여기서만 예외적으로
    // 등록한다(Spring 공식 샘플의 권장 방식).
    @Bean
    fun passwordEncoder(): org.springframework.security.crypto.password.PasswordEncoder =
        org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder()

    companion object {
        const val CONSENT_PAGE_URI = "/oauth2/consent"
    }
}
