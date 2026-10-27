package dev.yona.web

import dev.yona.access.AccessControl
import dev.yona.access.Resource
import dev.yona.access.ResourceLookup
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.WatchService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod

/**
 * 리소스 구독과 구독 해제이다. v1.6 `WatchApp`(`app/controllers/WatchApp.java`)에 해당한다.
 * 대상은 `resource.type`(예: `issue_post`)과 `resource.id`로 지정한다. 알 수 없는 종류나 없는 대상은 `400`이다.
 */
@Controller
class WatchController(
    private val lookup: ResourceLookup,
    private val accessControl: AccessControl,
    private val watchService: WatchService,
    private val currentUserProvider: CurrentUserProvider,
    private val urls: dev.yona.domain.misc.ResourceUrls,
    private val views: ProjectViewFactory,
    private val messages: MessageSource
) {

    @PostMapping("/watch")
    @Transactional
    fun watch(request: HttpServletRequest): Any {
        val resource = resolve(request) ?: return ErrorViews.badRequest()
        val user = currentUserProvider.get().user ?: return text(HttpStatus.FORBIDDEN, "Anonymous cannot watch it.")
        if (!accessControl.isAllowed(user, resource, Operation.READ)) return text(HttpStatus.FORBIDDEN, "You have no permission to watch it.")
        watchService.watch(user, resource.type, resource.id)
        return ResponseEntity.ok().build<Void>()
    }

    @RequestMapping("/unwatch", method = [RequestMethod.POST, RequestMethod.GET])
    @Transactional
    fun unwatch(request: HttpServletRequest, response: HttpServletResponse): Any {
        val resource = resolve(request) ?: return ErrorViews.badRequest()
        val user = currentUserProvider.get().user
        val project = resource.project
        if (user == null) return ErrorViews.forbidden("issue.error.unwatch.anonymous", views.of(requireNotNull(project)))
        if (!accessControl.isAllowed(user, resource, Operation.READ)) return ErrorViews.forbidden("issue.error.unwatch.permission", views.of(requireNotNull(project)))
        val first = watchService.unwatch(user, resource.type, resource.id)
        if (HttpPreference.isJsonPreferred(request.getHeader("Accept"))) return ResponseEntity.ok().build<Void>()
        val flash = if (first) unwatchMessage(resource.type)?.let { mapOf<String, Any>(RedirectSupport.SUCCESS to it) }.orEmpty() else emptyMap()
        return RedirectSupport.see(request, response, urls.urlOf(resource.type, resource.id) ?: "/", flash)
    }

    /**
     * 질의 문자열에서 이름이 [name]인 첫 값을 찾는다. v1.6의 Netty 질의 해석처럼 `&`뿐 아니라 `;`도 구분자로 본다.
     * 화면이 스크립트에 찍어 주는 구독 주소가 `&amp;`로 구분되어 있어(Twirl의 이스케이프) 서버가 이를 받아들여야 같은 동작이 된다.
     * 질의 문자열에 없으면 폼 본문까지 포함한 요청 인자로 되돌아간다.
     */
    private fun queryValue(request: HttpServletRequest, name: String): String? {
        val query = request.queryString
        if (query != null) {
            for (pair in query.split('&', ';')) {
                val eq = pair.indexOf('=')
                val key = java.net.URLDecoder.decode(if (eq < 0) pair else pair.substring(0, eq), Charsets.UTF_8)
                if (key == name) return if (eq < 0) "" else java.net.URLDecoder.decode(pair.substring(eq + 1), Charsets.UTF_8)
            }
        }
        return request.getParameter(name)
    }

    private fun resolve(request: HttpServletRequest): Resource? {
        val type = ResourceType.entries.firstOrNull { it.resource == queryValue(request, "resource.type") && it != ResourceType.NOT_A_RESOURCE } ?: return null
        val id = queryValue(request, "resource.id")?.takeIf { if (type == ResourceType.COMMIT) Regex("\\d+:[0-9a-fA-F]+").matches(it) else it.toLongOrNull() != null } ?: return null
        return try { lookup.get(type, id) } catch (e: RuntimeException) { null }
    }

    private fun unwatchMessage(type: ResourceType): String? {
        val key = when (type) {
            ResourceType.ISSUE_POST, ResourceType.ISSUE_COMMENT -> "issue.unwatch.start"
            ResourceType.BOARD_POST, ResourceType.NONISSUE_COMMENT -> "post.unwatch.start"
            ResourceType.PULL_REQUEST, ResourceType.REVIEW_COMMENT -> "pullRequest.unwatch.start"
            ResourceType.PROJECT -> "project.unwatch.start"
            else -> return null
        }
        return messages.getMessage(key, null, key, LocaleContextHolder.getLocale())
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).contentType(MediaType("text", "plain", Charsets.UTF_8)).body(body)
}
