package dev.yona.ssh.svn

import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Subversion의 `ra_svn` 프로토콜 부호기를 검증한다. 기대값은 실제 `svnserve -t`(1.14.5)와 `svn` 클라이언트가 주고받은 바이트를 녹화한 것이다
 * (`decisions.md`의 svn+ssh 조사). 문자열의 길이는 글자 수가 아니라 UTF-8 바이트 수이다.
 */
class RaSvnWireTest {

    private fun read(text: String): List<RaItem> {
        val reader = RaSvnReader(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        return generateSequence { reader.read() }.toList()
    }

    private fun write(item: RaItem): String = ByteArrayOutputStream().also { RaSvnWriter(it).apply { write(item); flush() } }.toString(Charsets.UTF_8)

    @Test
    fun `서버의 인사말을 읽는다`() {
        val item = read("( success ( 2 2 ( ) ( edit-pipeline svndiff1 depth ) ) ) ").single() as RaList
        assertEquals("success", (item.items[0] as RaWord).value)
        val tuple = item.items[1] as RaList
        assertEquals(2L, (tuple.items[0] as RaNumber).value)
        assertEquals(emptyList(), (tuple.items[2] as RaList).items)
        assertEquals(listOf("edit-pipeline", "svndiff1", "depth"), (tuple.items[3] as RaList).items.map { (it as RaWord).value })
    }

    @Test
    fun `클라이언트의 첫 응답에서 주소를 읽는다`() {
        val item = read("( 2 ( edit-pipeline svndiff1 ) 24:svn+rec://localhost/demo 32:SVN/1.14.5 (x86_64-pc-linux-gnu) ( ) ) ").single() as RaList
        assertEquals("svn+rec://localhost/demo", (item.items[2] as RaString).text)
        assertEquals("SVN/1.14.5 (x86_64-pc-linux-gnu)", (item.items[3] as RaString).text)
    }

    @Test
    fun `문자열 길이는 UTF-8 바이트 수이다`() {
        val item = read("( commit ( 16:첫 디렉터리 ( ) true ) ) ").single() as RaList
        assertEquals("첫 디렉터리", ((item.items[1] as RaList).items[0] as RaString).text)
        assertEquals("16:첫 디렉터리 ", write(RaString("첫 디렉터리")))
    }

    @Test
    fun `빈 문자열과 빈 목록과 공백이 든 문자열을 읽는다`() {
        val item = read("( EXTERNAL ( 0: ) ) ").single() as RaList
        assertEquals("", ((item.items[1] as RaList).items[0] as RaString).text)
        assertEquals("a b", (read("3:a b ").single() as RaString).text)
        assertEquals(emptyList(), (read("( ) ").single() as RaList).items)
    }

    @Test
    fun `부호 없는 64비트 수를 읽고 쓴다`() {
        val number = read("18446744073709551615 ").single() as RaNumber
        assertEquals(-1L, number.value)
        assertEquals("18446744073709551615 ", write(number))
        assertEquals("5 ", write(RaNumber(5)))
    }

    @Test
    fun `여러 항목이 이어져도 하나씩 읽고 끝에서 null이다`() {
        val reader = RaSvnReader(ByteArrayInputStream("( success ( ) ) ( success ( 1 ) ) done ".toByteArray()))
        assertTrue(reader.read() is RaList)
        assertTrue(reader.read() is RaList)
        assertEquals("done", (reader.read() as RaWord).value)
        assertNull(reader.read())
    }

    @Test
    fun `쓴 바이트는 svnserve가 쓴 것과 같다`() {
        val uuid = "9d677384-82cb-4412-b5dd-dfeab75fb267"
        assertEquals("( success ( ( ANONYMOUS EXTERNAL ) 36:$uuid ) ) ", write(raList(RaWord("success"), raList(raList(RaWord("ANONYMOUS"), RaWord("EXTERNAL")), RaString(uuid)))))
        assertEquals("( success ( ( ) 0: ) ) ", write(raList(RaWord("success"), raList(raList(), RaString("")))))
        assertEquals("( success ( 2 ) ) ", write(raList(RaWord("success"), raList(RaNumber(2)))))
    }

    @Test
    fun `읽은 것을 그대로 쓰면 같은 바이트가 나온다`() {
        val samples = listOf(
            "( success ( 36:9d677384-82cb-4412-b5dd-dfeab75fb267 24:svn+rec://localhost/demo ( mergeinfo ) ) ) ",
            "( ( ) 2 ( 5:alice ) ( 27:2026-11-16T10:02:20.139783Z ) ( 13:파일 추가 ) false false 0 ( ) false ) ",
            "( success ( ( ( dir 18446744073709551615 false 2 ( 27:2026-11-16T10:02:20.139783Z ) ( 5:alice ) ) ) ) ) "
        )
        for (sample in samples) assertEquals(sample, write(read(sample).single()), sample)
    }

    @Test
    fun `바이트가 모자라거나 형식이 틀리면 프로토콜 오류이다`() {
        assertThrowsProtocol { read("( success ( 2 ") }
        assertThrowsProtocol { read("5:ab") }
        assertThrowsProtocol { read(")") }
    }

    private fun assertThrowsProtocol(block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is RaSvnProtocolException, "프로토콜 오류여야 한다: $error")
    }
}
