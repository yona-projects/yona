package dev.yona.web

import dev.yona.web.access.AnonymousCheck
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
 * 로그인한 사용자가 올리면 그 사용자의 임시 첨부(컨테이너 `USER`)로 저장한다. 웹 편집기가 쓰는 `POST /files`는 세션 전용이고, 토큰으로 올리는 도구는 토큰 전용
 * `POST /-_-api/v1/files`를 쓴다(추가 개발 항목 X-5, `decisions.md` D-36. v1.6은 `/files`가 `Yona-Token` 헤더도 받았다). 글이나 프로필을 저장할 때
 * 실제 컨테이너로 옮긴다. 폼에 `authorEmail`(없거나 못 찾으면 `authorLoginId`)이 있으면 그 사용자의 임시 첨부로 저장한다. 소유자 아이디는
 * 명의가 아니라 요청한 로그인 사용자이다(v1.6 `AttachmentApp.findUploader`).
 *
 * ## 목록 (`GET /files?containerType=&containerId=`), 지우기 (`POST /files/:id`)
 * 목록의 첨부는 모두 읽을 수 있어야 하고, 지우려면 `DELETE` 권한이 있어야 한다.
 */
@Controller
@AnonymousCheck
class AttachmentController(
    private val attachmentRepository: AttachmentRepository,
    private val attachments: AttachmentService,
    private val resources: ResourceLookup,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val listing: AttachmentListing,
    private val userLookup: dev.yona.domain.user.UserLookup,
    private val users: dev.yona.domain.user.UserRepository
) {

    private val log = org.slf4j.LoggerFactory.getLogger(AttachmentController::class.java)


    private val json = JsonMapper.builder().build()


    @GetMapping("/files/{id}")
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

    @PostMapping("/files", "/-_-api/v1/files")
    @Transactional
    fun upload(
        @RequestParam(name = "filePath", required = false) file: MultipartFile?,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        file ?: return ResponseEntity.badRequest().build<Void>()
        val requester = currentUserProvider.get().user
        val uploader = findUploader(request) ?: requester ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        // Play 멀티파트는 IE가 보내는 `C:\dir\a.txt` 같은 경로만 역슬래시 기준으로 뗀다(슬래시는 그대로 둔다). 톰캣이 이미 슬래시 앞을 뗀 값이 아니라
        // 요청 머리글의 원래 파일 이름을 쓴다
        val submitted = (request as? org.springframework.web.multipart.MultipartHttpServletRequest)?.getMultipartHeaders("filePath")?.contentDisposition?.filename
        val name = Normalizer.normalize((submitted ?: file.originalFilename).orEmpty().substringAfterLast('\\'), Normalizer.Form.NFC)
        val stored = attachments.store(file.inputStream, name, ResourceType.USER, requireNotNull(uploader.id), requester?.loginId)

        val attachment = stored.attachment
        val url = "/files/${attachment.id}"
        // IE에서 JSON을 파일로 내려받으려 하는 문제를 피하려고, JSON을 받을 수 없는 클라이언트에는 text/html로 알린다 (본문은 JSON이다).
        val contentType = HttpPreference.preferredType(request.getHeader(HttpHeaders.ACCEPT), "application/json", "text/html")
        // v1.6는 `response().setHeader("Content-Type", ...)`이라 문자 집합이 없다
        request.setAttribute(JsonCharsetFilter.RAW_ATTRIBUTE, true)
        return ResponseEntity.status(if (stored.created) HttpStatus.CREATED else HttpStatus.OK)
            .header(HttpHeaders.LOCATION, url)
            .header(HttpHeaders.VARY, "Accept")
            .contentType(if (contentType == "text/html") MediaType.TEXT_HTML else MediaType.APPLICATION_JSON)
            .body(json.writeValueAsString(info(attachment, url)))
    }

    /**
     * 폼에 본문 항목이 있으면 `authorEmail`로, 못 찾으면 `authorLoginId`로 올린 사람을 찾는다. 항목이 없거나 둘 다 못 찾으면 `null`이다
     * (v1.6 `AttachmentApp.findUploader`). 쿼리 문자열의 값은 본문 항목이 아니므로 세지 않는다.
     */
    private fun findUploader(request: HttpServletRequest): dev.yona.domain.user.User? {
        val queryNames = org.springframework.web.util.UriComponentsBuilder.newInstance().query(request.queryString).build().queryParams.keys
        val body = request.parameterMap.filterKeys { it !in queryNames }
        if (body.isEmpty()) return null
        userLookup.findByEmail(body["authorEmail"]?.firstOrNull())?.let { return it }
        val loginId = body["authorLoginId"]?.firstOrNull()
        return if (loginId.isNullOrEmpty()) null else users.findByLoginId(loginId)
    }

    @GetMapping("/files")
    fun list(
        @RequestParam(name = "containerType", required = false) containerType: String?,
        @RequestParam(name = "containerId", required = false) containerId: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        request.setAttribute(JsonCharsetFilter.RAW_ATTRIBUTE, true) // v1.6는 문자 집합 없는 `application/json`을 직접 정한다
        val body = HashMap<String, Any>()
        if (!containerType.isNullOrEmpty() && !containerId.isNullOrEmpty()) {
            body.putAll(listing.fileList(ResourceType.valueOf(containerType), containerId) ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).build()) // v1.6는 403에도 Content-Type: application/json
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json.writeValueAsString(body))
    }

    @PostMapping("/files/{id}")
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
        logIfOriginFileIsNotValid(hash)
        return text(
            HttpStatus.OK,
            if (attachments.fileExists(hash)) "The attachment is removed successfully, but its origin file still exists."
            else "Both the attachment and its origin file are removed successfully."
        )
    }

    /** 지운 뒤 DB 참조와 디스크 원본이 어긋나면 기록한다 (v1.6 `AttachmentApp.logIfOriginFileIsNotValid`). 응답에는 영향이 없다. */
    private fun logIfOriginFileIsNotValid(hash: String) {
        val fileExists = attachments.fileExists(hash)
        val referred = attachments.exists(hash)
        if (!fileExists && referred) {
            log.error("The origin file '{}' cannot be found even if the file is still referred by some attachments.", hash)
        }
        if (fileExists && !referred) {
            log.warn("The attachment is removed successfully, but its origin file '{}' still exists abnormally even if the file referred by nowhere.", hash)
        }
    }

    /** 올리기 응답의 파일 정보이다. v1.6은 `HashMap`이라 JSON 필드 순서가 해시 순서이다(`size, name, id, mimeType, url`). */
    private fun info(attachment: dev.yona.domain.misc.Attachment, url: String, name: String? = attachment.name): Map<String, String> = HashMap<String, String>().also {
        it["id"] = attachment.id.toString(); it["mimeType"] = attachment.mimeType.orEmpty(); it["name"] = name.orEmpty()
        it["url"] = url; it["size"] = attachment.size.toString()
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).contentType(MediaType("text", "plain", Charsets.UTF_8)).body(body)

    private fun text(response: HttpServletResponse, status: HttpStatus, body: String) {
        response.status = status.value()
        response.contentType = "text/plain; charset=UTF-8"
        response.writer.write(body)
    }
}
