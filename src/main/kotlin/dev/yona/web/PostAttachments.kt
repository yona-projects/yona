package dev.yona.web

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.user.User
import dev.yona.access.AccessControl
import dev.yona.config.YonaProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Component

/**
 * 글을 저장할 때 편집기에서 올린 임시 첨부를 글로 옮긴다. v1.6 `AbstractPostingApp.attachUploadFilesToPost`
 * (`app/controllers/AbstractPostingApp.java:221-231, 254-268`)에 해당한다.
 *
 * 임시 첨부 번호는 폼의 숨은 값 `temporaryUploadFiles`(쉼표로 이은 번호)에 있다. v1.6은 **멀티파트 본문일 때만** 이 값을 읽는다.
 * 일반 폼(`application/x-www-form-urlencoded`)으로 보내는 화면에서는 임시 첨부가 옮겨지지 않는다. v1.6의 마일스톤 만들기 폼이 일반 폼이어서
 * 첨부가 옮겨지지 않았으므로 이 폼에 `enctype="multipart/form-data"`를 주었다(R-23). 이 클래스의 읽기 규칙은 v1.6 그대로이다.
 */
@Component
class PostAttachments(
    private val attachments: AttachmentService,
    private val accessControl: AccessControl,
    private val messages: MessageSource,
    private val properties: YonaProperties
) {

    /**
     * 요청이 멀티파트이고 `temporaryUploadFiles`가 있으면 그 임시 첨부를 [toType], [toId]로 옮긴다.
     *
     * @return 일부를 찾지 못했을 때 사용자에게 알릴 일회성 메시지(`title`, `description`). 문제가 없으면 빈 맵
     */
    fun attach(request: HttpServletRequest, user: User, toType: ResourceType, toId: Long): Map<String, Any> {
        val ids = temporaryFileIds(request)
        if (ids.isEmpty() || ids.first().isBlank()) return emptyMap()
        val moved = attachments.moveOnlySelected(requireNotNull(user.id), ids, toType, toId, accessControl.isSiteManager(user))
        if (moved == ids.size) return emptyMap()
        val locale = LocaleContextHolder.getLocale()
        val minutes = properties.temporaryFilesKeepUpTime.toMillis() / (60 * 1000L)
        return mapOf(
            RedirectSupport.TITLE to messages.getMessage("post.popup.fileAttach.hasMissing", arrayOf(ids.size - moved), locale),
            RedirectSupport.DESCRIPTION to messages.getMessage("post.popup.fileAttach.hasMissing.description", arrayOf(minutes), locale)
        )
    }

    /**
     * JSON 본문으로 받은 임시 첨부 번호 [ids]를 [toType], [toId]로 옮긴다 (v1.6 `attachUploadFilesToPost(JsonNode, Resource)`).
     * 번호가 없으면 아무것도 하지 않는다.
     */
    fun attachIds(user: User, ids: List<String>, toType: ResourceType, toId: Long) {
        if (ids.isEmpty()) return
        attachments.moveOnlySelected(requireNotNull(user.id), ids, toType, toId, accessControl.isSiteManager(user))
    }

    private fun temporaryFileIds(request: HttpServletRequest): List<String> {
        if (request.contentType?.startsWith("multipart/") != true) return emptyList()
        return request.getParameter(TAG_NAME)?.split(",") ?: emptyList()
    }

    private companion object {
        const val TAG_NAME = "temporaryUploadFiles"
    }
}
