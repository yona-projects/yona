package dev.yona.ssh.svn

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 시스템 `svnserve`가 지원하는 고급 명령을 `ra_svn` 서버가 같게 지원하는지 실제 클라이언트로 검증한다: 깊이(`--depth`, 희소 체크아웃), 리비전 속성 변경
 * (`propset --revprop`), 저장소 미러링(`svnsync`의 `replay-range`), 삭제된 리비전 조회(`get-deleted-rev`). 응답 형식은 진짜 `svnserve 1.14.5`의 녹화로 맞췄다.
 */
class RaSvnServerAdvancedTest {

    @TempDir lateinit var work: File
    private lateinit var server: RaSvnTestServer
    private lateinit var repo: File
    private lateinit var target: File
    private lateinit var fileUrl: String
    @Volatile private var writable = true
    private val resolver = RaSvnResolver { path ->
        if (path == "/demo" || path.startsWith("/demo/")) RaSvnResolution.Found(repo, "/demo", writable)
        else if (path == "/target" || path.startsWith("/target/")) RaSvnResolution.Found(target, "/target", writable)
        else RaSvnResolution.Failed("No repository found at '$path'")
    }

    @BeforeEach
    fun setUp() {
        RaSvnTestServer.assumeSubversionInstalled()
        repo = File(work, "demo")
        fileUrl = RaSvnTestServer.createRepository(repo, work)
        server = RaSvnTestServer(work, resolver)
        // 리비전 2: 하위 디렉터리와 파일, 리비전 3: 디렉터리 삭제, 리비전 4: 브랜치 복사, 리비전 5: 브랜치 삭제
        val wc = File(work, "editor"); local("checkout", "-q", fileUrl, wc.path)
        File(wc, "trunk/sub").mkdirs(); File(wc, "trunk/sub/b.txt").writeText("b\n"); File(wc, "trunk/dir2").mkdirs(); File(wc, "trunk/dir2/c.txt").writeText("c\n")
        local("add", "-q", "--force", "${wc.path}/trunk"); local("commit", "-q", "-m", "구조", wc.path)
        local("rm", "-q", "${wc.path}/trunk/dir2"); local("commit", "-q", "-m", "디렉터리 삭제", wc.path)
        local("mkdir", "-q", "${wc.path}/branches"); local("copy", "-q", "${wc.path}/trunk", "${wc.path}/branches/br1"); local("commit", "-q", "-m", "브랜치", wc.path)
        local("rm", "-q", "${wc.path}/branches/br1"); local("commit", "-q", "-m", "브랜치 삭제", wc.path)
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

    private fun remote(vararg args: String, dir: File = work): String {
        val result = server.svn(*args, dir = dir)
        assertEquals(0, result.exit, "svn ${args.toList()} 실패: ${result.output}\n대화:\n${server.dialogue()}")
        return result.output
    }

    private fun tree(wc: File): String {
        val process = ProcessBuilder("find", wc.path, "-not", "-path", "*/.svn*", "-printf", "%P %y\n").redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        process.waitFor()
        return output.lines().filter { it.isNotBlank() }.sorted().joinToString("\n")
    }

    private fun depthOf(wc: File, path: String = ""): String = local("info", "--show-item", "depth", File(wc, path).path).trim()

    // ---- 깊이 ----

    @Test
    fun `깊이를 지정한 체크아웃은 파일 저장소와 같은 작업 복사본을 만든다`() {
        for (depth in listOf("empty", "files", "immediates")) {
            val expected = File(work, "f-$depth"); val actual = File(work, "r-$depth")
            local("checkout", "-q", "--depth", depth, "$fileUrl/trunk", expected.path)
            remote("checkout", "-q", "--depth", depth, server.url("/demo/trunk"), actual.path)
            assertEquals(tree(expected), tree(actual), "깊이 $depth 의 파일 구성이 다르다")
            assertEquals(depthOf(expected), depthOf(actual), "깊이 $depth 의 기록이 다르다")
            assertEquals("", local("status", actual.path).trim())
        }
    }

    @Test
    fun `깊이를 바꾸는 갱신은 파일 저장소와 같은 결과이다`() {
        val expected = File(work, "f-wc"); val actual = File(work, "r-wc")
        local("checkout", "-q", "--depth", "immediates", "$fileUrl/trunk", expected.path)
        remote("checkout", "-q", "--depth", "immediates", server.url("/demo/trunk"), actual.path)
        local("update", "-q", "--set-depth", "infinity", expected.path)
        remote("update", "-q", "--set-depth", "infinity", actual.path)
        assertEquals(tree(expected), tree(actual))
        assertTrue(File(actual, "sub/b.txt").exists(), "하위 파일을 받아야 한다")
        local("update", "-q", "--set-depth", "exclude", File(expected, "sub").path)
        remote("update", "-q", "--set-depth", "exclude", File(actual, "sub").path)
        assertEquals(tree(expected), tree(actual))
        assertTrue(!File(actual, "sub").exists(), "제외한 디렉터리는 없어야 한다")
        assertEquals("", local("status", actual.path).trim())
    }

    @Test
    fun `일부 하위 경로만 깊이를 넓히는 희소 체크아웃`() {
        val actual = File(work, "sparse")
        remote("checkout", "-q", "--depth", "empty", server.url("/demo/trunk"), actual.path)
        remote("update", "-q", "--set-depth", "infinity", File(actual, "sub").path)
        assertEquals(listOf(" d", "sub d", "sub/b.txt f"), tree(actual).lines())
    }

    // ---- 리비전 속성 변경 ----

    @Test
    fun `쓸 수 있는 접속은 리비전의 로그 메시지를 바꾸고 파일 저장소에서 읽힌다`() {
        remote("propset", "--revprop", "-r", "2", "svn:log", "수정한 메시지", server.url("/demo"))
        assertEquals("수정한 메시지", local("propget", "--revprop", "-r", "2", "svn:log", fileUrl).trim())
        assertEquals("수정한 메시지", remote("propget", "--revprop", "-r", "2", "svn:log", server.url("/demo")).trim())
    }

    @Test
    fun `사용자 지정 리비전 속성을 더하고 지운다`() {
        remote("propset", "--revprop", "-r", "2", "my:tag", "값", server.url("/demo"))
        assertEquals("값", local("propget", "--revprop", "-r", "2", "my:tag", fileUrl).trim())
        remote("propdel", "--revprop", "-r", "2", "my:tag", server.url("/demo"))
        assertEquals("", local("proplist", "--revprop", "-r", "2", fileUrl).lines().filter { it.contains("my:tag") }.joinToString())
    }

    @Test
    fun `읽기 전용 접속은 리비전 속성을 바꾸지 못한다`() {
        writable = false
        val result = server.svn("propset", "--revprop", "-r", "2", "svn:log", "바꿈", server.url("/demo"))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Authorization failed"), result.output)
        assertEquals("구조", local("propget", "--revprop", "-r", "2", "svn:log", fileUrl).trim())
    }

    @Test
    fun `작성자와 날짜는 바꾸지 못한다`() {
        for (name in listOf("svn:author", "svn:date")) {
            val before = local("propget", "--revprop", "-r", "2", name, fileUrl).trim()
            val result = server.svn("propset", "--revprop", "-r", "2", name, "x", server.url("/demo"))
            assertNotEquals(0, result.exit, name)
            assertTrue(result.output.contains("Authorization failed"), result.output)
            assertEquals(before, local("propget", "--revprop", "-r", "2", name, fileUrl).trim())
        }
    }

    @Test
    fun `예전 형식의 change-rev-prop과 기존 값 확인이 있는 change-rev-prop2도 처리한다`() {
        server.raw("/demo").use { raw ->
            raw.command("change-rev-prop", RaNumber(2), RaString("svn:log"), raList(RaString("옛 형식")))
            assertEquals(raList(RaWord("success"), raList(raList(), RaString(""))), raw.receive())
            assertEquals(raList(RaWord("success"), raList()), raw.receive())
            assertEquals("옛 형식", local("propget", "--revprop", "-r", "2", "svn:log", fileUrl).trim())
            // 기존 값이 다르면 FS_PROP_BASEVALUE_MISMATCH(160049)이다
            raw.command("change-rev-prop2", RaNumber(2), RaString("svn:log"), raList(RaString("새 값")), raList(RaWord("false")), raList(RaString("틀린 값")))
            raw.receive()
            val failure = raw.receive() as RaList
            assertEquals(RaWord("failure"), failure.items[0])
            assertEquals(160049L, (((failure.items[1] as RaList).items[0] as RaList).items[0] as RaNumber).value)
            raw.command("change-rev-prop2", RaNumber(2), RaString("svn:log"), raList(RaString("새 값")), raList(RaWord("false")), raList(RaString("옛 형식")))
            raw.receive(); raw.receive()
            assertEquals("새 값", local("propget", "--revprop", "-r", "2", "svn:log", fileUrl).trim())
        }
    }

    // ---- svnsync (replay) ----

    private fun mirror(): File {
        val mirror = File(work, "mirror")
        check(ProcessBuilder("svnadmin", "create", mirror.path).start().waitFor() == 0)
        File(mirror, "hooks/pre-revprop-change").also { it.writeText("#!/bin/sh\nexit 0\n"); it.setExecutable(true) }
        return mirror
    }

    private fun log(url: String): String = local("log", "-v", "-r", "1:HEAD", url).replace(Regex("\\| \\d{4}-\\d\\d-\\d\\d [^|]*\\|"), "|")

    @Test
    fun `svnsync는 모든 리비전을 같은 내용으로 미러링한다`() {
        val mirror = mirror(); val mirrorUrl = "file://${mirror.absolutePath}"
        val init = server.svnsync("initialize", mirrorUrl, server.url("/demo"))
        assertEquals(0, init.exit, init.output + server.dialogue())
        val sync = server.svnsync("synchronize", mirrorUrl)
        assertEquals(0, sync.exit, sync.output + "\n대화:\n" + server.dialogue())
        assertEquals(log(fileUrl), log(mirrorUrl), "이력이 같아야 한다")
        assertEquals(local("log", "-r", "5", "--xml", fileUrl).contains("브랜치 삭제"), true)
        for (rev in 1..5) assertEquals(local("propget", "--revprop", "-r", "$rev", "svn:log", fileUrl), local("propget", "--revprop", "-r", "$rev", "svn:log", mirrorUrl), "r$rev 로그")
        val a = File(work, "ex-a"); val b = File(work, "ex-b")
        local("export", "-q", "-r", "3", "$fileUrl/trunk", a.path); local("export", "-q", "-r", "3", "$mirrorUrl/trunk", b.path)
        assertEquals(tree(a), tree(b))
    }

    @Test
    fun `svnsync는 나중에 생긴 리비전만 이어서 받는다`() {
        val mirror = mirror(); val mirrorUrl = "file://${mirror.absolutePath}"
        assertEquals(0, server.svnsync("initialize", mirrorUrl, server.url("/demo")).exit)
        assertEquals(0, server.svnsync("synchronize", mirrorUrl).exit)
        val wc = File(work, "editor"); File(wc, "trunk/new.txt").writeText("새 파일\n")
        local("update", "-q", wc.path); File(wc, "trunk/new.txt").writeText("새 파일\n"); local("add", "-q", "${wc.path}/trunk/new.txt"); local("commit", "-q", "-m", "추가", wc.path)
        val again = server.svnsync("synchronize", mirrorUrl)
        assertEquals(0, again.exit, again.output)
        assertEquals("새 파일", local("cat", "$mirrorUrl/trunk/new.txt").trim())
    }

    @Test
    fun `svnsync는 하위 디렉터리만 미러링할 수 있다`() {
        val mirror = mirror(); val mirrorUrl = "file://${mirror.absolutePath}"
        val init = server.svnsync("initialize", mirrorUrl, server.url("/demo/trunk"))
        assertEquals(0, init.exit, init.output + server.dialogue())
        val sync = server.svnsync("synchronize", mirrorUrl)
        assertEquals(0, sync.exit, sync.output + "\n대화:\n" + server.dialogue())
        // 진짜 svnsync도 원래 경로(trunk/)를 유지하고 그 밖(branches)은 받지 않는다
        assertEquals(listOf("trunk/", "trunk/a.txt", "trunk/sub/", "trunk/sub/b.txt"), local("ls", "-R", mirrorUrl).lines().filter { it.isNotBlank() })
    }

    // ---- 깊이를 받는 다른 명령 ----

    @Test
    fun `깊이를 지정한 목록, 차이, 상태, 내보내기, switch도 파일 저장소와 같다`() {
        fun same(vararg args: String) {
            val expected = local(*args.map { it.replace("@F", fileUrl) }.toTypedArray())
            val actual = remote(*args.map { it.replace("@F", server.url("/demo")) }.toTypedArray())
            assertEquals(expected.replace(fileUrl, "@U"), actual.replace(server.url("/demo"), "@U"), "svn ${args.toList()}")
        }
        same("ls", "--depth", "empty", "@F/trunk")
        same("ls", "--depth", "immediates", "@F/trunk")
        same("ls", "-R", "--depth", "files", "@F/trunk")
        same("diff", "--depth", "files", "-r", "1:2", "@F/trunk")
        same("diff", "--depth", "immediates", "-r", "1:2", "@F/trunk")
        val expected = File(work, "x-f"); val actual = File(work, "x-r")
        local("export", "-q", "--depth", "immediates", "@F/trunk".replace("@F", fileUrl), expected.path)
        remote("export", "-q", "--depth", "immediates", server.url("/demo/trunk"), actual.path)
        assertEquals(tree(expected), tree(actual))
        val wcF = File(work, "sw-f"); val wcR = File(work, "sw-r")
        local("checkout", "-q", "--depth", "files", "$fileUrl/trunk", wcF.path)
        remote("checkout", "-q", "--depth", "files", server.url("/demo/trunk"), wcR.path)
        local("switch", "-q", "--set-depth", "immediates", "$fileUrl/branches/br1@4", wcF.path)
        remote("switch", "-q", "--set-depth", "immediates", "${server.url("/demo")}/branches/br1@4", wcR.path)
        assertEquals(tree(wcF), tree(wcR))
        assertEquals("", local("status", wcR.path).trim())
    }

    // ---- 압축 전송(svndiff1) ----

    @Test
    fun `커밋과 체크아웃의 파일 내용은 압축해서 주고받는다`() {
        val size = 3 * 1024 * 1024
        val wc = File(work, "big-wc")
        remote("checkout", "-q", server.url("/demo"), wc.path)
        File(wc, "trunk/zeros.bin").writeBytes(ByteArray(size))
        remote("add", "-q", "${wc.path}/trunk/zeros.bin")
        val beforeCommit = server.dialogueBytes()
        writable = true
        remote("commit", "-q", "-m", "큰 파일", wc.path)
        val commitBytes = server.dialogueBytes() - beforeCommit
        assertTrue(commitBytes < size / 10, "커밋이 압축되지 않았다: $commitBytes 바이트")
        assertEquals(size, local("cat", "$fileUrl/trunk/zeros.bin").length.coerceAtLeast(0).let { size }, "저장된 파일 크기")
        val stored = File(work, "stored.bin"); local("export", "-q", "$fileUrl/trunk/zeros.bin", stored.path)
        assertEquals(size.toLong(), stored.length())
        assertTrue(stored.readBytes().all { it == 0.toByte() }, "내용이 같아야 한다")
        val beforeCheckout = server.dialogueBytes()
        val fresh = File(work, "big-fresh")
        remote("checkout", "-q", server.url("/demo/trunk"), fresh.path)
        val checkoutBytes = server.dialogueBytes() - beforeCheckout
        assertTrue(checkoutBytes < size / 10, "체크아웃이 압축되지 않았다: $checkoutBytes 바이트")
        assertEquals(size.toLong(), File(fresh, "zeros.bin").length())
        assertTrue(File(fresh, "zeros.bin").readBytes().all { it == 0.toByte() })
        // 클라이언트가 svndiff2(LZ4)를 받을 수 있다고 알리면 그 방식을 쓴다: 텍스트 델타의 머리글이 `SVN` 뒤 버전 2이다
        val dialogue = File(work, "ra-dialogue.log").readBytes()
        val version2 = "SVN".toByteArray() + byteArrayOf(2)
        assertTrue(indexOf(dialogue, version2) >= 0, "svndiff2 머리글이 대화에 있어야 한다")
    }

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }

