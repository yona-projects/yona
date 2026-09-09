package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.Saml2UserProvisioningService
import org.springframework.core.convert.converter.Converter
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.saml2.provider.service.authentication.OpenSaml5AuthenticationProvider
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal

/**
 * SAML 어서션 자체의 파싱/서명 검증은 Spring Security의
 * 기본 구현(`OpenSaml5AuthenticationProvider.createDefaultResponseAuthenticationConverter()`)에
 * 위임하고, 그 결과로 나온 `Saml2Authentication`의 principal(어서션 속성)을 받아 LDAP/OIDC와 동일한
 * JIT 프로비저닝을 수행한 뒤 로컬 User를 담은 principal로 교체한다.
 *
 * `buildAuthentication()`을 별도로 노출해 두어(실제 `convert()`는 이를 감쌀 뿐) 실제 OpenSAML
 * Response XML을 만들지 않고도 JIT 로직만 순수 단위테스트할 수 있게 했다.
 */
class EnterpriseSaml2ResponseAuthenticationConverter(
    private val saml2UserProvisioningService: Saml2UserProvisioningService,
    private val emailAttributeName: String,
    private val displayNameAttributeName: String,
    private val delegate: Converter<OpenSaml5AuthenticationProvider.ResponseToken, Saml2Authentication> =
        OpenSaml5AuthenticationProvider.createDefaultResponseAuthenticationConverter()
) : Converter<OpenSaml5AuthenticationProvider.ResponseToken, Saml2Authentication> {

    override fun convert(source: OpenSaml5AuthenticationProvider.ResponseToken): Saml2Authentication {
        val defaultAuthentication = delegate.convert(source)
            ?: throw IllegalStateException("기본 SAML2 응답 컨버터가 인증 객체를 생성하지 못했습니다.")
        return buildAuthentication(defaultAuthentication)
    }

    fun buildAuthentication(defaultAuthentication: Saml2Authentication): Saml2Authentication {
        val principal = defaultAuthentication.principal as Saml2AuthenticatedPrincipal
        val user = saml2UserProvisioningService.reconcile(principal, emailAttributeName, displayNameAttributeName)
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${user.state.name}"))
        val yonaPrincipal = YonaSaml2AuthenticatedPrincipal(user, principal)
        return Saml2Authentication(yonaPrincipal, defaultAuthentication.saml2Response, authorities)
    }
}
