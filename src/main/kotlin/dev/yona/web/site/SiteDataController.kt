package dev.yona.web.site

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.site.DataService
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.SiteManagerOnly
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 사이트 관리의 데이터 화면, 내려받기, 올리기와 아바타 도구이다. v1.6 `SiteApp.data`, `exportData`, `importData`, `noAvatarUsers`,
 * `setAttachmentToUserAvatar`(`app/controllers/SiteApp.java:352-431`)에 해당한다. 사이트 관리자만 쓴다.
 *
 * 데이터 화면은 사이드바에 링크가 없고 주소로만 들어간다(v1.6도 같다). 올리기는 기존 데이터를 교체하므로 [DataService]의 설명을 본다.
 */
@Controller
@AnonymousCheck
@SiteManagerOnly
class SiteDataController(
    private val data: DataService,
    private val users: UserRepository,
    private val userLookup: UserLookup,
    private val attachments: AttachmentRepository,
    private val attachmentService: AttachmentService
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val json = JsonMapper.builder().build()

    @GetMapping("/sites/data")
    fun data(): ModelAndView = ModelAndView("site/data").addObject("title", "title.siteSetting")

    /** DB 전체를 `yobi-data-<yyyyMMddHHmm>.json` 파일로 보낸다. 쓰는 동안의 오류는 로그만 남아 파일이 잘릴 수 있다. */
    @GetMapping("/sites/export")
    fun export(): ResponseEntity<StreamingResponseBody> {
        val filename = "yobi-data-" + SimpleDateFormat("yyyyMMddHHmm").format(Date()) + ".json"
        val body = StreamingResponseBody { out ->
            try {
                data.export(out)
            } catch (e: Exception) {
                log.error("Failed to export data", e)
            }
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, "application/x-download")
            .header("Content-disposition", "attachment; filename=$filename")
            .body(body)
    }

    /**
     * 올린 파일로 DB를 교체한다. 파일을 고르지 않았으면 데이터 화면으로, 처리했으면(테이블 몇 개가 실패해도) 처음 화면으로 이동한다.
     * JSON이 깨졌으면 400이다.
     */
    @PostMapping("/sites/import")
    fun import(@RequestParam(name = "data", required = false) file: MultipartFile?, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (file == null || file.isEmpty) return RedirectSupport.see(request, response, "/sites/data")
        return try {
            file.inputStream.use { data.import(it) }
            RedirectSupport.see(request, response, "/")
        } catch (e: Exception) {
            log.error("Failed to import data", e)
            ErrorViews.badRequest()
        }
    }

    /** 활성 사용자 중 아바타가 없는 사용자의 `loginId`, `name`, `email` 목록이다. */
    @GetMapping("/sites/noAvatarUsers")
    @Transactional(readOnly = true)
    fun noAvatarUsers(): ResponseEntity<Map<String, Any>> {
        val list = users.findAll().filter { it.state == UserState.ACTIVE }
            .filter { attachmentService.findByContainer(ResourceType.USER_AVATAR, requireNotNull(it.id)).isEmpty() }
            .map { linkedMapOf("loginId" to it.loginId.orEmpty(), "name" to it.name.orEmpty(), "email" to it.email.orEmpty()) }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("users" to list))
    }

    /**
     * 이미지 첨부를 사용자의 아바타로 옮긴다. 본문은 `{"avatarFileId": 첨부 번호, "email": "사용자 이메일"}`이다. 첨부가 이미지이고 사용자가 있을 때만
     * 이전 아바타를 지우고 옮기며, 그렇지 않아도 `{"status":200,"message":"OK"}`로 답한다. JSON이 아니면 400이다.
     */
    @PostMapping("/sites/setAttachmentToUserAvatar")
    @Transactional
    fun setAttachmentToUserAvatar(@RequestBody(required = false) body: String?): ResponseEntity<Map<String, Any>> {
        val node: JsonNode? = body?.let { runCatching { json.readTree(it) }.getOrNull() }?.takeIf { it.isObject }
        if (node == null) return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to "Expecting Json data"))
        val attachment = attachments.findById(requireNotNull(node.get("avatarFileId")) { "avatarFileId가 없습니다." }.asLong()).orElseThrow { NullPointerException("첨부가 없습니다.") }
        val target = userLookup.findByEmail(requireNotNull(node.get("email")) { "email이 없습니다." }.asString())
        if (attachment.mimeType.orEmpty().split("/")[0].lowercase() == "image" && target != null) {
            val userId = requireNotNull(target.id)
            attachmentService.deleteAll(ResourceType.USER_AVATAR, userId)
            attachmentService.moveTo(attachment, ResourceType.USER_AVATAR, userId)
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("status" to 200, "message" to "OK"))
    }
}
