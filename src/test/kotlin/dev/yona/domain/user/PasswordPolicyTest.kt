package dev.yona.domain.user

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 비밀번호 정책(NIST SP 800-63B 기준, 선생님 결정 2026-11-11, welcome-F3)을 검증한다: 길이 8자 이상 128자 이하, 문자 구성은 강제하지 않고,
 * 흔한 비밀번호와 아이디, 이메일과 같은 값은 거절한다.
 */
class PasswordPolicyTest {

    @Test
    fun `여덟 글자 미만은 너무 짧다`() {
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check("p"))
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check("Xk9#mQ2"))
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check(""))
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check(null))
        assertNull(PasswordPolicy.check("Xk9#mQ2v"))
    }

    @Test
    fun `길이는 글자 수로 세고 공백과 한글도 글자로 센다`() {
        assertNull(PasswordPolicy.check("가나다라마바사아"))
        assertNull(PasswordPolicy.check("correct horse battery"))
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check("가나다라마바사"))
        // 이모지처럼 두 단위로 저장되는 글자도 한 글자이다
        assertEquals(PasswordPolicy.Violation.TOO_SHORT, PasswordPolicy.check("😀😁😂😃😄😅😆"))
        assertNull(PasswordPolicy.check("😀😁😂😃😄😅😆😇"))
    }

    @Test
    fun `백스물여덟 글자를 넘으면 너무 길다`() {
        assertNull(PasswordPolicy.check("aB3$".repeat(32)))
        assertEquals(PasswordPolicy.Violation.TOO_LONG, PasswordPolicy.check("aB3$".repeat(32) + "x"))
    }

    @Test
    fun `문자 구성은 강제하지 않는다`() {
        assertNull(PasswordPolicy.check("lowercaseonlyphrase"))
        assertNull(PasswordPolicy.check("4829173650"))
    }

    @Test
    fun `흔한 비밀번호는 대소문자를 가리지 않고 거절한다`() {
        for (common in listOf("password", "PASSWORD", "Password123", "12345678", "qwertyuiop", "1q2w3e4r", "iloveyou", "p@ssw0rd")) {
            assertEquals(PasswordPolicy.Violation.COMMON, PasswordPolicy.check(common), common)
        }
    }

    @Test
    fun `같은 글자만 반복한 값은 흔한 비밀번호로 본다`() {
        assertEquals(PasswordPolicy.Violation.COMMON, PasswordPolicy.check("aaaaaaaa"))
        assertEquals(PasswordPolicy.Violation.COMMON, PasswordPolicy.check("9999999999"))
    }

    @Test
    fun `아이디나 이메일과 같은 값은 거절한다`() {
        assertEquals(PasswordPolicy.Violation.SAME_AS_ID, PasswordPolicy.check("somebody77", "somebody77"))
        assertEquals(PasswordPolicy.Violation.SAME_AS_ID, PasswordPolicy.check("SomeBody77", "somebody77"))
        assertEquals(PasswordPolicy.Violation.SAME_AS_ID, PasswordPolicy.check("kim.minsu@example.com", "kimmin", "kim.minsu@example.com"))
        assertEquals(PasswordPolicy.Violation.SAME_AS_ID, PasswordPolicy.check("kim.minsu99", "kimmin", "kim.minsu99@example.com"))
        assertNull(PasswordPolicy.check("Xk9#mQ2v", "somebody77", "a@example.com"))
    }
}
