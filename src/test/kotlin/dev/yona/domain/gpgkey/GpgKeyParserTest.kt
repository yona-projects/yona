package dev.yona.domain.gpgkey

import org.junit.jupiter.api.Test
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 공개키 읽기를 검증한다(X-4 1단계, `decisions.md` D-65). 실제 `gpg` 없이 BouncyCastle로 만든 키를 쓴다.
 */
class GpgKeyParserTest {

    private fun codeOf(block: () -> Unit) = assertFailsWith<GpgKeyException> { block() }.code

    @Test
    fun `공개키에서 지문과 키 ID와 이메일을 읽는다`() {
        val key = TestGpgKey.generate(userId = "Tester <tester@example.com>")
        val parsed = GpgKeyParser.parse(key.publicArmored)
        assertEquals(key.fingerprint, parsed.primary.fingerprint)
        assertEquals(key.keyId, parsed.primary.keyId)
        assertEquals(listOf("tester@example.com"), parsed.primary.emails)
        assertEquals(listOf("Tester <tester@example.com>"), parsed.primary.userIds)
        assertTrue(parsed.primary.canSign)
        assertFalse(parsed.primary.revoked)
        assertNull(parsed.primary.expiresAt)
        assertTrue(parsed.subkeys.isEmpty())
    }

    @Test
    fun `하위 키를 읽고 서명 가능 여부를 구분한다`() {
        val key = TestGpgKey.generate(signingSubkey = true, encryptionSubkey = true)
        val parsed = GpgKeyParser.parse(key.publicArmored)
        assertEquals(2, parsed.subkeys.size)
        assertEquals(listOf(true, false), parsed.subkeys.map { it.canSign })
        assertEquals(setOf(key.keyId) + parsed.subkeys.map { it.keyId }, parsed.allKeyIds)
    }

    @Test
    fun `만료 시각은 만든 시각에 유효 기간을 더한 값이다`() {
        val created = Date(System.currentTimeMillis() - 10 * 86_400_000L)
        val parsed = GpgKeyParser.parse(TestGpgKey.generate(created = created, validSeconds = 30 * 86_400L).publicArmored)
        val expires = assertNotNull(parsed.primary.expiresAt)
        assertEquals(created.toInstant().epochSecond + 30 * 86_400L, expires.epochSecond)
    }

    @Test
    fun `인증 전용 주 키와 서명 하위 키가 있으면 서명 가능한 키로 받는다`() {
        val parsed = GpgKeyParser.parse(TestGpgKey.generate(primaryCanSign = false, signingSubkey = true).publicArmored)
        assertFalse(parsed.primary.canSign)
        assertTrue(parsed.hasSigningKey)
    }

    @Test
    fun `서명할 수 있는 키가 하나도 없으면 거부한다`() {
        val key = TestGpgKey.generate(primaryCanSign = false, encryptionSubkey = true)
        assertEquals("no_signing_key", codeOf { GpgKeyParser.parse(key.publicArmored) })
    }

    @Test
    fun `비밀키는 거부한다`() {
        val key = TestGpgKey.generate()
        val armoredSecret = java.io.ByteArrayOutputStream().also { out -> org.bouncycastle.bcpg.ArmoredOutputStream(out).use { key.secretRing.encode(it) } }.toString(Charsets.UTF_8)
        assertEquals("private_key", codeOf { GpgKeyParser.parse(armoredSecret) })
    }

    @Test
    fun `비었거나 키가 아닌 글은 거부한다`() {
        assertEquals("empty", codeOf { GpgKeyParser.parse("  \n") })
        assertEquals("malformed", codeOf { GpgKeyParser.parse("ssh-ed25519 AAAAC3Nza... user@host") })
        assertEquals("malformed", codeOf { GpgKeyParser.parse("-----BEGIN PGP PUBLIC KEY BLOCK-----\n\nnot-base64!!\n-----END PGP PUBLIC KEY BLOCK-----") })
    }

    @Test
    fun `이미 만료되었거나 폐기된 키는 거부한다`() {
        val expired = TestGpgKey.generate(created = Date(System.currentTimeMillis() - 10 * 86_400_000L), validSeconds = 86_400L)
        assertEquals("expired", codeOf { GpgKeyParser.parse(expired.publicArmored) })
        assertEquals("revoked", codeOf { GpgKeyParser.parse(TestGpgKey.generate().revoked().publicArmored) })
    }

    @Test
    fun `키를 두 개 붙여 넣으면 거부한다`() {
        val both = TestGpgKey.generate().publicArmored + "\n" + TestGpgKey.generate().publicArmored
        assertEquals("multiple_keys", codeOf { GpgKeyParser.parse(both) })
    }

    @Test
    fun `사용자 ID가 없는 키는 거부한다`() {
        assertEquals("no_user_id", codeOf { GpgKeyParser.parse(TestGpgKey.generate(userId = "").publicArmored) })
    }

    @Test
    fun `이메일이 없는 사용자 ID는 이메일 목록에 넣지 않는다`() {
        val parsed = GpgKeyParser.parse(TestGpgKey.generate(userId = "Just A Name").publicArmored)
        assertEquals(emptyList(), parsed.primary.emails)
        assertEquals(listOf("Just A Name"), parsed.primary.userIds)
    }
}
