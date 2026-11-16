package dev.yona.ssh.svn

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ra_svn` 서버의 체크아웃과 갱신(`update`, 서버가 편집기 명령을 보내는 쪽)을 실제 `svn` 클라이언트로 검증한다. 같은 저장소를 `file://`로 받은 작업 복사본과
 * `svn+ra://`로 받은 작업 복사본이 파일 내용, 속성, 상태까지 같아야 한다.
 */
class RaSvnServerUpdateTest {

    @TempDir lateinit var work: File
    private lateinit var server: RaSvnTestServer
    private lateinit var repo: File
    private lateinit var fileUrl: String
    private val resolver = object : RaSvnResolver {
        override fun resolve(path: String): RaSvnResolution =
            if (path == "/demo" || path.startsWith("/demo/")) RaSvnResolution.Found(repo, "/demo", writable = false) else RaSvnResolution.Failed("No repository found at '$path'")
    }

    @BeforeEach
    fun setUp() {
        RaSvnTestServer.assumeSubversionInstalled()
        repo = File(work, "demo")
        fileUrl = RaSvnTestServer.createRepository(repo, work)
        server = RaSvnTestServer(work, resolver)
    }

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.close() }

    private fun local(vararg args: String, dir: File = work): String {
        val process = ProcessBuilder(listOf("svn", "--non-interactive") + args).directory(dir).redirectErrorStream(true).also { it.environment()["LC_ALL"] = "C.UTF-8" }.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과" }
        check(process.exitValue() == 0) { "svn ${args.toList()} 실패: $output" }
        return output
    }

    /** `file://` 작업 복사본에서 [change]를 하고 커밋한다(서버를 거치지 않는다). */
    private fun commitLocally(message: String, change: (File) -> Unit) {
        val wc = File(work, "editor-wc").also { if (!it.exists()) local("checkout", "-q", fileUrl, it.path) }
        local("update", "-q", wc.path)
        change(wc)
        local("commit", "-q", "-m", message, wc.path)
    }

    /** 두 작업 복사본의 파일 내용이 같고 `svn status`가 깨끗하며 속성이 같은지 본다. */
    private fun assertSameCopies(expected: File, actual: File) {
        val diff = ProcessBuilder("diff", "-r", "--exclude=.svn", expected.path, actual.path).redirectErrorStream(true).start()
        val output = diff.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(0, diff.waitFor(), "파일이 다르다:\n$output")
        assertEquals("", local("status", actual.path).trim(), "작업 복사본이 깨끗하지 않다")
        val props = { wc: File -> local("proplist", "-vR", wc.path).replace(wc.path, "@WC@") }
        assertEquals(props(expected), props(actual), "속성이 다르다")
        assertEquals(local("info", "--show-item", "revision", expected.path).trim(), local("info", "--show-item", "revision", actual.path).trim())
    }

    private fun checkout(url: String, name: String, vararg extra: String): File {
        val wc = File(work, name)
        val result = if (url.startsWith("file:")) null else server.svn("checkout", "-q", *extra, url, wc.path)
        if (result == null) local("checkout", "-q", *extra, url, wc.path) else assertEquals(0, result.exit, result.output)
        return wc
    }

    @Test
    fun `체크아웃은 파일 저장소와 같은 작업 복사본을 만든다`() {
        commitLocally("여러 종류") { wc ->
            File(wc, "trunk/docs").mkdirs(); File(wc, "trunk/docs/readme.md").writeText("# 문서\n한글 내용\n"); File(wc, "trunk/empty.txt").writeText("")
            File(wc, "trunk/bin.dat").writeBytes(ByteArray(2048) { it.toByte() })
            local("add", "-q", "--force", "${wc.path}/trunk", dir = wc)
            local("propset", "-q", "svn:ignore", "*.tmp", "${wc.path}/trunk", dir = wc)
            local("propset", "-q", "custom:note", "메모", "${wc.path}/trunk/a.txt", dir = wc)
        }
        assertSameCopies(checkout(fileUrl, "wc-file"), checkout(server.url("/demo"), "wc-ra"))
    }

    @Test
    fun `큰 파일은 여러 조각으로 받는다`() {
        commitLocally("큰 파일") { wc ->
            File(wc, "trunk/big.txt").writeText((1..40_000).joinToString("\n") { "line $it of a rather long text to cross several windows" })
            local("add", "-q", "${wc.path}/trunk/big.txt", dir = wc)
        }
        assertSameCopies(checkout(fileUrl, "wc-file"), checkout(server.url("/demo"), "wc-ra"))
    }

    @Test
    fun `하위 디렉터리만 체크아웃한다`() {
        assertSameCopies(checkout("$fileUrl/trunk", "wc-file"), checkout(server.url("/demo/trunk"), "wc-ra"))
    }

    @Test
    fun `갱신은 추가, 수정, 삭제, 속성 변경을 반영한다`() {
        val ra = checkout(server.url("/demo"), "wc-ra")
        commitLocally("변경") { wc ->
            File(wc, "trunk/a.txt").appendText("두 번째 줄\n")
            File(wc, "trunk/new.txt").writeText("새 파일\n"); File(wc, "trunk/sub").mkdirs(); File(wc, "trunk/sub/x.txt").writeText("x\n")
            local("add", "-q", "--force", "${wc.path}/trunk", dir = wc)
            local("propset", "-q", "custom:k", "v", "${wc.path}/trunk", dir = wc)
        }
        val update = server.svn("update", "-q", ra.path)
        assertEquals(0, update.exit, update.output)
        assertSameCopies(checkout(fileUrl, "wc-file"), ra)
        commitLocally("삭제") { wc -> local("rm", "-q", "${wc.path}/trunk/new.txt", "${wc.path}/trunk/sub", dir = wc) }
        assertEquals(0, server.svn("update", "-q", ra.path).exit)
        assertSameCopies(checkout(fileUrl, "wc-file2"), ra)
    }

    @Test
    fun `특정 리비전으로 체크아웃하고 그 뒤로 갱신한다`() {
        commitLocally("둘째") { wc -> File(wc, "trunk/a.txt").appendText("2\n") }
        val ra = checkout(server.url("/demo"), "wc-ra", "-r", "1")
        assertTrue(File(ra, "trunk/a.txt").readText() == "hello\n")
        assertEquals(0, server.svn("update", "-q", ra.path).exit)
        assertSameCopies(checkout(fileUrl, "wc-file"), ra)
    }

    @Test
    fun `export는 작업 복사본 없이 내용을 받는다`() {
        val target = File(work, "exported")
        val result = server.svn("export", "-q", server.url("/demo/trunk"), target.path)
        assertEquals(0, result.exit, result.output)
        assertEquals("hello\n", File(target, "a.txt").readText())
    }
}
