package dev.yona.web

import dev.yona.access.AccessControl
import dev.yona.access.ResourceLookup
import dev.yona.domain.enumeration.Operation
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
import java.nio.file.Files

/**
 * 첨부 파일 내려받기이다. v1.6 `AttachmentApp.getFile`(`app/controllers/AttachmentApp.java:131-172`)에 해당한다.
 *
 * 첨부를 읽을 권한이 있어야 하고, 해시와 형식으로 만든 `ETag`가 `If-None-Match`와 같으면 `304`이다. 파일은 한 번에 보내며
 * 부분 요청(`Range`)에는 응답하지 않는다. `RANGE` 헤더가 있으면 범위 지원 표시(`Accept-Ranges`)만 붙인다(v1.6과 같다).
 * `?action=download`이면 내려받기 형식(`attachment`), 아니면 화면 표시 형식(`inline`)이다.
 */
@Controller
class AttachmentController(
    private val attachmentRepository: AttachmentRepository,
    private val attachments: AttachmentService,
    private val resources: ResourceLookup,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider
) {

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

    private fun text(response: HttpServletResponse, status: HttpStatus, body: String) {
        response.status = status.value()
        response.contentType = "text/plain; charset=UTF-8"
        response.writer.write(body)
    }
}
