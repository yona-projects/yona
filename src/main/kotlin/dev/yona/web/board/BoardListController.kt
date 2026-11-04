package dev.yona.web.board

import dev.yona.domain.board.PostingSearchCondition
import dev.yona.domain.board.PostingSearchService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.issue.IssueFormOptions
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriUtils

/**
 * 프로젝트 게시판 목록이다. v1.6 `BoardApp.posts`(`app/controllers/BoardApp.java:114-130`)에 해당한다.
 *
 * 쪽 번호는 `pageNum`(1부터), 한 쪽에 15개이다. 공지는 쪽과 상관없이 목록 위에 모두 보인다.
 * 숫자가 아닌 `pageNum`, `labelIds`는 `400`이다.
 */
@Controller
@AnonymousCheck
class BoardListController(
    private val projectService: ProjectService,
    private val searchService: PostingSearchService,
    private val assembler: BoardListAssembler,
    private val labelService: IssueLabelService,
    private val formOptions: IssueFormOptions,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider
) {

    @GetMapping("/{user}/{project}/posts")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun posts(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val pageNum = request.getParameter("pageNum")?.let { it.toIntOrNull() ?: return ErrorViews.badRequest() } ?: 1
        val labelIds = request.getParameterValues("labelIds").orEmpty().filter { it.isNotEmpty() }
            .map { it.toLongOrNull() ?: return ErrorViews.badRequest() }.toSet()
        val defaults = PostingSearchCondition()
        val condition = PostingSearchCondition(
            orderBy = request.getParameter("orderBy")?.takeIf { it.isNotBlank() } ?: defaults.orderBy, orderDir = request.getParameter("orderDir") ?: defaults.orderDir,
            filter = request.getParameter("filter") ?: defaults.filter, pageNum = maxOf(pageNum - 1, 0), labelIds = labelIds
        )
        val page = searchService.search(project, condition, ITEMS_PER_PAGE)
        val labels = labelService.labels(project)
        val base = "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/posts"
        return ModelAndView("board/list")
            .addObject("title", "menu.board")
            .addObject("projectView", views.of(project))
            .addObject("cond", condition)
            .addObject("orderBy", condition.effectiveOrderBy)
            .addObject("items", assembler.assemble(page.items, viewer))
            .addObject("notices", assembler.assemble(searchService.notices(project), viewer))
            .addObject("totalRows", page.totalRows)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("sortLinks", sortLinks(base, condition, pageNum))
            .addObject("hasLabels", labels.isNotEmpty())
            .addObject("labelGroups", formOptions.labelGroups(labels))
            .addObject("selectedLabelIds", java.util.LinkedHashSet(labelIds))
    }

    /**
     * 정렬 막대의 링크이다. 현재 기준이면 방향을 뒤집고, 아니면 내림차순으로 시작한다. 검색어와 선택한 라벨을 이어 받는다.
     * v1.6 템플릿은 라벨 배열을 `labelIds=@param.labelIds`로 그대로 출력하는데 Twirl이 배열 원소를 구분자 없이 이어 붙인다.
     * 그래서 라벨 하나는 번호 그대로이고 둘 이상이면 번호가 한 값으로 붙는다(예: 3과 5는 `35`) ([refactoring-backlog.md] R-28). 같게 따른다.
     */
    private fun sortLinks(base: String, condition: PostingSearchCondition, pageNum: Int): List<SortLink> {
        // v1.6 `urlToList`는 `routes.BoardApp.posts(…, 쪽 번호)`라 쪽 번호가 1이 아니면 `?pageNum=N`이 붙은 채 `?filter=`가 이어 붙는다(물음표가 두 번 나온다).
        // 검색어도 인코딩하지 않고 그대로 출력한다
        val listUrl = if (pageNum == 1) base else "$base?pageNum=$pageNum"
        val filter = condition.filter
        // v1.6은 배열을 그대로 출력해 라벨 번호가 구분자 없이 붙었다(R-103). 라벨이 없으면 빈 `labelIds=`를 남긴다
        val labels = if (condition.labelIds.isEmpty()) "labelIds=" else condition.labelIds.joinToString("&") { "labelIds=$it" }
        val descending = !condition.orderDir.equals("asc", ignoreCase = true)
        return listOf("updatedDate" to "common.order.updatedDate", "createdDate" to "common.order.date", "numOfComments" to "common.order.comments").map { (field, key) ->
            val current = condition.effectiveOrderBy == field
            val nextDir = if (current && descending) "asc" else "desc"
            SortLink(field, "$listUrl?filter=$filter&$labels&orderBy=$field&orderDir=$nextDir", current, descending, key)
        }
    }

    private companion object {
        const val ITEMS_PER_PAGE = 15
    }
}
