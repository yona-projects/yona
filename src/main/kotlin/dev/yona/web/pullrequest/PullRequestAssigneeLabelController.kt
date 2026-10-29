package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.AssignResult
import dev.yona.domain.pullrequest.LabelResult
import dev.yona.domain.pullrequest.PullRequestAssigneeService
import dev.yona.domain.pullrequest.PullRequestLabelService
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping

/**
 * 풀 리퀘스트의 담당자와 라벨을 바꾸는 요청이다. v1.6에 없는 추가 항목이다(로드맵 X-14, `decisions.md` D-34).
 * 로그인해야 하고 풀 리퀘스트 수정 권한(`UPDATE`)이 필요하다(프로젝트 구성원과 공개, 보호 조직 프로젝트의 조직 구성원). 끝나면 풀 리퀘스트 화면으로 303 이동한다.
 * 로그인 검사는 [PullRequestActionController]와 같이 메서드마다 `@IsAllowed` 앞에 둔다.
 */
@Controller
@AnonymousCheck
class PullRequestAssigneeLabelController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val assigneeService: PullRequestAssigneeService,
    private val labelService: PullRequestLabelService,
    private val issueLabels: IssueLabelRepository,
    private val users: UserRepository,
    private val currentUserProvider: CurrentUserProvider,
    private val siteUrls: SiteUrls
) {

    /** 담당자를 지정한다. `assigneeId`가 비어 있으면 해제한다. 후보가 아닌 사람이나 없는 사용자는 400이다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/assignee")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun assignee(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        val raw = request.getParameter("assigneeId")
        val assignee = if (raw.isNullOrBlank()) null else (raw.toLongOrNull()?.let { users.findById(it).orElse(null) } ?: return ErrorViews.badRequest("error.validation"))
        if (assigneeService.set(pullRequest, assignee, requireNotNull(currentUserProvider.get().user)) == AssignResult.NOT_CANDIDATE) return ErrorViews.badRequest("error.validation")
        return redirect(request, response, owner, name, id)
    }

    /** 라벨을 `labelIds`(여러 개, 비어 있으면 모두 뗌)와 같게 맞춘다. 없는 라벨이나 다른 프로젝트의 라벨이 하나라도 있으면 400이고 아무것도 바꾸지 않는다. */
    @PostMapping("/{user}/{project}/pullRequest/{id}/labels")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun labels(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val pullRequest = pullRequestService.findOne(project, id) ?: return ErrorViews.notFound()
        val labels = request.getParameterValues("labelIds").orEmpty().filter { it.isNotBlank() }.map {
            it.toLongOrNull()?.let { labelId -> issueLabels.findById(labelId).orElse(null) } ?: return ErrorViews.badRequest("error.validation")
        }
        if (labelService.replace(pullRequest, labels, requireNotNull(currentUserProvider.get().user)) == LabelResult.WRONG_PROJECT) return ErrorViews.badRequest("error.validation")
        return redirect(request, response, owner, name, id)
    }

    private fun redirect(request: HttpServletRequest, response: HttpServletResponse, owner: String, name: String, number: Long) =
        RedirectSupport.see(request, response, siteUrls.path("${dev.yona.util.UrlPaths.projectPath(owner, name)}/pullRequest/$number"))
}
