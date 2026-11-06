package dev.yona.web.pullrequest

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.DismissResult
import dev.yona.domain.pullrequest.PullRequestReviewRepository
import dev.yona.domain.pullrequest.PullRequestReviewService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.pullrequest.ReviewResult
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
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
 * 풀 리퀘스트 리뷰 판정의 제출과 해제 요청이다(추가 개발 항목 X-12, `decisions.md` D-35 14번). v1.6의 `ReviewApp`(`review`, `unreview`)을 대신한다.
 * 경로는 GitHub 경로에서 `/repos/{owner}/{repo}/pulls/{n}` 접두사만 Yona의 `/:user/:project/pullRequest/:id`로 바꾼 것이다.
 *
 * 로그인해야 한다. 댓글 판정은 읽기 권한이 있으면 되고 승인, 변경 요청, 해제는 받아들일 권한(프로젝트 구성원)이 필요하다. 작성자는 승인과 변경 요청을 할 수 없다.
 */
@Controller
@AnonymousCheck
class PullRequestReviewController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val reviewService: PullRequestReviewService,
    private val reviews: PullRequestReviewRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val assembler: PullRequestPageAssembler
) {

    /**
     * 판정을 제출한다. `verdict`는 `APPROVED`, `CHANGES_REQUESTED`, `COMMENTED` 중 하나(필수)이고 `contents`는 요약 본문이다(댓글과 변경 요청은 필수).
     * 성공하면 풀 리퀘스트 화면으로 돌아간다. 모르는 판정, 빠진 본문, 닫힌 요청의 승인과 변경 요청은 400, 권한이 없거나 작성자의 승인과 변경 요청은 403이다.
     */
    @PostMapping("/{user}/{project}/pullRequest/{id}/reviews")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.READ, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun submit(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user)
        val verdict = request.getParameter("verdict")?.let { raw -> ReviewVerdict.entries.firstOrNull { it.name == raw } } ?: return ErrorViews.badRequest("error.validation")
        val resource = ProjectResource(requireNotNull(pullRequest.id).toString(), ResourceType.PULL_REQUEST, project, authorId = pullRequest.contributor?.id)
        if (verdict != ReviewVerdict.COMMENTED && !accessControl.isAllowed(viewer, resource, Operation.ACCEPT)) return ErrorViews.forbidden()
        return when (reviewService.submit(pullRequest, viewer, verdict, request.getParameter("contents"))) {
            ReviewResult.SUBMITTED -> RedirectSupport.see(request, response, assembler.baseUrl(pullRequest))
            ReviewResult.AUTHOR_NOT_ALLOWED -> ErrorViews.forbidden()
            ReviewResult.NOT_OPEN, ReviewResult.BODY_REQUIRED -> ErrorViews.badRequest("error.validation")
        }
    }

    /** 리뷰를 해제한다. `message`(사유)가 필수이고 받아들일 권한이 필요하다. 없는 리뷰는 404, 사유가 없거나 이미 해제했으면 400이다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/reviews/{reviewId}/dismissals")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.ACCEPT, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun dismiss(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, @PathVariable reviewId: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        val review = reviews.findById(reviewId).orElse(null) ?: return ErrorViews.notFound()
        return when (reviewService.dismiss(pullRequest, review, requireNotNull(currentUserProvider.get().user), request.getParameter("message"))) {
            DismissResult.DISMISSED -> RedirectSupport.see(request, response, assembler.baseUrl(pullRequest))
            DismissResult.NOT_FOUND -> ErrorViews.notFound()
            DismissResult.ALREADY_DISMISSED, DismissResult.MESSAGE_REQUIRED -> ErrorViews.badRequest("error.validation")
        }
    }
}
