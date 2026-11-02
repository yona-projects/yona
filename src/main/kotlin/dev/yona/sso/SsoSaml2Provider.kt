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

/**
 * SAML 2 응답을 검증하는 인증 제공자이다(X-16, `decisions.md` D-80 15, 23번). 서명(응답 또는 어설션), 발급자, 대상(Audience), 수신 주소(Destination, Recipient), 요청 ID(InResponseTo),
 * 유효 시간 검증은 스프링 시큐리티의 [OpenSaml5AuthenticationProvider]에 맡기고 허용 시계 오차만 설정에서 읽어 준다. **우리가 보낸 요청에 대한 응답만** 받는다.
 * 요청 없이 도착한 응답(IdP가 시작한 로그인)은 이 단계에서 거절한다(받는 길은 단계 5에서 보호 장치와 함께 연다).
 */
class SsoSaml2Provider(private val settings: SsoSettingsService) : AuthenticationProvider {

    private val delegate = OpenSaml5AuthenticationProvider().apply {
        setAssertionValidator(OpenSaml5AuthenticationProvider.createDefaultAssertionValidatorWithParameters { parameters ->
            parameters[SAML2AssertionValidationParameters.CLOCK_SKEW] = Duration.ofSeconds((settings.saml2().clockSkewSeconds ?: 60).toLong())
        })
    }

    override fun authenticate(authentication: Authentication): Authentication? {
        val token = authentication as? Saml2AuthenticationToken ?: return null
        if (token.authenticationRequest == null) {
            throw Saml2AuthenticationException(Saml2Error("unsolicited_response", "요청 없이 도착한 응답은 받지 않는다"))
        }
        return delegate.authenticate(authentication)
    }

    override fun supports(authentication: Class<*>): Boolean = delegate.supports(authentication)
}
