package dev.yona.web

import dev.yona.access.AccessControl
import dev.yona.access.ResourceLookup
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.AttachmentService
import dev.yona.security.CurrentUserProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.nio.file.Files
import java.text.Normalizer
import tools.jackson.databind.json.JsonMapper

/**
 * 첨부 파일 올리기, 목록, 내려받기, 지우기이다. v1.6 `AttachmentApp`(`app/controllers/AttachmentApp.java`)에 해당한다.
 *
 * ## 내려받기 (`getFile`, 131-172줄)
 *
 * 첨부를 읽을 권한이 있어야 하고, 해시와 형식으로 만든 `ETag`가 `If-None-Match`와 같으면 `304`이다. 파일은 한 번에 보내며
 * 부분 요청(`Range`)에는 응답하지 않는다. `RANGE` 헤더가 있으면 범위 지원 표시(`Accept-Ranges`)만 붙인다(v1.6과 같다).
 * `?action=download`이면 내려받기 형식(`attachment`), 아니면 화면 표시 형식(`inline`)이다.
 *
 * ## 올리기 (`POST /files`)
 * 로그인한 사용자(또는 `Yona-Token` 헤더의 사용자)가 올리면 그 사용자의 임시 첨부(컨테이너 `USER`)로 저장한다. 글이나 프로필을 저장할 때
 * 실제 컨테이너로 옮긴다. v1.6은 폼의 `authorEmail`, `authorLoginId`로 다른 사용자 명의로 올릴 수 있었지만 v2는 받지 않는다([decisions.md] A-31).
 *
 * ## 목록 (`GET /files?containerType=&containerId=`), 지우기 (`POST /files/:id`)
 * 목록의 첨부는 모두 읽을 수 있어야 하고, 지우려면 `DELETE` 권한이 있어야 한다.
 */
@Controller
class AttachmentController(
    private val attachmentRepository: AttachmentRepository,
    private val attachments: AttachmentService,
    private val resources: ResourceLookup,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider
) {

    private val json = JsonMapper.builder().build()


    @GetMapping("/files/{id}", "/files/{id}/")
    fun getFile(@PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse) {
        val attachment = attachmentRepository.findById(id).orElse(null)
            ?: return text(response, HttpStatus.NOT_FOUND, "The file does not exist.")
        val disposition = if (request.getParameter("action") == "download") "attachment" else "inline"
        val eTag = "\"${attachment.hash}-$disposition\""

        if (!accessControl.isAllowed(currentUserProvider.get().user, resources.attachmentResource(attachment), Operation.READ)) {
            return text(response, HttpStatus.FORBIDDEN, "You have no permission to get the file.")
        }

        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")

        if (request.getHeader(HttpHeaders.IF_NONE_MATCH) == eTag) {
            response.setHeader(HttpHeaders.ETAG, eTag)
            response.status = HttpStatus.NOT_MODIFIED.value()
            return
        }

        val file = attachments.fileOf(attachment)
        if (!file.exists()) {
            return text(response, HttpStatus.INTERNAL_SERVER_ERROR, "The file does not exist")
        }

        if (request.getHeader("RANGE") != null) response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes")
        response.contentType = attachment.mimeType
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition + "; " + ContentDispositions.encodeFilename(attachment.name.orEmpty()))
        response.setHeader(HttpHeaders.ETAG, eTag)
        response.setContentLengthLong(file.length())
        Files.copy(file.toPath(), response.outputStream)
    }

    @PostMapping("/files")
    @Transactional
    fun upload(
        @RequestParam(name = "filePath", required = false) file: MultipartFile?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        file ?: return ResponseEntity.badRequest().build<Void>()
        val uploader = currentUserProvider.get().user ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        val name = Normalizer.normalize(file.originalFilename.orEmpty(), Normalizer.Form.NFC)
        val stored = attachments.store(file.inputStream, name, ResourceType.USER, requireNotNull(uploader.id), uploader.loginId)

        val attachment = stored.attachment
        val url = "/files/${attachment.id}"
        // IE에서 JSON을 파일로 내려받으려 하는 문제를 피하려고, JSON을 받을 수 없는 클라이언트에는 text/html로 알린다 (본문은 JSON이다).
        val contentType = HttpPreference.preferredType(request.getHeader(HttpHeaders.ACCEPT), "application/json", "text/html")
        return ResponseEntity.status(if (stored.created) HttpStatus.CREATED else HttpStatus.OK)
            .header(HttpHeaders.LOCATION, url)
            .header(HttpHeaders.VARY, "Accept")
            .contentType(if (contentType == "text/html") MediaType("text", "html", Charsets.UTF_8) else MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(info(attachment, url)))
    }

    @GetMapping("/files")
    fun list(
        @RequestParam(name = "containerType", required = false) containerType: String?,
        @RequestParam(name = "containerId", required = false) containerId: String?
    ): ResponseEntity<String> {
        val body = linkedMapOf<String, Any>()
        if (!containerType.isNullOrEmpty() && !containerId.isNullOrEmpty()) {
            val viewer = currentUserProvider.get().user
            val items = attachments.findByContainer(ResourceType.valueOf(containerType), containerId.toLong()).map { attachment ->
                if (!accessControl.isAllowed(viewer, resources.attachmentResource(attachment), Operation.READ)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
                }
                info(attachment, "/files/${attachment.id}", Normalizer.normalize(attachment.name.orEmpty(), Normalizer.Form.NFC))
            }
            body["attachments"] = items
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))
    }

    @PostMapping("/files/{id}", "/files/{id}/")
    @Transactional
    fun delete(@PathVariable id: Long, request: HttpServletRequest): Any {
        // 멀티파트 요청은 `_method`가 `delete`여야 한다 (HTML 폼에서 DELETE를 흉내 내는 방식).
        if (request.contentType?.startsWith("multipart/") == true &&
            request.getParameter("_method")?.lowercase() != "delete"
        ) {
            return text(HttpStatus.BAD_REQUEST, "_method must be 'delete'.")
        }
        val attachment = attachmentRepository.findById(id).orElse(null) ?: return ResponseEntity.notFound().build<Void>()
        if (!accessControl.isAllowed(currentUserProvider.get().user, resources.attachmentResource(attachment), Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        }

        attachments.delete(attachment)
        val hash = attachment.hash.orEmpty()
        return text(
            HttpStatus.OK,
            if (attachments.fileExists(hash)) "The attachment is removed successfully, but its origin file still exists."
            else "Both the attachment and its origin file are removed successfully."
        )
    }

    private fun info(attachment: dev.yona.domain.misc.Attachment, url: String, name: String? = attachment.name): Map<String, String> = mapOf(
        "id" to attachment.id.toString(), "mimeType" to attachment.mimeType.orEmpty(), "name" to name.orEmpty(),
        "url" to url, "size" to attachment.size.toString()
    )

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).contentType(MediaType("text", "plain", Charsets.UTF_8)).body(body)

    private fun text(response: HttpServletResponse, status: HttpStatus, body: String) {
        response.status = status.value()
        response.contentType = "text/plain; charset=UTF-8"
        response.writer.write(body)
    }
}
