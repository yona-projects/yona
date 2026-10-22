package dev.yona.web

import dev.yona.access.AccessControl
import dev.yona.access.ResourceLookup
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentService
import dev.yona.security.CurrentUserProvider
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.text.Normalizer

/**
 * 컨테이너(이슈, 게시글, 마일스톤 등)에 붙은 첨부 목록을 화면과 API가 쓰는 모양으로 만든다.
 * v1.6 `AttachmentApp.getFileList`(`app/controllers/AttachmentApp.java:234-259`)에 해당한다.
 */
@Component
class AttachmentListing(
    private val attachments: AttachmentService,
    private val resources: ResourceLookup,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider
) {

    private val json = JsonMapper.builder().build()

    /**
     * 컨테이너의 첨부를 번호 순으로 `{"attachments": [{id, mimeType, name, url, size}]}` 모양으로 돌려준다.
     * 읽을 수 없는 첨부가 하나라도 있으면 `null`이다. 이름은 NFC로 정규화한다.
     */
    fun fileList(containerType: ResourceType, containerId: Long): Map<String, List<Map<String, String>>>? {
        val viewer = currentUserProvider.get().user
        val items = attachments.findByContainer(containerType, containerId).map { attachment ->
            if (!accessControl.isAllowed(viewer, resources.attachmentResource(attachment), Operation.READ)) return null
            info(attachment)
        }
        return mapOf("attachments" to items)
    }

    /** [fileList]를 JSON 문자열로 돌려준다. 화면의 `data-attachments`에 쓴다. 읽을 수 없는 첨부가 있으면 오류이다. */
    fun fileListJson(containerType: ResourceType, containerId: Long): String =
        json.writeValueAsString(fileList(containerType, containerId) ?: throw IllegalStateException("읽을 수 없는 첨부가 있습니다."))

    private fun info(attachment: Attachment): Map<String, String> = mapOf(
        "id" to attachment.id.toString(), "mimeType" to attachment.mimeType.orEmpty(),
        "name" to Normalizer.normalize(attachment.name.orEmpty(), Normalizer.Form.NFC),
        "url" to "/files/${attachment.id}", "size" to attachment.size.toString()
    )
}
