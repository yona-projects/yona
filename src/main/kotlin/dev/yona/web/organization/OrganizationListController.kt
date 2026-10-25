package dev.yona.web.organization

import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueSearchCondition
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequestCategory
import dev.yona.domain.pullrequest.PullRequestSearchCondition
import dev.yona.domain.pullrequest.PullRequestSearchService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.issue.IssueConditionBinder
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/** 프로젝트 선택기의 선택지이다. */
data class OrganizationProjectOption(val name: String, val logoUrl: String?, val selected: Boolean)

/**
 * 조직의 이슈 목록과 풀 리퀘스트 목록이다. v1.6 `IssueApp.organizationIssues`(`app/controllers/IssueApp.java:50-70`),
 * `OrganizationApp.organizationPullRequests`, `organizationClosedPullRequests`(`app/controllers/OrganizationApp.java:53-79`)에 해당한다.
 *
 * 조직의 프로젝트 가운데 현재 사용자가 볼 수 있는 프로젝트([OrganizationService.visibleProjects])만 대상이다. 로그인하지 않아도 볼 수 있다.
 * 이슈 목록은 PJAX, JSON을 구분하지 않고 항상 전체 화면을 그린다(`format` 매개변수는 v1.6도 쓰지 않는다). 동작은 [web/organization-lists.md]에 있다.
 */
@Controller
@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)
class OrganizationListController(
    private val organizationService: OrganizationService,
    private val issueSearch: IssueSearchService,
    private val pullRequestSearch: PullRequestSearchService,
    private val assembler: OrganizationListAssembler,
    private val orgViews: OrganizationViewFactory,
    private val projectViews: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 조직 이슈 목록이다. */
    @GetMapping("/organizations/{organizationName}/issues")
    @Transactional(readOnly = true)
    fun issues(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val organization = organizationService.findByName(organizationName) ?: return ErrorViews.notFound("error.notfound.organization")
        val viewer = currentUserProvider.get().user
        val pageNum = request.getParameter("pageNum")?.takeIf { it.isNotBlank() }?.let { it.toIntOrNull() ?: return ErrorViews.badRequest() } ?: 1
        val names = projectNames(request)
        val condition = (IssueConditionBinder.bind(request, maxOf(pageNum - 1, 0)) ?: return ErrorViews.badRequest()).copy(projectNames = names)
        val itemsPerPage = minOf(request.getParameter("itemsPerPage")?.toIntOrNull() ?: ITEMS_PER_PAGE, ITEMS_PER_PAGE_MAX)

        val visible = organizationService.visibleProjects(organization, viewer)
        val targets = selected(visible, names)
        val ids = targets.map { requireNotNull(it.id) }
        val page = issueSearch.searchIn(ids, condition, viewer, itemsPerPage)
        return ModelAndView("organization/issue-list")
            .addObject("title", "title.issueList")
            .addObject("orgView", orgViews.of(organization))
            .addObject("cond", condition)
            .addObject("items", assembler.issueRows(page.items, viewer))
            .addObject("pageIndex", page.pageIndex)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("openCount", issueSearch.countIn(ids, condition.copy(state = State.OPEN.state), viewer))
            .addObject("closedCount", issueSearch.countIn(ids, condition.copy(state = State.CLOSED.state), viewer))
            .addObject("meId", viewer?.id)
            .addObject("projectOptions", visible.map { OrganizationProjectOption(it.name.orEmpty(), projectViews.of(it).logoUrl, names.any { n -> n == it.name }) })
    }

    /** 조직의 풀 리퀘스트 목록이다. `category`가 `open`이 아니면 닫힌 요청이다. */
    @GetMapping("/organizations/{organizationName}/pullrequests")
    @Transactional(readOnly = true)
    fun pullRequests(@PathVariable organizationName: String, request: HttpServletRequest): Any =
        pullRequestList(organizationName, request.getParameter("category") ?: "open", request)

    /** `category=closed`와 같다. */
    @GetMapping("/organizations/{organizationName}/closedPullrequests")
    @Transactional(readOnly = true)
    fun closedPullRequests(@PathVariable organizationName: String, request: HttpServletRequest): Any =
        pullRequestList(organizationName, "closed", request)

    private fun pullRequestList(organizationName: String, category: String, request: HttpServletRequest): Any {
        val organization = organizationService.findByName(organizationName) ?: return ErrorViews.notFound("error.notfound.organization")
        val viewer = currentUserProvider.get().user
        val open = category == "open"
        val ids = organizationService.visibleProjects(organization, viewer).map { requireNotNull(it.id) }
        val condition = PullRequestSearchCondition(
            null, if (open) PullRequestCategory.OPEN else PullRequestCategory.CLOSED, request.getParameter("filter"),
            request.getParameter("contributorId")?.toLongOrNull(), request.getParameter("pageNum")?.toIntOrNull() ?: 1, ids
        )
        val page = pullRequestSearch.search(condition)
        val base = "/organizations/${organization.name}"
        return ModelAndView("organization/pullrequest-list")
            .addObject("title", "title.pullrequest")
            .addObject("orgView", orgViews.of(organization))
            .addObject("filter", condition.filter.orEmpty())
            .addObject("open", open)
            .addObject("openAction", "$base/pullrequests")
            .addObject("closedAction", "$base/closedPullrequests")
            .addObject("searchAction", if (open) "$base/pullrequests" else "$base/closedPullrequests")
            .addObject("openCount", pullRequestSearch.count(condition.withCategory(PullRequestCategory.OPEN)))
            .addObject("closedCount", pullRequestSearch.count(condition.withCategory(PullRequestCategory.CLOSED)))
            .addObject("rows", assembler.pullRequestRows(page.items, viewer))
            .addObject("totalPages", page.totalPages)
    }

    /** `projectNames[]`와 `projectNames`로 온 프로젝트 이름이다. 빈 값은 버린다. */
    private fun projectNames(request: HttpServletRequest): List<String> =
        (request.getParameterValues("projectNames[]").orEmpty().toList() + request.getParameterValues("projectNames").orEmpty().toList()).filter { it.isNotEmpty() }

    /**
     * 고른 이름의 프로젝트 가운데 보이는 것을 돌려준다. 이름이 없으면 보이는 모든 프로젝트이다.
     * 이름은 대소문자를 가리지 않고 맞춘다 (v1.6 `getFilteredProjectIds`).
     */
    private fun selected(visible: List<Project>, names: List<String>): List<Project> =
        if (names.isEmpty()) visible else names.mapNotNull { name -> visible.firstOrNull { it.name.equals(name, ignoreCase = true) } }

    private companion object {
        const val ITEMS_PER_PAGE = 15
        const val ITEMS_PER_PAGE_MAX = 45
    }
}
