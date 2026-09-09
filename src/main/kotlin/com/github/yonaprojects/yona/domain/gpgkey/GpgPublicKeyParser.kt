package com.github.yonaprojects.yona.domain.gpgkey

import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import java.io.ByteArrayInputStream

// ASCII armor로 내보낸 GPG 공개키(`gpg --export --armor`)를 파싱해
// 등록/검증에 필요한 정보를 뽑는다. BouncyCastle(bcpg/bcprov)로 실제 파싱한다 — 문자열 정규식
// 흉내가 아니다.
object GpgPublicKeyParser {

    class InvalidGpgKeyException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

    data class ParsedGpgKey(
        // 마스터(인증) 키의 Key ID. GitHub "GPG keys" 화면의 "Key ID" 컬럼과 동일한 개념.
        val keyId: String,
        // 마스터 키의 지문(40자리 16진수, v4 키 기준).
        val fingerprint: String,
        // 마스터 키 + 모든 서브키(서명 전용 서브키 포함)의 Key ID 집합. 실제 커밋 서명은 흔히
        // 별도 서명 서브키로 이뤄지므로, 서명의 issuer key ID가 이 집합 어딘가에 속하는지로
        // "이 공개키 묶음이 이 서명을 만들었을 수 있는 후보"를 찾는다.
        val associatedKeyIds: Set<String>,
        // 마스터 키에 붙은 User ID들에서 뽑은 이메일 주소 전부(등록 시점엔 이 중 계정 소유
        // 인증 이메일과 교집합만 실제로 매칭에 쓴다 — GpgKeyServiceImpl 참고).
        val uidEmails: Set<String>,
        val publicKeyRing: PGPPublicKeyRing
    )

    fun parse(armoredKeyText: String): ParsedGpgKey {
        if (armoredKeyText.isBlank()) {
            throw InvalidGpgKeyException("GPG 공개키를 입력해주세요.")
        }
        try {
            val decoderStream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armoredKeyText.toByteArray(Charsets.UTF_8))
            )
            val collection = PGPPublicKeyRingCollection(decoderStream, JcaKeyFingerprintCalculator())
            val ring = collection.keyRings.asSequence().firstOrNull()
                ?: throw InvalidGpgKeyException("공개키를 찾을 수 없습니다.")
            val masterKey = ring.publicKey
                ?: throw InvalidGpgKeyException("마스터 키를 찾을 수 없습니다.")

            val associatedKeyIds = ring.publicKeys.asSequence().map { formatKeyId(it.keyID) }.toSet()
            val uidEmails = masterKey.userIDs.asSequence().mapNotNull { extractEmail(it) }.toSet()

            return ParsedGpgKey(
                keyId = formatKeyId(masterKey.keyID),
                fingerprint = formatFingerprint(masterKey.fingerprint),
                associatedKeyIds = associatedKeyIds,
                uidEmails = uidEmails,
                publicKeyRing = ring
            )
        } catch (e: InvalidGpgKeyException) {
            throw e
        } catch (e: Exception) {
            throw InvalidGpgKeyException("올바른 GPG 공개키 형식이 아닙니다: ${e.message}", e)
        }
    }

    fun formatKeyId(keyId: Long): String = "%016X".format(keyId)

    private fun formatFingerprint(fingerprint: ByteArray): String =
        fingerprint.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    private val EMAIL_IN_ANGLE_BRACKETS = Regex("<([^>]+)>")

    private fun extractEmail(uid: String): String? {
        val match = EMAIL_IN_ANGLE_BRACKETS.find(uid)
        val email = if (match != null) match.groupValues[1] else uid.takeIf { it.contains("@") }
        return email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
    }
}
