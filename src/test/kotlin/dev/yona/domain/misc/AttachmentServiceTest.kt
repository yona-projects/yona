package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 첨부 파일의 저장, 중복 처리, 삭제를 검증한다.
 *
 * 원본: `app/models/Attachment.java:76-135, 205-372, 547-596`, `app/utils/FileUtil.java:105-135`.
 */
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-attachments"])
class AttachmentServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: AttachmentService
    @Autowired private lateinit var attachments: AttachmentRepository

    private val uploads = File("build/tmp/it-attachments/uploads")
    private var work = File("build/tmp/it-attachments-work").apply { mkdirs() }

    @AfterEach
    fun clean() {
        File("build/tmp/it-attachments").deleteRecursively()
        work.deleteRecursively()
    }

    private fun file(name: String, content: ByteArray): File = File(work, name).apply { parentFile.mkdirs(); writeBytes(content) }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)

    @Test
    fun `파일을 해시 이름으로 업로드 디렉터리에 옮기고 정보를 저장한다`() {
        val content = "안녕".toByteArray()
        val result = service.store(file("a.txt", content), "a.txt", ResourceType.PROJECT, 7L, "alice")
        assertTrue(result.created)
        val attachment = result.attachment
        assertEquals(sha256(content), attachment.hash)
        assertEquals("a.txt", attachment.name)
        assertEquals(ResourceType.PROJECT, attachment.containerType)
        assertEquals(7L, attachment.containerId)
        assertEquals(content.size.toLong(), attachment.size)
        assertEquals("alice", attachment.ownerLoginId)
        assertNotNull(attachment.createdDate)
        assertTrue(File(uploads, sha256(content)).isFile)
        assertFalse(File(work, "a.txt").exists(), "원본 파일은 옮겨진다")
    }

    @Test
    fun `같은 이름과 내용을 같은 컨테이너에 다시 올리면 새로 만들지 않는다`() {
        val first = service.store(file("a.txt", "x".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "alice")
        val second = service.store(file("a.txt", "x".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "alice")
        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.attachment.id, second.attachment.id)
        assertEquals(1, attachments.findAll().size)
    }

    @Test
    fun `내용이 같고 이름이 다르면 행은 따로 만들고 파일은 하나이다`() {
        val a = service.store(file("a.txt", "same".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "alice")
        val b = service.store(file("b.txt", "same".toByteArray()), "b.txt", ResourceType.PROJECT, 7L, "alice")
        assertNotEquals(a.attachment.id, b.attachment.id)
        assertEquals(1, uploads.listFiles()!!.size)
    }

    @Test
    fun `이름이 없으면 시각과 확장자로 이름을 만든다`() {
        val result = service.store(file("raw", png), null, ResourceType.USER, 3L, "alice")
        assertTrue(Regex("\\d+\\.png").matches(result.attachment.name!!), result.attachment.name)
    }

    @Test
    fun `파일 종류를 이름과 내용으로 감지한다`() {
        assertEquals("image/png", service.store(file("p.png", png), "p.png", ResourceType.PROJECT, 1L, "a").attachment.mimeType)
        assertEquals("application/pdf", service.store(file("d.pdf", "%PDF-1.4 test".toByteArray()), "d.pdf", ResourceType.PROJECT, 1L, "a").attachment.mimeType)
    }

    @Test
    fun `파일 종류는 파일 이름을 힌트로 쓰지 않고 내용만으로 감지한다 (v1_6 동작)`() {
        // v1.6 Attachment.save는 인자 fileName이 아니라 아직 비어 있는 필드 name으로 감지해서 이름 힌트가 없다 (R-86).
        val mime = service.store(file("x.css", "body { color: red }".toByteArray()), "x.css", ResourceType.PROJECT, 1L, "a").attachment.mimeType!!
        assertTrue(mime.startsWith("text/plain"), mime)
    }

    @Test
    fun `텍스트 파일은 문자셋을 덧붙인다`() {
        val mime = service.store(file("t.txt", "hello world".toByteArray()), "t.txt", ResourceType.PROJECT, 1L, "a").attachment.mimeType!!
        assertTrue(mime.startsWith("text/plain; charset="), mime)
    }

    @Test
    fun `스트림으로도 저장한다`() {
        val result = service.store("스트림".toByteArray().inputStream(), "s.txt", ResourceType.USER, 3L, "alice")
        assertTrue(result.created)
        assertEquals(sha256("스트림".toByteArray()), result.attachment.hash)
    }

    @Test
    fun `컨테이너의 첨부를 번호 순으로 찾는다`() {
        service.store(file("1.txt", "1".toByteArray()), "1.txt", ResourceType.PROJECT, 9L, "a")
        service.store(file("2.txt", "2".toByteArray()), "2.txt", ResourceType.PROJECT, 9L, "a")
        service.store(file("3.txt", "3".toByteArray()), "3.txt", ResourceType.PROJECT, 10L, "a")
        assertEquals(listOf("1.txt", "2.txt"), service.findByContainer(ResourceType.PROJECT, 9L).map { it.name })
    }

    @Test
    fun `첨부를 지우면 다른 곳에서 쓰지 않는 파일도 함께 지운다`() {
        val stored = service.store(file("a.txt", "only".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "a").attachment
        service.delete(stored)
        assertTrue(attachments.findAll().isEmpty())
        assertFalse(File(uploads, stored.hash!!).exists())
    }

    @Test
    fun `같은 내용을 쓰는 다른 첨부가 있으면 파일을 남긴다`() {
        val a = service.store(file("a.txt", "shared".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "a").attachment
        service.store(file("b.txt", "shared".toByteArray()), "b.txt", ResourceType.PROJECT, 8L, "a")
        service.delete(a)
        assertTrue(File(uploads, a.hash!!).isFile)
        assertEquals(1, attachments.findAll().size)
    }

    @Test
    fun `컨테이너의 첨부를 모두 지운다`() {
        service.store(file("1.txt", "1".toByteArray()), "1.txt", ResourceType.PROJECT, 9L, "a")
        service.store(file("2.txt", "2".toByteArray()), "2.txt", ResourceType.PROJECT, 9L, "a")
        val other = service.store(file("3.txt", "3".toByteArray()), "3.txt", ResourceType.PROJECT, 10L, "a").attachment
        service.deleteAll(ResourceType.PROJECT, 9L)
        assertEquals(listOf(other.id), attachments.findAll().map { it.id })
    }

    @Test
    fun `해시로 첨부와 파일이 있는지 확인한다`() {
        val stored = service.store(file("a.txt", "check".toByteArray()), "a.txt", ResourceType.PROJECT, 7L, "a").attachment
        assertTrue(service.exists(stored.hash!!))
        assertTrue(service.fileExists(stored.hash!!))
        assertFalse(service.exists("nothing"))
        assertFalse(service.fileExists("nothing"))
    }
}
