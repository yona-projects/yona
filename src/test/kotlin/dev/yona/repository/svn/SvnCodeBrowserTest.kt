package dev.yona.repository.svn

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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Subversion 프로젝트의 코드 브라우저 데이터(폴더와 파일 메타데이터, 원본 파일, 내려받기)를 검증한다.
 * 원본: `SVNRepository.getMetaDataFromPath`, `fileAsJson`, `getRawFile`, `getArchive`, `RepositoryService.getMetaDataFromAncestorDirectories`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-code-browser", "yona.code-browser-max-file-size=100"])
class SvnCodeBrowserTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: CodeBrowserService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var svn: SvnRepositories
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository

    private var sequence = 0

    private fun project(): Project {
        val p = projects.save(Project(owner = "svo${++sequence}", name = "svp$sequence", vcs = "Subversion", projectScope = ProjectScope.PUBLIC))
        repositories.createRepository(p)
        return p
    }

    private fun user(loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = "$loginId@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun commit(p: Project, author: String, message: String, files: Map<String, String> = emptyMap(), deletes: List<String> = emptyList()) =
        SvnFixtures.commit(svn.directoryOf(p.owner!!, p.name!!), author, message, files.mapValues { it.value.toByteArray() }, deletes)

    @Suppress("UNCHECKED_CAST")
    private fun entries(info: Map<String, Any?>) = info["data"] as Map<String, Map<String, Any?>>

    @Test
    fun `비어 있는 저장소는 비었다고 알린다`() {
        val p = project()
        assertTrue(service.isEmpty(p))
        commit(p, "dev", "처음", mapOf("a.txt" to "1"))
        assertFalse(service.isEmpty(p))
    }

    @Test
    fun `폴더는 항목마다 마지막 커밋 정보를 담는다`() {
        val p = project()
        val dev = user("svdev")
        commit(p, "svdev", "처음\n둘째 줄", mapOf("a.txt" to "hello", "dir/b.txt" to "x"))
        val root = assertNotNull(service.metaData(p, null, ""))
        assertEquals("folder", root["type"])
        val data = entries(root)
        assertEquals(setOf("a.txt", "dir"), data.keys)
        val a = data.getValue("a.txt")
        assertEquals("file", a["type"]); assertEquals("folder", data.getValue("dir")["type"])
        assertEquals("svdev", a["author"]); assertEquals("svdev", a["commiter"])
        assertEquals("이름svdev", a["userName"]); assertEquals("svdev", a["userLoginId"])
        assertEquals(1L, a["commitId"])
        assertEquals("/${p.owner}/${p.name}/commit/1", a["commitUrl"])
        assertEquals(5L, a["size"])
        assertEquals("처음\n둘째 줄", a["commitMessage"]); assertEquals("처음\n둘째 줄", a["msg"])
        assertEquals(a["createdDate"], a["commitDate"])
        assertTrue((a["createdDate"] as Long) > 0)
        assertNotNull(dev.id)
    }

    @Test
    fun `사용자가 아닌 작성자는 이름 없는 사용자로 보인다`() {
        val p = project()
        commit(p, "stranger", "msg", mapOf("a.txt" to "1"))
        val a = entries(service.metaData(p, null, "")!!).getValue("a.txt")
        assertEquals("", a["userLoginId"])
        assertEquals("stranger", a["author"])
        assertTrue((a["userName"] as String).isNotEmpty())
    }

    @Test
    fun `하위 폴더 목록과 빈 폴더를 읽는다`() {
        val p = project()
        commit(p, "dev", "처음", mapOf("dir/sub/c.txt" to "c", "dir/d.txt" to "d"))
        assertEquals(setOf("sub", "d.txt"), entries(service.metaData(p, null, "dir")!!).keys)
        assertEquals(setOf("c.txt"), entries(service.metaData(p, "HEAD", "dir/sub")!!).keys)
        assertNull(service.metaData(p, null, "dir/none"))
    }

    @Test
    fun `파일은 내용과 판정을 담는다`() {
        val p = project()
        commit(p, "dev", "처음", mapOf("a.txt" to "안녕\n", "b.bin" to String(ByteArray(10) { 0 })))
        val text = assertNotNull(service.metaData(p, null, "a.txt"))
        assertEquals("file", text["type"])
        assertEquals("1", text["revisionNo"])
        assertEquals("안녕\n", text["data"])
        assertEquals(false, text["isBinary"])
        assertEquals(7L, text["size"])
        assertEquals("dev", text["author"])
        assertTrue((text["mimeType"] as String).startsWith("text/"))
        val binary = service.metaData(p, null, "b.bin")!!
        assertEquals(true, binary["isBinary"])
        assertNull(binary["data"])
    }

    @Test
    fun `보여 줄 수 있는 크기를 넘는 파일은 바이너리로 본다`() {
        val p = project()
        commit(p, "dev", "처음", mapOf("big.txt" to "x".repeat(200)))
        val big = service.metaData(p, null, "big.txt")!!
        assertEquals(true, big["isBinary"])
        assertEquals("application/octet-stream", big["mimeType"])
        assertNull(big["data"])
        assertEquals(200L, big["size"])
    }

    @Test
    fun `폴더 정보는 항상 최신 리비전에서 읽는다`() {
        val p = project()
        commit(p, "dev", "하나", mapOf("a.txt" to "1"))
        commit(p, "dev", "둘", mapOf("b.txt" to "2"))
        // v1.6은 리비전을 알려 줘도 목록은 최신(HEAD)에서 읽는다(R-62).
        assertEquals(setOf("a.txt", "b.txt"), entries(service.metaData(p, "1", "")!!).keys)
    }

    @Test
    fun `상위 폴더를 차례로 모으되 중간 폴더를 건너뛰지 않는다`() {
        val p = project()
        commit(p, "dev", "처음", mapOf("one/two/c.txt" to "c"))
        val chain = assertNotNull(service.ancestors(p, "HEAD", "one/two"))
        assertEquals(listOf("", "one", "one/two"), chain.map { it["path"] })
        assertNull(service.ancestors(p, "HEAD", "one/nope"))
    }

    @Test
    fun `원본 파일을 리비전으로 읽는다`() {
        val p = project()
        commit(p, "dev", "하나", mapOf("a.txt" to "v1"))
        commit(p, "dev", "둘", mapOf("a.txt" to "v2"))
        assertEquals("v2", String(service.rawFile(p, "HEAD", "a.txt")!!))
        assertEquals("v1", String(service.rawFile(p, "1", "a.txt")!!))
        assertNull(service.rawFile(p, "HEAD", "none.txt"))
        assertNull(service.rawFile(p, "abc", "a.txt"))
    }

    @Test
    fun `내려받기는 아무것도 쓰지 않는다`() {
        val p = project()
        commit(p, "dev", "처음", mapOf("a.txt" to "1"))
        val out = ByteArrayOutputStream()
        service.archive(p, "HEAD", out)
        assertEquals(0, out.size())
    }
}
