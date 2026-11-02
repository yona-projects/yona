package dev.yona.domain.sshkey

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSH 서명(`gpg.format=ssh`로 만든 서명, sshsig 형식) 검증을 확인한다(X-3 후속, `decisions.md` D-68). 서명은 실제 `ssh-keygen -Y sign`이 만든 것이다. 검증은 서명이 밝힌 공개키로
 * 서명 수학을 확인하고, 그 키의 지문(`SHA256:...`)을 돌려주어 등록된 사용자 키를 찾을 수 있게 한다.
 */
class SshSignatureVerifierTest {

    @TempDir lateinit var dir: File

    private fun key(name: String) = File("src/test/resources/ssh/$name")

    /** 실제 ssh-keygen으로 [data]에 서명한다. 서명(armor)을 돌려준다. */
    private fun sign(keyName: String, data: ByteArray, namespace: String = "git"): ByteArray {
        val input = File(dir, "msg-${System.nanoTime()}").also { it.writeBytes(data) }
        val private = File(dir, "k-${System.nanoTime()}").also { key(keyName).copyTo(it) }
        java.nio.file.Files.setPosixFilePermissions(private.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))
        val process = ProcessBuilder("ssh-keygen", "-Y", "sign", "-n", namespace, "-f", private.path, input.path).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor() == 0) { "ssh-keygen -Y sign 실패: $output" }
        return File(input.path + ".sig").readBytes()
    }

    private val message = "tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\nauthor A <a@example.com> 1 +0000\ncommitter A <a@example.com> 1 +0000\n\nsigned\n".toByteArray()

    private fun fingerprintOf(keyName: String) = SshKeyParser.parse(File("src/test/resources/ssh/$keyName.pub").readText()).fingerprint

    @Test
    fun `ed25519 RSA ECDSA 서명을 검증하고 서명한 키의 지문을 돌려준다`() {
        for (name in listOf("k_ed25519", "k_rsa3072", "k_ecdsa")) {
            val parsed = assertNotNull(SshSignatureVerifier.parse(sign(name, message)), name)
            assertEquals(fingerprintOf(name), parsed.fingerprint, name)
            assertEquals("git", parsed.namespace)
            assertTrue(SshSignatureVerifier.verify(parsed, message), "$name 서명이 맞아야 한다")
        }
    }

    @Test
    fun `내용을 바꾸면 서명이 맞지 않는다`() {
        val parsed = assertNotNull(SshSignatureVerifier.parse(sign("k_ed25519", message)))
        assertFalse(SshSignatureVerifier.verify(parsed, message + "x".toByteArray()))
        assertFalse(SshSignatureVerifier.verify(parsed, "다른 내용".toByteArray()))
    }

    @Test
    fun `서명을 읽을 수 없으면 null이다`() {
        val good = String(sign("k_ed25519", message))
        listOf("", "garbage", good.replace("BEGIN SSH SIGNATURE", "BEGIN PGP SIGNATURE"), good.lines().take(3).joinToString("\n"), good.replace("\n", "").replace("A", "!")).forEach {
            assertNull(SshSignatureVerifier.parse(it.toByteArray()), it.take(40))
        }
    }

    @Test
    fun `네임스페이스가 git이 아닌 서명은 git 서명으로 받지 않는다`() {
        val parsed = assertNotNull(SshSignatureVerifier.parse(sign("k_ed25519", message, namespace = "file")))
        assertEquals("file", parsed.namespace)
        assertFalse(SshSignatureVerifier.verify(parsed, message), "네임스페이스가 다르면 git의 서명으로 인정하지 않는다")
    }

    @Test
    fun `SSH 서명인지 알아본다`() {
        assertTrue(SshSignatureVerifier.isSshSignature(sign("k_ed25519", message)))
        assertFalse(SshSignatureVerifier.isSshSignature("-----BEGIN PGP SIGNATURE-----\nabc\n-----END PGP SIGNATURE-----\n".toByteArray()))
    }
}
