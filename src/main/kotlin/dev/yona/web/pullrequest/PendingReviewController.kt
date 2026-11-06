package dev.yona.web.pullrequest

import dev.yona.domain.comment.PendingReviewService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 임시 저장 리뷰의 줄 댓글을 지우고 리뷰를 버리는 요청이다(로드맵 X-26, `decisions.md` D-38). 로그인만 하면 되고 내 임시 댓글만 다룬다.
 * 남의 임시 댓글이거나 공개된 댓글이면 404이다(공개 댓글은 기존 삭제 경로가 지운다). 제출은 리뷰 제출(`PullRequestReviewController`)이 함께 한다.
 */
@Controller
@AnonymousCheck
class PendingReviewController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val pendingReview: PendingReviewService,
    private val assembler: PullRequestPageAssembler,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 내 임시 댓글 하나를 지우고 변경 화면으로 돌아간다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/pendingReview/comments/{commentId}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun deleteComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @PathVariable commentId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val pullRequest = projectService.findByOwnerAndProjectName(owner, name)?.let { pullRequestService.findOne(it, id) } ?: return ErrorViews.notFound()
        if (!pendingReview.deleteComment(pullRequest, requireNotNull(currentUserProvider.get().user), commentId)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, assembler.baseUrl(pullRequest) + "/changes")
    }

    /** 내 임시 댓글과 임시 스레드를 모두 버리고 개요로 돌아간다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/pendingReview/discard")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun discard(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val pullRequest = projectService.findByOwnerAndProjectName(owner, name)?.let { pullRequestService.findOne(it, id) } ?: return ErrorViews.notFound()
        pendingReview.discard(pullRequest, requireNotNull(currentUserProvider.get().user))
        return RedirectSupport.see(request, response, assembler.baseUrl(pullRequest))
    }
}
