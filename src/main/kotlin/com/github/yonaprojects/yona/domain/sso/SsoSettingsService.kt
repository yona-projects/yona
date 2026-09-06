package com.github.yonaprojects.yona.domain.sso

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step4 — LDAP(`application.yml`의 `yona.ldap.*`)과 동일하게
 * "설정 파일만으로도 최소 동작"을 보장하되, 사용자 지시에 따라 확장된 관리자 UI(`SsoAdminController`)가
 * DB에 저장한 값이 있으면 그 값을 우선한다. DB에 아직 아무 것도 저장되지 않은 시점(앱을 막 띄운
 * 직후, UI를 한 번도 안 쓴 상태)에는 `yona.sso.oidc.*`/`yona.sso.saml2.*` 프로퍼티(환경변수로도
 * 주입 가능)로 만든 설정을 그대로 돌려준다 — LDAP의 `@Value` 기본값 패턴과 동일한 취지.
 */
@Service
class SsoSettingsService(
    private val oidcRepository: OidcSsoSettingsRepository,
    private val saml2Repository: Saml2SsoSettingsRepository,
    @Value("\${yona.sso.oidc.enabled:false}")
    private val defaultOidcEnabled: Boolean,
    @Value("\${yona.sso.oidc.registration-id:oidc}")
    private val defaultOidcRegistrationId: String,
    @Value("\${yona.sso.oidc.issuer-uri:}")
    private val defaultOidcIssuerUri: String,
    @Value("\${yona.sso.oidc.client-id:}")
    private val defaultOidcClientId: String,
    @Value("\${yona.sso.oidc.client-secret:}")
    private val defaultOidcClientSecret: String,
    @Value("\${yona.sso.saml2.enabled:false}")
    private val defaultSaml2Enabled: Boolean,
    @Value("\${yona.sso.saml2.registration-id:saml2}")
    private val defaultSaml2RegistrationId: String,
    @Value("\${yona.sso.saml2.idp-sso-url:}")
    private val defaultSaml2IdpSsoUrl: String,
    @Value("\${yona.sso.saml2.idp-entity-id:}")
    private val defaultSaml2IdpEntityId: String,
    @Value("\${yona.sso.saml2.idp-certificate:}")
    private val defaultSaml2IdpCertificate: String
) {
    fun getOidcSettings(): OidcSsoSettings =
        oidcRepository.findById(OidcSsoSettings.SINGLETON_ID).orElseGet {
            OidcSsoSettings(
                enabled = defaultOidcEnabled,
                registrationId = defaultOidcRegistrationId.ifBlank { "oidc" },
                issuerUri = defaultOidcIssuerUri.ifBlank { null },
                clientId = defaultOidcClientId.ifBlank { null },
                clientSecret = defaultOidcClientSecret.ifBlank { null }
            )
        }

    @Transactional
    fun saveOidcSettings(
        enabled: Boolean,
        registrationId: String,
        issuerUri: String?,
        clientId: String?,
        clientSecret: String?
    ): OidcSsoSettings {
        val settings = oidcRepository.findById(OidcSsoSettings.SINGLETON_ID).orElseGet { OidcSsoSettings() }
        settings.enabled = enabled
        settings.registrationId = registrationId.ifBlank { "oidc" }
        settings.issuerUri = issuerUri?.ifBlank { null }
        settings.clientId = clientId?.ifBlank { null }
        settings.clientSecret = clientSecret?.ifBlank { null }
        return oidcRepository.save(settings)
    }

    fun getSaml2Settings(): Saml2SsoSettings =
        saml2Repository.findById(Saml2SsoSettings.SINGLETON_ID).orElseGet {
            Saml2SsoSettings(
                enabled = defaultSaml2Enabled,
                registrationId = defaultSaml2RegistrationId.ifBlank { "saml2" },
                idpSsoUrl = defaultSaml2IdpSsoUrl.ifBlank { null },
                idpEntityId = defaultSaml2IdpEntityId.ifBlank { null },
                idpCertificate = defaultSaml2IdpCertificate.ifBlank { null }
            )
        }

    @Transactional
    fun saveSaml2Settings(
        enabled: Boolean,
        registrationId: String,
        idpSsoUrl: String?,
        idpEntityId: String?,
        idpCertificate: String?
    ): Saml2SsoSettings {
        val settings = saml2Repository.findById(Saml2SsoSettings.SINGLETON_ID).orElseGet { Saml2SsoSettings() }
        settings.enabled = enabled
        settings.registrationId = registrationId.ifBlank { "saml2" }
        settings.idpSsoUrl = idpSsoUrl?.ifBlank { null }
        settings.idpEntityId = idpEntityId?.ifBlank { null }
        settings.idpCertificate = idpCertificate?.ifBlank { null }
        return saml2Repository.save(settings)
    }
}
