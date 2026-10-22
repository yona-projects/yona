package dev.yona.security

import org.apache.shiro.crypto.hash.Sha256Hash
import org.apache.shiro.util.ByteSource
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * v1.6 `UserApp.hashedPassword`(Shiro `Sha256Hash`, 반복 1024회, Base64)와 같은 결과를 내는지 검증한다.
 */
class LegacyPasswordHasherTest {

    @Test
    fun `알려진 입력에 대해 v1_6과 같은 해시를 만든다`() {
        // 파이썬 hashlib으로 독립 계산한 값: SHA-256(솔트 + 평문)을 1024번 반복한 뒤 Base64
        assertEquals(
            "KuAEtT/Vmria/ruxPXxlnI4SVhEY+oImIruTqtonTsA=",
            LegacyPasswordHasher.hash("1234", "[B@1032a4")
        )
    }

    @Test
    fun `실제 Shiro 구현과 여러 입력에서 결과가 같다`() {
        val samples = listOf(
            "1234" to "salt", "" to "x", "비밀번호!" to "소금", "a".repeat(500) to "s".repeat(64),
            "p@ss w0rd" to "k4rBm5v9c2Q8sA==", "Yona" to "[B@1032a4"
        )
        samples.forEach { (plain, salt) ->
            val expected = Sha256Hash(plain, ByteSource.Util.bytes(salt), 1024).toBase64()
            assertEquals(expected, LegacyPasswordHasher.hash(plain, salt), "평문=$plain 솔트=$salt")
        }
    }

    @Test
    fun `솔트가 다르면 해시가 다르다`() {
        assertNotEquals(LegacyPasswordHasher.hash("1234", "a"), LegacyPasswordHasher.hash("1234", "b"))
    }

    @Test
    fun `새 솔트는 매번 다르고 Base64 문자열이다`() {
        val first = LegacyPasswordHasher.generateSalt()
        val second = LegacyPasswordHasher.generateSalt()
        assertNotEquals(first, second)
        assertTrue(first.matches(Regex("[A-Za-z0-9+/]+={0,2}")), first)
        assertEquals(24, first.length) // 16바이트 -> Base64 24자
    }

    @Test
    fun `해시가 일치하는지 검사한다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        val stored = LegacyPasswordHasher.hash("1234", salt)
        assertTrue(LegacyPasswordHasher.matches("1234", salt, stored))
        assertTrue(!LegacyPasswordHasher.matches("12345", salt, stored))
    }
}
