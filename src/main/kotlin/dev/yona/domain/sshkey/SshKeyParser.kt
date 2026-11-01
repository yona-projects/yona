package dev.yona.domain.sshkey

import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.common.config.keys.PublicKeyEntry
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver
import java.nio.ByteBuffer
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * 등록할 수 없는 SSH 공개키를 거부할 때 던진다(추가 개발 항목 X-3, `decisions.md` D-66).
 *
 * @property code 거부 이유 코드. 화면과 REST 오류 메시지의 열쇠이다: `empty`, `malformed`, `private_key`, `multiple_keys`, `unsupported`, `weak`
 */
class SshKeyException(val code: String) : IllegalArgumentException("SSH 키를 받을 수 없습니다: $code")

/**
 * 읽은 SSH 공개키이다.
 *
 * @property type 키 종류(`ssh-ed25519`, `ssh-rsa`, `ecdsa-sha2-nistp256` 등)
 * @property publicKey 저장하는 공개키. `종류 본문` 한 줄이며 주석은 넣지 않는다
 * @property fingerprint OpenSSH가 보이는 지문(`SHA256:...`). 사이트 전체에서 유일하다
 * @property comment 공개키 줄 끝의 주석. 없으면 빈 문자열이다. 이름을 비웠을 때 이름으로 쓴다
 */
data class ParsedSshKey(val type: String, val publicKey: String, val fingerprint: String, val comment: String)

/**
 * OpenSSH 형식의 공개키(`~/.ssh/id_*.pub`의 한 줄)를 읽는다(추가 개발 항목 X-3, `decisions.md` D-66). 읽는 일과 지문 계산은 Apache MINA SSHD의
 * [PublicKeyEntry]와 [KeyUtils]에 맡기고, 여기서는 받을 수 있는 키의 정책만 정한다.
 *
 * 정책: 한 줄에 키 하나만 받는다. 비밀키, `authorized_keys`의 옵션 접두(`command=...` 등), DSA는 받지 않는다. 종류 표시와 본문 안의 종류가 달라도,
 * 본문이 깨져도 거부한다. RSA는 2048비트 이상만 받는다(GitHub와 같다).
 */
object SshKeyParser {

    /** RSA 키의 최소 길이(비트)이다. */
    const val MIN_RSA_BITS = 2048

    private val SUPPORTED = Regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp(256|384|521)|sk-ssh-ed25519@openssh\\.com|sk-ecdsa-sha2-nistp256@openssh\\.com)$")

    /** 공개키 글을 읽는다. 받을 수 없으면 [SshKeyException]을 던진다. */
    fun parse(text: String): ParsedSshKey {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw SshKeyException("empty")
        if (lines.any { it.startsWith("-----BEGIN") } || lines.any { it.startsWith("PuTTY-User-Key-File") }) throw SshKeyException("private_key")
        if (lines.size > 1) throw SshKeyException("multiple_keys")
        val parts = lines.single().split(Regex("\\s+"), limit = 3)
        val type = parts[0]
        if (type == "ssh-dss") throw SshKeyException("unsupported")
        if (!SUPPORTED.matches(type) || parts.size < 2) throw SshKeyException("malformed")
        val blob = try {
            Base64.getDecoder().decode(parts[1])
        } catch (e: IllegalArgumentException) {
            throw SshKeyException("malformed")
        }
        if (blobType(blob) != type) throw SshKeyException("malformed")
        val key = resolve(type, parts[1])
        if (key is RSAPublicKey && key.modulus.bitLength() < MIN_RSA_BITS) throw SshKeyException("weak")
        return ParsedSshKey(type, "$type ${parts[1]}", KeyUtils.getFingerPrint(key) ?: throw SshKeyException("malformed"), parts.getOrNull(2).orEmpty().trim())
    }

    /** 본문(와이어 형식)의 맨 앞 문자열, 곧 키 종류이다. 읽을 수 없으면 `null`이다. */
    private fun blobType(blob: ByteArray): String? = try {
        val buffer = ByteBuffer.wrap(blob)
        val length = buffer.int
        if (length !in 1..64 || length > buffer.remaining()) null else ByteArray(length).also { buffer.get(it) }.toString(Charsets.US_ASCII)
    } catch (e: RuntimeException) {
        null
    }

    private fun resolve(type: String, base64: String): PublicKey = try {
        PublicKeyEntry.parsePublicKeyEntry("$type $base64").resolvePublicKey(null, null, PublicKeyEntryResolver.FAILING)
    } catch (e: Exception) {
        throw SshKeyException("malformed")
    }
}
