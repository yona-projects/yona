package com.github.yonaprojects.yona.config.oauth2server

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.intercept.AuthorizationFilter
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint
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
    fun authorizationServerSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
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
                // 비로그인 상태로 /oauth2/authorize 등에 접근하면 기존 yona 로그인 화면으로 리다이렉트.
                exceptions.authenticationEntryPoint(LoginUrlAuthenticationEntryPoint("/users/loginform"))
            }
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

    companion object {
        const val CONSENT_PAGE_URI = "/oauth2/consent"
    }
}
