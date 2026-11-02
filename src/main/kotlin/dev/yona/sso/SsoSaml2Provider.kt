package dev.yona.sso

import dev.yona.domain.sso.SsoSettingsService
import org.opensaml.saml.common.assertion.ValidationContext
import org.opensaml.saml.saml2.assertion.SAML2AssertionValidationParameters
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.saml2.core.Saml2Error
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationToken
import java.time.Duration
import java.time.Instant
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * SAML 2 응답을 검증하는 인증 제공자이다(X-16, `decisions.md` D-80 15, 23번). 서명(응답 또는 어설션), 발급자, 대상(Audience), 수신 주소(Destination, Recipient), 요청 ID(InResponseTo),
 * 유효 시간 검증은 스프링 시큐리티의 [OpenSaml5AuthenticationProvider]에 맡기고 허용 시계 오차만 설정에서 읽어 준다.
 *
 * 우리가 보낸 요청에 대한 응답은 요청 ID로 짝을 짓는다. **요청 없이 도착한 응답(IdP가 시작한 로그인)은 관리자가 켠 경우에만** 받고, 이때는 요청 ID가 없어야 하며
 * 같은 어설션의 재사용을 [SsoReplayCache]로 막는다.
 */
class SsoSaml2Provider(private val settings: SsoSettingsService, private val replay: SsoReplayCache) : AuthenticationProvider {

    private val delegate = OpenSaml5AuthenticationProvider().apply {
        setAssertionValidator(OpenSaml5AuthenticationProvider.createDefaultAssertionValidatorWithParameters { parameters ->
            parameters[SAML2AssertionValidationParameters.CLOCK_SKEW] = Duration.ofSeconds(skew())
        })
        val standard = OpenSaml5AuthenticationProvider.createDefaultResponseAuthenticationConverter()
        setResponseAuthenticationConverter { token ->
            token.response.assertions.forEach { assertion ->
                val issuer = assertion.issuer?.value.orEmpty()
                val expires = (assertion.conditions?.notOnOrAfter ?: Instant.now().plusSeconds(MAX_REMEMBER_SECONDS)).plusSeconds(skew())
                if (!replay.firstUse("$issuer|${assertion.id}", expires)) throw Saml2AuthenticationException(Saml2Error("assertion_replayed", "이미 받은 어설션이다"))
            }
            standard.convert(token)
        }
    }

    private fun skew() = (settings.saml2().clockSkewSeconds ?: 60).toLong()

    override fun authenticate(authentication: Authentication): Authentication? {
        val token = authentication as? Saml2AuthenticationToken ?: return null
        val unsolicited = token.authenticationRequest == null
        if (unsolicited && settings.saml2().initiateEnabled != true) {
            throw Saml2AuthenticationException(Saml2Error("unsolicited_response", "요청 없이 도착한 응답은 받지 않는다"))
        }
        val result = delegate.authenticate(authentication)
        if (unsolicited) (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.setAttribute(SsoReturn.INITIATED, true)
        return result
    }

    override fun supports(authentication: Class<*>): Boolean = delegate.supports(authentication)

    private companion object { const val MAX_REMEMBER_SECONDS = 600L }
}
