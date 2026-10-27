package dev.yona.web.board

import dev.yona.domain.board.PostingSearchCondition
import dev.yona.domain.board.PostingSearchService
import dev.yona.domain.project.OrganizationService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.organization.OrganizationViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 조직 게시판 목록이다. 조직의 모든 프로젝트 게시판 글을 모아 보여 준다. v1.6 `BoardApp.organizationBoards`(`app/controllers/BoardApp.java:96-113`)에 해당한다.
 *
 * 로그인한 사용자가 볼 수 있는 프로젝트의 글만 나온다. `projectNames[]`로 프로젝트를 고를 수 있고, 고른 프로젝트 가운데 보이지 않는 것은 무시한다.
 * 공지도 일반 글과 같이 섞여 나온다(별도 공지 영역은 없다).
 */
@Controller
@AnonymousCheck(requiresLogin = false, displaysFlashMessage = true)
class OrganizationBoardController(
    private val organizationService: OrganizationService,
    private val searchService: PostingSearchService,
    private val assembler: BoardListAssembler,
    private val orgViews: OrganizationViewFactory,
    private val projectLogos: dev.yona.web.project.ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider
) {

    @GetMapping("/organizations/{organizationName}/boards")
    @Transactional(readOnly = true)
    fun organizationBoards(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val organization = organizationService.findByName(organizationName) ?: return ErrorViews.notFound("error.notfound.organization")
        val viewer = currentUserProvider.get().user
        val pageNum = request.getParameter("pageNum")?.let { it.toIntOrNull() ?: return ErrorViews.badRequest() } ?: 1
        val defaults = PostingSearchCondition()
        val condition = PostingSearchCondition(
            orderBy = request.getParameter("orderBy")?.takeIf { it.isNotBlank() } ?: defaults.orderBy, orderDir = request.getParameter("orderDir") ?: defaults.orderDir,
            filter = request.getParameter("filter") ?: defaults.filter, pageNum = maxOf(pageNum - 1, 0)
        )
        val visible = organizationService.visibleProjects(organization, viewer)
        val names: List<String> = (request.getParameterValues("projectNames[]").orEmpty().toList() + request.getParameterValues("projectNames").orEmpty().toList()).filter { it.isNotEmpty() }
        val targets = if (names.isEmpty()) visible else names.mapNotNull { name -> visible.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val page = searchService.searchInProjects(targets.map { requireNotNull(it.id) }, condition, ITEMS_PER_PAGE)
        val base = "${dev.yona.util.UrlPaths.organizationPath(organization.name)}/boards"
        val descending = !condition.orderDir.equals("asc", ignoreCase = true)
        val sortLinks = listOf("updatedDate" to "common.order.updatedDate", "createdDate" to "common.order.date", "numOfComments" to "common.order.comments").map { (field, key) ->
            val current = condition.effectiveOrderBy == field
            SortLink(field, "$base?orderBy=$field&orderDir=${if (current && descending) "asc" else "desc"}", current, descending, key)
        }
        return ModelAndView("organization/board-list")
            .addObject("title", "menu.board")
            .addObject("orgView", orgViews.of(organization))
            .addObject("cond", condition)
            .addObject("orderBy", condition.effectiveOrderBy)
            .addObject("items", assembler.assemble(page.items, viewer, withProject = true))
            .addObject("totalRows", page.totalRows)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("sortLinks", sortLinks)
            .addObject("projectOptions", visible.map { ProjectOption(it.name.orEmpty(), projectLogos.of(it).logoUrl, names.any { n -> n == it.name }) })
    }

    /** 프로젝트 선택기의 선택지이다. */
    data class ProjectOption(val name: String, val logoUrl: String?, val selected: Boolean)

    private companion object {
        const val ITEMS_PER_PAGE = 15
    }
}
