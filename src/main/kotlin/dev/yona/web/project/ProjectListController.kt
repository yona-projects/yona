package dev.yona.web.project

import dev.yona.security.SessionPermissions
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.ProjectListItem
import dev.yona.domain.project.ProjectListService
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.GuestProhibit
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView

/**
 * 프로젝트 목록이다. v1.6 `ProjectApp.projects`(`app/controllers/ProjectApp.java:1052-1136`)에 해당한다.
 *
 * - 게스트는 홈으로 보낸다(`@GuestProhibit`). 사이트가 목록을 숨기면(`hide-project-listing`) 403이다.
 * - `Accept`로 응답 형식을 고른다. JSON이면 `소유자/이름` 목록, 아니면 HTML 쪽이다. 어느 쪽도 원하지 않으면 HTML이다.
 *   (v1.6에도 406 분기가 있지만 선택 함수가 항상 HTML로 대체해서 실행되지 않았다.)
 * - 응답에는 항상 `Vary: Accept`를 붙인다.
 */
@Controller
@AnonymousCheck
class ProjectListController(
    private val currentUserProvider: CurrentUserProvider,
    private val listService: ProjectListService,
    private val projectViews: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val permissions: SessionPermissions,
    private val properties: YonaProperties
) {

    @GetMapping("/projects")
    @GuestProhibit
    fun projects(
        request: HttpServletRequest,
        response: HttpServletResponse,
        @RequestParam(name = "filter", defaultValue = "") query: String,
        @RequestParam(name = "pageNum", defaultValue = "1") pageNum: Int,
        @RequestParam(name = "labelIds", required = false) labelIds: List<String>?
    ): Any {
        if (properties.hideProjectListing) return ErrorViews.forbidden("error.auth.unauthorized.waringMessage")

        response.setHeader(HttpHeaders.VARY, "Accept")
        val canSeeAll = permissions.siteManager(currentUserProvider.get().user?.id)
        return if (HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))) {
            namesAsJson(query, canSeeAll)
        } else {
            pagedList(query, pageNum, parseLabelIds(labelIds), canSeeAll)
        }
    }

    private fun namesAsJson(query: String, canSeeAll: Boolean): ResponseEntity<List<String>> {
        val (names, total) = listService.names(query, canSeeAll)
        val builder = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
        if (total > ProjectListService.MAX_FETCH_PROJECTS) {
            builder.header(HttpHeaders.CONTENT_RANGE, "items ${ProjectListService.MAX_FETCH_PROJECTS}/$total")
        }
        return builder.body(names)
    }

    private fun pagedList(query: String, pageNum: Int, labelIds: Set<Long>, canSeeAll: Boolean): ModelAndView {
        if (pageNum < 1) return ErrorViews.notFound("error.notfound")
        val viewer = currentUserProvider.get().user
        val page = listService.page(query, labelIds, pageNum, canSeeAll)
        val rows = page.content.map { item ->
            val view = projectViews.of(item.project)
            ProjectListRow(
                item = item,
                view = view,
                readable = properties.displayPrivateRepositories ||
                    accessControl.isAllowed(viewer, item.project.asResource(), Operation.READ),
                members = item.members.map { MemberView(it.loginId.orEmpty(), it.name.orEmpty(), avatars.avatarUrl(it, AvatarService.DEFAULT_SIZE)) }
            )
        }
        return ModelAndView("project/list")
            .addObject("title", "title.projectList")
            .addObject("rows", rows)
            .addObject("totalElements", page.totalElements)
            .addObject("totalPages", page.totalPages)
            .addObject("filter", query)
    }

    /** `labelIds` 쿼리 값에서 비어 있지 않은 숫자만 모은다 (v1.6 `LabelApp.getLabelIds`). */
    private fun parseLabelIds(values: List<String>?): Set<Long> =
        values.orEmpty().flatMap { it.split(',') }.filter { it.isNotEmpty() }.map { it.toLong() }.toSet()

    /** 목록의 한 행이다. [readable]이 거짓이면 내용 대신 권한 없음 안내를 보여 준다. */
    data class ProjectListRow(val item: ProjectListItem, val view: ProjectViewContext, val readable: Boolean, val members: List<MemberView>)

    /** 구성원 아바타 한 칸이다. */
    data class MemberView(val loginId: String, val name: String, val avatarUrl: String)
}
