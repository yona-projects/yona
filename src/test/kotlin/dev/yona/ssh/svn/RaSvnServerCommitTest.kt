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
 * `ra_svn` 서버의 커밋(클라이언트가 편집기 명령을 서버로 보내는 쪽)을 실제 `svn` 클라이언트로 검증한다. `svn+ra://`로 커밋한 결과를 `file://`로 읽어 확인한다.
 * 읽기 전용 접속은 `svnserve --read-only`처럼 인증 실패로 거절되고 저장소는 바뀌지 않아야 한다.
 */
class RaSvnServerCommitTest {

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
        server = RaSvnTestServer(work, resolver, user = "alice")
    }

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.close() }

    /** 서버를 거치지 않는 `svn`이다(`file://` 읽기와 쓰기). */
    private fun local(vararg args: String, dir: File = work): String {
        val out = File(work, "local-${System.nanoTime()}.txt")
        val process = ProcessBuilder(listOf("svn", "--non-interactive") + args).directory(dir).redirectErrorStream(true).redirectOutput(out).also { it.environment()["LC_ALL"] = "C.UTF-8" }.start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과" }
        check(process.exitValue() == 0) { "svn ${args.toList()} 실패: ${out.readText()}" }
        return out.readText()
    }

    private fun checkoutViaRa(name: String = "wc"): File = File(work, name).also { val r = server.svn("checkout", "-q", server.url("/demo"), it.path); assertEquals(0, r.exit, r.output) }

    private fun commitViaRa(wc: File, message: String = "커밋"): RaSvnTestServer.Result = server.svn("commit", "-q", "-m", message, wc.path)

    @Test
    fun `파일과 디렉터리를 추가하면 저장소에 반영된다`() {
        val wc = checkoutViaRa()
        File(wc, "trunk/docs").mkdirs(); File(wc, "trunk/docs/readme.md").writeText("# 문서\n한글\n"); File(wc, "trunk/b.txt").writeText("b\n")
        local("add", "-q", "--force", "${wc.path}/trunk")
        local("propset", "-q", "custom:note", "메모", "${wc.path}/trunk/b.txt")
        val result = commitViaRa(wc, "여러 개 추가")
        assertEquals(0, result.exit, result.output)
        assertEquals("# 문서\n한글\n", local("cat", "$fileUrl/trunk/docs/readme.md"))
        assertEquals("b\n", local("cat", "$fileUrl/trunk/b.txt"))
        assertEquals("메모", local("propget", "custom:note", "$fileUrl/trunk/b.txt").trim())
    }

    @Test
    fun `작성자와 메시지가 기록된다`() {
        val wc = checkoutViaRa()
        File(wc, "trunk/a.txt").appendText("수정\n")
        assertEquals(0, commitViaRa(wc, "한글 메시지").exit)
        val log = local("log", "-l", "1", fileUrl)
        assertTrue(log.contains("alice"), log)
        assertTrue(log.contains("한글 메시지"), log)
    }

    @Test
    fun `수정과 삭제와 복사와 이동을 반영한다`() {
        val wc = checkoutViaRa()
        File(wc, "trunk/a.txt").writeText("바뀐 내용\n")
        File(wc, "trunk/extra.txt").writeText("e\n"); local("add", "-q", "${wc.path}/trunk/extra.txt")
        assertEquals(0, commitViaRa(wc, "준비").exit)
        local("copy", "-q", "${wc.path}/trunk/a.txt", "${wc.path}/trunk/copy.txt")
        local("move", "-q", "${wc.path}/trunk/extra.txt", "${wc.path}/trunk/moved.txt")
        val result = commitViaRa(wc, "복사와 이동")
        assertEquals(0, result.exit, result.output)
        assertEquals("바뀐 내용\n", local("cat", "$fileUrl/trunk/a.txt"))
        assertEquals("바뀐 내용\n", local("cat", "$fileUrl/trunk/copy.txt"))
        assertEquals("e\n", local("cat", "$fileUrl/trunk/moved.txt"))
        assertTrue(!local("ls", "$fileUrl/trunk").contains("extra.txt"), "이동한 원본이 남아 있다")
        val delete = local("log", "-v", "-l", "1", fileUrl)
        assertTrue(delete.contains("copy.txt (from /trunk/a.txt:"), "복사 이력이 남아야 한다: $delete")
    }

    @Test
    fun `디렉터리를 삭제한다`() {
        val wc = checkoutViaRa()
        local("delete", "-q", "${wc.path}/trunk")
        assertEquals(0, commitViaRa(wc, "삭제").exit)
        assertTrue(!local("ls", fileUrl).contains("trunk"))
    }

    @Test
    fun `큰 파일과 이진 파일을 커밋한다`() {
        val wc = checkoutViaRa()
        val big = (1..60_000).joinToString("\n") { "line $it of a rather long text to cross several windows" }
        File(wc, "trunk/big.txt").writeText(big); File(wc, "trunk/bin.dat").writeBytes(ByteArray(4096) { (it * 7).toByte() })
        local("add", "-q", "${wc.path}/trunk/big.txt", "${wc.path}/trunk/bin.dat")
        assertEquals(0, commitViaRa(wc, "큰 파일").exit)
        assertEquals(big, local("cat", "$fileUrl/trunk/big.txt"))
        val exported = File(work, "exported.dat"); local("export", "-q", "$fileUrl/trunk/bin.dat", exported.path)
        assertTrue(exported.readBytes().contentEquals(ByteArray(4096) { (it * 7).toByte() }))
    }

    @Test
    fun `오래된 리비전에서 같은 파일을 고치면 충돌로 거절된다`() {
        val first = checkoutViaRa("wc1"); val second = checkoutViaRa("wc2")
        File(first, "trunk/a.txt").writeText("첫째\n"); assertEquals(0, commitViaRa(first, "첫째").exit)
        File(second, "trunk/a.txt").writeText("둘째\n")
        val result = commitViaRa(second, "둘째")
        assertTrue(result.exit != 0)
        assertTrue(result.output.contains("out of date") || result.output.contains("Out of date"), result.output)
        assertEquals("첫째\n", local("cat", "$fileUrl/trunk/a.txt"))
    }

    @Test
    fun `읽기 전용 접속의 커밋은 인증 실패로 거절되고 저장소는 그대로이다`() {
        val wc = checkoutViaRa()
        val before = local("info", "--show-item", "last-changed-revision", fileUrl).trim()
        writable = false
        File(wc, "trunk/a.txt").appendText("거절될 내용\n")
        val result = commitViaRa(wc, "거절")
        assertTrue(result.exit != 0)
        assertTrue(result.output.contains("Authorization failed"), result.output)
        assertEquals(before, local("info", "--show-item", "last-changed-revision", fileUrl).trim())
        assertEquals("hello\n", local("cat", "$fileUrl/trunk/a.txt"))
    }

    @Test
    fun `읽기 전용 접속도 읽기는 된다`() {
        writable = false
        assertEquals("hello\n", server.svn("cat", server.url("/demo/trunk/a.txt")).output)
    }
}
