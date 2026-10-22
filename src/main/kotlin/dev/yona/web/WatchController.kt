package dev.yona.web

import dev.yona.access.AccessControl
import dev.yona.access.Resource
import dev.yona.access.ResourceLookup
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueRepository
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
    private val issues: IssueRepository,
    private val comments: IssueCommentRepository,
    private val postings: PostingRepository,
    private val views: ProjectViewFactory,
    private val messages: MessageSource
) {

    @PostMapping("/watch")
    @Transactional
    fun watch(request: HttpServletRequest): Any {
        val resource = resolve(request) ?: return ResponseEntity.badRequest().build<Void>()
        val user = currentUserProvider.get().user ?: return text(HttpStatus.FORBIDDEN, "Anonymous cannot watch it.")
        if (!accessControl.isAllowed(user, resource, Operation.READ)) return text(HttpStatus.FORBIDDEN, "You have no permission to watch it.")
        watchService.watch(user, resource.type, resource.id)
        return ResponseEntity.ok().build<Void>()
    }

    @RequestMapping("/unwatch", method = [RequestMethod.POST, RequestMethod.GET])
    @Transactional
    fun unwatch(request: HttpServletRequest, response: HttpServletResponse): Any {
        val resource = resolve(request) ?: return ResponseEntity.badRequest().build<Void>()
        val user = currentUserProvider.get().user
        val project = resource.project
        if (user == null) return ErrorViews.forbidden("issue.error.unwatch.anonymous", views.of(requireNotNull(project)))
        if (!accessControl.isAllowed(user, resource, Operation.READ)) return ErrorViews.forbidden("issue.error.unwatch.permission", views.of(requireNotNull(project)))
        val first = watchService.unwatch(user, resource.type, resource.id)
        if (HttpPreference.isJsonPreferred(request.getHeader("Accept"))) return ResponseEntity.ok().build<Void>()
        val flash = if (first) unwatchMessage(resource.type)?.let { mapOf<String, Any>(RedirectSupport.SUCCESS to it) }.orEmpty() else emptyMap()
        return RedirectSupport.see(request, response, urlOf(resource) ?: "/", flash)
    }

    private fun resolve(request: HttpServletRequest): Resource? {
        val type = ResourceType.entries.firstOrNull { it.resource == request.getParameter("resource.type") && it != ResourceType.NOT_A_RESOURCE } ?: return null
        val id = request.getParameter("resource.id")?.takeIf { it.toLongOrNull() != null } ?: return null
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

    /** 리소스의 화면 주소이다 (v1.6 `RouteUtil.getUrl(type, id)`). */
    private fun urlOf(resource: Resource): String? {
        val project = resource.project ?: return null
        val base = "/${project.owner}/${project.name}"
        return when (resource.type) {
            ResourceType.PROJECT -> base
            ResourceType.ISSUE_POST -> "$base/issue/${issues.findById(resource.id.toLong()).orElse(null)?.number}"
            ResourceType.ISSUE_COMMENT -> comments.findById(resource.id.toLong()).orElse(null)?.let { "$base/issue/${it.issue?.number}#comment-${it.id}" }
            ResourceType.BOARD_POST -> postings.findById(resource.id.toLong()).orElse(null)?.let { "$base/post/${it.number}" }
            else -> null
        }
    }

    private fun text(status: HttpStatus, body: String): ResponseEntity<String> =
        ResponseEntity.status(status).contentType(MediaType("text", "plain", Charsets.UTF_8)).body(body)
}
