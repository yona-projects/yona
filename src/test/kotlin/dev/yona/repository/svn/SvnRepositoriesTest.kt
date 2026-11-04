package dev.yona.repository.svn

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SVNKit으로 Subversion 저장소를 만들고 읽는 부분을 검증한다. 원본: `playRepository.SVNRepository`
 * (`app/playRepository/SVNRepository.java`)와 `SvnCommit`.
 */
class SvnRepositoriesTest {

    private lateinit var root: File
    private lateinit var svn: SvnRepositories

    @BeforeEach
    fun setUp() {
        root = Files.createTempDirectory("yona-svn").toFile()
        svn = SvnRepositories(DataDirectory(YonaProperties(dataDir = root.path)))
    }

    @AfterEach
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun commit(message: String, files: Map<String, String> = emptyMap(), author: String = "alice", deletes: List<String> = emptyList()) =
        SvnFixtures.commit(svn.directoryOf("own", "proj"), author, message, files.mapValues { it.value.toByteArray() }, deletes)

    // --- 저장소 ---

    @Test
    fun `저장소는 데이터 디렉터리 아래 repo svn 소유자 이름에 만든다`() {
        assertEquals(File(File(root, "repo/svn"), "own/proj"), svn.directoryOf("own", "proj"))
        svn.create("own", "proj")
        assertTrue(File(svn.directoryOf("own", "proj"), "format").isFile)
    }

    @Test
    fun `새 저장소는 비어 있고 커밋이 있으면 비어 있지 않다`() {
        svn.create("own", "proj")
        assertTrue(svn.isEmpty("own", "proj"))
        assertEquals(0L, svn.latestRevision("own", "proj"))
        commit("첫 커밋", mapOf("a.txt" to "a"))
        assertFalse(svn.isEmpty("own", "proj"))
        assertEquals(1L, svn.latestRevision("own", "proj"))
    }

    @Test
    fun `저장소를 지우고 다른 위치로 옮긴다`() {
        svn.create("own", "proj")
        assertTrue(svn.move("own", "proj", "own", "renamed"))
        assertFalse(svn.directoryOf("own", "proj").exists())
        assertTrue(svn.directoryOf("own", "renamed").isDirectory)
        svn.delete("own", "renamed")
        assertFalse(svn.directoryOf("own", "renamed").exists())
        // 없는 저장소를 옮기거나 지워도 성공이다.
        assertTrue(svn.move("none", "none", "x", "y"))
        svn.delete("none", "none")
    }

    @Test
    fun `브랜치는 HEAD 하나뿐이고 기본 브랜치도 HEAD이다`() {
        assertEquals(listOf("HEAD"), svn.refNames())
        assertEquals("HEAD", svn.defaultBranch())
    }

    // --- 파일 ---

    @Test
    fun `파일 내용을 HEAD나 리비전으로 읽는다`() {
        svn.create("own", "proj")
        commit("one", mapOf("dir/a.txt" to "v1"))
        commit("two", mapOf("dir/a.txt" to "v2"))
        assertEquals("v2", String(assertNotNull(svn.rawFile("own", "proj", "HEAD", "dir/a.txt"))))
        assertEquals("v1", String(assertNotNull(svn.rawFile("own", "proj", "1", "dir/a.txt"))))
        assertNull(svn.rawFile("own", "proj", "HEAD", "dir"))
        assertNull(svn.rawFile("own", "proj", "HEAD", "missing.txt"))
    }

    @Test
    fun `경로가 파일인지 폴더인지 알려 준다`() {
        svn.create("own", "proj")
        commit("one", mapOf("dir/a.txt" to "x"))
        assertTrue(svn.isFile("own", "proj", "dir/a.txt"))
        assertFalse(svn.isFile("own", "proj", "dir"))
        assertFalse(svn.isFile("own", "proj", "none"))
        assertTrue(svn.isFile("own", "proj", "dir/a.txt", "1"))
    }

