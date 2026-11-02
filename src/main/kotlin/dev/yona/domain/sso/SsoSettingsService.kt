package dev.yona.domain.sso

import dev.yona.config.YonaProperties
import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.twofactor.SecretCipher
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.util.Date

/**
 * SSO 설정의 읽기와 저장이다(추가 개발 항목 X-16, `decisions.md` D-80). 설정은 관리자 화면(데이터베이스)에만 있다. OIDC는 저장할 때 연결을 확인하고(발견 문서의 발급자가 설정과 같아야 한다)
 * 결과를 캐시한다. 비밀 값은 `SecretCipher`(용도 `yona.sso.secret`)로 암호화하고 감사 기록에는 남기지 않는다.
 */
@Service
class SsoSettingsService(
    private val oidcSettings: OidcSsoSettingsRepository,
    private val saml2Settings: Saml2SsoSettingsRepository,
    private val discoveryClient: OidcDiscoveryClient,
    private val properties: YonaProperties,
    private val audit: AuditRecorder
) {

    private val cipher by lazy { SecretCipher(properties.secret, "yona.sso.secret") }
    private val mapper = JsonMapper.builder().build()

    /** OIDC 설정이다. 저장한 적이 없으면 꺼진 기본값이다(저장하지 않는다). */
    @Transactional(readOnly = true)
    fun oidc(): OidcSsoSettings = oidcSettings.findById(ROW).orElseGet { OidcSsoSettings() }

    /** 계정을 맞추는 규칙이다. */
    @Transactional(readOnly = true)
    fun oidcPolicy(): SsoPolicy = oidc().let { SsoPolicy(it.autoCreate != false, it.linkByEmail != false, it.allowUnverifiedEmail == true, it.nameSync == true) }

    /** 풀어 둔 클라이언트 비밀 값이다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun oidcClientSecret(): String? = oidc().clientSecret?.takeIf { it.isNotBlank() }?.let { String(cipher.decrypt(it)) }

    /** 저장할 때 확인한 발견 값이다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun oidcDiscovery(): OidcDiscovery? = oidc().discoveryJson?.let { runCatching { mapper.readValue(it, OidcDiscovery::class.java) }.getOrNull() }

    /**
     * OIDC 설정을 저장한다. 사용을 켤 때는 발급자 주소 형식(https, 루프백만 http), 클라이언트 ID와 비밀 값, 연결 확인(발견 문서)이 모두 맞아야 한다. 끄는 저장은 연결을 확인하지 않는다.
     * [actor]는 바꾼 관리자이다(감사 기록의 행위자).
     */
    @Transactional
    fun saveOidc(form: OidcSsoForm, actor: User?): SsoSaveResult {
        val issuer = form.issuerUri.trim()
        validateIssuer(issuer)?.let { if (form.enabled || issuer.isNotEmpty()) return SsoSaveResult.Rejected(it) }
        val existing = oidcSettings.findById(ROW).orElse(null)
        val secret = form.clientSecret?.takeIf { it.isNotBlank() }
        if (form.enabled && (form.clientId.isBlank() || (secret == null && existing?.clientSecret.isNullOrBlank()))) return SsoSaveResult.Rejected(SsoSaveError.MISSING_CLIENT)
        var discovery: OidcDiscovery? = existing?.discoveryJson?.let { runCatching { mapper.readValue(it, OidcDiscovery::class.java) }.getOrNull() }
        if (form.enabled) {
            val found = discoveryClient.fetch(issuer)
            discovery = found.discovery ?: return SsoSaveResult.Rejected(requireNotNull(found.error))
        }
        val row = existing ?: OidcSsoSettings()
        row.enabled = form.enabled; row.displayName = form.displayName.trim().take(100); row.issuerUri = issuer; row.clientId = form.clientId.trim()
        secret?.let { row.clientSecret = cipher.encrypt(it.toByteArray()) }
        row.scopes = form.scopes.trim().ifEmpty { "openid profile email" }; row.emailClaim = form.emailClaim.trim().ifEmpty { "email" }; row.nameClaim = form.nameClaim.trim().ifEmpty { "name" }
        row.loginIdClaim = form.loginIdClaim.trim(); row.autoCreate = form.autoCreate; row.linkByEmail = form.linkByEmail; row.allowUnverifiedEmail = form.allowUnverifiedEmail
        row.nameSync = form.nameSync; row.initiateEnabled = form.initiateEnabled; row.clockSkewSeconds = form.clockSkewSeconds.coerceIn(0, 600)
        if (discovery != null) { row.discoveryJson = mapper.writeValueAsString(discovery); row.discoveryCheckedDate = Date() }
        row.updatedDate = Date()
        oidcSettings.save(row)
        audit.record(
            AuditEvent(
                "site.sso_changed", targetType = "site", details = mapOf(
                    "protocol" to "oidc", "enabled" to form.enabled, "issuer" to issuer, "client_id" to row.clientId, "secret_changed" to (secret != null), "auto_create" to form.autoCreate,
                    "link_by_email" to form.linkByEmail, "allow_unverified_email" to form.allowUnverifiedEmail, "initiate_enabled" to form.initiateEnabled
                ),
                actor = actor?.let { AuditActor(AuditActorType.USER, it.id, it.loginId) }
            )
        )
        return SsoSaveResult.Saved(row)
    }

    // --- SAML 2 ---

    /** SAML 2 설정이다. 저장한 적이 없으면 꺼진 기본값이다(저장하지 않는다). */
    @Transactional(readOnly = true)
    fun saml2(): Saml2SsoSettings = saml2Settings.findById(ROW).orElseGet { Saml2SsoSettings() }

    /** 계정을 맞추는 규칙이다. */
    @Transactional(readOnly = true)
    fun saml2Policy(): SsoPolicy = saml2().let { SsoPolicy(it.autoCreate != false, it.linkByEmail != false, it.allowUnverifiedEmail == true, it.nameSync == true) }

    /** IdP가 서명에 쓰는 인증서들이다. */
    @Transactional(readOnly = true)
    fun saml2IdpCertificates(): List<java.security.cert.X509Certificate> = parseCertificates(saml2().idpCertificates.orEmpty()) ?: emptyList()

    /** 풀어 둔 SP 비밀 키이다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun saml2SpPrivateKey(): java.security.PrivateKey? = saml2().spPrivateKey?.takeIf { it.isNotBlank() }?.let { Saml2Certificates.parsePrivateKey(String(cipher.decrypt(it))) }

    /** 교체 중인 이전 SP 비밀 키이다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun saml2SpPreviousPrivateKey(): java.security.PrivateKey? = saml2().spPreviousPrivateKey?.takeIf { it.isNotBlank() }?.let { Saml2Certificates.parsePrivateKey(String(cipher.decrypt(it))) }

    /**
     * SAML 2 설정을 저장한다. 사용을 켤 때는 IdP의 발급자와 로그인 주소(https, 루프백만 http)와 인증서가 맞아야 하고, 요청 서명은 SP 키가 있어야 켤 수 있다.
     */
    @Transactional
    fun saveSaml2(form: Saml2SsoForm, actor: User?): SsoSaveResult {
        val entityId = form.idpEntityId.trim(); val ssoUrl = form.idpSsoUrl.trim()
        val existing = saml2Settings.findById(ROW).orElse(null)
        if (form.enabled) {
            if (entityId.isEmpty() || ssoUrl.isEmpty()) return SsoSaveResult.Rejected(SsoSaveError.MISSING_IDP)
        }
        if (ssoUrl.isNotEmpty()) validateIssuer(ssoUrl, allowQuery = true)?.let { return SsoSaveResult.Rejected(it) }
        val certificates = form.idpCertificates.trim()
        if (form.enabled || certificates.isNotEmpty()) {
            val parsed = parseCertificates(certificates)
            if (parsed.isNullOrEmpty()) return SsoSaveResult.Rejected(SsoSaveError.INVALID_CERTIFICATE)
        }
        if (form.signRequests && existing?.spCertificate.isNullOrBlank()) return SsoSaveResult.Rejected(SsoSaveError.SP_KEY_REQUIRED)
        val row = existing ?: Saml2SsoSettings()
        row.enabled = form.enabled; row.displayName = form.displayName.trim().take(100); row.idpEntityId = entityId; row.idpSsoUrl = ssoUrl
        row.idpSsoBinding = if (form.idpSsoBinding.equals("POST", ignoreCase = true)) "POST" else "REDIRECT"; row.idpCertificates = certificates
        row.spEntityId = form.spEntityId.trim().ifEmpty { null }; row.nameIdFormat = form.nameIdFormat.trim().ifEmpty { null }
        row.emailAttribute = form.emailAttribute.trim().ifEmpty { "email" }; row.nameAttribute = form.nameAttribute.trim().ifEmpty { "name" }; row.loginIdAttribute = form.loginIdAttribute.trim().ifEmpty { null }
        row.autoCreate = form.autoCreate; row.linkByEmail = form.linkByEmail; row.allowUnverifiedEmail = form.allowUnverifiedEmail; row.nameSync = form.nameSync
        row.initiateEnabled = form.initiateEnabled; row.signRequests = form.signRequests; row.clockSkewSeconds = form.clockSkewSeconds.coerceIn(0, 600); row.updatedDate = Date()
        saml2Settings.save(row)
        record(actor, mapOf("protocol" to "saml2", "enabled" to form.enabled, "idp_entity_id" to entityId, "idp_sso_url" to ssoUrl, "sign_requests" to form.signRequests, "initiate_enabled" to form.initiateEnabled,
            "auto_create" to form.autoCreate, "link_by_email" to form.linkByEmail))
        return SsoSaveResult.Saved(row)
    }

    /** IdP 메타데이터(XML)에서 발급자, 로그인 주소, 서명 인증서를 읽는다. 로그인 주소는 HTTP-Redirect를 우선하고 없으면 HTTP-POST이다. DOCTYPE이 든 XML은 거절한다. */
    fun importSaml2Metadata(xml: String): Saml2MetadataImport {
        if (xml.length > MAX_METADATA) return Saml2MetadataImport.Invalid()
        return try {
            org.springframework.security.saml2.core.OpenSamlInitializationService.initialize()
            val document = org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport.getParserPool()!!.parse(java.io.ByteArrayInputStream(xml.toByteArray()))
            val element = document.documentElement
            val entity = org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport.getUnmarshallerFactory().getUnmarshaller(element)?.unmarshall(element) as? org.opensaml.saml.saml2.metadata.EntityDescriptor
                ?: return Saml2MetadataImport.Invalid()
            val idp = entity.getIDPSSODescriptor(org.opensaml.saml.common.xml.SAMLConstants.SAML20P_NS) ?: return Saml2MetadataImport.Invalid()
            val redirect = idp.singleSignOnServices.firstOrNull { it.binding == org.opensaml.saml.common.xml.SAMLConstants.SAML2_REDIRECT_BINDING_URI }
            val post = idp.singleSignOnServices.firstOrNull { it.binding == org.opensaml.saml.common.xml.SAMLConstants.SAML2_POST_BINDING_URI }
            val chosen = redirect ?: post ?: return Saml2MetadataImport.Invalid()
            val certificates = idp.keyDescriptors.filter { it.use == null || it.use == org.opensaml.security.credential.UsageType.SIGNING || it.use == org.opensaml.security.credential.UsageType.UNSPECIFIED }
                .flatMap { org.opensaml.xmlsec.keyinfo.KeyInfoSupport.getCertificates(it.keyInfo) }.map { Saml2Certificates.toPem(it) }
            val id = entity.entityID?.takeIf { it.isNotBlank() } ?: return Saml2MetadataImport.Invalid()
            val location = chosen.location?.takeIf { it.isNotBlank() } ?: return Saml2MetadataImport.Invalid()
            if (certificates.isEmpty()) return Saml2MetadataImport.Invalid(SsoSaveError.INVALID_CERTIFICATE)
            Saml2MetadataImport.Imported(id, location, if (redirect != null) "REDIRECT" else "POST", certificates)
        } catch (e: Exception) {
            Saml2MetadataImport.Invalid()
        }
    }

    /** SP 키쌍(RSA 3072, 자체 서명 인증서 5년)을 새로 만든다. 이미 키가 있으면 이전 키로 옮겨 함께 둔다(IdP가 새 인증서를 올릴 때까지, `decisions.md` D-80 21번). */
    @Transactional
    fun generateSpKeys(commonName: String, actor: User?): SsoSaveResult {
        val material = Saml2Certificates.generate(commonName)
        return storeSpKeys(material.privateKeyPem, material.certificatePem, actor, "generated")
    }

    /** 외부에서 만든 SP 키와 인증서(PEM)를 올린다. 짝이 맞아야 한다. 이미 키가 있으면 이전 키로 옮긴다. */
    @Transactional
    fun uploadSpKeys(privateKeyPem: String, certificatePem: String, actor: User?): SsoSaveResult {
        val key = runCatching { Saml2Certificates.parsePrivateKey(privateKeyPem) }.getOrNull()
        val certificate = runCatching { Saml2Certificates.parseCertificate(certificatePem) }.getOrNull()
        if (key == null || certificate == null) return SsoSaveResult.Rejected(SsoSaveError.INVALID_KEY)
        if (!Saml2Certificates.matches(key, certificate)) return SsoSaveResult.Rejected(SsoSaveError.KEY_MISMATCH)
        return storeSpKeys(privateKeyPem.trim() + "\n", certificatePem.trim() + "\n", actor, "uploaded")
    }

    /** 교체 중인 이전 SP 키를 지운다(IdP가 새 인증서로 바꾼 것을 확인한 뒤). */
    @Transactional
    fun removePreviousSpKeys(actor: User?) {
        val row = saml2Settings.findById(ROW).orElse(null) ?: return
        if (row.spPreviousCertificate == null && row.spPreviousPrivateKey == null) return
        row.spPreviousCertificate = null; row.spPreviousPrivateKey = null; row.updatedDate = Date()
        saml2Settings.save(row); record(actor, mapOf("protocol" to "saml2", "sp_keys" to "previous_removed"))
    }

    private fun storeSpKeys(privateKeyPem: String, certificatePem: String, actor: User?, how: String): SsoSaveResult {
        val row = saml2Settings.findById(ROW).orElseGet { Saml2SsoSettings() }
        if (!row.spCertificate.isNullOrBlank()) { row.spPreviousCertificate = row.spCertificate; row.spPreviousPrivateKey = row.spPrivateKey }
        row.spPrivateKey = cipher.encrypt(privateKeyPem.toByteArray()); row.spCertificate = certificatePem; row.spKeyCreatedDate = Date(); row.updatedDate = Date()
        saml2Settings.save(row)
        record(actor, mapOf("protocol" to "saml2", "sp_keys" to how, "sp_fingerprint" to Saml2Certificates.describe(Saml2Certificates.parseCertificate(certificatePem)).fingerprintSha256))
        return SsoSaveResult.Saved(row)
    }

    /** PEM 인증서 글(여러 개)을 읽는다. 하나라도 틀리면 `null`이다. */
    private fun parseCertificates(pem: String): List<java.security.cert.X509Certificate>? {
        if (pem.isBlank()) return emptyList()
        val blocks = Regex("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL).findAll(pem).map { it.value }.toList()
        if (blocks.isEmpty() || pem.replace(Regex("-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", RegexOption.DOT_MATCHES_ALL), "").isNotBlank()) return null
        return blocks.map { runCatching { Saml2Certificates.parseCertificate(it) }.getOrNull() ?: return null }
    }

    private fun record(actor: User?, details: Map<String, Any?>) = audit.record(
        AuditEvent("site.sso_changed", targetType = "site", details = details, actor = actor?.let { AuditActor(AuditActorType.USER, it.id, it.loginId) })
    )

    /** 발급자 주소의 형식 오류를 돌려준다. 맞으면 `null`이다. https여야 하고 루프백 주소만 http를 받는다. */
    private fun validateIssuer(issuer: String, allowQuery: Boolean = false): SsoSaveError? {
        val uri = runCatching { URI(issuer) }.getOrNull()
        val host = uri?.host
        if (uri == null || host == null || uri.scheme?.lowercase() !in setOf("http", "https") || (!allowQuery && uri.rawQuery != null) || uri.rawFragment != null) return SsoSaveError.INVALID_ISSUER
        if (uri.scheme.equals("http", ignoreCase = true) && host !in LOOPBACK) return SsoSaveError.HTTPS_REQUIRED
        return null
    }

    private companion object {
        const val ROW = 1L
        const val MAX_METADATA = 1024 * 1024
        val LOOPBACK = setOf("localhost", "127.0.0.1", "[::1]", "::1")
    }
}
