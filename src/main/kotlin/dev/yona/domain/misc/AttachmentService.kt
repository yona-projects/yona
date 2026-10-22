package dev.yona.domain.misc

import dev.yona.domain.enumeration.ResourceType
import dev.yona.io.CharsetDetection
import dev.yona.repository.DataDirectory
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.metadata.TikaCoreProperties
import org.apache.tika.mime.MimeTypeException
import org.apache.tika.mime.MimeTypes
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.NotDirectoryException
import java.security.MessageDigest
import java.util.Date

/**
 * 첨부 파일을 저장하고 지운다. v1.6 `models.Attachment`의 저장, 삭제, 조회 메서드에 해당한다.
 *
 * 파일 내용은 SHA-256 해시를 이름으로 삼아 업로드 디렉터리(`<데이터 디렉터리>/uploads`)에 한 벌만 두고, 이름과 컨테이너 같은
 * 정보는 DB(`attachment`)에 저장한다. 같은 내용을 여러 컨테이너나 이름으로 올려도 파일은 하나이며, 그 해시를 쓰는 마지막
 * 첨부가 지워질 때 파일도 지운다.
 *
 * v1.6의 컨테이너별 목록 캐시(`AttachmentCache`)는 두지 않는다. 동작은 같고 조회만 매번 DB에서 한다.
 */
