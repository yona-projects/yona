package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import dev.yona.domain.audit.AuditActor
import dev.yona.domain.audit.AuditActorType
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.sso.SsoSaveError
import dev.yona.domain.sso.SsoSaveResult
import dev.yona.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Date

/**
 * 사이트의 LDAP 설정이다(추가 개발 항목, `decisions.md` D-82). 사이트에 한 행(번호 1)이며, 행이 없으면 설정 파일(`yona.ldap.*`, `yona.protocol`)의 값을 그대로 쓴다.
 * 각 열의 뜻은 [YonaProperties.Ldap]과 같다. [protocol]은 `ldap` 또는 `ldaps`이다.
 */
@Entity
@Table(name = "ldap_settings")
class LdapSettings(
    @Id var id: Long = 1,
    var enabled: Boolean? = false,
    @Column(length = 10) var protocol: String? = "ldap",
    @Column(length = 255) var host: String? = null,
    @Column(length = 10) var port: String? = null,
    @Column(name = "base_dn", length = 500) var baseDn: String? = null,
    @Column(name = "distinguished_name_postfix", length = 500) var distinguishedNamePostfix: String? = null,
    @Column(name = "login_property", length = 100) var loginProperty: String? = null,
    @Column(name = "display_name_property", length = 100) var displayNameProperty: String? = null,
    @Column(name = "user_name_property", length = 100) var userNameProperty: String? = null,
    @Column(name = "email_property", length = 100) var emailProperty: String? = null,
    @Column(name = "use_email_base_login") var useEmailBaseLogin: Boolean? = false,
    @Column(name = "fallback_to_local_login") var fallbackToLocalLogin: Boolean? = false,
    @Column(name = "english_name_attribute_name", length = 100) var englishNameAttributeName: String? = null,
    @Column(name = "updated_date") var updatedDate: Date? = null
)

/** [LdapSettings] 저장소이다. */
interface LdapSettingsRepository : JpaRepository<LdapSettings, Long>

/** 관리자 화면이 보내는 LDAP 설정 입력이다. */
data class LdapSettingsForm(
    val enabled: Boolean, val protocol: String, val host: String, val port: String, val baseDn: String, val distinguishedNamePostfix: String, val loginProperty: String,
    val displayNameProperty: String, val userNameProperty: String, val emailProperty: String, val useEmailBaseLogin: Boolean, val fallbackToLocalLogin: Boolean, val englishNameAttributeName: String
)

/**
 * LDAP 설정의 읽기와 저장이다(`decisions.md` D-82). 화면에서 한 번도 저장하지 않았으면 설정 파일 값을 그대로 쓰고(v1.6에서 올라온 사이트의 로그인이 끊기지 않게), 저장하면 그 뒤로는 화면 값이 이긴다.
 * 바인드 비밀번호는 저장하지 않는다(로그인하는 사용자가 입력한 비밀번호로 바인드한다).
 */
@Service
class LdapSettingsService(private val repository: LdapSettingsRepository, private val properties: YonaProperties, private val audit: AuditRecorder) {

    /** 화면에서 저장한 적이 있으면 `true`이다. */
    @Transactional(readOnly = true)
    fun isSaved(): Boolean = repository.existsById(ROW)

    /** 지금 적용되는 LDAP 설정이다(저장한 값, 없으면 설정 파일 값). */
    @Transactional(readOnly = true)
    fun effective(): YonaProperties.Ldap {
        val row = repository.findById(ROW).orElse(null) ?: return properties.ldap
        val file = properties.ldap
        return YonaProperties.Ldap(
            enabled = row.enabled == true, host = row.host ?: file.host, port = row.port ?: file.port, baseDn = row.baseDn ?: file.baseDn,
            distinguishedNamePostfix = row.distinguishedNamePostfix ?: file.distinguishedNamePostfix, loginProperty = row.loginProperty ?: file.loginProperty,
            displayNameProperty = row.displayNameProperty ?: file.displayNameProperty, userNameProperty = row.userNameProperty ?: file.userNameProperty, emailProperty = row.emailProperty ?: file.emailProperty,
            useEmailBaseLogin = row.useEmailBaseLogin == true, fallbackToLocalLogin = row.fallbackToLocalLogin == true, englishNameAttributeName = row.englishNameAttributeName ?: file.englishNameAttributeName
        )
    }

