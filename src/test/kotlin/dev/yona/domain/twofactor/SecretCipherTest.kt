package dev.yona.domain.twofactor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 2단계 인증 비밀 값의 암호화 저장(X-2 1단계, `decisions.md` D-69)을 검증한다. TOTP 비밀 값은 되돌려 읽어야 하므로 해시가 아니라 사이트 비밀 키에서 만든 키로 AES-GCM 암호화한다.
 * 데이터베이스 백업이 새어도 사이트 비밀 키 없이는 읽을 수 없다.
 */
class SecretCipherTest {

    private val data = "12345678901234567890".toByteArray()

    @Test
    fun `암호화한 값을 같은 사이트 비밀 키로 되돌린다`() {
        val cipher = SecretCipher("site-secret")
        val sealed = cipher.encrypt(data)
        assertTrue(sealed.startsWith("v1."), sealed)
        assertEquals(data.toList(), cipher.decrypt(sealed).toList())
    }

    @Test
    fun `같은 값도 매번 다르게 암호화한다(임의 IV)`() {
        val cipher = SecretCipher("site-secret")
        assertNotEquals(cipher.encrypt(data), cipher.encrypt(data))
    }

    @Test
    fun `저장한 글에는 비밀 값이 그대로 들어 있지 않다`() {
        val sealed = SecretCipher("site-secret").encrypt(data)
        assertTrue(!sealed.contains(Base32.encode(data)) && !sealed.contains("12345678901234567890"))
    }

    @Test
    fun `다른 사이트 비밀 키나 바뀐 글은 읽지 못한다`() {
        val sealed = SecretCipher("site-secret").encrypt(data)
        assertFailsWith<IllegalArgumentException> { SecretCipher("other-secret").decrypt(sealed) }
        val tampered = sealed.dropLast(3) + (if (sealed.takeLast(3) == "AAA") "BBB" else "AAA")
        assertFailsWith<IllegalArgumentException> { SecretCipher("site-secret").decrypt(tampered) }
        listOf("", "garbage", "v2.a.b", "v1.only").forEach { assertFailsWith<IllegalArgumentException>(it) { SecretCipher("site-secret").decrypt(it) } }
    }
}
