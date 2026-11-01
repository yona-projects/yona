package dev.yona.domain.gpgkey

import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream
import java.time.Instant

/**
 * 서명 판정 이유이다. 코드 문자열은 GitHub의 `verification.reason`과 같다(`decisions.md` D-65).
 * 사용자와 이메일을 확인하는 이유(`unverified_email`, `bad_email`, `no_user`)는 서명이 맞은 뒤에 서비스 계층이 더한다.
 *
 * @property code REST와 화면에 쓰는 코드
 */
enum class GpgReason(val code: String) {
    /** 서명이 맞고 키에 문제가 없다. */
    VALID("valid"),

    /** 서명이 없다. */
    UNSIGNED("unsigned"),

    /** 서명한 키가 등록된 키 가운데 없다. */
    UNKNOWN_KEY("unknown_key"),

    /** 서명한 키가 지금 만료되었거나 폐기되었다. */
    EXPIRED_KEY("expired_key"),

    /** 서명한 키에 서명 용도가 없다. */
    NOT_SIGNING_KEY("not_signing_key"),

    /** 서명 글을 읽을 수 없다. */
    MALFORMED_SIGNATURE("malformed_signature"),

    /** 서명이 내용과 맞지 않는다(내용이 바뀌었거나 다른 키의 서명이다). */
    INVALID("invalid")
}

/**
 * 서명 판정 결과이다.
 *
 * @property reason 판정 이유
 * @property signingKeyId 서명이 밝힌 발행 키 ID(16자리 대문자 16진수). 서명을 읽지 못했거나 서명이 없으면 `null`이다
 * @property key 서명한 키가 든 등록 키 묶음. 찾지 못했으면 `null`이다
 */
data class GpgVerification(val reason: GpgReason, val signingKeyId: String? = null, val key: ParsedGpgKey? = null) {
    val verified: Boolean get() = reason == GpgReason.VALID
}

/**
 * 등록된 공개키로 GPG 분리 서명을 검증한다(X-4, `decisions.md` D-65). 서버 로컬 키링을 읽지 않는다.
 * 만료와 폐기는 서명한 시점이 아니라 검증하는 시점([verify]의 `now`)으로 판정한다(D-65 5번).
 */
object GpgSignatureVerifier {

    /**
     * [payload]의 서명을 [keys] 가운데에서 서명한 키를 찾아 검증한다.
     * 순서: 서명 읽기, 키 찾기, 서명 수학 검증, 키 상태(만료, 폐기, 서명 용도) 확인.
     *
     * @param payload 서명과 서명 대상. 서명이 없으면 `null`
     * @param keys 후보 키 묶음. 주 키와 하위 키의 키 ID로 찾는다
     * @param now 만료를 판정할 시각
     */
    fun verify(payload: SignedPayload?, keys: List<ParsedGpgKey>, now: Instant = Instant.now()): GpgVerification {
        payload ?: return GpgVerification(GpgReason.UNSIGNED)
        val signature = readSignature(payload.signature) ?: return GpgVerification(GpgReason.MALFORMED_SIGNATURE)
        val keyId = "%016X".format(signature.keyID)
        val owner = keys.firstOrNull { it.ring.getPublicKey(signature.keyID) != null } ?: return GpgVerification(GpgReason.UNKNOWN_KEY, keyId)
        val signer = owner.ring.getPublicKey(signature.keyID)
        val valid = try {
            signature.init(BcPGPContentVerifierBuilderProvider(), signer)
            signature.update(payload.data)
            signature.verify()
        } catch (e: PGPException) {
            false
        } catch (e: RuntimeException) {
            false
        }
        if (!valid) return GpgVerification(GpgReason.INVALID, keyId, owner)
        val info = (listOf(owner.primary) + owner.subkeys).first { it.keyId == "%016X".format(signer.keyID) }
        if (owner.primary.revoked || info.revoked || owner.primary.isExpiredAt(now) || info.isExpiredAt(now)) return GpgVerification(GpgReason.EXPIRED_KEY, keyId, owner)
        if (!info.canSign) return GpgVerification(GpgReason.NOT_SIGNING_KEY, keyId, owner)
        return GpgVerification(GpgReason.VALID, keyId, owner)
    }

    private fun readSignature(bytes: ByteArray): PGPSignature? = try {
        val factory = PGPObjectFactory(PGPUtil.getDecoderStream(ByteArrayInputStream(bytes)), BcKeyFingerprintCalculator())
        (factory.nextObject() as? PGPSignatureList)?.takeIf { !it.isEmpty }?.get(0)
    } catch (e: Exception) {
        null
    }
}
