package com.github.yonaprojects.yona.domain.attachment

import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.support.FileUtil
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant

@Service
@Transactional
class AttachmentServiceImpl(
    private val attachmentRepository: AttachmentRepository,
    @Value("\${yona.upload.base-dir:/tmp/yona/uploads}")
    private val baseDir: String
) : AttachmentService {

    // yona utils/AttachmentCache.java의 remove(container) 대응 (P2-49) — 새 첨부가 추가되면 그
    // 컨테이너의 목록 캐시를 무효화한다(dedup으로 기존 행을 재사용하는 경우도 안전하게 함께 지운다).
    @CacheEvict("attachmentsByContainer", key = "#containerType.name() + #containerId")
    override fun store(
        inputStream: InputStream,
        name: String,
        containerType: ResourceType,
        containerId: String,
        ownerLoginId: String
    ): Pair<Attachment, Boolean> {
        val uploadDir = File(baseDir)
        if (!uploadDir.exists()) {
            uploadDir.mkdirs()
        }

        val tempFile = File.createTempFile("yona-upload-", null)
        val digest = MessageDigest.getInstance("SHA-256")
        var size: Long = 0

        FileOutputStream(tempFile).use { fos ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                fos.write(buffer, 0, bytesRead)
                digest.update(buffer, 0, bytesRead)
                size += bytesRead
            }
            fos.flush()
        }

        val hash = toHex(digest.digest())
        val targetFile = File(uploadDir, hash)

        if (!targetFile.exists()) {
            Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } else {
            tempFile.delete()
        }

        // yona Attachment.java:537-582 save()의 dedup 대응 (P2-24) — 동일 컨테이너에 동일 [GL-models_Attachment-039;GL-models_Attachment-040]
        // 이름·내용(name+hash+containerType+containerId)으로 이미 첨부된 기록이 있으면 새 행을
        // 만들지 않고 기존 행을 재사용한다. 이전에는 이 조회 없이 매번 새 Attachment를 저장해,
        // 재업로드마다 DB에 중복 행이 쌓였다.
        val existing = attachmentRepository.findFirstByNameAndHashAndContainerTypeAndContainerId(
            name, hash, containerType, containerId
        )
        if (existing != null) {
            return existing to false
        }

        // yona FileUtil.detectMediaType(file, name) 대응 (P2-25) — 해시 파일명(targetFile)의 실제
        // 콘텐츠를 Tika로 감지하고, 원본 파일명(name)은 힌트로만 넘긴다. 기존 Files.probeContentType()은
        // 사실상 확장자 기반이라 확장자 없는 해시 파일명에서 거의 항상 application/octet-stream으로
        // 오탐했다.
        val mimeType = try {
            FileUtil.detectMediaType(targetFile, name)
        } catch (e: Exception) {
            "application/octet-stream"
        }

        val attachment = Attachment(
            name = name,
            hash = hash,
            containerType = containerType,
            containerId = containerId,
            mimeType = mimeType,
            size = size,
            createdDate = Instant.now(),
            ownerLoginId = ownerLoginId
        )

        return attachmentRepository.save(attachment) to true
    }

    override fun getFile(attachment: Attachment): File {
        return File(baseDir, attachment.hash)
    }

    @CacheEvict("attachmentsByContainer", key = "#attachment.containerType.name() + #attachment.containerId")
    override fun delete(attachment: Attachment) {
        attachmentRepository.delete(attachment)
        // 동일한 해시를 참조하는 다른 파일 첨부 레코드가 없다면 디스크에서 삭제
        val remaining = attachmentRepository.findByHash(attachment.hash).filter { it.id != attachment.id }
        if (remaining.isEmpty()) {
            val file = File(baseDir, attachment.hash)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    // delete(attachment)를 내부에서 호출하지만 같은 클래스 내 self-invocation은 Spring AOP 프록시를
    // 우회해 그쪽 @CacheEvict가 발동하지 않으므로, 이 메서드 자체에도 명시적으로 걸어준다.
    @CacheEvict("attachmentsByContainer", key = "#containerType.name() + #containerId")
    override fun deleteAll(containerType: ResourceType, containerId: String) {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(containerType, containerId)
        for (attachment in attachments) {
            delete(attachment)
        }
    }

    @Caching(evict = [
        CacheEvict("attachmentsByContainer", key = "#fromType.name() + #fromId"),
        CacheEvict("attachmentsByContainer", key = "#toType.name() + #toId")
    ])
    override fun moveAll(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String
    ): Int {
        val attachments = attachmentRepository.findByContainerTypeAndContainerId(fromType, fromId)
        for (attachment in attachments) {
            attachment.containerType = toType
            attachment.containerId = toId
        }
        attachmentRepository.saveAll(attachments)
        return attachments.size
    }

    @Caching(evict = [
        CacheEvict("attachmentsByContainer", key = "#fromType.name() + #fromId"),
        CacheEvict("attachmentsByContainer", key = "#toType.name() + #toId")
    ])
    override fun moveOnlySelected(
        fromType: ResourceType,
        fromId: String,
        toType: ResourceType,
        toId: String,
        selectedIds: List<Long>,
        moverLoginId: String
    ): Int {
        val attachments = attachmentRepository.findAllById(selectedIds)
        val validAttachments = attachments.filter {
            it.containerType == fromType && it.containerId == fromId && it.ownerLoginId == moverLoginId
        }
        for (attachment in validAttachments) {
            attachment.containerType = toType
            attachment.containerId = toId
        }
        attachmentRepository.saveAll(validAttachments)
        return validAttachments.size
    }

    private fun toHex(bytes: ByteArray): String {
        val formatter = StringBuilder()
        for (b in bytes) {
            formatter.append(String.format("%02x", b))
        }
        return formatter.toString()
    }
}
