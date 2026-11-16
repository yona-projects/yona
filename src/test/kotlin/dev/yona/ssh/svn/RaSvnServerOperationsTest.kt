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
 * 클라이언트가 자주 쓰는 나머지 작업(`diff`, `status -u`, `switch`, `blame`, `merge`, 위치 조회)을 실제 `svn` 클라이언트로 검증한다. 같은 저장소를 `file://`로 실행한
 * 결과와 `svn+ra://`로 실행한 결과가 주소를 빼고 같아야 한다.
 */
class RaSvnServerOperationsTest {

    @TempDir lateinit var work: File
    private lateinit var server: RaSvnTestServer
    private lateinit var repo: File
    private lateinit var fileUrl: String
    @Volatile private var writable = true
    private val resolver = RaSvnResolver { path ->
        if (path == "/demo" || path.startsWith("/demo/")) RaSvnResolution.Found(repo, "/demo", writable) else RaSvnResolution.Failed("No repository found at '$path'")
    }

    @BeforeEach
    fun setUp() {
        RaSvnTestServer.assumeSubversionInstalled()
        repo = File(work, "demo")
        fileUrl = RaSvnTestServer.createRepository(repo, work)
        server = RaSvnTestServer(work, resolver)
        // 리비전 2: 파일 수정과 새 파일, 리비전 3: 브랜치 복사, 리비전 4: 브랜치에서 수정
        val wc = File(work, "editor"); local("checkout", "-q", fileUrl, wc.path)
        File(wc, "trunk/a.txt").appendText("둘째 줄\n"); File(wc, "trunk/b.txt").writeText("b\n"); local("add", "-q", "${wc.path}/trunk/b.txt"); local("commit", "-q", "-m", "둘째", wc.path)
        local("mkdir", "-q", "--parents", "-m", "브랜치 디렉터리", "$fileUrl/branches"); local("copy", "-q", "-m", "브랜치", "$fileUrl/trunk", "$fileUrl/branches/b")
        local("update", "-q", wc.path)
        File(wc, "branches/b/a.txt").appendText("브랜치 줄\n"); File(wc, "branches/b/c.txt").writeText("c\n"); local("add", "-q", "${wc.path}/branches/b/c.txt"); local("commit", "-q", "-m", "브랜치 수정", wc.path)
    }

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.close() }

    private fun local(vararg args: String, dir: File = work): String {
        val out = File(work, "local-${System.nanoTime()}.txt")
        val process = ProcessBuilder(listOf("svn", "--non-interactive") + args).directory(dir).redirectErrorStream(true).redirectOutput(out).also { it.environment()["LC_ALL"] = "C.UTF-8" }.start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과" }
        check(process.exitValue() == 0) { "svn ${args.toList()} 실패: ${out.readText()}" }
        return out.readText()
    }

    private fun sameOutput(vararg args: String) {
        fun run(base: String, viaRa: Boolean): String {
            val real = args.map { it.replace("@URL@", base) }.toTypedArray()
            return if (viaRa) server.svn(*real).also { assertEquals(0, it.exit, "svn+ra 실패: ${it.output}") }.output else local(*real)
        }
        val raUrl = server.url("/demo")
        val normalize = { text: String -> text.replace(fileUrl, "@URL@").replace(raUrl, "@URL@").replace(Regex("\\(\\d{4}-\\d\\d-\\d\\d[^)]*\\)"), "(date)") }
        assertEquals(normalize(run(fileUrl, false)), normalize(run(raUrl, true)), "출력이 다르다: ${args.toList()}")
    }

    @Test
    fun `diff는 파일 저장소와 같다`() {
        sameOutput("diff", "-r", "1:2", "@URL@/trunk")
        sameOutput("diff", "-c", "2", "@URL@/trunk/a.txt")
        sameOutput("diff", "@URL@/trunk", "@URL@/branches/b")
    }

    @Test
    fun `status -u는 서버에서 바뀐 것을 표시한다`() {
        val viaRa = File(work, "wc-ra").also { assertEquals(0, server.svn("checkout", "-q", "-r", "1", server.url("/demo"), it.path).exit) }
        val viaFile = File(work, "wc-file").also { local("checkout", "-q", "-r", "1", fileUrl, it.path) }
        File(viaRa, "trunk/a.txt").appendText("로컬 수정\n"); File(viaFile, "trunk/a.txt").appendText("로컬 수정\n")
        val ra = server.svn("status", "-u", viaRa.path); assertEquals(0, ra.exit, ra.output)
        // 같은 폴더 안 항목의 순서는 서버가 편집기를 보내는 순서에 따라 달라지므로 줄을 정렬해 비교한다
        val expected = local("status", "-u", viaFile.path).replace(viaFile.path, "@WC@").lines().sorted()
        assertEquals(expected, ra.output.replace(viaRa.path, "@WC@").lines().sorted())
        assertTrue(ra.output.contains("*"), ra.output)
    }

    @Test
    fun `switch는 작업 복사본을 다른 주소로 옮긴다`() {
        val viaRa = File(work, "wc-ra").also { assertEquals(0, server.svn("checkout", "-q", "$fileUrl/trunk".replace(fileUrl, server.url("/demo")), it.path).exit) }
        val viaFile = File(work, "wc-file").also { local("checkout", "-q", "$fileUrl/trunk", it.path) }
        val ra = server.svn("switch", "-q", server.url("/demo/branches/b"), viaRa.path); assertEquals(0, ra.exit, ra.output)
        local("switch", "-q", "$fileUrl/branches/b", viaFile.path)
        val diff = ProcessBuilder("diff", "-r", "--exclude=.svn", viaFile.path, viaRa.path).redirectErrorStream(true).start()
        val output = diff.inputStream.readBytes().toString(Charsets.UTF_8)
        assertEquals(0, diff.waitFor(), "파일이 다르다:\n$output")
        assertEquals("", server.svn("status", viaRa.path).output.trim())
    }

    @Test
    fun `blame은 파일 저장소와 같다`() {
        sameOutput("blame", "@URL@/branches/b/a.txt")
        sameOutput("blame", "-v", "@URL@/trunk/a.txt")
    }

    @Test
    fun `리비전 지정 접근과 이력 추적을 한다`() {
        sameOutput("cat", "@URL@/trunk/a.txt@1")
        sameOutput("log", "-v", "--stop-on-copy", "@URL@/branches/b")
        sameOutput("info", "-r", "2", "@URL@/trunk/b.txt")
    }

    @Test
    fun `merge는 파일 저장소와 같은 결과를 만든다`() {
        val viaRa = File(work, "m-ra").also { assertEquals(0, server.svn("checkout", "-q", server.url("/demo/trunk"), it.path).exit) }
        val viaFile = File(work, "m-file").also { local("checkout", "-q", "$fileUrl/trunk", it.path) }
        val ra = server.svn("merge", "-q", server.url("/demo/branches/b"), viaRa.path); assertEquals(0, ra.exit, ra.output)
        local("merge", "-q", "$fileUrl/branches/b", viaFile.path)
        assertEquals(local("status", viaFile.path).replace(viaFile.path, "@WC@"), server.svn("status", viaRa.path).output.replace(viaRa.path, "@WC@"))
        // propget -R은 경로를 현재 디렉터리 기준 상대 경로로 출력한다(`m-file - /branches/b:4-5`)
        val mergeinfo = { text: String -> text.replace(Regex("^m-(file|ra)", RegexOption.MULTILINE), "@WC@") }
        assertEquals(mergeinfo(local("propget", "svn:mergeinfo", "-R", viaFile.name)), mergeinfo(server.svn("propget", "svn:mergeinfo", "-R", viaRa.name).output))
    }

    @Test
    fun `잠금과 해제와 잠금 정보`() {
        val url = server.url("/demo/trunk/a.txt")
        val locked = server.svn("lock", "-m", "작업 중", url)
        assertEquals(0, locked.exit, locked.output)
        val info = server.svn("info", url)
        assertTrue(info.output.contains("Lock Owner: alice"), info.output)
        assertTrue(info.output.contains("작업 중"), info.output)
        assertEquals(0, server.svn("unlock", "--force", url).exit)
        assertTrue(!server.svn("info", url).output.contains("Lock Owner"), "해제한 뒤에는 잠금 정보가 없어야 한다")
    }

    @Test
    fun `잠근 파일은 잠금 토큰으로 커밋하고 커밋하면 풀린다`() {
        val wc = File(work, "lock-wc").also { assertEquals(0, server.svn("checkout", "-q", server.url("/demo/trunk"), it.path).exit) }
        assertEquals(0, server.svn("lock", "-m", "x", "${wc.path}/a.txt").exit)
        File(wc, "a.txt").appendText("잠금 중 수정\n")
        val commit = server.svn("commit", "-q", "-m", "잠금 커밋", wc.path)
        assertEquals(0, commit.exit, commit.output)
        assertEquals("hello\n둘째 줄\n잠금 중 수정\n", local("cat", "$fileUrl/trunk/a.txt"))
        assertTrue(!local("info", "$fileUrl/trunk/a.txt").contains("Lock Owner"), "커밋하면 잠금이 풀려야 한다")
    }

    @Test
    fun `이미 잠긴 파일을 다시 잠그면 거절되고 훔칠 수 있다`() {
        val url = server.url("/demo/trunk/a.txt")
        assertEquals(0, server.svn("lock", "-m", "첫째", url).exit)
        val again = server.svn("lock", "-m", "둘째", url)
        assertTrue(again.exit != 0 || again.output.contains("already locked") || again.output.contains("W160035"), again.output)
        assertEquals(0, server.svn("lock", "--force", "-m", "훔침", url).exit)
    }

    @Test
    fun `읽기 전용 접속은 잠글 수 없다`() {
        writable = false
        val result = server.svn("lock", "-m", "x", server.url("/demo/trunk/a.txt"))
        assertTrue(result.exit != 0)
        assertTrue(result.output.contains("Authorization failed"), result.output)
    }
}
