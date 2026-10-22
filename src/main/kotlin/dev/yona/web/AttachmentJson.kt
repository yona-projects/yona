package dev.yona.web

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import org.springframework.stereotype.Component

/**
 * 첨부를 외부용 JSON으로 만든다. v1.6은 `Attachment` 엔티티를 JSON 변환기에 그대로 넘겼으므로 엔티티의 모든 필드가 나간다
 * (`id, name, hash, containerType, mimeType, size, containerId(문자열), createdDate(밀리초), ownerLoginId`).
 */
@Component
class AttachmentJson(private val attachments: AttachmentRepository) {

    /** 컨테이너에 붙은 첨부를 번호 순서로 돌려준다. */
    fun list(containerType: ResourceType, containerId: Long): List<Map<String, Any?>> =
        attachments.findByContainerTypeAndContainerIdOrderById(containerType, containerId).map { of(it) }

    /** 첨부 하나의 JSON이다. */
    fun of(a: Attachment): Map<String, Any?> = linkedMapOf(
        "id" to a.id, "name" to a.name, "hash" to a.hash, "containerType" to a.containerType?.name, "mimeType" to a.mimeType, "size" to a.size,
        "containerId" to a.containerId.toString(), "createdDate" to a.createdDate?.time, "ownerLoginId" to a.ownerLoginId
    )
}
