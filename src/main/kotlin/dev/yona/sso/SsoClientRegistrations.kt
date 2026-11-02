package dev.yona.sso

import dev.yona.domain.sso.SsoSettingsService
import dev.yona.util.SiteUrls
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.stereotype.Component

/**
 * 관리자가 저장한 OIDC 설정을 스프링 시큐리티의 `ClientRegistration`으로 바꿔 준다(X-16, `decisions.md` D-80 1, 3번). 등록은 하나(`oidc`)이고, 매 요청마다 저장된 설정에서 만들므로
 * 관리자가 설정을 바꾸면 재시작 없이 바로 반영된다. 발견 문서는 저장할 때 확인해 캐시한 값을 쓴다. 사용자 정보 엔드포인트는 쓰지 않는다(ID 토큰의 클레임만 읽는다).
 */
@Component
class SsoClientRegistrations(private val settings: SsoSettingsService, private val siteUrls: SiteUrls) : ClientRegistrationRepository {

    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        if (registrationId != ID) return null
        val row = settings.oidc()
        if (row.enabled != true) return null
        val discovery = settings.oidcDiscovery() ?: return null
        val secret = settings.oidcClientSecret() ?: return null
        return ClientRegistration.withRegistrationId(ID)
            .clientId(requireNotNull(row.clientId)).clientSecret(secret).clientName(row.displayName.orEmpty())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(siteUrls.origin() + siteUrls.path(CALLBACK_PATH)).scope(*row.scopes.orEmpty().split(' ', ',').filter { it.isNotBlank() }.toTypedArray())
            .authorizationUri(discovery.authorizationEndpoint).tokenUri(discovery.tokenEndpoint).jwkSetUri(discovery.jwksUri).issuerUri(discovery.issuer).userNameAttributeName("sub")
            .build()
    }

    companion object {
        /** 등록 이름이다. 주소(`/sso/oidc/authorize/oidc`, `/sso/oidc/callback/oidc`)에 들어간다. */
        const val ID = "oidc"
        const val CALLBACK_PATH = "/sso/oidc/callback/$ID"
        const val AUTHORIZE_BASE = "/sso/oidc/authorize"
    }
}
