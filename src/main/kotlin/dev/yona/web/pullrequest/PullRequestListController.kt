package dev.yona.web.pullrequest

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.project.Project
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestCategory
import dev.yona.domain.pullrequest.PullRequestSearchCondition
import dev.yona.domain.pullrequest.PullRequestSearchService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
import dev.yona.web.code.CodePageAssembler
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView
import java.util.Date

/** 목록 한 줄이 보여 줄 보낸 사람이다. */
data class PullRequestPerson(val loginId: String, val name: String, val displayName: String, val href: String, val avatarUrl: String)

/** 목록 한 줄이 보여 줄 리뷰 스레드 진행 상황이다. */
data class PullRequestReviewProgress(val closed: Long, val total: Long, val percent: Int, val href: String)

/** 풀 리퀘스트 목록의 한 줄이다. */
data class PullRequestRow(
    val number: Long, val href: String, val headerWordsHtml: String, val title: String, val conflict: Boolean, val contributor: PullRequestPerson?,
    val ago: String, val agoTitle: String, val progress: PullRequestReviewProgress?, val reviewerCount: Int?, val reviewerNames: String, val reviewedByMe: Boolean,
    val toBranch: String, val toDefaultBranch: Boolean, val receiver: PullRequestPerson?, val stateClass: String, val stateKey: String,
    /** 담당자(추가 항목 X-14, D-34). 받은 사람(`receiver`)과 다른 역할이라 따로 보인다 */
    val assignee: PullRequestPerson? = null,
    /** 붙은 라벨(분류 이름, 라벨 이름 순) */
    val labels: List<dev.yona.web.issue.IssueLabelTag> = emptyList()
)

/** 최근 푸시한 브랜치 한 줄이다. */
data class PushedBranchRow(val label: String, val ago: String, val newPullRequestUrl: String, val deleteUrl: String)

/** 보낸 사람 선택지 하나이다. */
/** 담당자 필터에서 "담당자 없음"을 뜻하는 `assigneeId` 값이다(GitHub의 `no:assignee`). */
private const val NO_ASSIGNEE = "none"

data class ContributorOption(val value: String, val label: String, val avatarUrl: String?, val loginId: String?, val selected: Boolean)

/**
 * 풀 리퀘스트 목록(열림, 닫힘, 보냄)이다. v1.6 `PullRequestApp.pullRequests`, `closedPullRequests`, `sentPullRequests`
 * (`app/controllers/PullRequestApp.java:326-367`)와 `ProjectApp.deletePushedBranch`에 해당한다.
 *
 * 코드 접근을 구성원으로 제한한 프로젝트는 비구성원에게 403이다. PJAX 요청(`X-PJAX`)에는 검색 영역 조각만 돌려준다.
 */
