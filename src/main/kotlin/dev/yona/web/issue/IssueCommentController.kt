package dev.yona.web.issue

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueCommentService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.NullProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 이슈 댓글의 등록, 수정, 삭제이다. v1.6 `IssueApp.newComment`, `updateComment`, `deleteComment`
 * (`app/controllers/IssueApp.java:955-1140`)에 해당한다.
 *
 * 등록과 수정은 같은 처리이다. 폼에 `id`가 있으면 그 댓글을 고치고 없으면 새로 단다(v1.6은 `updateComment`가 `newComment`를 그대로 부른다).
 * 댓글은 멀티파트 폼으로 보낸다.
 */
@Controller
@AnonymousCheck
class IssueCommentController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val comments: IssueCommentRepository,
    private val commentService: IssueCommentService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val postAttachments: PostAttachments,
    private val views: ProjectViewFactory
) {

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/comments", "/{user}/{project}/issue/{number:[0-9]+}/comments/{commentId}")
    @NullProjectCheck
    @Transactional
    fun newComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.ISSUE_POST.resource)
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isResourceCreatable(viewer, issue.asResource(), ResourceType.ISSUE_COMMENT)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("post.comment.empty", views.of(project), "PROJECT_HOME")

        val withTransition = request.contentType?.startsWith("multipart/") == true && !request.getParameter("withStateTransition").isNullOrBlank()
        val existing = request.getParameter("id")?.toLongOrNull()?.let { comments.findById(it).orElse(null) }
        val saved = if (existing != null) {
            if (!accessControl.isAllowed(viewer, existing.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
            commentService.update(existing, viewer, contents, request.getParameter("notificationMail") == "yes", withTransition)
        } else {
            commentService.add(issue, viewer, contents, request.getParameter("parentCommentId")?.toLongOrNull(), withTransition)
        }
        val flash = postAttachments.attach(request, viewer, ResourceType.ISSUE_COMMENT, requireNotNull(saved.id))
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/issue/$number#comment-${saved.id}", flash)
    }

    /** 댓글을 지운다. 삭제 권한이 없으면 `403`이다. Ajax 요청에는 `204`와 이슈 주소의 `Location`, 아니면 이슈로 `303`이다. */
    @DeleteMapping("/{user}/{project}/issue/{number:[0-9]+}/comment/{commentId}/delete")
    @NullProjectCheck
    @Transactional
    fun deleteComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, @PathVariable("commentId") commentId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val comment = comments.findById(commentId).orElse(null) ?: return ErrorViews.notFound()
        val project = comment.issue?.project ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (!accessControl.isAllowed(viewer, comment.asResource(), Operation.DELETE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        commentService.delete(comment)
        val location = "/${project.owner}/${project.name}/issue/$number"
        if (AjaxRequests.isXhr(request)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, location).build<Void>()
        }
        return RedirectSupport.see(request, response, location)
    }
}
