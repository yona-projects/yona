package dev.yona.domain.twofactor

import dev.yona.config.YonaProperties
import dev.yona.domain.audit.AuditEvent
import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.audit.auditUser
import dev.yona.domain.user.User
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date

/**
 * 사이트 비밀 키(`yona.secret`)에서 만든 2단계 인증의 암호 도구이다(추가 개발 항목 X-2, `decisions.md` D-69). **사이트 비밀 키를 바꾸면 이미 저장한 비밀 값과 백업 코드를 읽을 수 없게 되어**
 * 사용자가 2단계 인증을 다시 등록해야 한다(관리자가 해제할 수 있다).
 */
@Component
class TwoFactorCrypto(properties: YonaProperties) {
    val cipher = SecretCipher(properties.secret)
    val backupCodes = BackupCodes(properties.secret)
}

/** 인증 앱 등록을 시작한 결과이다. [secret]은 사용자가 직접 입력할 수 있는 base32 글이고 [otpauthUri]는 QR 코드의 내용이다. */
data class TotpEnrollment(val secret: String, val otpauthUri: String)

/** 등록 확인 결과이다. */
sealed interface TotpConfirmation {
    /** 확인했다. [backupCodes]는 이때 한 번만 보여 주는 백업 코드이다. */
    data class Confirmed(val backupCodes: List<String>) : TotpConfirmation

    /** 시작한 등록이 없다. */
    data object NotStarted : TotpConfirmation

    /** 코드가 맞지 않는다. */
    data object InvalidCode : TotpConfirmation
}

/** 로그인할 때 2단계 코드를 확인한 결과이다. */
enum class TwoFactorResult { OK_TOTP, OK_BACKUP_CODE, INVALID, NOT_ENABLED }

/**
 * 2단계 인증(인증 앱과 백업 코드)이다(추가 개발 항목 X-2, `decisions.md` D-69). 등록은 비밀 값을 만들어 보여 주고(확인하지 않은 상태), 사용자가 인증 앱의 코드를 입력해 맞으면 켜진다.
 * 켤 때 백업 코드 10개를 한 번 보여 준다. 로그인할 때는 6자리 TOTP 코드나 백업 코드를 받는다. 같은 TOTP 코드는 다시 쓰지 못하고(구간 번호를 데이터베이스에서 한 번에 올린다), 백업 코드는 한 번만 쓴다.
 * 비밀 값은 암호화해 저장하고 백업 코드는 해시만 저장한다. 감사 기록에는 비밀 값과 코드를 남기지 않는다.
 */