    // --- 이력 ---

    @Test
    fun `이력은 최신부터 쪽 단위로 돌려주며 쪽 경계의 리비전이 겹치지 않는다 (R-62-1)`() {
        svn.create("own", "proj")
        (1..5).forEach { commit("메시지 $it\n두 번째 줄", mapOf("f$it.txt" to "x"), author = "u$it") }
        val first = svn.history("own", "proj", page = 0, limit = 2, path = null)
        // v1.6은 시작과 끝 리비전을 모두 포함해 한 쪽에 limit + 1개가 나오고 다음 쪽의 첫 리비전과 겹쳤다(R-62-1). 한 쪽은 limit개이다
        assertEquals(listOf("5", "4"), first.map { it.id })
        assertEquals(listOf("3", "2"), svn.history("own", "proj", 1, 2, null).map { it.id })
        assertEquals(listOf("1"), svn.history("own", "proj", 2, 2, null).map { it.id })
        assertEquals(emptyList(), svn.history("own", "proj", 3, 2, null))
        val top = first.first()
        assertEquals("u5", top.authorName)
        assertEquals("메시지 5", top.shortMessage)
        assertTrue(top.message!!.startsWith("메시지 5"))
        assertEquals(1, top.parentCount)
        assertEquals(0, svn.history("own", "proj", 2, 2, null).single().parentCount)
        assertNotNull(top.date)
    }

    @Test
    fun `이력은 경로로 좁힐 수 있다`() {
        svn.create("own", "proj")
        commit("a", mapOf("a/x.txt" to "1"))
        commit("b", mapOf("b/y.txt" to "1"))
        commit("a2", mapOf("a/x.txt" to "2"))
        assertEquals(listOf("3", "1"), svn.history("own", "proj", 0, 10, "a").map { it.id })
    }

    @Test
    fun `리비전 하나의 커밋을 읽는다`() {
        svn.create("own", "proj")
        commit("hello", mapOf("a.txt" to "x"), author = "carol")
        val found = assertNotNull(svn.commit("own", "proj", 1))
        assertEquals("carol", found.authorName)
        assertEquals("hello", found.message)
        assertNull(svn.commit("own", "proj", 9))
    }

    // --- 패치 ---

    @Test
    fun `두 리비전 사이의 차이를 통합 형식으로 돌려준다`() {
        svn.create("own", "proj")
        commit("one", mapOf("a.txt" to "line1\n"))
        commit("two", mapOf("a.txt" to "line1\nline2\n"))
        val patch = svn.patch("own", "proj", 1, 2)
        assertTrue(patch.contains("--- a.txt"), patch)
        assertTrue(patch.contains("+line2"), patch)
        assertEquals(patch, svn.patch("own", "proj", "2"))
    }

    @Test
    fun `없는 저장소는 예외이다`() {
        assertFailsWith<Exception> { svn.latestRevision("none", "none") }
    }

    @Test
    fun `shortMessage는 Java trim처럼 U+0020 이하의 문자만 앞뒤에서 지운다`() {
        // v1.6 SvnCommit.getShortMessage: message.trim().split("\n")[0]. Kotlin trim()과 달리 NBSP와 전각 공백은 지우지 않는다.
        fun short(message: String?) = SvnCommit("1", "u", message, null).shortMessage
        assertEquals("\u00a0subject", short("\u00a0subject\nbody"))
        assertEquals("subject", short("\u0001subject\nbody"))
        assertEquals("\u3000title\r", short("\u3000title\r\nbody"))
        assertEquals("x", short("\u0000\u0008x\ny"))
        assertEquals("\u00a0em", short("\u00a0em\nz"))
        assertEquals("fix bug ", short("  \tfix bug \n\n  body"))
        assertEquals("", short("   "))
        assertEquals("", short(""))
        assertEquals("", short(null))
        assertEquals("\u00a0", short("\u00a0"))
    }
}
