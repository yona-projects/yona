package dev.yona.web.search

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.search.SearchScope
import dev.yona.domain.search.SearchService
import dev.yona.domain.search.SearchType
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.organization.OrganizationViewFactory
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 통합 검색이다. v1.6 `SearchApp`(`app/controllers/SearchApp.java`)에 해당한다.
 *
 * 범위는 전체(`/search`), 조직(`/organizations/:name/search`), 프로젝트(`/:user/:project/search`)이다. 쿼리 `keyword`(공백만이어도 유효),
 * `searchType`(`auto`이면 결과가 있는 첫 종류), `pageNum`(1부터)을 받는다. 한 쪽은 20개이다.
 *
 * 오류 응답은 범위마다 다르다. 조직 범위의 앞쪽 검사는 본문 없는 400이다.
 */
@Controller
@AnonymousCheck
class SearchController(
    private val search: SearchService,
    private val rows: SearchRowAssembler,
    private val organizations: OrganizationService,
    private val projectService: ProjectService,
    private val orgViews: OrganizationViewFactory,
    private val projectViews: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties
) {

    @GetMapping("/search")
    @Transactional(readOnly = true)
    fun searchInAll(request: HttpServletRequest): Any {
        val keyword = request.getParameter("keyword")
        val requested = request.getParameter("searchType")
        if (keyword.isNullOrEmpty() || requested.isNullOrEmpty()) return ErrorViews.badRequest()
        val type = SearchType.of(requested)
        if (type == SearchType.NA) return ErrorViews.badRequest()
        val pageIndex = pageIndex(request) ?: return ErrorViews.badRequest()
        val view = result(SearchScope(), keyword, type, pageIndex, "/search")
        return ModelAndView("search/result-site").addObject("title", "title.search").addObject("search", view)
    }

    @GetMapping("/organizations/{organizationName}/search")
    @Transactional(readOnly = true)
    fun searchInAGroup(@PathVariable organizationName: String, request: HttpServletRequest): Any {
        val keyword = request.getParameter("keyword")
        val requested = request.getParameter("searchType")
        if (organizationName.isEmpty() || keyword.isNullOrEmpty() || requested.isNullOrEmpty()) return ResponseEntity.badRequest().build<Void>()
        val organization = organizations.findByName(organizationName)
        // v1.6은 조직이 있는지 보기 전에 구성원인지 먼저 물어, 이 검사에서는 조직 구성원이면서 관리자 역할도 가진 사용자만 통과한다.
        if (properties.hideProjectListing && organization != null) {
            val view = orgViews.of(organization)
            if (!view.member || !view.admin) return ResponseEntity.badRequest().build<Void>()
        }
        val type = SearchType.of(requested)
        if (type == SearchType.NA || organization == null) return ErrorViews.badRequest()
        val pageIndex = pageIndex(request) ?: return ErrorViews.badRequest()
        val view = result(SearchScope(organization = organization), keyword, type, pageIndex, "/organizations/${organization.name}/search")
        return ModelAndView("search/result-organization").addObject("title", "title.search").addObject("search", view)
            .addObject("orgView", orgViews.of(organization))
    }

    @GetMapping("/{user}/{project}/search")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun searchInAProject(@PathVariable("user") ownerName: String, @PathVariable("project") projectName: String, request: HttpServletRequest): Any {
        val keyword = request.getParameter("keyword")
        val requested = request.getParameter("searchType")
        val project = projectService.findByOwnerAndProjectName(ownerName, projectName)
        if (keyword.isNullOrEmpty() || requested.isNullOrEmpty() || project == null) return ErrorViews.badRequest()
        val type = SearchType.of(requested)
        if (type == SearchType.NA || type == SearchType.PROJECT) return ErrorViews.badRequest()
        val pageIndex = pageIndex(request) ?: return ErrorViews.badRequest()
        val view = result(SearchScope(project = project), keyword, type, pageIndex, "/${project.owner}/${project.name}/search")
        return ModelAndView("search/result-project").addObject("title", "title.search").addObject("search", view)
            .addObject("projectView", projectViews.of(project))
    }

    /** 쪽 번호(0부터)이다. 없으면 0이고 숫자가 아니거나 1보다 작으면 `null`이다(v1.6은 500이거나 Ebean 동작에 맡긴다, A-87). */
    private fun pageIndex(request: HttpServletRequest): Int? {
        val value = request.getParameter("pageNum") ?: return 0
        val number = value.toIntOrNull() ?: return null
        return if (number < 1) null else number - 1
    }

    private fun result(scope: SearchScope, keyword: String, requested: SearchType, pageIndex: Int, action: String): SearchView {
        val viewer = currentUserProvider.get().user
        val counts = search.counts(scope, keyword, viewer)
        val type = search.resolve(requested, counts)
        val page = search.page(type, scope, keyword, viewer, pageIndex)
        return SearchView(keyword, type, counts, rows.rows(page.items, keyword, scope.project != null, viewer), page.totalPageCount, action, scope.project != null)
    }
}