    /** 지금 적용되는 접속 프로토콜(`ldap` 또는 `ldaps`)이다. */
    @Transactional(readOnly = true)
    fun protocol(): String = repository.findById(ROW).orElse(null)?.protocol?.takeIf { it.isNotBlank() } ?: properties.protocol

    /** LDAP으로 로그인하도록 켜져 있으면 `true`이다. */
    @Transactional(readOnly = true)
    fun enabled(): Boolean = effective().enabled

    /**
     * 설정을 저장한다. 켤 때는 서버 주소(글자, 숫자, `.`, `-`, `_`, `:`, 대괄호만), 포트(1~65535), 기준 DN이 맞아야 하고 프로토콜은 `ldap`이나 `ldaps`여야 한다. 끄는 저장은 서버 정보가 비어 있어도 된다.
     */
    @Transactional
    fun save(form: LdapSettingsForm, actor: User?): SsoSaveResult {
        val protocol = form.protocol.trim().lowercase()
        if (protocol != "ldap" && protocol != "ldaps") return SsoSaveResult.Rejected(SsoSaveError.LDAP_PROTOCOL_INVALID)
        val host = form.host.trim(); val port = form.port.trim(); val baseDn = form.baseDn.trim()
        if (form.enabled) {
            if (host.isEmpty() || !HOST.matches(host)) return SsoSaveResult.Rejected(SsoSaveError.LDAP_HOST_REQUIRED)
            if (port.toIntOrNull()?.let { it in 1..65535 } != true) return SsoSaveResult.Rejected(SsoSaveError.LDAP_PORT_INVALID)
            if (baseDn.isEmpty()) return SsoSaveResult.Rejected(SsoSaveError.LDAP_BASE_DN_REQUIRED)
        } else if (host.isNotEmpty() && !HOST.matches(host)) return SsoSaveResult.Rejected(SsoSaveError.LDAP_HOST_REQUIRED)
        val row = repository.findById(ROW).orElseGet { LdapSettings() }
        row.enabled = form.enabled; row.protocol = protocol; row.host = host.ifEmpty { null }; row.port = port.ifEmpty { null }; row.baseDn = baseDn.ifEmpty { null }
        row.distinguishedNamePostfix = form.distinguishedNamePostfix.trim().ifEmpty { null }; row.loginProperty = form.loginProperty.trim().ifEmpty { null }
        row.displayNameProperty = form.displayNameProperty.trim().ifEmpty { null }; row.userNameProperty = form.userNameProperty.trim().ifEmpty { null }; row.emailProperty = form.emailProperty.trim().ifEmpty { null }
        row.useEmailBaseLogin = form.useEmailBaseLogin; row.fallbackToLocalLogin = form.fallbackToLocalLogin; row.englishNameAttributeName = form.englishNameAttributeName.trim()
        row.updatedDate = Date()
        repository.save(row)
        audit.record(
            AuditEvent(
                "site.sso_changed", targetType = "site", details = mapOf("protocol" to "ldap", "enabled" to form.enabled, "host" to host, "port" to port, "base_dn" to baseDn, "ldap_protocol" to protocol,
                    "use_email_base_login" to form.useEmailBaseLogin, "fallback_to_local_login" to form.fallbackToLocalLogin),
                actor = actor?.let { AuditActor(AuditActorType.USER, it.id, it.loginId) }
            )
        )
        return SsoSaveResult.Saved(row)
    }

    /** 서버의 주소와 포트로 연결할 수 있는지 본다(5초 안에). 연결하면 `null`, 못 하면 [SsoSaveError.LDAP_UNREACHABLE]이다. 바인드는 하지 않는다. */
    fun check(): SsoSaveError? {
        val config = effective()
        return try {
            Socket().use { it.connect(InetSocketAddress(config.host, config.port.toInt()), TIMEOUT_MILLIS) }
            null
        } catch (e: Exception) {
            SsoSaveError.LDAP_UNREACHABLE
        }
    }

    private companion object {
        const val ROW = 1L
        const val TIMEOUT_MILLIS = 5000
        val HOST = Regex("[A-Za-z0-9._:\\[\\]-]+")
    }
}
