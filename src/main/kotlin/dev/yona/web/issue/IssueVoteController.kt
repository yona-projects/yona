package dev.yona.web.issue

import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueCommentService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 이슈와 댓글의 추천이다. v1.6 `VoteApp`(`app/controllers/VoteApp.java`)에 해당한다. 로그인해야 하고 프로젝트를 읽을 수 있어야 한다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class IssueVoteController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val comments: IssueCommentRepository,
    private val commentService: IssueCommentService,
    private val currentUserProvider: CurrentUserProvider
) {

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/vote")
    @IsAllowed(Operation.READ)
    @Transactional
    fun vote(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, request: HttpServletRequest, response: HttpServletResponse): Any =
        voteIssue(owner, name, number, request, response, true)

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/unvote")
    @IsAllowed(Operation.READ)
    @Transactional
    fun unvote(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, request: HttpServletRequest, response: HttpServletResponse): Any =
        voteIssue(owner, name, number, request, response, false)

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/comment/{commentId}/vote")
    @IsAllowed(Operation.READ)
    @Transactional
    fun voteComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, @PathVariable("commentId") commentId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val comment = comments.findById(commentId).orElse(null) ?: return plain("issue.comment.error.vote")
        commentService.voteComment(comment, requireNotNull(currentUserProvider.get().user))
        return RedirectSupport.see(request, response, commentUrl(comment.issue?.project?.owner, comment.issue?.project?.name, comment.issue?.number, commentId))
    }

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/comment/{commentId}/unvote")
    @IsAllowed(Operation.READ)
    @Transactional
    fun unvoteComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, @PathVariable("commentId") commentId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val comment = comments.findById(commentId).orElse(null) ?: return plain("issue.comment.error.unvote")
        val viewer = requireNotNull(currentUserProvider.get().user)
        if (comment.voters.none { it.id == viewer.id }) return plain("issue.comment.error.have.not.voted")
        commentService.unvoteComment(comment, viewer)
        return RedirectSupport.see(request, response, commentUrl(comment.issue?.project?.owner, comment.issue?.project?.name, comment.issue?.number, commentId))
    }

    private fun voteIssue(owner: String, name: String, number: Long, request: HttpServletRequest, response: HttpServletResponse, vote: Boolean): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user)
        if (vote) commentService.vote(issue, viewer) else commentService.unvote(issue, viewer)
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/issue/$number")
    }

    private fun commentUrl(owner: String?, name: String?, number: Long?, commentId: Long) = "/$owner/$name/issue/$number#comment-$commentId"

    private fun plain(text: String): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType("text", "plain", Charsets.UTF_8)).body(text)
}
