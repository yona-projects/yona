package com.github.yonaprojects.yona.domain.twofactor

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class TwoFactorServiceImpl(
    private val totpCredentialRepository: TwoFactorTotpCredentialRepository,
    private val webauthnCredentialRepository: WebauthnCredentialRepository,
    private val backupCodeRepository: BackupCodeRepository,
    private val userRepository: UserRepository,
    private val totpSecretEncryptor: TotpSecretEncryptor,
    private val totpCodeVerifier: TotpCodeVerifier,
    private val backupCodeGenerator: BackupCodeGenerator
) : TwoFactorService {

    @Transactional(readOnly = true)
    override fun isTwoFactorEnabled(user: User): Boolean {
        val userId = user.id ?: return false
        return totpCredentialRepository.findByUserIdAndEnabledTrue(userId).isNotEmpty() ||
            webauthnCredentialRepository.findByUserId(userId).isNotEmpty()
    }

    @Transactional(readOnly = true)
    override fun listWebauthnCredentials(user: User): List<WebauthnCredential> =
        webauthnCredentialRepository.findByUserId(user.id ?: return emptyList())

    @Transactional(readOnly = true)
    override fun listEnabledTotpCredentials(user: User): List<TwoFactorTotpCredential> =
        totpCredentialRepository.findByUserIdAndEnabledTrue(user.id ?: return emptyList())

    @Transactional
    override fun beginTotpEnrollment(user: User, accountLabel: String): Pair<TwoFactorTotpCredential, TotpEnrollment> {
        // 검증 없이 방치된 이전 미확정 등록 시도를 정리한다(같은 계정이 QR 화면을 여러 번 새로고침해도
        // 미확정 행이 계속 쌓이지 않도록).
        val userId = requireNotNull(user.id) { "저장되지 않은 사용자입니다." }
        totpCredentialRepository.findByUserId(userId)
            .filter { !it.enabled }
            .forEach { totpCredentialRepository.delete(it) }

        val enrollment = totpCodeVerifier.generateEnrollment(accountLabel)
        val credential = totpCredentialRepository.save(
            TwoFactorTotpCredential(
                user = user,
                label = accountLabel,
                encryptedSecret = totpSecretEncryptor.encrypt(enrollment.rawSecret),
                enabled = false,
                createdAt = Instant.now()
            )
        )
        return credential to enrollment
    }

    @Transactional
    override fun verifyAndActivateTotp(user: User, credentialId: Long, code: String): TotpActivationResult {
        val credential = totpCredentialRepository.findById(credentialId).orElse(null)
        if (credential == null || credential.user?.id != user.id) {
            return TotpActivationResult.NotFound
        }

        val secret = totpSecretEncryptor.decrypt(credential.encryptedSecret)
        if (!totpCodeVerifier.isValid(secret, code)) {
            return TotpActivationResult.InvalidCode
        }

        credential.enabled = true
        credential.activatedAt = Instant.now()
        totpCredentialRepository.save(credential)
        refreshSummaryFlag(user)

        return TotpActivationResult.Success(ensureBackupCodesInitialized(user))
    }

    @Transactional(readOnly = true)
    override fun reviewPendingTotpEnrollment(user: User, credentialId: Long): TotpEnrollment? {
        val credential = totpCredentialRepository.findById(credentialId).orElse(null) ?: return null
        if (credential.user?.id != user.id || credential.enabled) return null
        val secret = totpSecretEncryptor.decrypt(credential.encryptedSecret)
        return TotpEnrollment(rawSecret = secret, qrCodeDataUri = totpCodeVerifier.qrCodeDataUri(credential.label, secret))
    }

    @Transactional
    override fun deleteTotpCredential(user: User, credentialId: Long) {
        val credential = totpCredentialRepository.findById(credentialId).orElse(null) ?: return
        if (credential.user?.id != user.id) return
        totpCredentialRepository.delete(credential)
        refreshSummaryFlag(user)
    }

    @Transactional
    override fun deleteWebauthnCredential(user: User, credentialId: Long) {
        val credential = webauthnCredentialRepository.findById(credentialId).orElse(null) ?: return
        if (credential.user?.id != user.id) return
        webauthnCredentialRepository.delete(credential)
        refreshSummaryFlag(user)
    }

    @Transactional
    override fun completeWebauthnRegistration(user: User, credential: WebauthnCredential): List<String>? {
        refreshSummaryFlag(user)
        return ensureBackupCodesInitialized(user)
    }

    @Transactional(readOnly = true)
    override fun verifyTotpForLogin(user: User, code: String): Boolean {
        val userId = user.id ?: return false
        return totpCredentialRepository.findByUserIdAndEnabledTrue(userId).any {
            totpCodeVerifier.isValid(totpSecretEncryptor.decrypt(it.encryptedSecret), code)
        }
    }

    @Transactional(readOnly = true)
    override fun hasUnusedBackupCodes(user: User): Boolean {
        val userId = user.id ?: return false
        return backupCodeRepository.findByUserIdAndUsedAtIsNull(userId).isNotEmpty()
    }

    @Transactional
    override fun regenerateBackupCodes(user: User): List<String> {
        val userId = requireNotNull(user.id) { "저장되지 않은 사용자입니다." }
        // 재발급 시 기존 코드 전량 무효화(확정 요구사항) — 일부만 남기면 분실한 코드가 여전히
        // 유효한 채로 남는다.
        backupCodeRepository.deleteByUserId(userId)
        val rawCodes = backupCodeGenerator.generate(8)
        rawCodes.forEach { code ->
            backupCodeRepository.save(BackupCode(user = user, codeHash = backupCodeGenerator.hash(code)))
        }
        return rawCodes
    }

    @Transactional
    override fun consumeBackupCode(user: User, code: String): Boolean {
        val userId = user.id ?: return false
        val unused = backupCodeRepository.findByUserIdAndUsedAtIsNull(userId)
        val match = unused.firstOrNull { backupCodeGenerator.matches(code, it.codeHash) } ?: return false
        match.usedAt = Instant.now()
        backupCodeRepository.save(match)
        return true
    }

    @Transactional
    override fun disableAll(user: User) {
        val userId = user.id ?: return
        totpCredentialRepository.findByUserId(userId).forEach { totpCredentialRepository.delete(it) }
        webauthnCredentialRepository.findByUserId(userId).forEach { webauthnCredentialRepository.delete(it) }
        backupCodeRepository.deleteByUserId(userId)
        user.isTwoFactorEnabled = false
        userRepository.save(user)
    }

    @Transactional
    override fun refreshSummaryFlag(user: User) {
        user.isTwoFactorEnabled = isTwoFactorEnabled(user)
        userRepository.save(user)
    }

    // 계정이 2FA를 처음으로 하나라도 활성화하는 순간 백업 코드 8개를 함께 발급한다(GitHub 관례).
    // 이미 미사용 코드가 있으면(예: 두 번째 방식을 추가 등록하는 경우) 건드리지 않는다 — 재발급은
    // 사용자가 명시적으로 요청할 때만(regenerateBackupCodes) 기존 코드를 무효화한다.
    private fun ensureBackupCodesInitialized(user: User): List<String>? {
        val userId = user.id ?: return null
        if (backupCodeRepository.findByUserId(userId).isNotEmpty()) return null
        return regenerateBackupCodes(user)
    }
}
