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

    /** 발급자 주소의 형식 오류를 돌려준다. 맞으면 `null`이다. https여야 하고 루프백 주소만 http를 받는다. */
    private fun validateIssuer(issuer: String): SsoSaveError? {
        val uri = runCatching { URI(issuer) }.getOrNull()
        val host = uri?.host
        if (uri == null || host == null || uri.scheme?.lowercase() !in setOf("http", "https") || uri.rawQuery != null || uri.rawFragment != null) return SsoSaveError.INVALID_ISSUER
        if (uri.scheme.equals("http", ignoreCase = true) && host !in LOOPBACK) return SsoSaveError.HTTPS_REQUIRED
        return null
    }

    private companion object {
        const val ROW = 1L
        val LOOPBACK = setOf("localhost", "127.0.0.1", "[::1]", "::1")
    }
}
