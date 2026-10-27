package dev.yona.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6 `java-semver` 0.7.2 `VersionParser`의 관대한 접두 파싱과 비교 규칙을 옮긴 [LenientVersion]을 검증한다.
 * 기대값은 옛 라이브러리를 직접 실행해 얻은 결과이다.
 */
class LenientVersionTest {

    private val minimum = LenientVersion.of(2, 1, 0)

    private fun allowed(text: String): Boolean = LenientVersion.parse(text)?.let { it >= minimum } ?: false

    @Test
    fun `세 자리 뒤의 나머지는 읽지 않고 버린다`() {
        assertTrue(allowed("2.20.1 (Apple Git-117)"))
        assertTrue(allowed("5.13.0.202109080827-r"))
        assertTrue(allowed("2.1.0.1"))
        assertTrue(allowed("2.1.0_1"))
    }

    @Test
    fun `사전 배포판은 정식 배포판보다 낮다`() {
        assertEquals(false, allowed("2.1.0-rc1"))
        assertEquals(false, allowed("2.1.0-rc1.1"))
        assertTrue(allowed("2.1.1-rc1"))
    }

    @Test
    fun `앞자리 0이나 음수나 정수 범위를 넘는 숫자는 읽지 못한다`() {
        assertNull(LenientVersion.parse("02.1.0"))
        assertNull(LenientVersion.parse("2.1.-1"))
        assertNull(LenientVersion.parse("2.1.0-01"))
        assertNull(LenientVersion.parse("2147483648.0.0"))
        assertNull(LenientVersion.parse("2.1"))
        assertNotNull(LenientVersion.parse("2.1.0"))
    }

    @Test
    fun `사전 배포판 식별자는 숫자끼리는 수로 아니면 문자열로 비교한다`() {
        val a = LenientVersion.parse("1.0.0-alpha.2")!!
        val b = LenientVersion.parse("1.0.0-alpha.10")!!
        val c = LenientVersion.parse("1.0.0-beta")!!
        assertTrue(a < b)
        assertTrue(b < c)
        assertTrue(LenientVersion.parse("1.0.0-alpha")!! < a)
    }
}
