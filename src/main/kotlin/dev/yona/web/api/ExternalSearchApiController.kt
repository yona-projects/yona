package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.search.SearchCounts
import dev.yona.domain.search.SearchScope
import dev.yona.domain.search.SearchService
import dev.yona.domain.search.SearchType
import dev.yona.util.SiteUrls
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.search.SearchItemRow
import dev.yona.web.search.SearchMilestoneRow
import dev.yona.web.search.SearchProjectRow
import dev.yona.web.search.SearchRowAssembler
import dev.yona.web.search.SearchUserRow
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 검색 REST이다(로드맵 X-30c, `decisions.md` D-49). 웹 검색([dev.yona.web.search.SearchController])과 같은 서비스와 가시성 규칙을 쓰고 결과를 JSON으로 돌려준다.
 * 전체(`/-_-api/v1/search`), 조직(`/-_-api/v1/orgs/{org}/search`), 프로젝트(`.../projects/{projectName}/search`) 세 범위가 있다. 토큰 전용이다(X-5).
 *
 * 쿼리: `keyword`(필수), `searchType`(`auto` 기본, `issue`, `post`, `milestone`, `issue_comment`, `post_comment`, `review`, `user`, `project`. 프로젝트 범위는 `project`를
 * 받지 않는다), `pageNum`(1부터, 기본 1). 응답은 `{"keyword", "searchType"(확정된 종류), "counts", "items", "page", "totalPages"}`이다. 한 쪽은 20개이다.
 *
 * 전체와 조직 범위는 비공개 프로젝트의 항목까지 찾을 수 있어 `repo` 범위가 필요하고, 프로젝트 범위는 `repo`나 공개 프로젝트의 `public_repo`이다(표는 `ApiScopeRules`).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalSearchApiController(
    private val search: SearchService,
    private val rows: SearchRowAssembler,
    private val organizationService: OrganizationService,
    private val organizationUsers: OrganizationUserRepository,
    private val projectService: ProjectService,
    private val properties: YonaProperties,
    private val requests: ApiRequests,
    private val accessControl: AccessControl,
    private val siteUrls: SiteUrls
) {

    /** 사이트 전체에서 찾는다. 토큰 사용자가 볼 수 있는 항목만 나온다. */
    @ApiOperation(operationId = "searchAll", tags = [ApiTags.SEARCH], summary = "사이트 전체 검색", description = "토큰 주인이 볼 수 있는 이슈, 글, 마일스톤, 댓글, 코드 리뷰와 사용자, 프로젝트에서 찾는다. 비공개 프로젝트의 항목까지 찾으므로 `repo` 범위가 필요하다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "keyword", description = "찾을 낱말. 대소문자를 구분하지 않고 필드 안에 통째로 들어 있는지만 본다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "searchType", description = "찾을 종류. 생략하면 `auto`(결과가 있는 첫 종류)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "auto", schema = Schema(allowableValues = ["auto", "issue", "post", "milestone", "issue_comment", "post_comment", "review", "user", "project"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "pageNum", description = "쪽 번호(1부터). 한 쪽은 20개", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "1")
    @ApiResponse(responseCode = "200", description = "검색 결과", content = [Content(schema = Schema(implementation = SearchResponse::class))])
    @ApiResponse(responseCode = "400", description = "`keyword`가 없거나 `searchType`, `pageNum`이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/search")
    @Transactional(readOnly = true)
    fun searchAll(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        return run(SearchScope(), null, user, request)
    }

    /** 조직 안에서 찾는다. 사이트가 프로젝트 목록을 숨기면 조직 구성원만 찾는다. */
    @ApiOperation(operationId = "searchInOrganization", tags = [ApiTags.SEARCH], summary = "조직 안 검색", description = "조직의 프로젝트와 구성원 안에서 찾는다. 사이트가 프로젝트 목록을 숨기면 조직 구성원만 찾을 수 있다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "keyword", description = "찾을 낱말. 대소문자를 구분하지 않고 필드 안에 통째로 들어 있는지만 본다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "searchType", description = "찾을 종류. 생략하면 `auto`(결과가 있는 첫 종류)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "auto", schema = Schema(allowableValues = ["auto", "issue", "post", "milestone", "issue_comment", "post_comment", "review", "user", "project"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "pageNum", description = "쪽 번호(1부터). 한 쪽은 20개", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "1")
    @ApiResponse(responseCode = "200", description = "검색 결과", content = [Content(schema = Schema(implementation = SearchResponse::class))])
    @ApiResponse(responseCode = "400", description = "`keyword`가 없거나 `searchType`, `pageNum`이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "조직이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/orgs/{org}/search")
    @Transactional(readOnly = true)
    fun searchInOrganization(@PathVariable org: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val organization = organizationService.findByName(org) ?: return ErrorViews.notFound()
        val member = organizationUsers.existsByOrganizationIdAndUserId(requireNotNull(organization.id), requireNotNull(user.id)) || accessControl.isAllowed(user, organization.asResource(), Operation.UPDATE)
        if (properties.hideProjectListing && !member) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return run(SearchScope(organization = organization), null, user, request)
    }

    /** 프로젝트 안에서 찾는다. 프로젝트를 읽을 수 있어야 하고 `searchType=project`는 받지 않는다. */
    @ApiOperation(operationId = "searchInProject", tags = [ApiTags.SEARCH], summary = "프로젝트 안 검색", description = "프로젝트의 이슈, 글, 마일스톤, 댓글, 코드 리뷰와 구성원에서 찾는다. 프로젝트를 읽을 수 있어야 하고 `searchType=project`는 받지 않는다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "keyword", description = "찾을 낱말. 대소문자를 구분하지 않고 필드 안에 통째로 들어 있는지만 본다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, required = true)
    @io.swagger.v3.oas.annotations.Parameter(name = "searchType", description = "찾을 종류. 생략하면 `auto`(결과가 있는 첫 종류)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "auto", schema = Schema(allowableValues = ["auto", "issue", "post", "milestone", "issue_comment", "post_comment", "review", "user", "project"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "pageNum", description = "쪽 번호(1부터). 한 쪽은 20개", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "1")
    @ApiResponse(responseCode = "200", description = "검색 결과", content = [Content(schema = Schema(implementation = SearchResponse::class))])
    @ApiResponse(responseCode = "400", description = "`keyword`가 없거나 `searchType`, `pageNum`이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/search")
    @Transactional(readOnly = true)
    fun searchInProject(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, project.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return run(SearchScope(project = project), project, user, request)
    }

    private fun run(scope: SearchScope, project: Project?, user: dev.yona.domain.user.User, request: HttpServletRequest): Any {
        val keyword = request.getParameter("keyword")?.takeIf { it.isNotEmpty() } ?: return requests.message(HttpStatus.BAD_REQUEST, "keyword is required")
        val requested = SearchType.of(request.getParameter("searchType") ?: SearchType.AUTO.type)
        if (requested == SearchType.NA || (project != null && requested == SearchType.PROJECT)) return requests.message(HttpStatus.BAD_REQUEST, "searchType is not available")
        val page = request.getParameter("pageNum")?.let { it.toIntOrNull()?.takeIf { number -> number >= 1 } ?: return requests.message(HttpStatus.BAD_REQUEST, "pageNum must be 1 or more") } ?: 1
        val counts = search.counts(scope, keyword, user)
        val type = search.resolve(requested, counts)
        val result = search.page(type, scope, keyword, user, page - 1)
        val projectLabel = project?.let { "${it.owner}/${it.name}" }
        val items = rows.rows(result.items, keyword, project != null, user).map { item(it, projectLabel) }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(
            linkedMapOf("keyword" to keyword, "searchType" to type.type, "counts" to countsNode(counts), "items" to items, "page" to page, "totalPages" to result.totalPageCount)
        )
    }

    private fun countsNode(c: SearchCounts): Map<String, Long> = linkedMapOf(
        "users" to c.users, "projects" to c.projects, "issues" to c.issues, "posts" to c.posts, "milestones" to c.milestones,
        "issueComments" to c.issueComments, "postComments" to c.postComments, "reviews" to c.reviews
    )

    /** 화면 행을 JSON 항목으로 바꾼다. 주소는 사이트 경로이고, 프로젝트 범위에서 비는 `project`는 범위의 프로젝트로 채운다. */
    private fun item(row: Any, scopedProject: String?): Map<String, Any?> = when (row) {
        is SearchItemRow -> linkedMapOf(
            "number" to row.postId?.removePrefix("#")?.toLongOrNull(), "title" to row.titleText, "url" to row.titleUrl?.let { siteUrls.path(it) },
            "snippets" to row.snippets.map { it.text }, "project" to (row.projectLabel ?: scopedProject),
            "author" to linkedMapOf("loginId" to row.authorLoginId, "name" to row.authorName), "createdAt" to row.createdTitle
        )
        is SearchMilestoneRow -> linkedMapOf(
            "title" to row.title, "url" to siteUrls.path(row.url), "snippets" to row.snippets.map { it.text }, "project" to (row.projectLabel ?: scopedProject), "dueDate" to row.dueDateString
        )
        is SearchUserRow -> linkedMapOf("loginId" to row.loginId, "name" to row.name, "since" to row.since)
        is SearchProjectRow -> linkedMapOf(
            "owner" to row.owner, "name" to row.name, "overview" to row.overview, "createdAt" to row.createdTitle,
            "originalProject" to row.originalOwner?.let { linkedMapOf("owner" to it, "name" to row.originalName) }
        )
        else -> throw IllegalArgumentException("지원하지 않는 검색 결과입니다: ${row.javaClass}")
    }
}
