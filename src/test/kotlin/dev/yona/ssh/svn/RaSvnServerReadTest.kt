package dev.yona.ssh.svn

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ra_svn` 서버의 읽기 명령(정보, 목록, 내용, 로그)을 실제 `svn` 클라이언트로 검증한다. 같은 저장소를 `file://`로 읽은 결과와 `svn+ra://`로 읽은 결과가 주소를 빼고 같아야 한다.
 */
class RaSvnServerReadTest {

    @TempDir lateinit var work: File
    private lateinit var server: RaSvnTestServer
    private lateinit var fileUrl: String
    private val resolver = object : RaSvnResolver {
        override fun resolve(path: String): RaSvnResolution =
            if (path == "/demo" || path.startsWith("/demo/")) RaSvnResolution.Found(File(work, "demo"), "/demo", writable = false) else RaSvnResolution.Failed("No repository found at '$path'")
    }

    @BeforeEach
    fun setUp() {
        RaSvnTestServer.assumeSubversionInstalled()
        fileUrl = RaSvnTestServer.createRepository(File(work, "demo"), work)
        server = RaSvnTestServer(work, resolver)
    }

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.close() }

    /** 같은 명령을 `file://`와 `svn+ra://`로 실행해 주소를 같은 글자로 바꾼 출력이 같은지 본다. */
    private fun same(vararg args: String) {
        fun run(base: String): RaSvnTestServer.Result = server.svn(*args.map { it.replace("@URL@", base) }.toTypedArray())
        val viaFile = run(fileUrl); val viaRa = run(server.url("/demo"))
        val normalized = { text: String -> text.replace(fileUrl, "@URL@").replace(server.url("/demo"), "@URL@") }
        assertEquals(0, viaRa.exit, "svn+ra 실패: ${viaRa.output}")
        assertEquals(normalized(viaFile.output), normalized(viaRa.output), "출력이 다르다: ${args.toList()}")
    }

    @Test
    fun `info는 파일 저장소와 같다`() {
        same("info", "@URL@/trunk/a.txt")
        same("info", "@URL@/trunk")
    }

    @Test
    fun `ls는 파일 저장소와 같다`() {
        same("ls", "@URL@/trunk")
        same("ls", "-v", "@URL@/trunk")
    }

    @Test
    fun `cat은 파일 내용을 돌려준다`() {
        val result = server.svn("cat", server.url("/demo/trunk/a.txt"))
        assertEquals(0, result.exit, result.output)
        assertEquals("hello\n", result.output)
    }

    @Test
    fun `log는 파일 저장소와 같다`() {
        same("log", "@URL@")
        same("log", "-v", "@URL@")
    }

    @Test
    fun `없는 저장소는 읽을 수 있는 오류로 거절한다`() {
        val result = server.svn("info", server.url("/other"))
        assertTrue(result.exit != 0)
        assertTrue(result.output.contains("No repository found at '/other'"), result.output)
    }

    @Test
    fun `없는 경로는 파일 저장소와 같은 오류이다`() {
        val result = server.svn("cat", server.url("/demo/trunk/missing.txt"))
        assertTrue(result.exit != 0)
        assertTrue(result.output.contains("File not found: revision 1, path '/trunk/missing.txt'"), result.output)
        assertTrue(result.output.contains("W160013"), "코드가 파일 저장소와 같아야 한다: ${result.output}")
    }
}
