package dev.yona.domain.gpgkey

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant

/**
 * 등록할 수 없는 공개키를 거부할 때 던진다(추가 개발 항목 X-4, `decisions.md` D-65).
 *
 * @property code 거부 이유 코드. 화면과 REST 오류 메시지의 열쇠이다: `empty`, `malformed`, `private_key`, `multiple_keys`,
 * `no_user_id`, `no_signing_key`, `expired`, `revoked`
 */
class GpgKeyException(val code: String) : IllegalArgumentException("GPG 키를 받을 수 없습니다: $code")

/**
 * 키 한 개(주 키 또는 하위 키)의 읽은 정보이다.
 *
 * @property fingerprint 지문(대문자 16진수)
 * @property keyId 키 ID(16자리 대문자 16진수). 서명의 발행자 표시와 맞춰 볼 때 쓴다
 * @property userIds 사용자 ID 문자열. 하위 키는 비어 있다
 * @property emails 사용자 ID에 든 이메일(소문자). 하위 키는 비어 있다
 * @property createdAt 키를 만든 시각
 * @property expiresAt 만료 시각. 만료가 없으면 `null`이다
 * @property revoked 폐기 인증이 있으면 `true`이다
 * @property canSign 서명 용도로 쓸 수 있으면 `true`이다
 */
data class GpgKeyInfo(
    val fingerprint: String,
    val keyId: String,
    val userIds: List<String>,
    val emails: List<String>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val revoked: Boolean,
    val canSign: Boolean
) {
    /** [now]에 만료된 키이면 `true`이다. */
    fun isExpiredAt(now: Instant) = expiresAt?.let { !it.isAfter(now) } == true
}

/**
 * 읽은 공개키 한 묶음(주 키와 하위 키)이다.
 *
 * @property primary 주 키
 * @property subkeys 하위 키. 묶음 안의 순서를 따른다
 */
class ParsedGpgKey internal constructor(internal val ring: PGPPublicKeyRing, val primary: GpgKeyInfo, val subkeys: List<GpgKeyInfo>) {

    /** 주 키와 하위 키의 키 ID 전체이다. */
    val allKeyIds: Set<String> get() = (listOf(primary) + subkeys).map { it.keyId }.toSet()

    /** 주 키나 하위 키 가운데 서명할 수 있는 키가 있으면 `true`이다. */
    val hasSigningKey: Boolean get() = primary.canSign || subkeys.any { it.canSign }

    /** 저장과 표시에 쓰는 ASCII armor 문자열이다. 붙여 넣은 글의 앞뒤 잡글을 뺀 공개키 패킷만 담는다. */
    val armored: String
        get() {
            val bytes = ByteArrayOutputStream()
            ArmoredOutputStream(bytes).use { ring.encode(it) }
            return bytes.toString(Charsets.UTF_8)
        }
}

/**
 * 사용자가 붙여 넣은 GPG 공개키를 읽는다(X-4). 서버 로컬 키링이 아니라 사용자가 등록한 키만 다룬다.
 *
 * - [parse]: 등록 때 쓰며 비밀키, 키 여러 개, 사용자 ID 없음, 서명 키 없음, 만료와 폐기를 거부한다.
 * - [parseUnchecked]: 이미 저장된 키를 서명 검증 때 다시 읽을 때 쓴다. 만료나 폐기여도 읽으며, 그 판정은 검증기가 한다.
 */
object GpgKeyParser {

    private val EMAIL = Regex("<([^<>\\s@]+@[^<>\\s]+)>")
    private const val PRIVATE_BLOCK = "-----BEGIN PGP PRIVATE KEY BLOCK-----"
    private val BLOCK_BEGIN = Regex("-----BEGIN PGP [A-Z ]+-----")
    private val BARE_EMAIL = Regex("^[^<>\\s@]+@[^<>\\s]+$")

    /** 등록할 공개키를 읽고 정책을 확인한다. 받을 수 없으면 [GpgKeyException]을 던진다. */
    fun parse(armored: String, now: Instant = Instant.now()): ParsedGpgKey {
        val parsed = parseUnchecked(armored)
        if (parsed.primary.userIds.isEmpty()) throw GpgKeyException("no_user_id")
        if (!parsed.hasSigningKey) throw GpgKeyException("no_signing_key")
        if (parsed.primary.revoked) throw GpgKeyException("revoked")
        if (parsed.primary.isExpiredAt(now)) throw GpgKeyException("expired")
        return parsed
    }

