package dev.yona.domain.sshkey

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * SSH 공개키 읽기(추가 개발 항목 X-3, `decisions.md` D-66)를 검증한다. 시험 키는 실제 `ssh-keygen`이 만든 것이다
 * (`src/test/resources/ssh/`). 지문은 `ssh-keygen -lf`의 `SHA256:...`와 같아야 한다.
 */
class SshKeyParserTest {

    private fun pub(name: String) = File("src/test/resources/ssh/$name.pub").readText()

    private fun code(text: String) = assertFailsWith<SshKeyException> { SshKeyParser.parse(text) }.code

    @Test
    fun `ed25519 키의 종류와 지문과 주석을 읽는다`() {
        val key = SshKeyParser.parse(pub("k_ed25519"))
        assertEquals("ssh-ed25519", key.type)
        assertEquals("ed@test", key.comment)
        assertTrue(key.fingerprint.startsWith("SHA256:"))
        // 실제 ssh-keygen이 출력하는 지문과 같아야 한다
        val expected = ProcessBuilder("ssh-keygen", "-lf", "src/test/resources/ssh/k_ed25519.pub").start().inputStream.bufferedReader().readText().split(" ")[1]
        assertEquals(expected, key.fingerprint)
    }

    @Test
    fun `RSA 3072와 ECDSA 키를 받는다`() {
        assertEquals("ssh-rsa", SshKeyParser.parse(pub("k_rsa3072")).type)
        assertEquals("ecdsa-sha2-nistp256", SshKeyParser.parse(pub("k_ecdsa")).type)
    }

    @Test
    fun `저장하는 공개키는 종류와 본문만 남기고 줄바꿈과 앞뒤 공백을 없앤다`() {
        val key = SshKeyParser.parse("  \n" + pub("k_ed25519").trim() + "  \n")
        assertEquals(pub("k_ed25519").trim().split(" ").take(2).joinToString(" "), key.publicKey)
    }

    @Test
    fun `주석이 없으면 주석은 비어 있다`() {
        val line = pub("k_ed25519").trim().split(" ").take(2).joinToString(" ")
        assertEquals("", SshKeyParser.parse(line).comment)
    }

    @Test
    fun `2048비트 미만 RSA 키는 약한 키로 거부한다`() = assertEquals("weak", code(pub("k_rsa1024")))

    @Test
    fun `비어 있으면 empty이다`() { assertEquals("empty", code("")); assertEquals("empty", code("  \n ")) }

    @Test
    fun `비밀키는 거부한다`() = assertEquals("private_key", code(File("src/test/resources/ssh/k_ed25519").readText()))

    @Test
    fun `여러 키는 거부한다`() = assertEquals("multiple_keys", code(pub("k_ed25519") + pub("k_ecdsa")))

    @Test
    fun `형식이 아닌 글이나 깨진 본문은 malformed이다`() {
        assertEquals("malformed", code("hello world"))
        assertEquals("malformed", code("ssh-ed25519 AAAA!!!! x"))
        assertEquals("malformed", code("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA"))
    }

    @Test
    fun `종류와 본문이 맞지 않으면 malformed이다`() {
        val body = pub("k_ed25519").split(" ")[1]
        assertEquals("malformed", code("ssh-rsa $body"))
    }

    @Test
    fun `authorized_keys의 옵션 접두는 받지 않는다`() {
        assertEquals("malformed", code("command=\"/bin/true\" " + pub("k_ed25519")))
    }

    @Test
    fun `지원하지 않는 종류(DSA)는 unsupported이다`() = assertEquals("unsupported", code("ssh-dss AAAAB3NzaC1kc3MAAAAA x"))
}
