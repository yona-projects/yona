package dev.yona.domain.twofactor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TOTP(RFC 6238) 핵심을 검증한다(X-2 1단계, `decisions.md` D-69). 시험 벡터는 RFC 6238 부록 B(SHA-1, 비밀 값 `12345678901234567890`)이다. RFC는 8자리 값이므로 6자리는 끝 여섯 자리이다.
 */
class TotpTest {

    private val rfcSecret = "12345678901234567890".toByteArray()

    @Test
    fun `RFC 6238 시험 벡터와 같은 코드를 만든다`() {
        mapOf(59L to "287082", 1111111109L to "081804", 1111111111L to "050471", 1234567890L to "005924", 2000000000L to "279037", 20000000000L to "353130").forEach { (time, expected) ->
            assertEquals(expected, Totp.code(rfcSecret, time / 30), "시각 $time")
        }
    }

    @Test
    fun `코드는 여섯 자리 숫자이고 앞의 0을 지키며 30초마다 바뀐다`() {
        val a = Totp.code(rfcSecret, 1); val b = Totp.code(rfcSecret, 2)
        assertTrue(a.matches(Regex("\\d{6}")) && b.matches(Regex("\\d{6}")))
        assertEquals("005924", Totp.code(rfcSecret, 1234567890L / 30))
        assertNotEquals(a, b)
    }

    @Test
    fun `현재 구간과 앞뒤 한 구간의 코드를 받고 그 밖은 받지 않는다`() {
        val now = 1_700_000_000L; val step = now / 30
        assertEquals(step, Totp.verify(rfcSecret, Totp.code(rfcSecret, step), now, lastUsedStep = null))
        assertEquals(step - 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 1), now, lastUsedStep = null), "시계가 조금 느린 기기")
        assertEquals(step + 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 1), now, lastUsedStep = null), "시계가 조금 빠른 기기")
        assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 2), now, lastUsedStep = null))
        assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 2), now, lastUsedStep = null))
    }

    @Test
    fun `이미 쓴 구간과 그 이전 구간의 코드는 다시 받지 않는다 (재사용 방지)`() {
        val now = 1_700_000_000L; val step = now / 30
        assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step), now, lastUsedStep = step), "같은 코드를 두 번 쓰지 못한다")
        assertNull(Totp.verify(rfcSecret, Totp.code(rfcSecret, step - 1), now, lastUsedStep = step))
        assertEquals(step + 1, Totp.verify(rfcSecret, Totp.code(rfcSecret, step + 1), now, lastUsedStep = step))
    }

    @Test
    fun `형식이 틀린 코드는 받지 않고 공백과 하이픈은 무시한다`() {
        val now = 1_700_000_000L; val code = Totp.code(rfcSecret, now / 30)
        listOf("", "12345", "1234567", "abcdef", "12 34", "１２３４５６").forEach { assertNull(Totp.verify(rfcSecret, it, now, null), it) }
        assertEquals(now / 30, Totp.verify(rfcSecret, code.substring(0, 3) + " " + code.substring(3), now, null))
        assertEquals(now / 30, Totp.verify(rfcSecret, " $code ", now, null))
    }

    @Test
    fun `새 비밀 값은 20바이트이고 매번 다르며 base32로 오간다`() {
        val a = Totp.newSecret(); val b = Totp.newSecret()
        assertEquals(20, a.size); assertNotEquals(a.toList(), b.toList())
        assertEquals(a.toList(), Base32.decode(Base32.encode(a)).toList())
    }

    @Test
    fun `otpauth 주소는 발급자와 계정과 비밀 값을 담는다`() {
        val uri = Totp.otpauthUri(rfcSecret, issuer = "Yona 사이트", account = "alice@example.com")
        assertTrue(uri.startsWith("otpauth://totp/Yona%20%EC%82%AC%EC%9D%B4%ED%8A%B8:alice%40example.com?"), uri)
        assertTrue(uri.contains("secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"), uri)
        assertTrue(uri.contains("issuer=Yona%20%EC%82%AC%EC%9D%B4%ED%8A%B8") && uri.contains("algorithm=SHA1") && uri.contains("digits=6") && uri.contains("period=30"), uri)
    }

    @Test
    fun `base32는 RFC 4648 시험 벡터를 따르고 소문자와 공백과 패딩을 허용하며 틀린 글자는 거부한다`() {
        mapOf("" to "", "f" to "MY======", "fo" to "MZXQ====", "foo" to "MZXW6===", "foob" to "MZXW6YQ=", "fooba" to "MZXW6YTB", "foobar" to "MZXW6YTBOI======").forEach { (plain, encoded) ->
            assertEquals(encoded.trimEnd('='), Base32.encode(plain.toByteArray()), plain)
            assertEquals(plain, String(Base32.decode(encoded)), plain)
        }
        assertEquals("foobar", String(Base32.decode("mzxw 6ytb oi")))
        assertFailsWith<IllegalArgumentException> { Base32.decode("MZXW1") }
    }
}
