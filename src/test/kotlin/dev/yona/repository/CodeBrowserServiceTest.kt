package dev.yona.repository

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 코드 브라우저가 쓰는 폴더와 파일의 메타데이터, 원본 파일, 압축 내려받기를 검증한다.
 *
 * 원본: `GitRepository.getMetaDataFromPath`, `treeAsJson`, `fileAsJson`, `ObjectFinder`, `extendPath`, `isIntermediateFolder`,
 * `getRawFile`, `getArchive`, `RepositoryService.getMetaDataFromAncestorDirectories`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-code-browser"])
class CodeBrowserServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: CodeBrowserService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    private fun project(): Project {
        val p = projects.save(Project(owner = "cbo${++sequence}", name = "cbp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        return p
    }

    private fun user(email: String, loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = email, state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun bytes(text: String) = text.toByteArray()

    @Test
    fun `폴더는 항목마다 마지막으로 바꾼 커밋 정보를 담는다`() {
        val p = project()
        val author = user("dev@example.com", "dev")
        GitTestSupport.pushCommits(repositories, p,
            TestCommit("처음", mapOf("a.txt" to bytes("1"), "dir/b.txt" to bytes("2")), authorName = "개발자", authorEmail = author.email!!),
            TestCommit("a만 수정\n\n둘째 문단", mapOf("a.txt" to bytes("11")), authorEmail = author.email!!))
        val root = service.metaData(p, null, "")!!
        assertEquals("folder", root["type"])
        @Suppress("UNCHECKED_CAST") val data = root["data"] as Map<String, Map<String, Any?>>
        assertEquals(listOf("a.txt", "dir"), data.keys.toList())
        assertEquals("file", data["a.txt"]!!["type"]); assertEquals("folder", data["dir"]!!["type"])
        assertEquals("a만 수정", data["a.txt"]!!["msg"]); assertEquals("처음", data["dir"]!!["msg"])
        assertEquals("dev", data["a.txt"]!!["userLoginId"]); assertEquals("이름dev", data["a.txt"]!!["userName"]); assertEquals("개발자", data["dir"]!!["author"])
        assertEquals(1_700_000_000_000L + 86_400_000L, data["a.txt"]!!["createdDate"])
        assertEquals(7, (data["a.txt"]!!["commitId"] as String).length)
        assertTrue((data["a.txt"]!!["commitUrl"] as String).startsWith("/${p.owner}/${p.name}/commit/"))
        assertNotNull(data["a.txt"]!!["avatar"])
    }

    @Test
    fun `알 수 없는 작성자는 사용자 없음으로 나온다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to bytes("1")), authorEmail = "stranger@example.com"))
        @Suppress("UNCHECKED_CAST") val data = service.metaData(p, null, "")!!["data"] as Map<String, Map<String, Any?>>
        assertEquals("", data["a.txt"]!!["userLoginId"])
        assertNotNull(data["a.txt"]!!["userName"])
    }

    @Test
    fun `비어 있는 중간 폴더는 건너뛰어 합친 경로로 나온다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("one/two/c.txt" to bytes("c"), "z.txt" to bytes("z"))))
        @Suppress("UNCHECKED_CAST") val data = service.metaData(p, null, "")!!["data"] as Map<String, Map<String, Any?>>
        assertEquals(listOf("one/two", "z.txt"), data.keys.toList())
    }

    @Test
    fun `파일은 내용과 종류, 크기를 담고 이진 파일은 내용이 없다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("note.txt" to bytes("안녕\n"), "bin.dat" to byteArrayOf(0, 1, 2, 3, 0, 5), "pic.png" to byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13))))
        val text = service.metaData(p, null, "note.txt")!!
        assertEquals("file", text["type"]); assertEquals(false, text["isBinary"]); assertEquals("안녕\n", text["data"]); assertEquals(7L, text["size"])
        assertEquals("처음", text["msg"]); assertEquals("tester", text["author"]); assertEquals("text/plain", text["mimeType"])
        val binary = service.metaData(p, null, "bin.dat")!!
        assertEquals(true, binary["isBinary"]); assertNull(binary["data"])
        assertEquals("image/png", service.metaData(p, null, "pic.png")!!["mimeType"])
    }

    @Test
    fun `보기 한도보다 큰 텍스트 파일은 내용을 싣지 않는다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("big.txt" to ByteArray(1024 * 1024 + 10) { 'a'.code.toByte() })))
        val big = service.metaData(p, null, "big.txt")!!
        assertEquals(false, big["isBinary"]); assertNull(big["data"]); assertEquals(1024L * 1024 + 10, big["size"])
    }

    @Test
    fun `없는 경로와 브랜치는 null이고 빈 저장소도 null이다`() {
        val p = project()
        assertNull(service.metaData(p, null, ""))
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to bytes("1"))))
        assertNull(service.metaData(p, "nope", ""))
        assertNull(service.metaData(p, null, "missing/dir"))
    }

    @Test
    fun `브랜치를 정하면 그 브랜치의 내용이다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to bytes("1"))))
        GitTestSupport.pushCommits(repositories, p, TestCommit("다른 브랜치", mapOf("only-here.txt" to bytes("x"))), branch = "feature")
        @Suppress("UNCHECKED_CAST") val data = service.metaData(p, "feature", "")!!["data"] as Map<String, Any?>
        assertTrue("only-here.txt" in data.keys)
    }

    @Test
    fun `상위 폴더의 메타데이터를 차례로 모으되 폴더 하나만 담은 중간 폴더(a와 b 사이)는 건너뛴다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a/b/c/file.txt" to bytes("1"), "a/b/c/other.txt" to bytes("2"), "a/x.txt" to bytes("3"))))
        val chain = service.ancestors(p, "master", "a/b/c/file.txt")!!
        assertEquals(listOf("", "a", "a/b/c", "a/b/c/file.txt"), chain.map { it["path"] })
        assertEquals(listOf("folder", "folder", "folder", "file"), chain.map { it["type"] })
        assertNull(service.ancestors(p, "master", "a/nope"))
    }

    @Test
    fun `원본 파일 내용을 돌려주고 폴더나 없는 파일은 null이다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("dir/a.txt" to bytes("hello"))))
        assertEquals("hello", String(service.rawFile(p, "master", "dir/a.txt")!!))
        assertNull(service.rawFile(p, "master", "dir"))
        assertNull(service.rawFile(p, "master", "nope.txt"))
        assertNull(service.rawFile(p, "nobranch", "dir/a.txt"))
    }

    @Test
    fun `브랜치를 zip으로 내려받는다`() {
        val p = project()
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("dir/a.txt" to bytes("hello"), "b.txt" to bytes("b"))))
        val out = java.io.ByteArrayOutputStream()
        service.archive(p, "master", out)
        val names = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }
        assertTrue("dir/a.txt" in names && "b.txt" in names, names.toString())
    }

    @Test
    fun `저장소가 비어 있는지 알려 준다`() {
        val p = project()
        assertTrue(service.isEmpty(p))
        GitTestSupport.pushCommits(repositories, p, TestCommit("처음", mapOf("a.txt" to bytes("1"))))
        assertEquals(false, service.isEmpty(p))
    }
}
