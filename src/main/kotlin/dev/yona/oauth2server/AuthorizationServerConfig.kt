package dev.yona.oauth2server

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.audit.AuditBoundary
import dev.yona.domain.oauth2server.RedirectUris
import dev.yona.domain.user.UserRepository
import dev.yona.security.SessionUserCache
import dev.yona.security.TokenFailureLimiter
import dev.yona.security.TwoFactorGate
import dev.yona.security.YonaAuthenticationFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint
import org.springframework.security.web.context.SecurityContextHolderFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.RequestMatcher
import java.security.MessageDigest
import java.util.function.Consumer

/**
 * OAuth2 인가 서버의 보안 설정이다(추가 개발 항목 X-1, `decisions.md` D-71, `architecture/oauth2-server-plan.md`). 인가 서버 엔드포인트(`/oauth2/authorize`, `/oauth2/token`, `/oauth2/revoke`,
 * `/oauth2/introspect`, `/.well-known/oauth-authorization-server`)만 이 체인이 맡고 나머지 요청은 기존 체인([dev.yona.config.SecurityConfig])이 맡는다. 사용자는 기존과 같은
 * [YonaAuthenticationFilter]로 세션에서 식별하고, 로그인하지 않았으면 기존 로그인 화면으로 보낸다(로그인이 끝나면 `/oauth2/resume`으로 돌아와 요청을 이어 간다).
 *
 * 동적 클라이언트 등록(RFC 7591)은 열지 않는다. 앱은 설정 화면에서 등록하고 CIMD로 가져온 앱도 같은 저장소에서 찾는다.
 */
@Configuration(proxyBeanMethods = false)
class AuthorizationServerConfig {

    @Bean
    @Order(1)
    fun authorizationServerSecurityFilterChain(
        http: HttpSecurity, users: UserRepository, sessionUsers: SessionUserCache, apiTokens: ApiTokenService, tokenFailures: TokenFailureLimiter, audit: AuditBoundary, gate: TwoFactorGate, oauthTokens: dev.yona.domain.oauth2server.OAuthAccessTokenService,
        registeredClients: YonaRegisteredClientRepository, authorizations: YonaOAuth2AuthorizationService, consents: YonaOAuth2AuthorizationConsentService
    ): SecurityFilterChain {
        val configurer = OAuth2AuthorizationServerConfigurer()
        http.securityMatcher(configurer.endpointsMatcher)
            .with(configurer) { server ->
                server.registeredClientRepository(registeredClients).authorizationService(authorizations).authorizationConsentService(consents)
                    .tokenGenerator(DelegatingOAuth2TokenGenerator(YonaTokenGenerator()))
                    .authorizationEndpoint { endpoint -> endpoint.consentPage(CONSENT_PAGE).authenticationProviders { providers -> providers.forEach { useRequestValidator(it) } } }
                    .clientAuthentication { auth ->
                        auth.authenticationConverters { it.add(0, PublicClientTokenConverter()) }
                            .authenticationProviders { providers -> providers.forEach { provider -> useSecretHash(provider) }; providers.add(0, PublicClientTokenProvider(registeredClients)) }
                    }
            }
            .csrf { it.disable() }
            .authorizeHttpRequests { it.requestMatchers(HttpMethod.GET, "/.well-known/oauth-authorization-server", "/.well-known/oauth-authorization-server/**").permitAll().anyRequest().authenticated() }
            .exceptionHandling { exceptions ->
                val entryPoints = linkedMapOf<RequestMatcher, AuthenticationEntryPoint>(PathPatternRequestMatcher.withDefaults().matcher("/oauth2/authorize") to OAuthLoginEntryPoint())
                exceptions.authenticationEntryPoint(DelegatingAuthenticationEntryPoint(entryPoints).also { it.setDefaultEntryPoint(Http403ForbiddenEntryPoint()) })
            }
            .addFilterAfter(YonaAuthenticationFilter(users, sessionUsers, apiTokens, tokenFailures, audit, gate, oauthTokens), SecurityContextHolderFilter::class.java)
        return http.build()
    }

