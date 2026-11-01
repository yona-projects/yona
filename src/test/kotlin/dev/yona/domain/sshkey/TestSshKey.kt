package dev.yona.domain.sshkey

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.util.Base64

/**
 * 시험용 SSH 키이다. 호출마다 새 Ed25519 키 쌍을 만들어 OpenSSH 공개키 한 줄과 지문을 함께 돌려준다.
 *
 * @property keyPair 개인키를 쓰는 시험(실제 SSH 접속)에서 쓴다
 * @property publicLine `ssh-ed25519 본문 주석` 한 줄
 * @property fingerprint `SHA256:...`
 */
class TestSshKey private constructor(val keyPair: KeyPair, val publicLine: String, val fingerprint: String) {

    companion object {
        fun generate(comment: String = "test@example.com"): TestSshKey {
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val raw = pair.public.encoded.takeLast(32).toByteArray()
            val blob = ByteArrayOutputStream().also { out ->
                DataOutputStream(out).apply {
                    val type = "ssh-ed25519".toByteArray()
                    writeInt(type.size); write(type); writeInt(raw.size); write(raw)
                }
            }.toByteArray()
            val fingerprint = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
            return TestSshKey(pair, "ssh-ed25519 ${Base64.getEncoder().encodeToString(blob)} $comment".trim(), fingerprint)
        }
    }
}
