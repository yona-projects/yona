package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.sso.OidcSsoSettings
import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper
import org.springframework.security.oauth2.client.registration.ClientRegistration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.stereotype.Component

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 관리자 UI로 등록한 엔터프라이즈 OIDC를 기존 소셜
 * 로그인(google/github, `application.yml`의 `spring.security.oauth2.client.registration.*`)과
 * 같은 `/oauth2/authorization/{registrationId}` 경로로 동작하게 하려면 이 저장소가 필요하다.
 *
 * 이 빈을 직접 정의하면 Spring Boot의 `OAuth2ClientConfigurations.ClientRegistrationRepositoryConfiguration`
 * (`@ConditionalOnMissingBean(ClientRegistrationRepository::class)`)이 통째로 물러난다 — 그런데
 * `@EnableConfigurationProperties(OAuth2ClientProperties::class)`가 바로 그 백오프하는 클래스에
 * 걸려 있어서(실측 확인: `NoSuchBeanDefinitionException`), google/github의 `OAuth2ClientProperties`
 * 바인딩 자체도 우리가 직접 재선언해야 한다 — 아래 `@EnableConfigurationProperties`가 그 역할이다.
 *
 * 엔터프라이즈 OIDC는 매 요청마다 `SsoSettingsService`에서 최신 설정을 읽어 즉석에서
 * `ClientRegistration`을 만든다(관리자 UI가 값을 바꾸면 재시작 없이 바로 반영, LDAP의 매 로그인
 * 시도마다 서버에 바인딩하는 것과 같은 결의 트레이드오프 — 매번 IdP에 OIDC Discovery 문서를
 * 다시 조회하는 비용이 있으나 로그인 시도 빈도를 고려하면 캐싱은 후속 최적화로 미룬다).
 */
@Component
@EnableConfigurationProperties(OAuth2ClientProperties::class)
class YonaClientRegistrationRepository(
    oauth2ClientProperties: OAuth2ClientProperties,
    private val ssoSettingsService: SsoSettingsService,
    private val enterpriseRegistrationBuilder: (OidcSsoSettings) -> ClientRegistration = ::buildFromIssuerDiscovery
) : ClientRegistrationRepository {

    private val staticRegistrations: Map<String, ClientRegistration> =
        OAuth2ClientPropertiesMapper(oauth2ClientProperties).asClientRegistrations()

    override fun findByRegistrationId(registrationId: String): ClientRegistration? {
        staticRegistrations[registrationId]?.let { return it }

        val settings = ssoSettingsService.getOidcSettings()
        if (!settings.enabled || settings.registrationId != registrationId || settings.issuerUri.isNullOrBlank()) {
            return null
        }
        return enterpriseRegistrationBuilder(settings)
    }

    companion object {
        fun buildFromIssuerDiscovery(settings: OidcSsoSettings): ClientRegistration =
            ClientRegistrations.fromIssuerLocation(requireNotNull(settings.issuerUri))
                .registrationId(settings.registrationId)
                .clientId(settings.clientId ?: "")
                .clientSecret(settings.clientSecret ?: "")
                .scope("openid", "profile", "email")
                .build()
    }
}
