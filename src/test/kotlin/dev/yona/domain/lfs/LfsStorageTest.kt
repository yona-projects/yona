package dev.yona.domain.lfs

import dev.yona.domain.project.Project
import dev.yona.repository.DataDirectory
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LFS 객체 저장소(X-8 단계 1, `decisions.md` D-79)를 검증한다: 프로젝트별 디렉터리 `repo/lfs/<소유자>/<이름>/objects/ab/cd/<oid>`에 두고,
 * 올릴 때 SHA-256과 크기를 확인하며, 이미 있는 객체는 다시 쓰지 않고, 잘못된 값은 임시 파일을 남기지 않는다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-lfs-storage"])
class LfsStorageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var storage: LfsStorage
    @Autowired private lateinit var dataDirectory: DataDirectory

    private val project = Project(id = 1, owner = "acme", name = "assets", vcs = "GIT")

    @AfterEach
    fun clean() { File("build/tmp/it-lfs-storage").deleteRecursively() }

    private fun oidOf(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `객체는 프로젝트별 디렉터리에 해시 앞 두 자리씩 나눠 둔다`() {
        val data = "hello lfs".toByteArray(); val oid = oidOf(data)
        assertIs<LfsStorage.StoreResult.Stored>(storage.store(project, oid, ByteArrayInputStream(data), Long.MAX_VALUE))
        val expected = File(File(dataDirectory.root(), "repo/lfs"), "acme/assets/objects/${oid.substring(0, 2)}/${oid.substring(2, 4)}/$oid")
        assertTrue(expected.isFile); assertEquals(data.size.toLong(), expected.length()); assertEquals(data.size.toLong(), storage.sizeOf(project, oid))
        assertTrue(storage.exists(project, oid)); assertEquals(expected.canonicalPath, storage.objectFile(project, oid).canonicalPath)
    }

    @Test
    fun `내용의 해시가 이름과 다르면 거절하고 아무것도 남기지 않는다`() {
        val oid = oidOf("원래 내용".toByteArray())
        assertIs<LfsStorage.StoreResult.HashMismatch>(storage.store(project, oid, ByteArrayInputStream("다른 내용".toByteArray()), Long.MAX_VALUE))
        assertFalse(storage.exists(project, oid))
        assertEquals(emptyList(), File(dataDirectory.root(), "repo/lfs/acme/assets").walkTopDown().filter { it.isFile }.toList())
    }

    @Test
    fun `한도를 넘는 내용은 읽다가 멈추고 거절한다`() {
        val data = ByteArray(2048) { 7 }
        assertIs<LfsStorage.StoreResult.TooLarge>(storage.store(project, oidOf(data), ByteArrayInputStream(data), 1024))
        assertFalse(storage.exists(project, oidOf(data)))
        assertEquals(emptyList(), File(dataDirectory.root(), "repo/lfs/acme/assets").walkTopDown().filter { it.isFile }.toList())
    }

    @Test
    fun `이미 있는 객체는 다시 쓰지 않고 성공으로 본다`() {
        val data = "same".toByteArray(); val oid = oidOf(data)
        assertTrue(assertIs<LfsStorage.StoreResult.Stored>(storage.store(project, oid, ByteArrayInputStream(data), Long.MAX_VALUE)).created)
        val file = storage.objectFile(project, oid); val modified = file.lastModified().also { Thread.sleep(20) }
        assertFalse(assertIs<LfsStorage.StoreResult.Stored>(storage.store(project, oid, ByteArrayInputStream(data), Long.MAX_VALUE)).created)
        assertEquals(modified, file.lastModified())
    }

    @Test
    fun `oid는 소문자 16진수 64자여야 하고 아니면 경로를 만들지 않는다`() {
        for (bad in listOf("", "abc", "../../etc/passwd", "A".repeat(64), "g".repeat(64), "a".repeat(63), "a".repeat(65))) {
            assertFalse(LfsStorage.validOid(bad), bad)
            assertFailsWith<IllegalArgumentException> { storage.objectFile(project, bad) }
        }
        assertTrue(LfsStorage.validOid("a".repeat(64)))
    }

    @Test
    fun `없는 객체의 크기는 null이고 사용량은 객체 수와 바이트이다`() {
        assertNull(storage.sizeOf(project, "a".repeat(64))); assertEquals(LfsStorage.Usage(0, 0), storage.usage(project))
        val a = "aaa".toByteArray(); val b = "bbbbb".toByteArray()
        storage.store(project, oidOf(a), ByteArrayInputStream(a), Long.MAX_VALUE); storage.store(project, oidOf(b), ByteArrayInputStream(b), Long.MAX_VALUE)
        assertEquals(LfsStorage.Usage(2, 8), storage.usage(project))
    }

    @Test
    fun `프로젝트마다 저장 위치가 달라 서로의 객체를 보지 못한다`() {
        val data = "x".toByteArray(); val oid = oidOf(data); val other = Project(id = 2, owner = "acme", name = "other", vcs = "GIT")
        storage.store(project, oid, ByteArrayInputStream(data), Long.MAX_VALUE)
        assertTrue(storage.exists(project, oid)); assertFalse(storage.exists(other, oid))
    }

    @Test
    fun `GIT 프로젝트만 LFS를 지원한다`() {
        assertTrue(LfsSupport.supports(Project(vcs = "GIT"))); assertFalse(LfsSupport.supports(Project(vcs = "Subversion"))); assertFalse(LfsSupport.supports(Project(vcs = null)))
    }
}
