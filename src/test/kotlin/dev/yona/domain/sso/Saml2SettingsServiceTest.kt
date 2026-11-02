package dev.yona.domain.sso

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SAML 2 설정의 저장과 IdP 메타데이터 읽기, SP 키 관리(X-16 단계 3, `decisions.md` D-80 15, 19, 21번)를 검증한다. 사용을 켜려면 IdP의 발급자, 로그인 주소, 인증서가 맞아야 하고,
 * IdP 메타데이터(XML)에서 이 값들을 읽을 수 있다. SP 키쌍은 앱 안에서 만들거나 올리고, 교체하면 이전 키를 함께 두며(IdP가 인증서를 새로 올릴 시간), 비밀 키는 암호화해 저장한다.
 */
@Transactional
class Saml2SettingsServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: SsoSettingsService
    @Autowired private lateinit var repository: Saml2SsoSettingsRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private val idpCertificate = Saml2Certificates.generate("idp.corp.example")

    private fun form(enabled: Boolean = true, entityId: String = "https://idp.corp.example/metadata", ssoUrl: String = "https://idp.corp.example/sso", certificates: String = idpCertificate.certificatePem) = Saml2SsoForm(
        enabled = enabled, displayName = "회사 SAML", idpEntityId = entityId, idpSsoUrl = ssoUrl, idpCertificates = certificates, spEntityId = "", nameIdFormat = "", emailAttribute = "email", nameAttribute = "name",
        loginIdAttribute = "", autoCreate = true, linkByEmail = true, allowUnverifiedEmail = false, nameSync = false, initiateEnabled = false, signRequests = false, clockSkewSeconds = 60
    )

    private fun metadataXml(entityId: String = "https://idp.corp.example/metadata", location: String = "https://idp.corp.example/sso", certificate: String = idpCertificate.certificatePem) = """
        <md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="$entityId">
          <md:IDPSSODescriptor WantAuthnRequestsSigned="false" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
            <md:KeyDescriptor use="signing"><ds:KeyInfo><ds:X509Data><ds:X509Certificate>${certificate.lines().filter { !it.startsWith("-----") }.joinToString("")}</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor>
            <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://idp.corp.example/post"/>
            <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="$location"/>
          </md:IDPSSODescriptor>
        </md:EntityDescriptor>
    """.trimIndent()

    @Test
    fun `기본은 꺼져 있고 정책은 기본값이다`() {
        assertFalse(service.saml2().enabled == true); assertEquals(SsoPolicy(), service.saml2Policy()); assertNull(service.saml2().spCertificate)
    }

    @Test
    fun `IdP 정보를 저장하고 인증서를 확인한다`() {
        assertIs<SsoSaveResult.Saved>(service.saveSaml2(form(), null))
        val row = repository.findAll().single()
        assertEquals("https://idp.corp.example/metadata", row.idpEntityId); assertEquals("https://idp.corp.example/sso", row.idpSsoUrl); assertTrue(row.enabled == true)
        assertEquals(1, service.saml2IdpCertificates().size)
    }

    @Test
    fun `인증서가 여러 개여도 받고 하나도 없거나 형식이 틀리면 거절한다`() {
        val second = Saml2Certificates.generate("second.corp.example")
        service.saveSaml2(form(certificates = idpCertificate.certificatePem + "\n" + second.certificatePem), null); assertEquals(2, service.saml2IdpCertificates().size)
        assertEquals(SsoSaveError.INVALID_CERTIFICATE, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form(certificates = ""), null)).error)
        assertEquals(SsoSaveError.INVALID_CERTIFICATE, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form(certificates = "not a certificate"), null)).error)
    }

    @Test
    fun `발급자와 로그인 주소가 없거나 https가 아닌 바깥 주소는 거절한다`() {
        assertEquals(SsoSaveError.MISSING_IDP, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form(entityId = ""), null)).error)
        assertEquals(SsoSaveError.INVALID_ISSUER, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form(ssoUrl = "not a url"), null)).error)
        assertEquals(SsoSaveError.HTTPS_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form(ssoUrl = "http://idp.corp.example/sso"), null)).error)
        assertIs<SsoSaveResult.Saved>(service.saveSaml2(form(ssoUrl = "http://127.0.0.1:9999/sso"), null))
    }

    @Test
    fun `끄는 저장은 IdP 정보가 비어 있어도 된다`() {
        assertIs<SsoSaveResult.Saved>(service.saveSaml2(form(enabled = false, entityId = "", ssoUrl = "", certificates = ""), null))
    }

    @Test
    fun `IdP 메타데이터에서 발급자와 로그인 주소와 인증서를 읽는다`() {
        val metadata = assertIs<Saml2MetadataImport.Imported>(service.importSaml2Metadata(metadataXml()))
        assertEquals("https://idp.corp.example/metadata", metadata.entityId); assertEquals("https://idp.corp.example/sso", metadata.ssoUrl)
        assertEquals(1, metadata.certificatesPem.size); assertEquals(Saml2Certificates.describe(Saml2Certificates.parseCertificate(idpCertificate.certificatePem)).fingerprintSha256, Saml2Certificates.describe(Saml2Certificates.parseCertificate(metadata.certificatesPem.single())).fingerprintSha256)
    }

    @Test
    fun `잘못된 메타데이터는 읽지 못한다`() {
        assertIs<Saml2MetadataImport.Invalid>(service.importSaml2Metadata("not xml")); assertIs<Saml2MetadataImport.Invalid>(service.importSaml2Metadata("<a/>"))
        assertIs<Saml2MetadataImport.Invalid>(service.importSaml2Metadata("""<?xml version="1.0"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" entityID="&xxe;"/>"""))
    }

    // --- SP 키 ---

    @Test
    fun `SP 키쌍을 만들면 인증서를 공개하고 비밀 키는 암호화해 저장한다`() {
        service.generateSpKeys("yona.example.com", null)
        val row = service.saml2()
        assertTrue(row.spCertificate!!.startsWith("-----BEGIN CERTIFICATE-----")); assertTrue(row.spPrivateKey!!.startsWith("v1.")); assertFalse(row.spPrivateKey!!.contains("PRIVATE KEY"))
        assertTrue(Saml2Certificates.matches(service.saml2SpPrivateKey()!!, Saml2Certificates.parseCertificate(row.spCertificate!!)))
        assertNull(row.spPreviousCertificate)
    }

    @Test
    fun `키를 다시 만들면 이전 키를 교체 중으로 함께 두고 지우면 사라진다`() {
        service.generateSpKeys("yona.example.com", null); val first = service.saml2().spCertificate
        service.generateSpKeys("yona.example.com", null)
        val row = service.saml2()
        assertNotEquals(first, row.spCertificate); assertEquals(first, row.spPreviousCertificate)
        assertTrue(Saml2Certificates.matches(service.saml2SpPreviousPrivateKey()!!, Saml2Certificates.parseCertificate(first!!)))
        service.removePreviousSpKeys(null); assertNull(service.saml2().spPreviousCertificate); assertNull(service.saml2SpPreviousPrivateKey())
    }

    @Test
    fun `외부에서 만든 키와 인증서를 올릴 수 있고 짝이 맞지 않으면 거절한다`() {
        val a = Saml2Certificates.generate("a.example.com"); val b = Saml2Certificates.generate("b.example.com")
        assertEquals(SsoSaveError.KEY_MISMATCH, assertIs<SsoSaveResult.Rejected>(service.uploadSpKeys(a.privateKeyPem, b.certificatePem, null)).error)
        assertEquals(SsoSaveError.INVALID_KEY, assertIs<SsoSaveResult.Rejected>(service.uploadSpKeys("nope", a.certificatePem, null)).error)
        assertIs<SsoSaveResult.Saved>(service.uploadSpKeys(a.privateKeyPem, a.certificatePem, null))
        assertEquals(a.certificatePem.trim(), service.saml2().spCertificate!!.trim())
    }

    @Test
    fun `요청 서명은 SP 키가 있어야 켤 수 있다`() {
        assertEquals(SsoSaveError.SP_KEY_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.saveSaml2(form().copy(signRequests = true), null)).error)
        service.generateSpKeys("yona.example.com", null)
        assertIs<SsoSaveResult.Saved>(service.saveSaml2(form().copy(signRequests = true), null))
    }

    @Test
    fun `키 변경과 설정 변경은 감사 기록에 남지만 비밀 키는 남기지 않는다`() {
        service.saveSaml2(form(), null); service.generateSpKeys("yona.example.com", null)
        val all = logs.findAll().filter { it.action == "site.sso_changed" }
        assertTrue(all.any { it.details!!.contains("saml2") }); assertTrue(all.any { it.details!!.contains("sp_keys") })
        assertTrue(all.none { (it.details + it.targetLabel).contains("PRIVATE KEY") || (it.details + "").contains("v1.") })
    }
}