    @Bean
    fun authorizationServerSettings(): AuthorizationServerSettings = AuthorizationServerSettings.builder().build()

    private fun useRequestValidator(provider: AuthenticationProvider) {
        if (provider is OAuth2AuthorizationCodeRequestAuthenticationProvider) provider.setAuthenticationValidator(authorizationRequestValidator())
    }

    /** 기밀 앱의 비밀 값은 SHA-256 해시로 저장하므로 같은 방식으로 비교한다(비밀 값이 충분히 무작위라 느린 해시가 필요 없다). */
    private fun useSecretHash(provider: AuthenticationProvider) {
        if (provider is ClientSecretAuthenticationProvider) provider.setPasswordEncoder(SecretHashEncoder)
    }

    /**
     * 승인 요청 검증이다. 요청한 리디렉션 주소가 등록한 주소와 맞아야 하고(루프백은 포트를 무시한다, [RedirectUris.matches]), 틀리면 사용자를 그 주소로 보내지 않고 오류 화면을 낸다.
     * 범위 검증은 라이브러리 기본 규칙(앱의 허용 범위 안)을 그대로 쓴다.
     */
    private fun authorizationRequestValidator(): Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> = Consumer { context ->
        val request = context.getAuthentication<OAuth2AuthorizationCodeRequestAuthenticationToken>()
        val registered = context.registeredClient.redirectUris.toList()
        val requested = request.redirectUri
        val accepted = if (requested.isNullOrBlank()) registered.size == 1 else RedirectUris.matches(registered, requested)
        if (!accepted) throw redirectUriError(request)
        OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR.accept(context)
    }

    private fun redirectUriError(request: OAuth2AuthorizationCodeRequestAuthenticationToken) = OAuth2AuthorizationCodeRequestAuthenticationException(
        OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 Parameter: redirect_uri", ERROR_URI),
        OAuth2AuthorizationCodeRequestAuthenticationToken(request.authorizationUri, request.clientId, request.principal as org.springframework.security.core.Authentication, null, request.state, request.scopes, request.additionalParameters)
    )

    companion object {
        /** 승인 화면의 주소이다. 라이브러리가 사용자의 승인이 필요하면 여기로 보낸다. */
        const val CONSENT_PAGE = "/oauth2/consent"
        private const val ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.1.2.1"
    }
}

/** 비밀 값 비교기이다. 저장값은 SHA-256 16진수이고 상수 시간으로 비교한다. 새로 인코딩하는 일은 없다(앱을 만들 때 서비스가 직접 해시한다). */
object SecretHashEncoder : PasswordEncoder {
    override fun encode(rawPassword: CharSequence?): String = TokenHasher.sha256Hex(rawPassword.toString())
    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean =
        encodedPassword != null && rawPassword != null && MessageDigest.isEqual(encodedPassword.toByteArray(), TokenHasher.sha256Hex(rawPassword.toString()).toByteArray())
}

/**
 * 로그인하지 않고 승인 요청이 오면 요청 전체(주소와 질의)를 세션에 보관하고 기존 로그인 화면으로 보낸다. 로그인 화면은 이동할 주소의 질의를 인코딩해 망가뜨리므로(v1.6 동작) 주소를
 * 직접 넘기지 않고 `/oauth2/resume`을 넘긴다. 로그인(2단계 인증 포함)이 끝나면 그 주소가 보관한 요청으로 돌려보낸다.
 */
class OAuthLoginEntryPoint : AuthenticationEntryPoint {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        val query = request.queryString?.let { "?$it" }.orEmpty()
        request.getSession(true).setAttribute(SAVED_REQUEST, request.requestURI + query)
        response.sendRedirect(request.contextPath + "/users/loginform?redirectUrl=" + RESUME_PATH)
    }

    companion object {
        const val SAVED_REQUEST = "dev.yona.oauth2server.SavedAuthorizationRequest"
        const val RESUME_PATH = "/oauth2/resume"
    }
}
