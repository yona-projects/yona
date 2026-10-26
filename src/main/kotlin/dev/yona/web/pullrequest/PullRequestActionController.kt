package dev.yona.web.pullrequest

import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.AcceptResult
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestForm
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.pullrequest.PullRequestWorkflow
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.repository.BranchService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 풀 리퀘스트의 받아들이기, 닫기, 다시 열기, 수정, 보내는 브랜치 삭제와 복원이다. v1.6 `PullRequestApp.accept`, `close`, `open`, `editPullRequestForm`,
 * `editPullRequest`, `deleteFromBranch`, `restoreFromBranch`(`app/controllers/PullRequestApp.java:384-560`)에 해당한다. 모두 로그인해야 하고 풀 리퀘스트에 대한
 * 권한(받아들이기 `ACCEPT`, 닫기 `CLOSE`, 다시 열기 `REOPEN`, 수정과 브랜치 삭제 `UPDATE`)이 필요하다. 끝나면 풀 리퀘스트 화면으로 303 이동한다.
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class PullRequestActionController(
    private val projectService: ProjectService,
    private val pullRequestService: PullRequestService,
    private val workflow: PullRequestWorkflow,
    private val branches: BranchService,
    private val postAttachments: PostAttachments,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val siteUrls: SiteUrls
) {

    @PostMapping("/{user}/{project}/pullRequest/{id}/accept")
    @IsAllowed(Operation.ACCEPT, resourceType = ResourceType.PULL_REQUEST)
    // 트랜잭션을 걸지 않는다: 병합 중 예외가 나도 먼저 저장한 병합 중 표시가 남아야 v1.6과 같다 (R-76)
    @IsOnlyGitAvailable
    fun accept(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        if (workflow.accept(pullRequest, requireNotNull(currentUserProvider.get().user)) == AcceptResult.NOT_REVIEWED) {
            return ErrorViews.badRequest("pullRequest.not.enough.review.point")
        }
        return redirect(request, response, project, pullRequest)
    }

    @PostMapping("/{user}/{project}/pullRequest/{id}/close")
    @IsAllowed(Operation.CLOSE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun close(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        workflow.close(pullRequest, requireNotNull(currentUserProvider.get().user))
        return redirect(request, response, project, pullRequest)
    }

    @PostMapping("/{user}/{project}/pullRequest/{id}/open")
    @IsAllowed(Operation.REOPEN, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun open(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        if (!workflow.reopen(pullRequest, requireNotNull(currentUserProvider.get().user))) return ErrorViews.badRequest()
        return redirect(request, response, project, pullRequest)
    }

    @GetMapping("/{user}/{project}/pullRequest/{id}/editform")
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun editForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long): Any {
        val (toProject, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        val from = requireNotNull(pullRequest.fromProject)
        val fromBase = siteUrls.path("/${from.owner}/${from.name}")
        return ModelAndView("pullrequest/edit")
            .addObject("title", "title.editPullRequest")
            .addObject("projectView", views.of(toProject))
            .addObject("markdownProject", views.of(from))
            .addObject("action", "${base(pullRequest)}/edit")
            .addObject("pullRequestId", pullRequest.id)
            .addObject("fromLabel", "${from.owner} / ${from.name}").addObject("fromProjectId", from.id).addObject("fromBranch", pullRequest.fromBranch)
            .addObject("toLabel", "${toProject.owner} / ${toProject.name}").addObject("toProjectId", toProject.id).addObject("toBranch", pullRequest.toBranch)
            .addObject("fromBranches", branches.branches(from).map { BranchChoice(it.name, it.name == pullRequest.fromBranch) })
            .addObject("toBranches", branches.branches(toProject).map { BranchChoice(it.name, it.name == pullRequest.toBranch) })
            .addObject("state", (pullRequest.state ?: dev.yona.domain.enumeration.State.OPEN).name)
            .addObject("open", pullRequest.open)
            .addObject("prTitle", pullRequest.title.orEmpty())
            .addObject("body", pullRequest.body.orEmpty())
            .addObject("mergeResultUrl", "$fromBase/newPullRequest/mergeResult")
            .addObject("mentionUrl", "$fromBase/mentionList")
    }

    @PostMapping("/{user}/{project}/pullRequest/{id}/edit")
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun edit(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        val title = request.getParameter("title")
        val fromBranch = request.getParameter("fromBranch")
        val toBranch = request.getParameter("toBranch")
        if (title.isNullOrEmpty() || fromBranch.isNullOrEmpty() || toBranch.isNullOrEmpty() || title.length > MAX_TITLE) {
            return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
        }
        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()
        val from = requireNotNull(pullRequest.fromProject)

        if (fromBranch != pullRequest.fromBranch || toBranch != pullRequest.toBranch) {
            val duplicate = pullRequestService.findDuplicate(PullRequestForm(title, body, from, project, fromBranch, toBranch))
            if (duplicate != null) {
                // v1.6는 수정 화면 주소를 받는 프로젝트가 아니라 보내는 프로젝트로 만든다 ([refactoring-backlog.md] R-43).
                return RedirectSupport.see(
                    request, response, siteUrls.path("/${from.owner}/${from.name}/pullRequest/${pullRequest.number}/editform"), mapOf(RedirectSupport.WARNING to "pullRequest.duplicated")
                )
            }
        }

        val user = requireNotNull(currentUserProvider.get().user)
        workflow.update(pullRequest, title, body, fromBranch, toBranch)
        val flash = postAttachments.attach(request, user, ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id))
        return RedirectSupport.see(request, response, base(pullRequest), flash)
    }

    @DeleteMapping("/{user}/{project}/pullRequest/{id}/deletefrombranch")
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun deleteFromBranch(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        workflow.deleteFromBranch(pullRequest)
        return redirect(request, response, project, pullRequest)
    }

    @PostMapping("/{user}/{project}/pullRequest/{id}/restorefrombranch")
    @IsAllowed(Operation.UPDATE, resourceType = ResourceType.PULL_REQUEST)
    @Transactional
    @IsOnlyGitAvailable
    fun restoreFromBranch(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val (project, pullRequest) = find(owner, name, id) ?: return ErrorViews.notFound()
        workflow.restoreFromBranch(pullRequest)
        return redirect(request, response, project, pullRequest)
    }

    private fun find(owner: String, name: String, id: Long): Pair<Project, PullRequest>? {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return null
        val pullRequest = pullRequestService.findOne(project, id) ?: return null
        return project to pullRequest
    }

    private fun base(pullRequest: PullRequest): String {
        val project = requireNotNull(pullRequest.toProject)
        return siteUrls.path("/${project.owner}/${project.name}/pullRequest/${pullRequest.number}")
    }

    private fun redirect(request: HttpServletRequest, response: HttpServletResponse, project: Project, pullRequest: PullRequest) =
        RedirectSupport.see(request, response, siteUrls.path("/${project.owner}/${project.name}/pullRequest/${pullRequest.number}"))

    private companion object {
        const val MAX_TITLE = 255
    }
}
