package dev.yona.domain.gpgkey

import com.github.benmanes.caffeine.cache.Caffeine
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sshkey.SshSignatureVerifier
import dev.yona.domain.user.EmailRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 서명 판정의 최종 결과이다(GitHub의 `verification`에 해당).
 *
 * @property reason 판정 이유. [GpgReason.VALID]일 때만 Verified이다
 * @property keyId 서명이 밝힌 발행 키 ID. 서명이 없거나 읽을 수 없으면 `null`이다
 * @property fingerprint 서명한 키 묶음의 주 키 지문. 등록된 키를 찾지 못했으면 `null`이다
 * @property signerId 서명한 키의 주인 번호. 주인을 확정하지 못했으면 `null`이다
 * @property signerLogin 서명한 키 주인의 로그인 ID. [signerId]와 함께 있다
 * @property format 서명 형식이다. `gpg`(OpenPGP) 또는 `ssh`(`gpg.format=ssh`, `decisions.md` D-68). SSH 서명은 [keyId]와 [fingerprint]가 모두 키 지문(`SHA256:...`)이다
 */
data class SignatureVerification(
    val reason: GpgReason, val keyId: String? = null, val fingerprint: String? = null, val signerId: Long? = null, val signerLogin: String? = null, val format: String = "gpg"
) {
    val verified: Boolean get() = reason == GpgReason.VALID
}

/**
 * 커밋과 주석 태그의 서명을 등록된 GPG 키로 검증한다(추가 개발 항목 X-4, `decisions.md` D-65). `gpg.format=ssh`로 한 SSH 서명은 등록된 사용자 SSH 키로 검증한다(D-68). Verified이려면 서명이 맞고([GpgSignatureVerifier]),
 * 서명한 키의 주인이 활성 사용자이며, 커밋터(태그는 태그를 단 사람) 이메일이 키의 사용자 ID에 있고, 그 이메일이 주인이 확인한 이메일(대표 이메일 또는 확인한 보조 이메일)이어야 한다.
 *
 * 판정마다 `yona.gpg.verifications` 지표를 센다([dev.yona.observability.GpgMetrics]).
 *
 * 결과는 키와 이메일 등록에 따라 바뀌므로 저장하지 않고 요청마다 계산한다(서명 한 건의 검증은 밀리초 미만이다). 읽은 공개키 묶음만 캐시한다.
 */
