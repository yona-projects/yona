package dev.yona.web.issue

import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssuePage
import dev.yona.domain.issue.IssueSearchCondition
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.misc.FavoriteIssueRepository
import dev.yona.domain.user.User
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.user.UserPageAssembler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.servlet.ModelAndView

/**
 * 내 이슈이다. v1.6 `IssueApp.userIssuesPage`, `userIssues`(`app/controllers/IssueApp.java:68-111`)와
 * `app/views/issue/my_list.scala.html`, `my_partial_search`, `my_partial_list`, `my_partial_list_quicksearch`에 해당한다.
 *
 * 프로젝트를 가리지 않고 현재 사용자와 관련된 이슈(담당, 작성, 댓글, 멘션, 공유, 즐겨찾기)를 찾는다. 조건이 하나도 없으면 담당한 이슈이다.
 * 정렬 기본은 갱신 시각이다. 응답 형식은 [IssueListController]와 같다(`format`, Ajax는 JSON, PJAX는 조각). 엑셀은 없다.
 * 읽기 권한을 따지지 않는다(v1.6이 그렇다, R-49).
 */
@Controller
@AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
class MyIssuesController(
    private val searchService: IssueSearchService,
    private val sharers: IssueSharerRepository,
    private val favorites: FavoriteIssueRepository,
    private val rows: UserPageAssembler,
    private val currentUserProvider: CurrentUserProvider
) {

    /** 왼쪽 빠른 검색의 괄호 안 개수이다. 검색어가 없을 때만 보인다. */
    data class QuickCounts(val mentioned: Long, val shared: Long, val favorite: Long)

    @GetMapping("/user/issues")
    @Transactional(readOnly = true)
    fun userIssues(request: HttpServletRequest, response: HttpServletResponse): Any {
        val viewer = requireNotNull(currentUserProvider.get().user) { "로그인이 필요합니다." }
        val pageNum = request.getParameter("pageNum")?.takeIf { it.isNotBlank() }?.toInt() ?: 1
        var condition = IssueConditionBinder.bind(request, pageNum - 1) ?: return ErrorViews.badRequest()
        if (!condition.hasCondition()) condition = condition.copy(assigneeId = viewer.id)
        if (request.getParameter("orderBy").isNullOrBlank()) condition = condition.copy(orderBy = "updatedDate")

        var format = request.getParameter("format") ?: "html"
        if (request.getHeader("X-Requested-With") == "XMLHttpRequest") format = if (request.getHeader("X-PJAX").toBoolean()) "pjax" else "json"

        val page = searchService.searchAll(condition, viewer, itemsPerPage(request))
        return when (format) {
            "pjax" -> {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                listModel("issue/my-partial-search :: mySearch", condition, page, viewer)
            }
            "json" -> json(page, request.getParameter("exceptId"))
            else -> listModel("issue/my-list", condition, page, viewer)
        }
    }

    private fun itemsPerPage(request: HttpServletRequest): Int =
        minOf(request.getParameter("itemsPerPage")?.toIntOrNull() ?: ITEMS_PER_PAGE, ITEMS_PER_PAGE_MAX)

    /** 프로젝트가 없으므로 이슈마다 주소(`link`)는 담지 않는다. */
    private fun json(page: IssuePage, exceptIdParam: String?): Any {
        val exceptId = if (exceptIdParam.isNullOrEmpty()) -1L else exceptIdParam.toLongOrNull()
            ?: return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(emptyMap<String, Any>())
        val body = linkedMapOf<String, Map<String, Any?>>()
        for (issue in page.items) {
            val number = issue.number ?: continue
            if (number == exceptId) continue
            body[issue.id.toString()] = linkedMapOf(
                "id" to number, "title" to issue.title, "state" to issue.state?.name, "createdDate" to issue.createdDate?.toString()
            )
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
    }

    private fun listModel(view: String, condition: IssueSearchCondition, page: IssuePage, viewer: User): ModelAndView {
        val userId = requireNotNull(viewer.id)
        return ModelAndView(view)
            .addObject("title", "issue.myIssue")
            .addObject("menuTab", "TAB_MY_ISSUES")
            .addObject("cond", condition)
            .addObject("items", rows.issueRows(page.items, viewer))
            .addObject("pageIndex", page.pageIndex)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("openCount", searchService.countAll(condition.copy(state = State.OPEN.state), viewer))
            .addObject("closedCount", searchService.countAll(condition.copy(state = State.CLOSED.state), viewer))
            .addObject("meId", userId)
            .addObject("quickCounts", QuickCounts(searchService.countMentionedOpenIssues(userId), sharers.countOpenSharedWith(userId), favorites.countOpen(userId)))
    }

    private companion object {
        const val ITEMS_PER_PAGE = 15
        const val ITEMS_PER_PAGE_MAX = 45
    }
}