@Service
class TwoFactorService(
    private val credentials: TwoFactorCredentialRepository,
    private val backupCodeRows: TwoFactorBackupCodeRepository,
    private val devices: KnownDeviceRepository,
    private val crypto: TwoFactorCrypto,
    private val audit: AuditRecorder
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 사용자가 2단계 인증을 켰으면(확인을 마친 수단이 있으면) `true`이다. */
    @Transactional(readOnly = true)
    fun isEnabled(user: User): Boolean = credentials.existsByUserIdAndConfirmedTrue(requireNotNull(user.id))

    /**
     * 인증 앱 등록을 시작한다. 새 비밀 값을 만들어 암호화해 저장하고(확인하지 않은 상태) 돌려준다. 이전에 확인하지 않은 등록은 지운다. 이미 켜져 있으면 `null`이다(먼저 끄고 다시 등록한다).
     *
     * @param issuer 인증 앱에 보일 발급자(사이트 이름)
     * @param account 인증 앱에 보일 계정 이름
     */
    @Transactional
    fun beginTotp(user: User, issuer: String, account: String, now: Date = Date()): TotpEnrollment? {
        val userId = requireNotNull(user.id)
        if (isEnabled(user)) return null
        credentials.findByUserIdOrderById(userId).filter { it.confirmed != true }.forEach { credentials.delete(it) }
        credentials.flush()
        val secret = Totp.newSecret()
        credentials.save(TwoFactorCredential(user = user, name = "TOTP", secretEncrypted = crypto.cipher.encrypt(secret), confirmed = false, createdDate = now))
        return TotpEnrollment(Base32.encode(secret), Totp.otpauthUri(secret, issuer, account))
    }

    /** 시작했지만 아직 확인하지 않은 등록이다. 틀린 코드를 입력했을 때 같은 비밀 값으로 설정 화면을 다시 보이는 데 쓴다. 없으면 `null`이다. */
    @Transactional(readOnly = true)
    fun pending(user: User, issuer: String, account: String): TotpEnrollment? {
        val credential = credentials.findByUserIdOrderById(requireNotNull(user.id)).firstOrNull { it.confirmed != true } ?: return null
        val secret = secretOf(credential) ?: return null
        return TotpEnrollment(Base32.encode(secret), Totp.otpauthUri(secret, issuer, account))
    }

    /** 시작한 등록을 사용자가 입력한 코드로 확인한다. 맞으면 2단계 인증이 켜지고 백업 코드를 만든다. */
    @Transactional
    fun confirmTotp(user: User, code: String, now: Instant = Instant.now()): TotpConfirmation {
        val credential = credentials.findByUserIdOrderById(requireNotNull(user.id)).firstOrNull { it.confirmed != true } ?: return TotpConfirmation.NotStarted
        val secret = secretOf(credential) ?: return TotpConfirmation.InvalidCode
        val step = Totp.verify(secret, code, now.epochSecond, lastUsedStep = null) ?: return TotpConfirmation.InvalidCode
        credential.confirmed = true
        credential.lastUsedStep = step
        credential.lastUsedDate = Date.from(now)
        credentials.save(credential)
        val codes = replaceBackupCodes(user)
        audit.record(auditUser("two_factor.enabled", user, mapOf("method" to "totp")))
        return TotpConfirmation.Confirmed(codes)
    }

    /**
     * 로그인할 때 입력한 [input]을 확인한다. 열 글자 백업 코드 모양이면 백업 코드로, 아니면 TOTP 코드로 본다. 맞으면 같은 코드를 다시 쓰지 못하게 기록한다.
     * 2단계 인증을 켜지 않았으면 [TwoFactorResult.NOT_ENABLED]이다.
     */
    @Transactional
    fun verify(user: User, input: String, now: Instant = Instant.now()): TwoFactorResult {
        val userId = requireNotNull(user.id)
        if (!isEnabled(user)) return TwoFactorResult.NOT_ENABLED
        if (crypto.backupCodes.looksLikeCode(input)) {
            val row = backupCodeRows.findByUserIdAndCodeHashAndUsedDateIsNull(userId, crypto.backupCodes.hash(input)) ?: return TwoFactorResult.INVALID
            if (backupCodeRows.markUsed(requireNotNull(row.id), Date.from(now)) == 0) return TwoFactorResult.INVALID
            audit.record(auditUser("two_factor.backup_code_used", user, mapOf("remaining" to backupCodeRows.countByUserIdAndUsedDateIsNull(userId))))
            return TwoFactorResult.OK_BACKUP_CODE
        }
        for (credential in credentials.findByUserIdAndTypeOrderById(userId, TwoFactorCredential.TOTP).filter { it.confirmed == true }) {
            val secret = secretOf(credential) ?: continue
            val step = Totp.verify(secret, input, now.epochSecond, credential.lastUsedStep) ?: continue
            if (credentials.advance(requireNotNull(credential.id), step, Date.from(now)) == 1) return TwoFactorResult.OK_TOTP
        }
        return TwoFactorResult.INVALID
    }

    /** 아직 쓰지 않은 백업 코드의 수이다. */
    @Transactional(readOnly = true)
    fun backupCodesRemaining(user: User): Long = backupCodeRows.countByUserIdAndUsedDateIsNull(requireNotNull(user.id))

    /** 백업 코드를 새로 만든다. 이전 코드는 모두 쓸 수 없게 된다. 2단계 인증이 꺼져 있으면 `null`이다. */
    @Transactional
    fun regenerateBackupCodes(user: User): List<String>? {
        if (!isEnabled(user)) return null
        return replaceBackupCodes(user).also { audit.record(auditUser("two_factor.backup_codes_regenerated", user)) }
    }

    /** 사용자 본인이 2단계 인증을 끈다. 수단, 백업 코드, 알려진 기기를 모두 지운다. 켜져 있지 않았으면 `false`이다. */
    @Transactional
    fun disable(user: User): Boolean {
        if (!isEnabled(user)) return false
        clear(user)
        audit.record(auditUser("two_factor.disabled", user, mapOf("by" to "user")))
        return true
    }

    /** 사이트 관리자가 사용자의 2단계 인증을 해제한다(기기를 잃어버린 사용자를 돕는다). 켜져 있지 않았으면 `false`이다. */
    @Transactional
    fun resetByAdmin(target: User): Boolean {
        if (!isEnabled(target) && credentials.findByUserIdOrderById(requireNotNull(target.id)).isEmpty()) return false
        clear(target)
        audit.record(auditUser("two_factor.disabled", target, mapOf("by" to "admin")))
        return true
    }

    /** 서버 설정에 의한 긴급 해제이다(`yona.two-factor.reset-user`). 감사 기록의 구분은 `emergency`이다. 지울 데이터가 없었으면 `false`이다. */
    @Transactional
    fun resetByEmergency(target: User): Boolean {
        if (credentials.findByUserIdOrderById(requireNotNull(target.id)).isEmpty()) return false
        clear(target)
        audit.record(auditUser("two_factor.disabled", target, mapOf("by" to "emergency")))
        return true
    }

    /** 사용자의 2단계 인증 데이터를 모두 지운다(감사 기록을 남기지 않는다. 호출하는 쪽이 남긴다). */
    @Transactional
    fun clear(user: User) {
        val id = requireNotNull(user.id)
        backupCodeRows.deleteByUserId(id); devices.deleteByUserId(id); credentials.deleteByUserId(id)
    }

    private fun replaceBackupCodes(user: User, now: Date = Date()): List<String> {
        val id = requireNotNull(user.id)
        backupCodeRows.deleteByUserId(id)
        backupCodeRows.flush()
        val codes = crypto.backupCodes.generate()
        codes.forEach { backupCodeRows.save(TwoFactorBackupCode(user = user, codeHash = crypto.backupCodes.hash(it), createdDate = now)) }
        return codes
    }

    /** 저장한 비밀 값을 복호화한다. 사이트 비밀 키가 바뀌었거나 내용이 깨졌으면 로그를 남기고 `null`이다(인증은 실패로 처리한다). */
    private fun secretOf(credential: TwoFactorCredential): ByteArray? = try {
        crypto.cipher.decrypt(credential.secretEncrypted.orEmpty())
    } catch (e: IllegalArgumentException) {
        log.warn("2단계 인증 비밀 값을 읽을 수 없다(사이트 비밀 키가 바뀌었을 수 있다): credentialId={}", credential.id)
        null
    }
}