@Controller
@AnonymousCheck
class PullRequestListController(
    private val projectService: ProjectService,
    private val search: PullRequestSearchService,
    private val users: UserRepository,
    private val threads: CommentThreadRepository,
    private val pushedBranches: PushedBranchRepository,
    private val projects: dev.yona.domain.project.ProjectRepository,
    private val repositories: RepositoryService,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val listService: dev.yona.domain.issue.IssueListService,
    private val labelService: dev.yona.domain.issue.IssueLabelService,
    private val formOptions: dev.yona.web.issue.IssueFormOptions
) {

    @GetMapping("/{user}/{project}/pullRequests")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun open(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any = list(owner, name, PullRequestCategory.OPEN, request, response)

    @GetMapping("/{user}/{project}/closedPullRequests")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun closed(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any = list(owner, name, PullRequestCategory.CLOSED, request, response)

    @GetMapping("/{user}/{project}/sentPullRequests")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun sent(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any = list(owner, name, PullRequestCategory.SENT, request, response)

    private fun list(owner: String, name: String, category: PullRequestCategory, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (project.isCodeAccessibleMemberOnly == true && !projectService.hasMember(project, viewer)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }

        val condition = PullRequestSearchCondition(
            project, category, request.getParameter("filter"), request.getParameter("contributorId")?.toLongOrNull(),
            request.getParameter("pageNum")?.toIntOrNull() ?: 1,
            assigneeId = request.getParameter("assigneeId")?.toLongOrNull(), noAssignee = request.getParameter("assigneeId") == NO_ASSIGNEE,
            labelIds = request.getParameterValues("labelIds").orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            reviewRequestedId = request.getParameter("reviewRequestedId")?.toLongOrNull()
        )
        val projectLabels = labelService.labels(project)
        val page = search.search(condition)
        val base = siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name))
        val defaultBranch = repositories.defaultBranch(project)
        val projectView = views.of(project)
        val sentLabel = "${search.count(condition.withCategory(PullRequestCategory.ACCEPTED))} / ${search.count(condition.withCategory(PullRequestCategory.SENT))}"

        val view = if (AjaxRequests.isPjax(request)) "pullrequest/partial-search :: search" else "pullrequest/list"
        if (view != "pullrequest/list") response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        return ModelAndView(view)
            .addObject("title", "menu.pullRequest")
            .addObject("projectView", projectView)
            .addObject("categoryCode", category.code)
            .addObject("filter", condition.filter.orEmpty())
            .addObject("hasContributorFilter", category != PullRequestCategory.SENT)
            .addObject("searchAction", searchAction(base, category))
            .addObject("openAction", searchAction(base, PullRequestCategory.OPEN))
            .addObject("closedAction", searchAction(base, PullRequestCategory.CLOSED))
            .addObject("sentAction", searchAction(base, PullRequestCategory.SENT))
            .addObject("openCount", search.count(condition.withCategory(PullRequestCategory.OPEN)))
            .addObject("closedCount", search.count(condition.withCategory(PullRequestCategory.CLOSED)))
            .addObject("sentLabel", sentLabel)
            .addObject("forked", project.originalProject != null)
            .addObject("newPullRequestUrl", "$base/newPullRequestForm")
            .addObject("contributors", contributorOptions(project, condition.contributorId, viewer))
            .addObject("assignees", assigneeOptions(project, condition, viewer))
            // 추가 항목 X-27(D-37): "리뷰 요청" 필터는 로그인했을 때 "나에게 요청됨"만 고른다(GitHub의 `review-requested:@me`)
            .addObject("reviewRequestedMe", viewer?.let { ContributorOption(it.id.toString(), "", null, null, condition.reviewRequestedId == it.id) })
            .addObject("labelGroups", formOptions.labelGroups(projectLabels))
            .addObject("selectedLabelIds", condition.labelIds.toMutableSet())
            .addObject("hasLabels", projectLabels.isNotEmpty())
            .addObject("pushedBranches", pushedBranchRows(project, viewer))
            .addObject("rows", page.items.map { row(it, project, defaultBranch, viewer) })
            .addObject("totalPages", page.totalPages)
    }

    /** 최근 푸시 기록의 닫기 단추가 부르는 요청이다. 삭제 권한이 필요하다 (v1.6 `ProjectApp.deletePushedBranch`). */
    @DeleteMapping("/{user}/{project}/pushedBranch/{id}/delete")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.DELETE)
    @Transactional
    fun deletePushedBranch(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable id: Long): ResponseEntity<Void> {
        val project = projectService.findByOwnerAndProjectName(owner, name)
        pushedBranches.findById(id).orElse(null)?.takeIf { it.project?.id == project?.id }?.let { pushedBranches.delete(it) }
        return ResponseEntity.ok().build()
    }

    private fun searchAction(base: String, category: PullRequestCategory) = when (category) {
        PullRequestCategory.SENT -> "$base/sentPullRequests"
        PullRequestCategory.CLOSED -> "$base/closedPullRequests"
        else -> "$base/pullRequests"
    }

    private fun contributorOptions(project: Project, selectedId: Long?, viewer: User?): List<ContributorOption> {
        val contributors = users.findPullRequestContributors(requireNotNull(project.id))
        val options = mutableListOf(ContributorOption("", "", null, null, false))
        if (viewer != null && contributors.any { it.id == viewer.id }) options += ContributorOption(viewer.id.toString(), "", null, null, false)
        contributors.forEach { options += ContributorOption(it.id.toString(), it.name.orEmpty(), avatars.avatarUrl(it), it.loginId, selectedId != null && selectedId == it.id) }
        return options
    }

    /**
     * 담당자 필터의 선택지이다(D-34, GitHub 기준). 전체, 로그인했으면 "내게 할당됨"(`sentByMe`처럼 아바타 없는 항목), "담당자 없음"(`none`) 다음에
     * 담당자 후보(받는 프로젝트의 이슈 담당자 후보 규칙)를 이름순으로 보인다.
     */
    private fun assigneeOptions(project: Project, condition: PullRequestSearchCondition, viewer: User?): List<ContributorOption> {
        val selectedId = condition.assigneeId
        val options = mutableListOf(ContributorOption("", "", null, null, false))
        if (viewer != null) options += ContributorOption(viewer.id.toString(), "", null, null, selectedId == viewer.id)
        options += ContributorOption(NO_ASSIGNEE, "", null, null, condition.noAssignee)
        listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer)).forEach {
            options += ContributorOption(it.id.toString(), it.name.orEmpty(), avatars.avatarUrl(it), it.loginId, selectedId != null && selectedId == it.id)
        }
        return options
    }

    /** 프로젝트가 포크이면 그 프로젝트의 최근 푸시 브랜치를, 아니면 내가 이 프로젝트에서 포크한 프로젝트들의 것을 보여 준다. */
    private fun pushedBranchRows(project: Project, viewer: User?): List<PushedBranchRow> {
        val branches = if (project.originalProject != null) {
            recentlyPushed(project)
        } else if (accessControl.isProjectResourceCreatable(viewer, project, ResourceType.PULL_REQUEST) && viewer != null) {
            projects.findByOwnerAndOriginalProjectId(viewer.loginId.orEmpty(), requireNotNull(project.id)).flatMap { recentlyPushed(it) }
        } else {
            emptyList()
        }
        return branches.map { branch ->
            val from = requireNotNull(branch.project)
            val base = siteUrls.path(dev.yona.util.UrlPaths.projectPath(from.owner, from.name))
            val target = repositories.defaultBranch(from.originalProject ?: from)
            PushedBranchRow(
                "${from.owner}/${from.name}:${CodePageAssembler.branchItemName(branch.name.orEmpty())}", helper.agoOrDateString(branch.pushedDate),
                "$base/newPullRequestForm?fromBranch=${branch.name}&toBranch=$target", "$base/pushedBranch/${branch.id}/delete"
            )
        }
    }

    private fun recentlyPushed(project: Project) =
        pushedBranches.findByProjectIdAndPushedDateAfter(requireNotNull(project.id), Date(System.currentTimeMillis() - RECENT_MILLIS))

    private fun row(pr: PullRequest, project: Project, defaultBranch: String, viewer: User?): PullRequestRow {
        val toProject = requireNotNull(pr.toProject)
        val base = siteUrls.path("${dev.yona.util.UrlPaths.projectPath(toProject.owner, toProject.name)}/pullRequest/${pr.number}")
        val id = requireNotNull(pr.id)
        val total = threads.countByPullRequestId(id)
        val closed = if (total > 0) threads.countByPullRequestIdAndState(id, ThreadState.CLOSED) else 0
        val conflict = pr.isConflict == true
        val state = (pr.state ?: dev.yona.domain.enumeration.State.OPEN).name.lowercase()
        val toBranch = CodePageAssembler.branchItemName(pr.toBranch.orEmpty())
        return PullRequestRow(
            number = requireNotNull(pr.number), href = base, headerWordsHtml = helper.headerWordsHtml(pr.title), title = helper.removeHeaderWords(pr.title), conflict = conflict,
            contributor = pr.contributor?.let { person(it, viewer, true) }, ago = helper.agoOrDateString(pr.created), agoTitle = helper.dateString(pr.created),
            progress = if (total > 0) PullRequestReviewProgress(closed, total, helper.percent(closed.toDouble(), total.toDouble()), "$base/changes") else null,
            reviewerCount = if (pr.reviewers.isNotEmpty()) pr.reviewers.size else null,
            reviewerNames = pr.reviewers.joinToString("<br>") { it.name.orEmpty() }, reviewedByMe = viewer != null && pr.reviewers.any { it.id == viewer.id },
            toBranch = toBranch, toDefaultBranch = CodePageAssembler.branchItemName(defaultBranch) == toBranch,
            receiver = pr.receiver?.let { person(it, viewer, false) }, stateClass = if (conflict) "conflict" else state, stateKey = if (conflict) "pullRequest.state.conflict" else "pullRequest.state.$state",
            assignee = pr.assignees.firstOrNull()?.let { person(it, viewer, false) },
            labels = labelTagsOf(pr)
        )
    }

    private fun person(user: User, viewer: User?, gravatarFallback: Boolean): PullRequestPerson {
        val avatar = avatars.avatarUrl(user)
        val shown = if (gravatarFallback && avatar == AvatarService.DEFAULT_AVATAR_URL) avatars.gravatarUrl(user.email, AVATAR_SIZE) else avatar
        return PullRequestPerson(user.loginId.orEmpty(), user.name.orEmpty(), UserDisplay.of(user, viewer?.lang), siteUrls.path("/${user.loginId}"), shown)
    }

    private companion object {
        const val RECENT_MILLIS = 1000L * 60 * 60
        const val AVATAR_SIZE = 32
    }
}
