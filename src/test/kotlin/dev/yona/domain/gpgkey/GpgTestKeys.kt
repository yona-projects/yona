package dev.yona.domain.gpgkey

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date

/**
 * 시험에서 쓰는 GPG 키 생성과 서명 도우미이다. 실제 `gpg` 프로그램 없이 BouncyCastle로 키와 분리 서명을 만든다(X-4).
 * 비밀키는 암호를 걸지 않는다. RSA 2048이다.
 *
 * @property secretRing 비밀키 묶음(주 키와 하위 키)
 * @property publicRing 공개키 묶음. 폐기 인증이 있으면 포함한다
 */
class TestGpgKey(val secretRing: PGPSecretKeyRing, val publicRing: PGPPublicKeyRing) {

    /** 공개키를 ASCII armor 문자열로 낸다. 등록 화면에 붙여 넣는 값과 같다. */
    val publicArmored: String get() = armor { publicRing.encode(it) }

    /** 주 키의 키 ID이다(16자리 대문자 16진수). */
    val keyId: String get() = "%016X".format(publicRing.publicKey.keyID)

    /** 주 키의 지문이다(대문자 16진수). */
    val fingerprint: String get() = publicRing.publicKey.fingerprint.joinToString("") { "%02X".format(it) }

    /** 하위 키 중 [index]번째의 키 ID이다(0부터, 주 키 제외). */
    fun subkeyId(index: Int): Long = publicRing.publicKeys.asSequence().drop(1).toList()[index].keyID

    /**
     * [data]에 분리 서명을 만들어 armor 문자열로 낸다.
     *
     * @param signerKeyId 서명할 키 ID. 비우면 주 키이다
     */
    fun sign(data: ByteArray, signerKeyId: Long? = null, hash: Int = HashAlgorithmTags.SHA256): String {
        val secret = secretRing.getSecretKey(signerKeyId ?: secretRing.secretKey.keyID)
        val generator = org.bouncycastle.openpgp.PGPSignatureGenerator(BcPGPContentSignerBuilder(secret.publicKey.algorithm, hash))
        generator.init(PGPSignature.BINARY_DOCUMENT, secret.extractPrivateKey(null))
        generator.update(data)
        val signature = generator.generate()
        return armor { signature.encode(it) }
    }

    /** 주 키를 폐기한 공개키 묶음을 가진 키를 돌려준다. */
    fun revoked(): TestGpgKey {
        val master = secretRing.secretKey
        val generator = org.bouncycastle.openpgp.PGPSignatureGenerator(BcPGPContentSignerBuilder(master.publicKey.algorithm, HashAlgorithmTags.SHA256))
        generator.init(PGPSignature.KEY_REVOCATION, master.extractPrivateKey(null))
        val revocation = generator.generateCertification(master.publicKey)
        val withRevocation = PGPPublicKey.addCertification(publicRing.publicKey, revocation)
        return TestGpgKey(secretRing, PGPPublicKeyRing.insertPublicKey(publicRing, withRevocation))
    }

    private fun armor(write: (java.io.OutputStream) -> Unit): String {
        val bytes = ByteArrayOutputStream()
        ArmoredOutputStream(bytes).use { write(it) }
        return bytes.toString(Charsets.UTF_8)
    }

    companion object {

        /**
         * 새 키를 만든다.
         *
         * @param userId 사용자 ID(예: `Tester <tester@example.com>`)
         * @param created 키 만든 시각. 만료 시험은 과거로 둔다
         * @param validSeconds 주 키의 유효 기간(초). 0이면 만료 없음
         * @param signingSubkey 서명용 하위 키를 더할지
         * @param encryptionSubkey 암호화 전용 하위 키를 더할지
         * @param primaryCanSign 주 키에 서명 용도를 둘지. 끄면 인증 전용 주 키이다
         */
        fun generate(
            userId: String = "Tester <tester@example.com>",
            created: Date = Date(),
            validSeconds: Long = 0,
            signingSubkey: Boolean = false,
            encryptionSubkey: Boolean = false,
            primaryCanSign: Boolean = true
        ): TestGpgKey {
            val master = BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, rsa(), created)
            val sha1 = BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1)
            val hashed = PGPSignatureSubpacketGenerator().apply {
                setKeyFlags(false, if (primaryCanSign) KeyFlags.CERTIFY_OTHER or KeyFlags.SIGN_DATA else KeyFlags.CERTIFY_OTHER)
                if (validSeconds > 0) setKeyExpirationTime(false, validSeconds)
            }
            val generator = PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION, master, userId, sha1, hashed.generate(), null,
                BcPGPContentSignerBuilder(PublicKeyAlgorithmTags.RSA_GENERAL, HashAlgorithmTags.SHA256), null
            )
            if (signingSubkey) generator.addSubKey(BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, rsa(), created), subpackets(KeyFlags.SIGN_DATA), null)
            if (encryptionSubkey) generator.addSubKey(BcPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, rsa(), created), subpackets(KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE), null)
            return TestGpgKey(generator.generateSecretKeyRing(), generator.generatePublicKeyRing())
        }

        private fun subpackets(flags: Int) = PGPSignatureSubpacketGenerator().apply { setKeyFlags(false, flags) }.generate()

        private fun rsa() = RSAKeyPairGenerator().run {
            init(RSAKeyGenerationParameters(BigInteger.valueOf(0x10001), SecureRandom(), 2048, 12))
            generateKeyPair()
        }
    }
}
