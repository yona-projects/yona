package dev.yona.domain.twofactor

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 백업 코드(X-2 1단계, `decisions.md` D-69)를 검증한다. 10개를 한 번 보여 주고 서버에는 해시만 둔다. 코드는 열 글자(다섯 글자씩 하이픈으로 나눈다)이고 헷갈리는 글자는 쓰지 않으며 입력은 대소문자와
 * 하이픈과 공백을 가리지 않는다.
 */
class BackupCodesTest {

    private val codes = BackupCodes("site-secret")

    @Test
    fun `열 개를 만들고 모두 다르며 xxxxx-xxxxx 모양이다`() {
        val generated = codes.generate()
        assertEquals(10, generated.size); assertEquals(10, generated.toSet().size)
        assertTrue(generated.all { it.matches(Regex("[a-z2-9]{5}-[a-z2-9]{5}")) }, generated.toString())
        assertTrue(generated.none { code -> code.any { it in "01ilo" } }, "헷갈리는 글자(0 1 i l o)가 없다")
    }

    @Test
    fun `해시는 같은 코드에 같고 다른 코드에 다르며 코드가 그대로 들어 있지 않다`() {
        val a = codes.hash("abcde-fghjk"); val b = codes.hash("abcde-fghjm")
        assertEquals(a, codes.hash("abcde-fghjk")); assertNotEquals(a, b)
        assertTrue(a.matches(Regex("[0-9a-f]{64}")) && !a.contains("abcde"))
        assertNotEquals(a, BackupCodes("other-secret").hash("abcde-fghjk"), "사이트 비밀 키가 해시에 들어간다")
    }

    @Test
    fun `입력은 대소문자와 하이픈과 공백을 가리지 않는다`() {
        val expected = codes.hash("abcde-fghjk")
        listOf("abcde-fghjk", "ABCDE-FGHJK", "abcdefghjk", " abcde fghjk ", "AbCdE-fGhJk").forEach { assertEquals(expected, codes.hash(it), it) }
    }

    @Test
    fun `모양이 맞는 코드만 코드로 본다`() {
        assertTrue(codes.looksLikeCode("abcde-fghjk") && codes.looksLikeCode("ABCDEFGHJK") && codes.looksLikeCode(" abcde fghjk "))
        listOf("", "abcde", "abcde-fghjk1", "abcd!-fghjk", "123456").forEach { assertTrue(!codes.looksLikeCode(it), it) }
    }
}
