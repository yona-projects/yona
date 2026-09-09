package com.github.yonaprojects.yona.domain.twofactor

import com.github.yonaprojects.yona.domain.user.User

sealed class TotpActivationResult {
    // freshBackupCodes는 이 TOTP 활성화가 계정의 "첫" 2FA 등록이라 백업 코드를 새로 발급한
    // 경우에만 채워진다(그 외엔 null) — 컨트롤러가 이 값이 있을 때만 1회성 코드 표시 화면으로
    // 보낸다.
    data class Success(val freshBackupCodes: List<String>?) : TotpActivationResult()
    object InvalidCode : TotpActivationResult()
    object NotFound : TotpActivationResult()
}

sealed class WebauthnRegistrationResult {
    data class Success(val credential: WebauthnCredential, val freshBackupCodes: List<String>?) : WebauthnRegistrationResult()
    data class Failure(val message: String) : WebauthnRegistrationResult()
}

interface TwoFactorService {
    // 계정 실제 2FA 등록 여부의 단일 진실 공급원(source of truth) — 로그인 게이트가 이걸 쓴다.
    // User.isTwoFactorEnabled는 이 값을 반영하는 캐시일 뿐이다.
    fun isTwoFactorEnabled(user: User): Boolean

    fun listWebauthnCredentials(user: User): List<WebauthnCredential>
    fun listEnabledTotpCredentials(user: User): List<TwoFactorTotpCredential>

    fun beginTotpEnrollment(user: User, accountLabel: String): Pair<TwoFactorTotpCredential, TotpEnrollment>
    fun verifyAndActivateTotp(user: User, credentialId: Long, code: String): TotpActivationResult
    // 코드 검증 실패 후 등록 화면을 다시 그릴 때, 새 secret을 발급하지 않고 기존 미확정 등록의
    // QR/수동입력 키를 재구성한다. 소유자가 아니거나 이미 확정된 등록이면 null.
    fun reviewPendingTotpEnrollment(user: User, credentialId: Long): TotpEnrollment?
    fun deleteTotpCredential(user: User, credentialId: Long)

    fun deleteWebauthnCredential(user: User, credentialId: Long)
    fun completeWebauthnRegistration(user: User, credential: WebauthnCredential): List<String>?

    fun verifyTotpForLogin(user: User, code: String): Boolean
    fun hasUnusedBackupCodes(user: User): Boolean
    fun regenerateBackupCodes(user: User): List<String>
    fun consumeBackupCode(user: User, code: String): Boolean

    fun disableAll(user: User)
    fun refreshSummaryFlag(user: User)
}
