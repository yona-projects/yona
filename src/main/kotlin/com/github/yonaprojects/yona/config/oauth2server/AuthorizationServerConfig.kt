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
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.oidc.OidcScopes
import org.springframework.security.oauth2.core.oidc.StandardClaimNames
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.UUID

// yona가 자체 OAuth2 인가 서버(Authorization Server) 역할을 하는 핵심 설정.
//
// 비로그인 상태로 /oauth2/authorize에 접근하면 아래 LoginUrlAuthenticationEntryPoint가 기존
// SecurityConfig의 "/users/loginform"으로 그대로 리다이렉트한다 — 별도 로그인 화면을 두지 않는다.
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

        // securityMatcher를 생략하면 이 체인이 anyRequest로 모든 요청과 매치돼 아래
        // ResourceServerConfig/SecurityConfig 체인이 도달 불가능해진다(UnreachableFilterChainException)
        // — 인가 서버 엔드포인트로만 명시적으로 좁힌다.
        http
            .securityMatcher(authorizationServerConfigurer.endpointsMatcher)
            .with(authorizationServerConfigurer) { configurer ->
                configurer
                    // RFC7591 Dynamic Client Registration — 클라이언트가 사전등록 없이 자동으로
                    // 자신을 등록하는 플로우. 스펙상 인증 없이 여는 게 정상이며, 스팸 방지는
                    // DcrRateLimitFilter(아래 addFilterBefore)가 담당한다.
                    .clientRegistrationEndpoint { it.openRegistrationAllowed(true) }
                    // GitHub 방식 동의 화면(web/OAuthConsentController.kt) 재사용.
                    .authorizationEndpoint { it.consentPage(CONSENT_PAGE_URI) }
                    // OIDC("Sign in with yona") 활성화 — 이 한 줄로 discovery, /userinfo, ID 토큰
                    // 발급이 켜진다. ID 토큰의 identity 클레임 매핑은
                    // ResourceIndicatorTokenCustomizer가 담당한다.
                    .oidc { oidc ->
                        oidc.providerConfigurationEndpoint { endpoint ->
                            endpoint.providerConfigurationCustomizer { builder ->
                                // 기본값은 scopes_supported=[openid] 하나뿐이라, 실제 지원하는
                                // profile/email 스코프와 클레임을 discovery 문서에 명시적으로
                                // 광고한다(claims_supported는 프레임워크가 채워주지 않는다).
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
                    // DCR 엔드포인트(OAuth2ClientRegistrationEndpointFilter)는 AuthorizationFilter
                    // *뒤*에 붙는다 — anyRequest().authenticated()만 두면 openRegistrationAllowed(true)로
                    // 열어둔 익명 등록도 여기서 먼저 막힌다. DCR 경로만 명시적으로 permitAll(스팸
                    // 방지는 DcrRateLimitFilter가 담당).
                    .requestMatchers(HttpMethod.POST, "/oauth2/register").permitAll()
                    .anyRequest().authenticated()
            }
            .exceptionHandling { exceptions ->
                // /oauth2/token처럼 기계가 호출하는 엔드포인트에서 클라이언트 인증이 실패했을 때
                // 표준 OAuth2 JSON 오류 대신 로그인 화면으로 리다이렉트되면 MCP 클라이언트가 해석할
                // 수 없다 — 사람이 브라우저로 접근하는 두 엔드포인트(/oauth2/authorize, /oauth2/consent)만
                // 로그인 화면으로 리다이렉트하고, 나머지는 Spring 기본 처리(403)로 남겨 OAuth2 예외
                // 변환 필터가 JSON 오류를 만들게 한다.
                val browserFacingEntryPoint = LoginUrlAuthenticationEntryPoint("/users/loginform")
                val entryPoints = linkedMapOf<RequestMatcher, AuthenticationEntryPoint>(
                    PathPatternRequestMatcher.pathPattern("/oauth2/authorize") to browserFacingEntryPoint,
                    PathPatternRequestMatcher.pathPattern(CONSENT_PAGE_URI) to browserFacingEntryPoint
                )
                val delegatingEntryPoint = DelegatingAuthenticationEntryPoint(entryPoints)
                delegatingEntryPoint.setDefaultEntryPoint(Http403ForbiddenEntryPoint())
                exceptions.authenticationEntryPoint(delegatingEntryPoint)
            }
            // /userinfo는 이 체인에서 유일하게 세션 쿠키가 아니라 Bearer 액세스 토큰으로 인증하므로
            // 리소스 서버(JWT Bearer) 필터가 필요하다. 오디언스 무관 디코더를 쓰는 이유는
            // ResourceServerConfig.userInfoJwtDecoder() 참고.
            .oauth2ResourceServer { rs -> rs.jwt { it.decoder(userInfoJwtDecoder) } }
            // OAuth2ClientRegistrationEndpointFilter는 필터 순서 레지스트리에 없어 직접
            // addFilterBefore(..., OAuth2ClientRegistrationEndpointFilter::class.java)를 못 쓴다 —
            // 이 필터가 항상 AuthorizationFilter 뒤에 붙는다는 점을 이용해 앞에 걸어 순서를 보장한다.
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
    // domain/oauth2server/의 @Component가 이미 구현하므로 여기서 별도 @Bean을 선언하지 않는다.

    // 이 코드베이스는 사용자 비밀번호도 자체 SHA-256+Base64 해시를 쓰고 PasswordEncoder를 쓰지
    // 않는다 — 유일한 예외가 여기다. confidential 클라이언트(OAuthAppsAdminController가 등록)의
    // client_secret은 Spring Authorization Server의 client_secret_basic/post 인증이 이 빈을
    // 직접 사용하는 계약이라 예외적으로 등록한다.
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        PasswordEncoderFactories.createDelegatingPasswordEncoder()

    companion object {
        const val CONSENT_PAGE_URI = "/oauth2/consent"
    }
}
