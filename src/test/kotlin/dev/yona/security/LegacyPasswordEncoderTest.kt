package dev.yona.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 스프링 `PasswordEncoder` 모양으로 v1.6 해시를 다루는 [LegacyPasswordEncoder]를 검증한다(로드맵 X-43 1단계 작업 1-2).
 * 솔트가 별도 열에 있어 인코딩 문자열 하나로는 확인할 수 없으므로, 두 열을 합친 **메모리 안의 합성 표현**(`{yona-sha256}솔트$해시`)을 쓰며 저장하지 않는다.
 */
class LegacyPasswordEncoderTest {

    private val encoder = LegacyPasswordEncoder()

    @Test
    fun `인코딩한 표현은 접두사와 솔트와 해시로 이루어지고 같은 평문을 확인한다`() {
        val encoded = encoder.encode("pw")
        assertTrue(encoded.startsWith("{yona-sha256}"))
        assertTrue(encoder.matches("pw", encoded))
        assertFalse(encoder.matches("other", encoded))
    }

    @Test
    fun `인코딩마다 솔트가 달라 표현이 다르다`() {
        assertNotEquals(encoder.encode("pw"), encoder.encode("pw"))
    }

    @Test
    fun `두 열에서 합성한 표현은 v1_6 해시를 그대로 확인한다`() {
        val stored = LegacyPasswordHasher.hash("secret", "legacy-salt")
        val composed = LegacyPasswordEncoder.compose("legacy-salt", stored)
        assertEquals("{yona-sha256}legacy-salt\$$stored", composed)
        assertTrue(encoder.matches("secret", composed))
        assertFalse(encoder.matches("wrong", composed))
    }

    @Test
    fun `솔트에 달러 기호가 있어도 마지막 구분자로 나눈다`() {
        val stored = LegacyPasswordHasher.hash("secret", "sa\$lt")
        assertTrue(encoder.matches("secret", LegacyPasswordEncoder.compose("sa\$lt", stored)))
    }

    @Test
    fun `모양이 맞지 않는 표현은 거절한다`() {
        assertFalse(encoder.matches("pw", ""))
        assertFalse(encoder.matches("pw", "plain"))
        assertFalse(encoder.matches("pw", "{yona-sha256}nodollar"))
        assertFalse(encoder.matches("pw", "{other}salt\$hash"))
    }

    @Test
    fun `갱신이 필요한 인코딩이 아니다`() {
        assertFalse(encoder.upgradeEncoding(encoder.encode("pw")))
    }
}
