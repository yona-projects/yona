package dev.yona.io

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * v1.6(JGit 4.5)의 이진 파일 판정을 검증한다. 규칙은 "앞 8000바이트에 NUL이 있으면 이진"뿐이다.
 * 기대값은 JGit 4.5.0 jar로 직접 실행해 얻었다. JGit 7.x는 CR 뒤에 LF가 아닌 문자가 오는 경우 등도 이진으로 본다.
 */
class RawTextBinaryTest {

    private fun binary(vararg bytes: Int) = RawTextBinary.isBinary(ByteArray(bytes.size) { bytes[it].toByte() })

    private fun text(s: String) = RawTextBinary.isBinary(s.toByteArray())

    @Test
    fun `홀로 CR과 옛 Mac 줄바꿈과 끝의 CR은 텍스트이다`() {
        assertEquals(false, text("a\rb"))
        assertEquals(false, text("line1\rline2\rline3"))
        assertEquals(false, RawTextBinary.isBinary("ab\r".byteInputStream()))
        assertEquals(false, text("a\r\rb"))
    }

    @Test
    fun `NUL이 앞 8000바이트 안에 있으면 이진이고 그 뒤에 있으면 텍스트이다`() {
        fun withNul(at: Int) = ByteArray(9000) { 'a'.code.toByte() }.also { it[at] = 0 }
        assertEquals(true, RawTextBinary.isBinary(withNul(7999)))
        assertEquals(false, RawTextBinary.isBinary(withNul(8000)))
        assertEquals(true, RawTextBinary.isBinary(withNul(7999).inputStream()))
        assertEquals(false, RawTextBinary.isBinary(withNul(8000).inputStream()))
    }

    @Test
    fun `일반 텍스트와 빈 입력과 한글은 텍스트이다`() {
        assertEquals(false, text("a\r\nb"))
        assertEquals(false, RawTextBinary.isBinary(ByteArray(0)))
        assertEquals(false, text("한글 UTF-8 본문"))
    }

    @Test
    fun `PNG 머리는 NUL이 있으면 이진이고 앞 8바이트만 있으면 텍스트이다`() {
        assertEquals(true, binary(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D))
        assertEquals(false, binary(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
    }
}
