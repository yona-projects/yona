package dev.yona.web.project

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.project.ProjectService
import dev.yona.domain.webhook.WebhookService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/** 웹훅 목록의 한 줄이다. */
data class WebhookRow(val id: Long, val payloadUrl: String, val secret: String, val type: String, val gitPush: Boolean, val deleteUrl: String)

/**
 * 프로젝트 웹훅 설정이다. v1.6 `ProjectApp.webhooks`, `newWebhook`, `deleteWebhook`(`app/controllers/ProjectApp.java:1262-1325`)에 해당한다.
 * 모두 프로젝트 수정 권한이 필요하다. 목록 화면은 웹훅 만들기 권한이 있을 때만 등록 폼을 보여 준다.
 *
 * 등록은 보낼 주소(`payloadUrl`, 필수, 2000자 이하), 토큰(`secret`, 250자 이하), 형식(`webhookType`), 푸시 포함 여부(`gitPush`)를 받고 목록으로 303 이동한다.
 * 입력이 올바르지 않으면 오류 내용을 담은 400이다.
 */
@Controller
@AnonymousCheck
class WebhookController(
    private val projectService: ProjectService,
    private val webhooks: WebhookService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val siteUrls: SiteUrls,
    private val messages: org.springframework.context.MessageSource
) {

    @GetMapping("/{user}/{project}/webhooks")
    @IsAllowed(Operation.UPDATE)
    @Transactional(readOnly = true)
    fun list(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val base = siteUrls.path("/${project.owner}/${project.name}")
        return ModelAndView("project/webhooks")
            .addObject("title", "project.webhook")
            .addObject("projectView", views.of(project))
            .addObject("creatable", accessControl.isProjectResourceCreatable(currentUserProvider.get().user, project, ResourceType.WEBHOOK))
            .addObject("action", "$base/webhooks")
            .addObject("webhooks", webhooks.list(project).map {
                WebhookRow(requireNotNull(it.id), it.payloadUrl.orEmpty(), it.secret.orEmpty(), (it.webhookType ?: WebhookType.SIMPLE).name, it.gitPush == true, "$base/webhooks/${it.id}")
            })
    }

    @PostMapping("/{user}/{project}/webhooks")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun create(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val payloadUrl = request.getParameter("payloadUrl")
        val secret = request.getParameter("secret")
        val errors = linkedMapOf<String, String>()
        if (payloadUrl.isNullOrEmpty()) errors["payloadUrl"] = "error.required"
        else if (payloadUrl.length > MAX_URL) errors["payloadUrl"] = "project.webhook.payloadUrl.tooLong"
        if (secret != null && secret.length > MAX_SECRET) errors["secret"] = "project.webhook.secret.tooLong"
        val type = request.getParameter("webhookType")?.let { value -> WebhookType.entries.firstOrNull { it.name == value } ?: run { errors["webhookType"] = "error.invalid"; null } }
        // v1.6는 `ErrorViews.BadRequest.render(errorsAsJson)`로 오류 JSON 문자열을 문구로 담은 오류 화면을 400으로 보여 준다.
        if (errors.isNotEmpty()) return ErrorViews.badRequest(errors.entries.joinToString(",", "{", "}") { "\"${it.key}\":[\"${errorText(it.value)}\"]" })

        // 체크박스는 켜면 값(`on`)이 오고 끄면 아예 오지 않는다 (v1.6 폼 묶기의 불리언 변환).
        webhooks.create(project, requireNotNull(payloadUrl), secret, request.getParameter("gitPush")?.lowercase() in TRUE_VALUES, type)
        return RedirectSupport.see(request, response, siteUrls.path("/${project.owner}/${project.name}/webhooks"))
    }

    @DeleteMapping("/{user}/{project}/webhooks/{id}")
    @IsAllowed(Operation.UPDATE)
    @Transactional
    fun delete(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        return if (webhooks.delete(project, id)) ResponseEntity.ok().build<Void>() else ErrorViews.notFound()
    }

    /** Play `errorsAsJson`이 담는 문구이다. `error.required`와 `error.invalid`는 Play 기본 문구, 나머지는 메시지 번들이다. */
    private fun errorText(key: String): String = when (key) {
        "error.required" -> "This field is required"
        "error.invalid" -> "Invalid value"
        else -> messages.getMessage(key, null, key, LocaleContextHolder.getLocale()).orEmpty()
    }

    private companion object {
        const val MAX_URL = 2000
        const val MAX_SECRET = 250
        val TRUE_VALUES = setOf("true", "on", "yes", "1")
    }
}
