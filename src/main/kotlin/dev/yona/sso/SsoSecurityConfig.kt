package dev.yona.sso

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoderFactory
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Duration

/**
 * 엔터프라이즈 SSO 전용 보안 필터 체인이다(X-16, `decisions.md` D-80 1번). `/sso/` 아래만 맡아 기존 보안 체인(`YonaAuthenticationFilter`)과 섞이지 않는다. OIDC는 스프링 시큐리티의 `oauth2Login`이
 * 인가 코드 흐름(PKCE, state, nonce)과 ID 토큰 검증을 맡고, 성공하면 [SsoLoginSuccessHandler]가 우리 세션 로그인으로 잇는다. 토큰은 저장하지 않는다(인가된 클라이언트 저장소가 비어 있다).
 */
@Configuration
class SsoSecurityConfig {

    @Bean
    @Order(2)
    fun ssoSecurityFilterChain(http: HttpSecurity, registrations: SsoClientRegistrations, success: SsoLoginSuccessHandler, failure: SsoLoginFailureHandler): SecurityFilterChain =
        http.securityMatcher("/sso/**")
            .csrf { it.disable() }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .oauth2Login { login ->
                login.clientRegistrationRepository(registrations)
                login.authorizedClientRepository(NoAuthorizedClients)
                login.authorizationEndpoint { endpoint ->
                    endpoint.baseUri(SsoClientRegistrations.AUTHORIZE_BASE)
                    endpoint.authorizationRequestResolver(authorizationRequestResolver(registrations))
                }
                login.redirectionEndpoint { it.baseUri("/sso/oidc/callback/*") }
                login.successHandler(success)
                login.failureHandler(failure)
            }
            .build()

    /**
     * 인가 요청 주소를 만든다. 비밀 값이 있는 클라이언트에도 PKCE를 붙이고, 로그인 시작 주소가 세션에 둔 `login_hint`를 IdP에 알린다.
     */
    private fun authorizationRequestResolver(registrations: ClientRegistrationRepository) =
        DefaultOAuth2AuthorizationRequestResolver(registrations, SsoClientRegistrations.AUTHORIZE_BASE).apply {
            setAuthorizationRequestCustomizer { builder ->
                OAuth2AuthorizationRequestCustomizers.withPkce().accept(builder)
                val hint = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.getSession(false)?.getAttribute(SsoReturn.LOGIN_HINT) as? String
                if (!hint.isNullOrBlank()) builder.additionalParameters { it["login_hint"] = hint }
            }
        }

    /**
     * ID 토큰 해독기이다. 등록이 요청마다 바뀔 수 있어 캐시하지 않는다(스프링 기본 해독기는 등록 이름으로 캐시해 설정을 바꿔도 옛 값을 쓴다). 서명 알고리즘은 RSA와 ECDSA 계열을 받고,
     * 발급자, 대상, 시간(허용 시계 오차는 설정)을 검증한다.
     */
    @Bean
    fun ssoJwtDecoderFactory(settings: dev.yona.domain.sso.SsoSettingsService): JwtDecoderFactory<ClientRegistration> = JwtDecoderFactory { registration ->
        val decoder: NimbusJwtDecoder = NimbusJwtDecoder.withJwkSetUri(requireNotNull(registration.providerDetails.jwkSetUri)).jwsAlgorithms { algorithms ->
            algorithms.addAll(listOf(SignatureAlgorithm.RS256, SignatureAlgorithm.RS384, SignatureAlgorithm.RS512, SignatureAlgorithm.ES256, SignatureAlgorithm.ES384, SignatureAlgorithm.PS256))
        }.build()
        decoder.setJwtValidator(OidcIdTokenValidator(registration).also { it.setClockSkew(Duration.ofSeconds((settings.oidc().clockSkewSeconds ?: 60).toLong())) })
        decoder as JwtDecoder
    }

    /** 인가된 클라이언트(액세스 토큰)를 어디에도 저장하지 않는다. 로그인에만 쓰고 IdP의 API는 부르지 않는다. */
    private object NoAuthorizedClients : OAuth2AuthorizedClientRepository {
        override fun <T : OAuth2AuthorizedClient> loadAuthorizedClient(clientRegistrationId: String, principal: Authentication, request: HttpServletRequest): T? = null
        override fun saveAuthorizedClient(authorizedClient: OAuth2AuthorizedClient, principal: Authentication, request: HttpServletRequest, response: HttpServletResponse) = Unit
        override fun removeAuthorizedClient(clientRegistrationId: String, principal: Authentication, request: HttpServletRequest, response: HttpServletResponse) = Unit
    }
}