@Service
class GpgVerificationService(
    private val keys: GpgKeyService,
    private val emails: EmailRepository,
    private val metrics: dev.yona.observability.GpgMetrics,
    private val sshKeys: dev.yona.domain.sshkey.SshKeyService
) {

    /** 키 행 번호별로 읽은 공개키 묶음이다. 행은 등록 뒤 바뀌지 않으므로 번호가 곧 열쇠이고, 지운 행은 조회되지 않아 쓰이지 않는다. */
    private val parsed = Caffeine.newBuilder().maximumSize(PARSED_CACHE_SIZE).build<Long, ParsedGpgKey>()

    /** 커밋의 서명을 검증한다. [payload]가 `null`이면 서명이 없는 커밋이다. [committerEmail]은 커밋터 이메일이다. */
    @Transactional(readOnly = true)
    fun verifyCommit(payload: SignedPayload?, committerEmail: String?, now: Instant = Instant.now()): SignatureVerification = verify("commit", payload, committerEmail, now)

    /** 주석 태그의 서명을 검증한다. [taggerEmail]은 태그를 단 사람의 이메일이다. */
    @Transactional(readOnly = true)
    fun verifyTag(payload: SignedPayload?, taggerEmail: String?, now: Instant = Instant.now()): SignatureVerification = verify("tag", payload, taggerEmail, now)

    private fun verify(target: String, payload: SignedPayload?, email: String?, now: Instant): SignatureVerification =
        judge(payload, email, now).also { metrics.verification(target, it.reason) }

    private fun judge(payload: SignedPayload?, email: String?, now: Instant): SignatureVerification {
        payload ?: return SignatureVerification(GpgReason.UNSIGNED)
        if (SshSignatureVerifier.isSshSignature(payload.signature)) return judgeSsh(payload, email)
        val candidates = GpgSignatureVerifier.issuerKeyId(payload)?.let { keys.findByKeyId(it) }.orEmpty().map { it.parent ?: it }.distinctBy { it.id }
        val result = GpgSignatureVerifier.verify(payload, candidates.map { parsedOf(it) }, now)
        val key = result.key ?: return SignatureVerification(result.reason, result.signingKeyId)
        val fingerprint = key.primary.fingerprint
        if (!result.verified) return SignatureVerification(result.reason, result.signingKeyId, fingerprint)
        val owner = candidates.first { it.fingerprint == fingerprint }.user
        if (owner == null || owner.state == UserState.LOCKED || owner.state == UserState.DELETED) return SignatureVerification(GpgReason.NO_USER, result.signingKeyId, fingerprint)
        val reason = when {
            email.isNullOrBlank() || key.primary.emails.none { it.equals(email.trim(), ignoreCase = true) } -> GpgReason.BAD_EMAIL
            email.trim().lowercase() !in verifiedEmails(owner) -> GpgReason.UNVERIFIED_EMAIL
            else -> GpgReason.VALID
        }
        return SignatureVerification(reason, result.signingKeyId, fingerprint, owner.id, owner.loginId)
    }

    /**
     * SSH 서명을 판정한다(`decisions.md` D-68). 서명이 밝힌 공개키의 지문으로 등록된 사용자 키를 찾고(배포 키는 서명자가 될 수 없다), 서명 수학을 확인하고, 주인이 활성 사용자이며,
     * 커밋터 이메일이 주인이 확인한 이메일인지 본다. SSH 키에는 사용자 ID가 없어 키 쪽 이메일 확인(`bad_email`)은 없다.
     */
    private fun judgeSsh(payload: SignedPayload, email: String?): SignatureVerification {
        val signature = SshSignatureVerifier.parse(payload.signature) ?: return SignatureVerification(GpgReason.MALFORMED_SIGNATURE, format = SSH)
        val fingerprint = signature.fingerprint
        val key = sshKeys.findByFingerprint(fingerprint) ?: return SignatureVerification(GpgReason.UNKNOWN_KEY, fingerprint, fingerprint, format = SSH)
        if (!SshSignatureVerifier.verify(signature, payload.data)) return SignatureVerification(GpgReason.INVALID, fingerprint, fingerprint, format = SSH)
        val owner = key.user
        if (owner == null || owner.state == UserState.LOCKED || owner.state == UserState.DELETED) return SignatureVerification(GpgReason.NO_USER, fingerprint, fingerprint, format = SSH)
        val reason = if (email.isNullOrBlank() || email.trim().lowercase() !in verifiedEmails(owner)) GpgReason.UNVERIFIED_EMAIL else GpgReason.VALID
        return SignatureVerification(reason, fingerprint, fingerprint, owner.id, owner.loginId, SSH)
    }

    /** 사용자가 확인한 이메일(소문자)이다: 대표 이메일과 확인을 마친 보조 이메일. */
    private fun verifiedEmails(owner: dev.yona.domain.user.User): Set<String> =
        (listOfNotNull(owner.email) + emails.findByUserIdOrderById(requireNotNull(owner.id)).filter { it.valid == true }.mapNotNull { it.email }).map { it.lowercase() }.toSet()

    private fun parsedOf(row: GpgKey): ParsedGpgKey = parsed.get(requireNotNull(row.id)) { GpgKeyParser.parseUnchecked(row.publicKey.orEmpty()) }

    private companion object {
        const val PARSED_CACHE_SIZE = 1000L
        const val SSH = "ssh"
    }
}
