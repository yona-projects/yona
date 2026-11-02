package dev.yona.domain.lfs

import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectRepository
import dev.yona.repository.DataDirectory
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 프로젝트의 수명 주기와 LFS 객체(X-8 단계 2, `decisions.md` D-79 3, 4번): 개명과 이전은 저장소와 함께 LFS 디렉터리를 옮기고, 삭제는 지우며,
 * 포크는 원본의 객체를 읽기로 빌려 쓰고, 원본을 지우면 포크가 빌려 쓰던 객체를 포크로 옮겨 준다.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-lifecycle"])
class LfsLifecycleTest : AbstractIntegrationTest() {

    @Autowired private lateinit var storage: LfsStorage
    @Autowired private lateinit var lfs: LfsService
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var dataDirectory: DataDirectory

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-lifecycle").deleteRecursively() }

    private fun project(origin: Project? = null): Project =
        projects.save(Project(owner = "lc$stamp", name = "p${++n}", vcs = "GIT", originalProject = origin, createdDate = java.util.Date()))

    private fun oidOf(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun put(p: Project, text: String): String { val data = text.toByteArray(); val oid = oidOf(data); storage.store(p, oid, data.inputStream(), Long.MAX_VALUE); return oid }

    @Test
    fun `개명하면 저장소와 함께 LFS 객체도 새 위치로 옮겨진다`() {
        val p = project(); repositories.createRepository(p); val oid = put(p, "개명 전")
        assertTrue(repositories.moveRepository(p, p.owner!!, "renamed${n}"))
        val moved = Project(owner = p.owner, name = "renamed${n}", vcs = "GIT")
        assertTrue(storage.exists(moved, oid)); assertFalse(storage.exists(p, oid))
    }

    @Test
    fun `소유자가 바뀌어도 LFS 객체가 따라간다`() {
        val p = project(); repositories.createRepository(p); val oid = put(p, "이전 전")
        assertTrue(repositories.moveRepository(p, "other$stamp", p.name!!))
        assertTrue(storage.exists(Project(owner = "other$stamp", name = p.name, vcs = "GIT"), oid)); assertFalse(storage.exists(p, oid))
    }

    @Test
    fun `LFS 객체가 없는 프로젝트의 개명은 영향이 없다`() {
        val p = project(); repositories.createRepository(p)
        assertTrue(repositories.moveRepository(p, p.owner!!, "plain${n}"))
    }

    @Test
    fun `프로젝트를 지우면 LFS 디렉터리도 지워진다`() {
        val p = project(); val oid = put(p, "지울 객체")
        repositories.deleteLfs(p)
        assertFalse(storage.exists(p, oid)); assertFalse(storage.directoryOf(p).exists())
    }

    @Test
    fun `포크는 원본의 객체를 읽기로 빌려 쓰고 올릴 필요도 없다`() {
        val origin = project(); val fork = project(origin); val grand = project(fork); val oid = put(origin, "원본에만 있는 내용"); val size = "원본에만 있는 내용".toByteArray().size.toLong()
        for (p in listOf(fork, grand)) {
            assertEquals(origin.id, lfs.locate(p, oid)?.id)
            assertTrue(lfs.plan(p, LfsService.DOWNLOAD, listOf(LfsObjectRequest(oid, size))).single() is LfsPlan.Download)
            assertTrue(lfs.plan(p, LfsService.UPLOAD, listOf(LfsObjectRequest(oid, size))).single() is LfsPlan.Present)
        }
        assertFalse(storage.exists(fork, oid))
        assertNull(lfs.locate(project(), oid))
    }

    @Test
    fun `원본을 지우면 포크가 빌려 쓰던 객체를 포크가 가진다`() {
        val origin = project(); val fork = project(origin); val other = project(); val oid = put(origin, "물려줄 내용"); val unrelated = put(other, "무관한 내용")
        deletion.delete(origin); repositories.deleteLfs(origin)
        assertFalse(storage.exists(origin, oid)); assertTrue(storage.exists(fork, oid)); assertEquals("물려줄 내용".toByteArray().size.toLong(), storage.sizeOf(fork, oid))
        assertTrue(storage.exists(other, unrelated)); assertNotNull(lfs.locate(fork, oid))
    }
}
