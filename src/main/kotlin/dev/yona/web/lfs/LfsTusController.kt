package dev.yona.web.lfs

import dev.yona.domain.lfs.LfsService
import dev.yona.domain.lfs.LfsStorage
import dev.yona.util.SiteUrls
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseBody
import java.util.Base64

/**
 * Git LFS의 `tus`(이어올리기) 전송이다(추가 개발 항목 X-8 단계 7, `decisions.md` D-79 1번). tus 1.0.0의 핵심 규격과 `creation` 확장을 따른다: `POST`로 올리기를 만들고
 * (`Upload-Length`), `HEAD`로 지금까지 받은 위치(`Upload-Offset`)를 알려 주며, `PATCH`(`application/offset+octet-stream`)로 그 위치부터 이어 받는다.
 * 끊긴 업로드를 처음부터 다시 올리지 않아도 된다. 다 받으면 SHA-256을 확인해 객체로 옮긴다. 권한과 한도는 기본 전송의 올리기와 같다.
 * git-lfs 클라이언트는 `lfs.tustransfers`를 켠 사용자에게만 `tus`를 쓴다.
 */
@Controller
class LfsTusController(private val support: LfsRequestSupport, private val lfs: LfsService, private val storage: LfsStorage, private val siteUrls: SiteUrls) {

    /**
     * 올리기를 만든다(객체가 이미 있으면 만들 것 없이 성공). git-lfs 클라이언트는 batch가 미리 만든 업로드에 곧바로 `HEAD`와 `PATCH`를 하므로 이 `POST`는 규격을 따르는
     * 다른 클라이언트를 위한 것이다. `OPTIONS`(지원 버전 알림)는 지원하지 않는다: 이 경로에 매핑해도 스프링이 정적 자원 처리기로 넘겨 404가 났고, 실제 클라이언트가 쓰지 않아 뺐다.
     */
    @RequestMapping(value = ["/{owner}/{project}/info/lfs/tus/{oid}", "/{owner}/{project}.git/info/lfs/tus/{oid}"], method = [RequestMethod.POST])
    @ResponseBody
    fun create(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("tus_create") {
            version(request)?.let { return@measured it }
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            val target = requireNotNull(context.project)
            if (!LfsStorage.validOid(oid)) return@measured support.error(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid oid")
            val length = request.getHeader("Upload-Length")?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return@measured support.error(HttpStatus.BAD_REQUEST, "Upload-Length is required")
            metadataOid(request)?.let { if (it != oid) return@measured support.error(HttpStatus.BAD_REQUEST, "The metadata oid does not match the address") }
            if (length > lfs.maxUploadBytes(target)) return@measured support.error(HttpStatus.PAYLOAD_TOO_LARGE, "Object exceeds the maximum size")
            storage.tusCreate(target, oid, length)
            tus(HttpStatus.CREATED).header(HttpHeaders.LOCATION, location(target.owner, target.name, oid)).build<Void>()
        }

    /** 지금까지 받은 위치를 알린다. 만들지 않은 업로드는 404이다. */
    @RequestMapping(value = ["/{owner}/{project}/info/lfs/tus/{oid}", "/{owner}/{project}.git/info/lfs/tus/{oid}"], method = [RequestMethod.HEAD])
    @ResponseBody
    fun offset(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("tus_head") {
            version(request)?.let { return@measured it }
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            if (!LfsStorage.validOid(oid)) return@measured support.error(HttpStatus.NOT_FOUND, "Upload does not exist")
            val state = storage.tusState(requireNotNull(context.project), oid) ?: return@measured support.error(HttpStatus.NOT_FOUND, "Upload does not exist")
            tus(HttpStatus.OK).header("Upload-Offset", state.offset.toString()).header("Upload-Length", state.length.toString()).header(HttpHeaders.CACHE_CONTROL, "no-store").build<Void>()
        }

    /** [oid] 업로드에 지금 위치부터 이어 받는다. */
    @RequestMapping(value = ["/{owner}/{project}/info/lfs/tus/{oid}", "/{owner}/{project}.git/info/lfs/tus/{oid}"], method = [RequestMethod.PATCH])
    @ResponseBody
    fun append(@PathVariable owner: String, @PathVariable project: String, @PathVariable oid: String, request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> =
        support.measured("tus_patch") {
            version(request)?.let { return@measured it }
            val context = support.resolve(owner, project, request, response, write = true)
            if (context.failure != null) return@measured context.failure
            if (!LfsStorage.validOid(oid)) return@measured support.error(HttpStatus.NOT_FOUND, "Upload does not exist")
            if (MediaType.parseMediaType(request.contentType ?: "text/plain").let { it.type != "application" || it.subtype != "offset+octet-stream" }) return@measured support.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/offset+octet-stream")
            val offset = request.getHeader("Upload-Offset")?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: return@measured support.error(HttpStatus.BAD_REQUEST, "Upload-Offset is required")
            when (val result = storage.tusAppend(requireNotNull(context.project), oid, offset, request.inputStream)) {
                is LfsStorage.TusAppend.Progress -> tus(HttpStatus.NO_CONTENT).header("Upload-Offset", result.offset.toString()).build<Void>()
                is LfsStorage.TusAppend.Completed -> tus(HttpStatus.NO_CONTENT).header("Upload-Offset", result.size.toString()).build<Void>()
                is LfsStorage.TusAppend.OffsetMismatch -> support.error(HttpStatus.CONFLICT, "Upload-Offset does not match the current offset (${result.current})")
                LfsStorage.TusAppend.TooLong -> support.error(HttpStatus.PAYLOAD_TOO_LARGE, "The content is longer than the declared Upload-Length")
                LfsStorage.TusAppend.HashMismatch -> support.error(HttpStatus.UNPROCESSABLE_ENTITY, "The content does not match the oid")
                LfsStorage.TusAppend.NotFound -> support.error(HttpStatus.NOT_FOUND, "Upload does not exist")
            }
        }

    /** `Tus-Resumable`이 없거나 지원하지 않는 버전이면 412이다. */
    private fun version(request: HttpServletRequest): ResponseEntity<*>? =
        if (request.getHeader("Tus-Resumable") == VERSION) null else ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).header("Tus-Version", VERSION).header("Tus-Resumable", VERSION).build<Void>()

    /** `Upload-Metadata`(`키 Base64값` 쉼표 목록)에서 `oid` 값을 읽는다. 없으면 `null`이다. */
    private fun metadataOid(request: HttpServletRequest): String? =
        request.getHeader("Upload-Metadata")?.split(',')?.map { it.trim() }?.firstOrNull { it.startsWith("oid ") }?.removePrefix("oid ")?.let { runCatching { String(Base64.getDecoder().decode(it)) }.getOrNull() }

    private fun tus(status: HttpStatus) = ResponseEntity.status(status).header("Tus-Resumable", VERSION)

    private fun location(owner: String?, name: String?, oid: String) = "${siteUrls.origin()}${siteUrls.path("/$owner/$name.git/info/lfs/tus/$oid")}"

    private companion object {
        const val VERSION = "1.0.0"
    }
}