    /** 공개키를 읽기만 한다. 비밀키이거나 키 한 묶음이 아니면 [GpgKeyException]을 던진다. */
    fun parseUnchecked(armored: String): ParsedGpgKey {
        if (armored.isBlank()) throw GpgKeyException("empty")
        // 붙여 넣은 글에 armor 블록이 여럿이면 디코더는 첫 블록만 읽으므로 글자로 먼저 센다
        if (armored.contains(PRIVATE_BLOCK)) throw GpgKeyException("private_key")
        if (BLOCK_BEGIN.findAll(armored).count() > 1) throw GpgKeyException("multiple_keys")
        val rings = mutableListOf<PGPPublicKeyRing>()
        try {
            val factory = PGPObjectFactory(PGPUtil.getDecoderStream(ByteArrayInputStream(armored.toByteArray(Charsets.UTF_8))), BcKeyFingerprintCalculator())
            while (true) {
                when (val item = factory.nextObject() ?: break) {
                    is PGPSecretKeyRing -> throw GpgKeyException("private_key")
                    is PGPPublicKeyRing -> rings += item
                    else -> throw GpgKeyException("malformed")
                }
            }
        } catch (e: GpgKeyException) {
            throw e
        } catch (e: Exception) {
            throw GpgKeyException("malformed")
        }
        if (rings.isEmpty()) throw GpgKeyException("malformed")
        if (rings.size > 1) throw GpgKeyException("multiple_keys")
        return describe(rings.single())
    }

    private fun describe(ring: PGPPublicKeyRing): ParsedGpgKey {
        val keys = ring.publicKeys.asSequence().toList()
        val master = keys.first()
        val userIds = master.userIDs.asSequence().toList().filter { it.isNotBlank() }
        val primary = info(master, userIds)
        return ParsedGpgKey(ring, primary, keys.drop(1).map { info(it, emptyList()) })
    }

    private fun info(key: PGPPublicKey, userIds: List<String>): GpgKeyInfo {
        val created = key.creationTime.toInstant()
        return GpgKeyInfo(
            fingerprint = key.fingerprint.joinToString("") { "%02X".format(it) },
            keyId = "%016X".format(key.keyID),
            userIds = userIds,
            emails = userIds.mapNotNull(::emailOf).distinct(),
            createdAt = created,
            expiresAt = key.validSeconds.takeIf { it > 0 }?.let { created.plusSeconds(it) },
            revoked = key.hasRevocation(),
            canSign = canSign(key)
        )
    }

    private fun emailOf(userId: String): String? =
        EMAIL.find(userId)?.groupValues?.get(1)?.lowercase() ?: userId.trim().takeIf { BARE_EMAIL.matches(it) }?.lowercase()

    /**
     * 키 용도 표시가 있으면 그 값으로, 없으면 알고리즘으로 서명 가능 여부를 정한다. 키 용도는 주 키의 자기 인증이나 하위 키의 결합 서명에 실려 있고, 가장 늦게 만든 것이 유효하다.
     */
    private fun canSign(key: PGPPublicKey): Boolean {
        val latest = key.signatures.asSequence()
            .filter { it.signatureType in FLAG_CARRYING && it.hashedSubPackets?.hasSubpacket(org.bouncycastle.bcpg.SignatureSubpacketTags.KEY_FLAGS) == true }
            .maxByOrNull { it.creationTime.time }
        if (latest != null) return latest.hashedSubPackets.keyFlags and KeyFlags.SIGN_DATA != 0
        return key.algorithm in SIGNING_ALGORITHMS
    }

    private val FLAG_CARRYING = setOf(PGPSignature.POSITIVE_CERTIFICATION, PGPSignature.CASUAL_CERTIFICATION, PGPSignature.DEFAULT_CERTIFICATION, PGPSignature.DIRECT_KEY, PGPSignature.SUBKEY_BINDING)

    private val SIGNING_ALGORITHMS = setOf(
        PublicKeyAlgorithmTags.RSA_GENERAL, PublicKeyAlgorithmTags.DSA, PublicKeyAlgorithmTags.ECDSA,
        PublicKeyAlgorithmTags.EDDSA_LEGACY, PublicKeyAlgorithmTags.Ed25519, PublicKeyAlgorithmTags.Ed448
    )
}
