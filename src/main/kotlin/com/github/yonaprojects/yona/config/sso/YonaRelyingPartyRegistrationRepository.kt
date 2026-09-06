package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.saml2.core.Saml2X509Credential
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step4 — 관리자 UI(`SsoAdminController`, GitHub의 조직 SAML
 * SSO 설정 화면과 동일한 필드 구성)로 등록한 IdP 메타데이터를 매 요청마다 읽어 `RelyingPartyRegistration`을
 * 조립한다. Spring Boot 4.1.1은 `spring.security.saml2.relyingparty.*` 프로퍼티 기반 자동구성
 * 모듈을 아직 제공하지 않아(spring-boot-security-saml2 같은 모듈이 없음, 실측 확인) 어차피 수동
 * 빈 등록이 필요했다 — DB/설정 파일 어느 쪽에서 읽어오든 이 저장소가 유일한 통합 지점이 된다.
 */
@Component
class YonaRelyingPartyRegistrationRepository(
    private val ssoSettingsService: SsoSettingsService,
    @Value("\${yona.base-url}")
    private val baseUrl: String
) : RelyingPartyRegistrationRepository {

    override fun findByRegistrationId(registrationId: String): RelyingPartyRegistration? {
        val settings = ssoSettingsService.getSaml2Settings()
        if (!settings.enabled || settings.registrationId != registrationId) {
            return null
        }
        val idpSsoUrl = settings.idpSsoUrl
        val idpEntityId = settings.idpEntityId
        val idpCertificatePem = settings.idpCertificate
        if (idpSsoUrl.isNullOrBlank() || idpEntityId.isNullOrBlank() || idpCertificatePem.isNullOrBlank()) {
            return null
        }

        val verificationCredential = Saml2X509Credential.verification(parseCertificate(idpCertificatePem))

        return RelyingPartyRegistration.withRegistrationId(registrationId)
            .entityId("$baseUrl/saml2/service-provider-metadata/{registrationId}")
            .assertionConsumerServiceLocation("$baseUrl/login/saml2/sso/{registrationId}")
            .assertingPartyMetadata { party ->
                party
                    .entityId(idpEntityId)
                    .singleSignOnServiceLocation(idpSsoUrl)
                    .wantAuthnRequestsSigned(false)
                    .verificationX509Credentials { it.add(verificationCredential) }
            }
            .build()
    }

    private fun parseCertificate(pem: String): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        return factory.generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
    }
}
