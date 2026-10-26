package dev.yona.io

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * v1.6 `FileUtil.detectMediaType`와 `new Tika().detect(...)`(Tika 1.2, juniversalchardet 1.0.3)의 결과를 검증한다.
 * 기대값은 v1.6 라이브러리로 실제 실행해 얻었다.
 */
class MediaTypesTest {

    private val ascii = "hello world\n".toByteArray()
    private val korean = ("# 제목\n" + "한글 문서입니다. 이 문장은 한글로만 구성되어 있습니다.\n".repeat(20)).toByteArray()
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)

    @Test
    fun `이름 글롭과 텍스트 판정은 Tika 1_2를 따른다`() {
        assertEquals("application/javascript", MediaTypes.detect(ascii, "file.js"))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ascii, "README.md"))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ascii, "x.bat"))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ascii, "x.cmd"))
        for (name in listOf("x.properties", "x.conf", "x.ini")) assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ascii, name), name)
        assertEquals("application/javascript", MediaTypes.detect(ascii, "dir/sub/file.js"))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ascii, "a#b.js"))
    }

    @Test
    fun `비ASCII 비율이 높은 UTF-8 텍스트는 octet-stream이다 (v1_6 결함)`() {
        assertEquals("application/octet-stream", MediaTypes.detect(korean, "a.md"))
        assertEquals("application/octet-stream", MediaTypes.detect(korean, null))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(("hello world and some plain english text goes here and there and everywhere " + "한글").toByteArray(), null))
    }

    @Test
    fun `이름 없이 감지한 종류는 v1_6과 같다`() {
        assertEquals("image/png", MediaTypes.detectRaw(png, "a.png"))
        assertEquals("text/plain", MediaTypes.detectRaw("<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".toByteArray(), null))
        assertEquals("application/x-gzip", MediaTypes.detectRaw(byteArrayOf(0x1f, 0x8b.toByte(), 8, 0, 0, 0, 0, 0, 0, 3), "a.gz"))
        assertEquals("image/x-icon", MediaTypes.detectRaw(byteArrayOf(0, 0, 1, 0, 1, 0, 16, 16), "a.ico"))
    }

    @Test
    fun `Ogg는 ogv 이름이면 video ogg이다`() {
        val ogg = byteArrayOf(0x4f, 0x67, 0x67, 0x53, 0x00, 0x02) + ByteArray(40)
        assertEquals("audio/ogg", MediaTypes.detectRaw(ogg, "a.ogv"))
        assertEquals("video/ogg", MediaTypes.detect(ogg, "a.ogv"))
        // v1.6은 이름이 없으면 NullPointerException(500)이다. v2는 오류 없이 audio/ogg로 돌려준다 (R-93)
        assertEquals("audio/ogg", MediaTypes.detect(ogg, null))
    }

    @Test
    fun `엑셀 이름만으로 감지한다`() {
        assertEquals("application/vnd.ms-excel", MediaTypes.detectName("issues.xls"))
        assertEquals("application/vnd.ms-excel", MediaTypes.detectName("filename*=UTF-8''%ED%95%9C.xls"))
    }

    @Test
    fun `문자 집합은 65536번째 바이트 이후로 감지한다`() {
        val euckr = java.nio.charset.Charset.forName("EUC-KR")
        fun body(size: Int): ByteArray { val unit = "안녕하세요 한글 문서 내용입니다.\n".toByteArray(euckr); return ByteArray(size) { unit[it % unit.size] } }
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(body(30_000), "a.txt"))
        assertEquals("text/plain; charset=EUC-KR", MediaTypes.detect(body(70_000), "a.txt"))
        assertEquals("text/plain; charset=UTF-8", MediaTypes.detect(ByteArray(0), "a.txt"))
        assertEquals("application/octet-stream", MediaTypes.detect(ByteArray(0), null))
    }

    @Test
    fun `문자 집합 이름은 JVM 정규 이름이다`() {
        fun large(charset: String, text: String): ByteArray {
            val unit = text.toByteArray(java.nio.charset.Charset.forName(charset))
            return ByteArray(70_000) { unit[it % unit.size] }
        }
        val sjis = large("Shift_JIS", "これは日本語の文章です。日本語の文字をたくさん書きます。\n")
        assertEquals("text/plain; charset=Shift_JIS", MediaTypes.detect(sjis, "a.txt"))
        val w1252 = large("windows-1252", "Voilà une phrase en français avec des caractères accentués été\n")
        assertEquals("text/plain; charset=windows-1252", MediaTypes.detect(w1252, "a.txt"))
    }

    @Test
    fun `순수 ASCII는 US-ASCII가 아니라 UTF-8이다`() {
        assertEquals("UTF-8", CharsetDetection.detect("hello".toByteArray()))
        assertEquals("UTF-8", CharsetDetection.detect(ByteArray(0)))
    }
}