@Service
@Transactional
class AttachmentService(
    private val attachments: AttachmentRepository,
    private val dataDirectory: DataDirectory
) {

    /**
     * 저장한 결과이다.
     *
     * @property attachment 저장된(또는 이미 있던) 첨부
     * @property created 새로 만들었으면 `true`, 같은 첨부가 이미 있어 새로 만들지 않았으면 `false`
     */
    data class Stored(val attachment: Attachment, val created: Boolean)

    /** 업로드 디렉터리이다. */
    fun uploadDirectory(): File = File(dataDirectory.root(), UPLOAD_DIRECTORY)

    /**
     * 파일을 업로드 디렉터리로 옮기고 컨테이너에 첨부한다. [file]은 옮겨지므로 이 메서드를 부른 뒤에는 남아 있지 않다.
     *
     * 같은 이름과 해시를 가진 첨부가 이미 컨테이너에 있으면 새로 만들지 않는다.
     *
     * @param name 파일 이름. `null`이면 시각과 감지한 확장자로 만든다
     * @param ownerLoginId 올린 사용자의 로그인 ID
     */
    fun store(file: File, name: String?, containerType: ResourceType, containerId: Long, ownerLoginId: String?): Stored {
        val hash = sha256(file.readBytes())
        return save(moveIntoUploadDirectory(file, hash), name, containerType, containerId, ownerLoginId)
    }

    /** 스트림의 내용을 임시 파일로 받아 [store]와 같이 저장한다. */
    fun store(input: InputStream, name: String?, containerType: ResourceType, containerId: Long, ownerLoginId: String?): Stored {
        val temp = File.createTempFile("yobi", null)
        try {
            input.use { source -> temp.outputStream().use { source.copyTo(it) } }
            return store(temp, name, containerType, containerId, ownerLoginId)
        } finally {
            temp.delete()
        }
    }

    /** 컨테이너에 붙은 첨부를 번호 순으로 조회한다 (v1.6 `Attachment.findByContainer`). */
    @Transactional(readOnly = true)
    fun findByContainer(containerType: ResourceType, containerId: Long): List<Attachment> =
        attachments.findByContainerTypeAndContainerIdOrderById(containerType, containerId)

    /** [fromType], [fromId] 컨테이너의 첨부를 모두 [toType], [toId]로 옮기고 옮긴 개수를 돌려준다 (v1.6 `Attachment.moveAll`). */
    @Transactional
    fun moveAll(fromType: ResourceType, fromId: Long, toType: ResourceType, toId: Long): Int {
        val found = attachments.findByContainerTypeAndContainerIdOrderById(fromType, fromId)
        found.forEach { moveTo(it, toType, toId) }
        return found.size
    }

    /**
     * 사용자의 임시 첨부 중 [selectedIds]에 든 것만 [toType], [toId]로 옮기고 찾은 첨부의 개수를 돌려준다
     * (v1.6 `Attachment.moveOnlySelected`). 사이트 관리자는 임시 첨부가 아닌 것도 옮길 수 있고, 숫자가 아닌 번호는 건너뛴다.
     */
    @Transactional
    fun moveOnlySelected(fromUserId: Long, selectedIds: List<String>, toType: ResourceType, toId: Long, siteManager: Boolean): Int {
        val found = attachments.findAllById(selectedIds.mapNotNull { it.trim().toLongOrNull() })
        for (attachment in found) {
            if ((attachment.containerId == fromUserId && attachment.containerType == ResourceType.USER) || siteManager) {
                moveTo(attachment, toType, toId)
            }
        }
        return found.size
    }

    /**
     * 첨부를 지운다. 같은 해시를 쓰는 다른 첨부가 없으면 업로드 디렉터리의 파일도 지운다.
     */
    fun delete(attachment: Attachment) {
        attachments.delete(attachment)
        attachments.flush()
        val hash = attachment.hash ?: return
        if (!attachments.existsByHash(hash)) Files.deleteIfExists(File(uploadDirectory(), hash).toPath())
    }

    /** 첨부를 다른 컨테이너로 옮긴다 (v1.6 `Attachment.moveTo`). */
    fun moveTo(attachment: Attachment, containerType: ResourceType, containerId: Long) {
        attachment.containerType = containerType
        attachment.containerId = containerId
        attachments.save(attachment)
    }

    /** 컨테이너에 붙은 첨부를 모두 지운다 (v1.6 `Attachment.deleteAll`). */
    fun deleteAll(containerType: ResourceType, containerId: Long) {
        findByContainer(containerType, containerId).forEach { delete(it) }
    }

    /** 해당 해시의 첨부가 하나라도 있는지 확인한다. */
    @Transactional(readOnly = true)
    fun exists(hash: String): Boolean = attachments.existsByHash(hash)

    /** 업로드 디렉터리에 해당 해시의 파일이 있는지 확인한다. */
    fun fileExists(hash: String): Boolean = File(uploadDirectory(), hash).isFile

    /** 첨부의 실제 파일이다. 없을 수도 있다. */
    fun fileOf(attachment: Attachment): File = File(uploadDirectory(), attachment.hash.orEmpty())

    private fun save(file: File, name: String?, containerType: ResourceType, containerId: Long, ownerLoginId: String?): Stored {
        val mime = detectMediaType(file, name)
        val attachment = Attachment(
            containerType = containerType,
            containerId = containerId,
            createdDate = Date(),
            hash = file.name,
            size = file.length(),
            ownerLoginId = ownerLoginId,
            mimeType = mime,
            name = name ?: (System.currentTimeMillis().toString() + extensionOf(mime))
        )
        val same = attachments.findByNameAndHashAndContainerTypeAndContainerId(
            requireNotNull(attachment.name), requireNotNull(attachment.hash), containerType, containerId
        ).firstOrNull()
        if (same != null) return Stored(same, false)
        return Stored(attachments.save(attachment), true)
    }

    private fun extensionOf(mime: String): String = try {
        // v1.6은 감지한 종류(문자셋 매개변수 포함)로 확장자를 찾고, 찾지 못하면 확장자 없이 이름을 만든다.
        "." + MimeTypes.getDefaultMimeTypes().forName(mime).extension.removePrefix(".")
    } catch (e: MimeTypeException) {
        ""
    }

    private fun moveIntoUploadDirectory(file: File, hash: String): File {
        val directory = uploadDirectory()
        directory.mkdirs()
        if (!directory.isDirectory) throw NotDirectoryException("'${directory.absolutePath}' is not a directory.")
        val target = File(directory, hash)
        if (!file.renameTo(target)) {
            file.copyTo(target, overwrite = true)
            file.delete()
        }
        return target
    }

    /**
     * 이름과 내용으로 파일 종류를 감지한다 (v1.6 `FileUtil.detectMediaType`). 텍스트이면 문자셋을 덧붙인다.
     * v1.6은 앞부분을 버퍼에 읽은 뒤의 스트림 나머지로 문자셋을 감지하므로, 8192바이트 이하의 파일은 항상 UTF-8이 된다
     * ([refactoring-backlog.md] R-17).
     */
    private fun detectMediaType(file: File, name: String?): String {
        val bytes = file.readBytes()
        val metadata = Metadata()
        name?.let { metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, it) }
        val media = Tika().detector.detect(bytes.inputStream().buffered(), metadata)
        return when {
            media.type.lowercase() == "text" -> {
                val rest = if (bytes.size > BUFFER_SIZE) bytes.copyOfRange(BUFFER_SIZE, bytes.size) else ByteArray(0)
                "$media; charset=${CharsetDetection.detect(rest)}"
            }
            media.toString() == "audio/ogg" && name?.substringAfterLast('.', "")?.lowercase() == "ogv" -> "video/ogg"
            else -> media.toString()
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val UPLOAD_DIRECTORY = "uploads"
        const val BUFFER_SIZE = 8192
    }
}
