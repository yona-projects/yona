package dev.yona.web.board

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingCommentService
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
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
 * 게시글 댓글의 등록, 수정, 삭제이다. v1.6 `BoardApp.newComment`, `updateComment`, `deleteComment`(`app/controllers/BoardApp.java:260-340`)에 해당한다.
 *
 * 등록과 수정은 같은 처리이다. 폼에 `id`가 있으면 그 댓글을 고치고 없으면 새로 단다. 댓글은 멀티파트 폼으로 보낸다.
 */
@Controller
@AnonymousCheck
class BoardCommentController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val commentService: PostingCommentService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val postAttachments: PostAttachments,
    private val views: ProjectViewFactory
) {

    @PostMapping("/{user}/{project}/post/{number:[0-9]+}/comment", "/{user}/{project}/post/{number:[0-9]+}/comment/{commentId}")
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    @NullProjectCheck
    @Transactional
    fun newComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.BOARD_POST.resource)
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("error.validation", views.of(project), "BOARD")
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isResourceCreatable(viewer, posting.asResource(), ResourceType.NONISSUE_COMMENT)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }

        val existing = request.getParameter("id")?.toLongOrNull()?.let { postingComments.findById(it).orElse(null) }
        val saved = if (existing != null) {
            if (!accessControl.isAllowed(viewer, existing.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
            commentService.update(existing, viewer, contents, request.getParameter("notificationMail") == "yes")
        } else {
            commentService.add(posting, viewer, contents, request.getParameter("parentCommentId")?.toLongOrNull())
        }
        postAttachments.attach(request, viewer, ResourceType.NONISSUE_COMMENT, requireNotNull(saved.id))
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/post/$number#comment-${saved.id}")
    }

    /** 댓글을 지운다. 삭제 권한이 없으면 `403`이다. Ajax 요청에는 `204`와 글 주소의 `Location`, 아니면 글로 `303`이다. */
    @DeleteMapping("/{user}/{project}/post/{number:[0-9]+}/comment/{commentId}/delete")
    @NullProjectCheck
    @Transactional
    fun deleteComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, @PathVariable("commentId") commentId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val comment = postingComments.findById(commentId).orElse(null) ?: return ErrorViews.notFound()
        val project = comment.posting?.project ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (!accessControl.isAllowed(viewer, comment.asResource(), Operation.DELETE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        commentService.delete(comment)
        val location = "/${project.owner}/${project.name}/post/$number"
        if (request.getHeader("X-Requested-With") == "XMLHttpRequest") return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, location).build<Void>()
        return RedirectSupport.see(request, response, location)
    }
}
