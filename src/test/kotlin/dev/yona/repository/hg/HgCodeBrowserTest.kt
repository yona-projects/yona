package dev.yona.repository.hg

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.CodeBrowserService
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.HgTestSupport
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mercurial 프로젝트의 코드 보기(폴더와 파일 정보, 상위 폴더, 원본, zip)를 [CodeBrowserService]를 거쳐 실제 `hg`로 만든 저장소에서 확인한다.
 * Git의 [dev.yona.repository.CodeBrowserServiceTest]와 같은 결과 모양이다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-code"])
class HgCodeBrowserTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: CodeBrowserService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    @BeforeEach
    fun requireHg() = assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")

    private fun project(): Project {
        val p = projects.save(Project(owner = "hco${++sequence}", name = "hcp$sequence", vcs = "Mercurial", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        return p
    }

    private fun dir(p: Project): File = hgRepositories.directoryOf(p.owner!!, p.name!!)

    private fun user(email: String, loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = email, state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    @Suppress("UNCHECKED_CAST")
    private fun entries(info: Map<String, Any?>?) = info!!["data"] as Map<String, Map<String, Any?>>

    @Test
    fun `폴더는 항목마다 마지막으로 바꾼 커밋 정보를 담는다`() {
        val p = project()
        val author = user("dev@example.com", "dev")
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "1\n", "dir/b.txt" to "2\n"), "2026-11-01 10:00 +0900", "개발자 <dev@example.com>")
        val c1 = HgTestSupport.commit(dir(p), "a만 수정\n\n둘째 문단", mapOf("a.txt" to "11\n"), "2026-11-02 10:00 +0900", "개발자 <dev@example.com>")
        val root = service.metaData(p, null, "")!!
        assertEquals("folder", root["type"])
        val data = entries(root)
        assertEquals(listOf("a.txt", "dir"), data.keys.toList())
        assertEquals("file", data["a.txt"]!!["type"]); assertEquals("folder", data["dir"]!!["type"])
        assertEquals("a만 수정", data["a.txt"]!!["msg"]); assertEquals("처음", data["dir"]!!["msg"])
        assertEquals(author.loginId, data["a.txt"]!!["userLoginId"]); assertEquals("이름dev", data["a.txt"]!!["userName"]); assertEquals("개발자", data["dir"]!!["author"])
        // 2026-11-02 10:00 +0900
        assertEquals(1793581200L * 1000, data["a.txt"]!!["createdDate"])
        assertEquals(c1.take(12), data["a.txt"]!!["commitId"])
        assertTrue((data["a.txt"]!!["commitUrl"] as String) == "/${p.owner}/${p.name}/commit/${c1.take(12)}", data["a.txt"]!!["commitUrl"].toString())
        assertNotNull(data["a.txt"]!!["avatar"])
    }

    @Test
    fun `알 수 없는 작성자는 사용자 없음으로 나온다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "1\n"), user = "Stranger <stranger@example.com>")
        val data = entries(service.metaData(p, null, ""))
        assertEquals("", data["a.txt"]!!["userLoginId"])
        assertNotNull(data["a.txt"]!!["userName"])
    }

    @Test
    fun `비어 있는 중간 폴더는 건너뛰어 합친 경로로 나온다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("one/two/c.txt" to "c\n", "z.txt" to "z\n"))
        assertEquals(listOf("one/two", "z.txt"), entries(service.metaData(p, null, "")).keys.toList())
    }

    @Test
    fun `하위 폴더의 목록은 그 폴더 안 항목만 담는다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("dir/a.txt" to "a\n", "dir/sub/b.txt" to "b\n", "dir/sub/c.txt" to "c\n", "top.txt" to "t\n"))
        HgTestSupport.commit(dir(p), "sub 수정", mapOf("dir/sub/b.txt" to "b2\n"), "2026-11-02 10:00 +0900")
        val data = entries(service.metaData(p, null, "dir"))
        assertEquals(listOf("a.txt", "sub"), data.keys.toList())
        assertEquals("sub 수정", data["sub"]!!["msg"]); assertEquals("처음", data["a.txt"]!!["msg"])
    }

    @Test
    fun `파일은 내용과 종류, 크기를 담고 이진 파일은 내용이 없다`() {
        val p = project()
        val node = HgTestSupport.commitBytes(dir(p), "처음", mapOf("note.txt" to "안녕\n".toByteArray(), "bin.dat" to byteArrayOf(0, 1, 2, 3, 0, 5), "pic.png" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13)), user = "tester")
        val text = service.metaData(p, null, "note.txt")!!
        assertEquals("file", text["type"]); assertEquals(false, text["isBinary"]); assertEquals("안녕\n", text["data"]); assertEquals(7L, text["size"])
        assertEquals("처음", text["msg"]); assertEquals("tester", text["author"]); assertEquals("text/plain", text["mimeType"])
        // 파일 화면의 커밋 번호는 보고 있는 리비전의 전체 번호이다(Git과 같다)
        assertEquals(node, text["commitId"])
        val binary = service.metaData(p, null, "bin.dat")!!
        assertEquals(true, binary["isBinary"]); assertNull(binary["data"])
        assertEquals("image/png", service.metaData(p, null, "pic.png")!!["mimeType"])
    }

    @Test
    fun `보기 한도보다 큰 텍스트 파일은 내용을 싣지 않는다`() {
        val p = project()
        HgTestSupport.commitBytes(dir(p), "처음", mapOf("big.txt" to ByteArray(1024 * 1024 + 10) { 'a'.code.toByte() }))
        val big = service.metaData(p, null, "big.txt")!!
        assertEquals(false, big["isBinary"]); assertNull(big["data"]); assertEquals(1024L * 1024 + 10, big["size"])
    }

    @Test
    fun `없는 경로와 브랜치는 null이고 빈 저장소도 null이다`() {
        val p = project()
        assertNull(service.metaData(p, null, ""))
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "1\n"))
        assertNull(service.metaData(p, "nope", ""))
        assertNull(service.metaData(p, null, "missing/dir"))
    }

    @Test
    fun `이름표를 정하면 그 이름표의 내용이다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "1\n"))
        HgTestSupport.commit(dir(p), "둘째", mapOf("only-here.txt" to "x\n"), "2026-11-02 10:00 +0900")
        // `-r`로 만들어야 활성 이름표가 되지 않는다(활성 이름표는 다음 커밋을 따라 움직인다)
        HgTestSupport.bookmark(dir(p), "old", "0")
        assertTrue("only-here.txt" !in entries(service.metaData(p, "refs/heads/old", "")).keys)
        assertTrue("only-here.txt" in entries(service.metaData(p, null, "")).keys)
    }

    @Test
    fun `상위 폴더의 메타데이터를 차례로 모으되 폴더 하나만 담은 중간 폴더는 건너뛴다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("a/b/c/file.txt" to "1\n", "a/b/c/other.txt" to "2\n", "a/x.txt" to "3\n"))
        val chain = service.ancestors(p, null, "a/b/c/file.txt")!!
        assertEquals(listOf("", "a", "a/b/c", "a/b/c/file.txt"), chain.map { it["path"] })
        assertEquals(listOf("folder", "folder", "folder", "file"), chain.map { it["type"] })
        assertNull(service.ancestors(p, null, "a/nope"))
    }

    @Test
    fun `원본 파일 내용을 돌려주고 폴더나 없는 파일은 null이다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("dir/a.txt" to "hello"))
        HgTestSupport.bookmark(dir(p), "main")
        assertEquals("hello", String(service.rawFile(p, "refs/heads/main", "dir/a.txt")!!))
        assertEquals("hello", String(service.rawFile(p, "main", "dir/a.txt")!!))
        assertNull(service.rawFile(p, "main", "dir"))
        assertNull(service.rawFile(p, "main", "nope.txt"))
        assertNull(service.rawFile(p, "nobranch", "dir/a.txt"))
    }

    @Test
    fun `이름표를 zip으로 내려받고 항목에 저장소 이름 폴더가 붙지 않는다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("dir/a.txt" to "hello", "b.txt" to "b"))
        HgTestSupport.bookmark(dir(p), "main")
        val out = java.io.ByteArrayOutputStream()
        service.archive(p, "refs/heads/main", out)
        val names = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue("dir/a.txt" in names && "b.txt" in names, names.toString())
        // Mercurial의 기본 압축은 `.hg_archival.txt`를 더한다. Git의 내려받기와 같은 내용만 담는다
        assertTrue(names.none { it.startsWith(".hg") }, names.toString())
    }

    @Test
    fun `풀 수 없는 이름표로 내려받으면 아무것도 쓰지 않는다`() {
        val p = project()
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "a"))
        val out = java.io.ByteArrayOutputStream()
        service.archive(p, "nobranch", out)
        assertEquals(0, out.size())
    }

    @Test
    fun `저장소가 비어 있는지 알려 준다`() {
        val p = project()
        assertTrue(service.isEmpty(p))
        HgTestSupport.commit(dir(p), "처음", mapOf("a.txt" to "1\n"))
        assertEquals(false, service.isEmpty(p))
    }
}
