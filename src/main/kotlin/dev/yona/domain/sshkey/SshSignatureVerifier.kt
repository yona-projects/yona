package dev.yona.domain.sshkey

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import org.apache.sshd.common.signature.BuiltinSignatures
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * 읽은 SSH 서명이다(sshsig 형식, OpenSSH `PROTOCOL.sshsig`).
 *
 * @property key 서명이 밝힌 공개키
 * @property fingerprint 그 키의 지문(`SHA256:...`). 등록된 사용자 키를 찾는 열쇠이다
 * @property namespace 서명의 네임스페이스. git이 만든 서명은 `git`이다
 * @property hashAlgorithm 서명 대상을 해시한 방식(`sha256`, `sha512`)
 */
class ParsedSshSignature internal constructor(
    val key: PublicKey,
    val fingerprint: String,
    val namespace: String,
    val hashAlgorithm: String,
    internal val reserved: ByteArray,
    internal val signature: ByteArray,
    internal val signatureAlgorithm: String
)

/**
 * `gpg.format=ssh`로 서명한 커밋과 주석 태그의 SSH 서명을 검증한다(추가 개발 항목 X-3 후속, `decisions.md` D-68). 서명 글의 ASCII armor(`-----BEGIN SSH SIGNATURE-----`)를 읽어 서명이 밝힌
 * 공개키로 서명 수학을 확인한다. 그 키가 등록된 사용자 키인지, 주인이 누구인지는 호출하는 쪽([dev.yona.domain.gpgkey.GpgVerificationService])이 지문으로 찾는다.
 *
 * 받는 서명: 네임스페이스가 `git`이고 해시가 `sha256`이나 `sha512`이며 서명 방식이 `ssh-ed25519`, `rsa-sha2-256`, `rsa-sha2-512`, `ecdsa-sha2-nistp256/384/521`인 것.
 * `ssh-rsa`(SHA-1)와 보안키(`sk-*`)는 받지 않는다.
 */
object SshSignatureVerifier {

    private const val BEGIN = "-----BEGIN SSH SIGNATURE-----"
    private const val END = "-----END SSH SIGNATURE-----"
    private val MAGIC = "SSHSIG".toByteArray()
    private val ALLOWED_ALGORITHMS = setOf("ssh-ed25519", "rsa-sha2-256", "rsa-sha2-512", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521")

    /** 서명 글이 SSH 서명이면 `true`이다. */
    fun isSshSignature(armored: ByteArray): Boolean = String(armored, Charsets.UTF_8).trimStart().startsWith(BEGIN)

    /** 서명 글(ASCII armor)을 읽는다. 읽을 수 없으면 `null`이다. */
    fun parse(armored: ByteArray): ParsedSshSignature? = try {
        val text = String(armored, Charsets.UTF_8).trim()
        if (!text.startsWith(BEGIN) || !text.endsWith(END)) null else read(Base64.getMimeDecoder().decode(text.removePrefix(BEGIN).removeSuffix(END).trim()))
    } catch (e: Exception) {
        null
    }

    /** [data]가 서명한 내용과 같고 서명이 맞으면 `true`이다. 네임스페이스가 `git`이 아니거나 받지 않는 해시나 서명 방식이면 `false`이다. */
    fun verify(signature: ParsedSshSignature, data: ByteArray): Boolean = try {
        if (signature.namespace != "git" || signature.signatureAlgorithm !in ALLOWED_ALGORITHMS) {
            false
        } else {
            val digest = MessageDigest.getInstance(when (signature.hashAlgorithm) { "sha256" -> "SHA-256"; "sha512" -> "SHA-512"; else -> return false }).digest(data)
            val signed = ByteArrayOutputStream().also { out ->
                out.write(MAGIC); out.writeString(signature.namespace.toByteArray()); out.writeString(signature.reserved); out.writeString(signature.hashAlgorithm.toByteArray()); out.writeString(digest)
            }.toByteArray()
            val verifier = BuiltinSignatures.resolveFactory(signature.signatureAlgorithm)?.create() ?: return false
            verifier.initVerifier(null, signature.key)
            verifier.update(null, signed)
            verifier.verify(null, signature.signature)
        }
    } catch (e: Exception) {
        false
    }

    private fun read(bytes: ByteArray): ParsedSshSignature? {
        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(MAGIC.size).also { buffer.get(it) }
        if (!magic.contentEquals(MAGIC) || buffer.int != 1) return null
        val keyBlob = buffer.string()
        val namespace = String(buffer.string(), Charsets.UTF_8)
        val reserved = buffer.string()
        val hashAlgorithm = String(buffer.string(), Charsets.UTF_8)
        val signature = buffer.string()
        val keyType = String(ByteBuffer.wrap(keyBlob).string(), Charsets.US_ASCII)
        val key = PublicKeyEntry(keyType, *keyBlob).resolvePublicKey(null, null, PublicKeyEntryResolver.FAILING)
        val algorithm = String(ByteBuffer.wrap(signature).string(), Charsets.US_ASCII)
        return ParsedSshSignature(key, KeyUtils.getFingerPrint(key) ?: return null, namespace, hashAlgorithm, reserved, signature, algorithm)
    }

    private fun ByteBuffer.string(): ByteArray {
        val length = int
        require(length in 0..remaining()) { "잘못된 길이" }
        return ByteArray(length).also { get(it) }
    }

    private fun ByteArrayOutputStream.writeString(value: ByteArray) {
        write(ByteBuffer.allocate(4).putInt(value.size).array()); write(value)
    }
}
