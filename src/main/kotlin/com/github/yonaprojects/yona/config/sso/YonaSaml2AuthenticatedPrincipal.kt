package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step3 — `config/sso/YonaOidcUser`/`config/oauth2/YonaOAuth2User`와
 * 동일한 취지. `getName()`이 SAML NameID가 아니라 로컬 User.loginId를 반환해야 기존
 * `authentication.name` 기반 관례(Git/SVN 인증 필터, `userRepository.findByLoginId` 등)가 그대로
 * 동작한다.
 */
class YonaSaml2AuthenticatedPrincipal(
    val user: User,
    private val delegate: Saml2AuthenticatedPrincipal
) : Saml2AuthenticatedPrincipal {
    override fun getName(): String = user.loginId
    override fun getAttributes(): Map<String, List<Any>> = delegate.attributes
    override fun getRelyingPartyRegistrationId(): String? = delegate.relyingPartyRegistrationId
    override fun getSessionIndexes(): List<String> = delegate.sessionIndexes
}