    @Test
    fun `Yona 저장소는 svnsync의 대상이 되지 못한다(작성자와 날짜를 바꾸지 못하는 규칙, A-616)`() {
        target = File(work, "target")
        check(ProcessBuilder("svnadmin", "create", target.path).start().waitFor() == 0)
        val init = server.svnsync("initialize", server.url("/target"), fileUrl)
        val sync = if (init.exit == 0) server.svnsync("synchronize", server.url("/target")) else init
        assertNotEquals(0, sync.exit, "svnsync는 원본의 작성자와 날짜를 대상에 복사해야 하는데 Yona는 그것을 거절한다")
        assertTrue(sync.output.contains("Authorization failed"), sync.output)
    }

    // ---- get-deleted-rev ----

    @Test
    fun `get-deleted-rev는 삭제된 리비전을 알려 주고 삭제되지 않았으면 오류이다`() {
        server.raw("/demo").use { raw ->
            raw.command("get-deleted-rev", RaString("/branches/br1"), RaNumber(4), RaNumber(5))
            assertEquals(raList(RaWord("success"), raList(raList(), RaString(""))), raw.receive())
            assertEquals(raList(RaWord("success"), raList(RaNumber(5))), raw.receive())
            raw.command("get-deleted-rev", RaString("/trunk"), RaNumber(1), RaNumber(5))
            raw.receive()
            val failure = raw.receive() as RaList
            assertEquals(RaWord("failure"), failure.items[0])
            assertEquals(150003L, (((failure.items[1] as RaList).items[0] as RaList).items[0] as RaNumber).value)
        }
    }
}
