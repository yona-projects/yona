package dev.yona.sso

import dev.yona.domain.sso.Saml2Certificates
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.util.SiteUrls
import org.springframework.security.saml2.core.Saml2X509Credential
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding
import org.springframework.stereotype.Component

/**
 * 관리자가 저장한 SAML 2 설정을 스프링 시큐리티의 `RelyingPartyRegistration`으로 바꿔 준다(X-16, `decisions.md` D-80 15번). 등록은 하나(`saml2`)이고 매 요청마다 저장된 설정에서 만들므로,
 * 설정이나 SP 키를 바꾸면 재시작 없이 반영된다. 꺼져 있거나 IdP 정보가 비어 있으면 등록이 없다(`null`). 이전 SP 키는 암호화된 어설션을 풀 때 함께 쓴다.
 */
@Component
class SsoSaml2Registrations(private val settings: SsoSettingsService, private val siteUrls: SiteUrls) : RelyingPartyRegistrationRepository {

    override fun findByRegistrationId(registrationId: String): RelyingPartyRegistration? {
        if (registrationId != ID) return null
        val row = settings.saml2()
        if (row.enabled != true) return null
        val certificates = settings.saml2IdpCertificates()
        val ssoUrl = row.idpSsoUrl?.takeIf { it.isNotBlank() } ?: return null
        val idpEntityId = row.idpEntityId?.takeIf { it.isNotBlank() } ?: return null
        if (certificates.isEmpty()) return null
        val privateKey = settings.saml2SpPrivateKey()
        val certificate = row.spCertificate?.takeIf { it.isNotBlank() }?.let { Saml2Certificates.parseCertificate(it) }
        val previousKey = settings.saml2SpPreviousPrivateKey()
        val previousCertificate = row.spPreviousCertificate?.takeIf { it.isNotBlank() }?.let { Saml2Certificates.parseCertificate(it) }
        val builder = RelyingPartyRegistration.withRegistrationId(ID)
            .entityId(entityId(row.spEntityId))
            .assertionConsumerServiceLocation(siteUrls.origin() + siteUrls.path(ACS_PATH))
            .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
            .assertingPartyMetadata { party ->
                party.entityId(idpEntityId).singleSignOnServiceLocation(ssoUrl)
                    .singleSignOnServiceBinding(if (row.idpSsoBinding == "POST") Saml2MessageBinding.POST else Saml2MessageBinding.REDIRECT)
                    .wantAuthnRequestsSigned(row.signRequests == true && privateKey != null)
                    .verificationX509Credentials { list -> certificates.forEach { list.add(Saml2X509Credential.verification(it)) } }
            }
        row.nameIdFormat?.takeIf { it.isNotBlank() }?.let { builder.nameIdFormat(it) }
        if (privateKey != null && certificate != null) {
            builder.signingX509Credentials { it.add(Saml2X509Credential.signing(privateKey, certificate)) }
            builder.decryptionX509Credentials {
                it.add(Saml2X509Credential.decryption(privateKey, certificate))
                if (previousKey != null && previousCertificate != null) it.add(Saml2X509Credential.decryption(previousKey, previousCertificate))
            }
        }
        return builder.build()
    }

    /** 이 사이트(SP)의 Entity ID이다. 설정하지 않으면 SP 메타데이터 주소를 쓴다. */
    fun entityId(configured: String?): String = configured?.takeIf { it.isNotBlank() } ?: (siteUrls.origin() + siteUrls.path(METADATA_PATH))

    companion object {
        /** 등록 이름이다. */
        const val ID = "saml2"
        const val ACS_PATH = "/sso/saml2/acs"
        const val METADATA_PATH = "/sso/saml2/metadata/$ID"
        const val AUTHENTICATE_PATH = "/sso/saml2/authenticate/$ID"
    }
}
