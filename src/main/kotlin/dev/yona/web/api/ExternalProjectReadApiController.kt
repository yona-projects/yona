package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssueSearchCondition
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMemberService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.UserRepository
import dev.yona.domain.apitoken.ApiScopes
import dev.yona.security.YonaAuthentication
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 외부용 일반 REST API 1차의 조회이다(로드맵 X-19, `decisions.md` D-40): 프로젝트 정보와 목록, 구성원과 담당자 후보, 라벨, 마일스톤, 이슈 목록과 검색.
 * v1.6에 없는 추가 항목이며 규칙은 풀 리퀘스트 REST와 같다: `/-_-api/v1`, 개인용 토큰만, 범위(`repo`, 공개 프로젝트는 `public_repo`)에 더해 화면과 같은 읽기 권한을 확인한다.
 * 이슈 댓글은 따로 두지 않는다. `GET .../issues/{number}`(v1.6)가 댓글과 첨부를 이미 담는다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalProjectReadApiController(
    private val support: PullRequestApiSupport,
    private val json: ProjectJson,
    private val projects: ProjectRepository,
    private val members: ProjectMemberService,
    private val listService: IssueListService,
    private val labelService: IssueLabelService,
    private val milestones: MilestoneService,
    private val issueSearch: IssueSearchService,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val requests: ApiRequests,
    private val orgPolicy: dev.yona.security.OAuthOrgPolicyGuard
) {

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        const val DEFAULT_PAGE_SIZE = 15
        const val MAX_PAGE_SIZE = 100
        val SORTS = setOf("createdDate", "updatedDate", "number", "title", "numOfComments", "dueDate")
    }

    /** 프로젝트 정보이다. 읽을 수 있는 프로젝트만 보인다. */
    @ApiOperation(operationId = "getProject", tags = [ApiTags.PROJECT], summary = "프로젝트 한 건 조회", description = "프로젝트 정보(소유자, 이름, 설명, 저장소 종류, 공개 범위, 켜 둔 메뉴, 포크 원본, 소속 조직)를 돌려준다. 읽을 수 있는 프로젝트만 보인다.")
    @ApiResponse(responseCode = "200", description = "프로젝트", content = [Content(schema = Schema(implementation = ProjectDetail::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping(PROJECT)
    @Transactional(readOnly = true)
    fun get(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        return support.ok(json.project(requireNotNull(target.project)))
    }

    /** 토큰 사용자가 구성원인 프로젝트이다. `public_repo`만 가진 토큰은 공개 프로젝트만 본다. 응답은 `{"projects": [...]}`이다. */
    @ApiOperation(operationId = "listMyProjects", tags = [ApiTags.PROJECT], summary = "내가 속한 프로젝트", description = "토큰 주인이 구성원인 프로젝트이다. `public_repo`만 가진 토큰은 공개 프로젝트만 본다.")
    @ApiResponse(responseCode = "200", description = "프로젝트 목록", content = [Content(schema = Schema(implementation = ProjectListResponse::class))])
    @GetMapping("/-_-api/v1/user/projects")
    @Transactional(readOnly = true)
    fun mine(request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val found = projects.findProjectsByMember(requireNotNull(user.id)).distinctBy { it.id }
        return support.ok(linkedMapOf("projects" to found.filter { visibleToToken(it) && orgPolicy.visible(it) }.sortedBy { it.id }.map { json.project(it) }))
    }

    /** 소유자(사용자나 조직)의 프로젝트 가운데 토큰 사용자가 읽을 수 있는 것이다. 응답은 `{"projects": [...]}`이다. */
    @ApiOperation(operationId = "listOwnerProjects", tags = [ApiTags.PROJECT], summary = "소유자의 프로젝트", description = "사용자나 조직이 소유한 프로젝트 가운데 토큰 주인이 읽을 수 있는 것이다. 조직의 프로젝트 목록도 이것으로 얻는다.")
    @ApiResponse(responseCode = "200", description = "프로젝트 목록", content = [Content(schema = Schema(implementation = ProjectListResponse::class))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects")
    @Transactional(readOnly = true)
    fun ofOwner(@PathVariable owner: String, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val found = projects.findByOwnerIgnoreCaseOrderByCreatedDateDesc(owner).filter { accessControl.isAllowed(user, it.asResource(), Operation.READ) && visibleToToken(it) }
        return support.ok(linkedMapOf("projects" to found.map { json.project(it) }))
    }

    /** 프로젝트 구성원과 역할(`manager`, `member`)이다. 사이트 관리자 역할은 뺀다. 응답은 `{"members": [{"loginId", "name", "role"}]}`이다. */
    @ApiOperation(operationId = "listProjectMembers", tags = [ApiTags.PROJECT], summary = "프로젝트 구성원", description = "구성원과 역할(`manager`, `member`)이다. 사이트 관리자 역할은 뺀다.")
    @ApiResponse(responseCode = "200", description = "구성원 목록", content = [Content(schema = Schema(implementation = ProjectMemberList::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/members")
    @Transactional(readOnly = true)
    fun members(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val list = members.members(requireNotNull(target.project)).mapNotNull { pu -> pu.user?.let { linkedMapOf("loginId" to it.loginId, "name" to it.name, "role" to pu.role?.name) } }
        return support.ok(linkedMapOf("members" to list))
    }

    /**
     * 담당자나 리뷰어로 정할 수 있는 사람(프로젝트 구성원과 소속 조직의 구성원, 이슈 담당자 후보 규칙)이다. `query`는 이름이나 로그인 ID의 일부이고 생략하면 전체이다(최대 100명).
     * 풀 리퀘스트를 만들기 **전에** 담당자를 고르는 데 쓴다. 응답은 `{"users": [{"loginId", "name"}]}`이다.
     */
    @ApiOperation(operationId = "listAssignableUsers", tags = [ApiTags.PROJECT], summary = "담당자 후보", description = "담당자나 리뷰어로 정할 수 있는 사람(프로젝트 구성원과 소속 조직의 구성원)이다. 풀 리퀘스트를 만들기 전에 담당자를 고르는 데 쓴다. 최대 100명이다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "query", description = "이름이나 로그인 ID의 일부. 생략하면 전체", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "후보 목록", content = [Content(schema = Schema(implementation = AssignableUserList::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/assignableusers")
    @Transactional(readOnly = true)
    fun assignableUsers(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false, defaultValue = "") query: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val viewer = requireNotNull(target.user)
        val needle = query.trim().lowercase()
        val found = listService.assignableUsers(requireNotNull(target.project), viewer, accessControl.isSiteManager(viewer)).filter {
            needle.isEmpty() || it.loginId.orEmpty().lowercase().contains(needle) || it.name.orEmpty().lowercase().contains(needle)
        }.take(MAX_PAGE_SIZE)
        return support.ok(linkedMapOf("users" to found.map { linkedMapOf("loginId" to it.loginId, "name" to it.name) }))
    }

    /** 프로젝트의 라벨이다(분류 이름, 라벨 이름 순서). 풀 리퀘스트와 이슈의 라벨 번호(`id`)를 알아내는 데 쓴다. 응답은 `{"labels": [...]}`이다. */
    @ApiOperation(operationId = "listLabels", tags = [ApiTags.LABEL], summary = "라벨 목록", description = "프로젝트의 라벨이다(분류 이름, 라벨 이름 순서). 이슈와 풀 리퀘스트의 라벨 번호(`id`)를 알아내는 데 쓴다.")
    @ApiResponse(responseCode = "200", description = "라벨 목록", content = [Content(schema = Schema(implementation = LabelListResponse::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/labels")
    @Transactional(readOnly = true)
    fun labels(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        return support.ok(linkedMapOf("labels" to labelService.labels(requireNotNull(target.project)).map { json.label(it) }))
    }

    /** 프로젝트의 마일스톤이다. `state`는 `open`(기본), `closed`, `all`이다. 응답은 `{"milestones": [...]}`이다. */
    @ApiOperation(operationId = "listMilestones", tags = [ApiTags.MILESTONE], summary = "마일스톤 목록", description = "프로젝트의 마일스톤을 마감일 순서로 돌려준다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "state", description = "마일스톤 상태", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "open", schema = Schema(allowableValues = ["open", "closed", "all"]))
    @ApiResponse(responseCode = "200", description = "마일스톤 목록", content = [Content(schema = Schema(implementation = MilestoneListResponse::class))])
    @ApiResponse(responseCode = "400", description = "`state`가 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/milestones")
    @Transactional(readOnly = true)
    fun milestones(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(defaultValue = "open") state: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        val wanted = when (state) { "open" -> State.OPEN; "closed" -> State.CLOSED; "all" -> State.ALL; else -> return support.badRequest("state must be open, closed or all") }
        return support.ok(linkedMapOf("milestones" to milestones.list(requireNotNull(target.project), wanted, "dueDate", false).map { json.milestone(it) }))
    }

    /**
     * 이슈 목록과 검색이다. 조건: `state`(`open` 기본, `closed`, `all`), `q`(제목과 본문 검색어), `assignee`와 `author`(로그인 ID), `labelIds`(쉼표나 반복, 모두 가진 이슈),
     * `milestoneId`, `sort`(`createdDate` 기본, `updatedDate`, `number`, `title`, `numOfComments`, `dueDate`), `order`(`desc` 기본, `asc`), `page`(1부터), `pageSize`(1~100, 기본 15).
     * 응답은 `{"items": [...], "page", "pageSize", "totalPages", "totalCount"}`이다. 이슈 한 건의 본문과 댓글은 `GET .../issues/{number}`로 읽는다.
     */
    @ApiOperation(operationId = "listIssues", tags = [ApiTags.ISSUE], summary = "이슈 목록과 검색", description = "조건에 맞는 이슈를 쪽으로 나누어 돌려준다. 본문과 댓글은 담지 않으므로 한 건은 `GET .../issues/{number}`로 읽는다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "state", description = "이슈 상태", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "open", schema = Schema(allowableValues = ["open", "closed", "all"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "q", description = "제목과 본문에서 찾을 검색어", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "assignee", description = "담당자의 로그인 ID", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "author", description = "작성자의 로그인 ID", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "milestoneId", description = "마일스톤 번호", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "labelIds", description = "라벨 번호(쉼표로 구분하거나 반복). 모두 가진 이슈만 찾는다", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @io.swagger.v3.oas.annotations.Parameter(name = "sort", description = "정렬 기준", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "createdDate", schema = Schema(allowableValues = ["createdDate", "updatedDate", "number", "title", "numOfComments", "dueDate"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "order", description = "정렬 방향", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "desc", schema = Schema(allowableValues = ["asc", "desc"]))
    @io.swagger.v3.oas.annotations.Parameter(name = "page", description = "쪽 번호(1부터)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "1")
    @io.swagger.v3.oas.annotations.Parameter(name = "pageSize", description = "한 쪽의 개수(1에서 100)", `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY, example = "15")
    @ApiResponse(responseCode = "200", description = "이슈 목록", content = [Content(schema = Schema(implementation = IssueListResponse::class))])
    @ApiResponse(responseCode = "400", description = "`state`, `sort`, `order`가 틀렸거나 없는 사용자이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/issues")
    @Transactional(readOnly = true)
    fun issues(
        @PathVariable owner: String, @PathVariable projectName: String, @RequestParam(defaultValue = "open") state: String, @RequestParam(required = false) q: String?,
        @RequestParam(required = false) assignee: String?, @RequestParam(required = false) author: String?, @RequestParam(required = false) milestoneId: Long?,
        @RequestParam(defaultValue = "createdDate") sort: String, @RequestParam(defaultValue = "desc") order: String, @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(required = false) pageSize: Int?, request: HttpServletRequest
    ): Any {
        val target = support.target(owner, projectName, null, request, requireCodeAccess = false)
        target.error?.let { return it }
        if (state !in setOf("open", "closed", "all")) return support.badRequest("state must be open, closed or all")
        if (sort !in SORTS) return support.badRequest("sort must be one of ${SORTS.joinToString(", ")}")
        if (order != "asc" && order != "desc") return support.badRequest("order must be asc or desc")
        val assigneeId = assignee?.takeIf { it.isNotBlank() }?.let { login -> users.findByLoginId(login)?.id ?: return support.badRequest("no user by $login") }
        val authorId = author?.takeIf { it.isNotBlank() }?.let { login -> users.findByLoginId(login)?.id ?: return support.badRequest("no user by $login") }
        val labelIds = request.getParameterValues("labelIds").orEmpty().flatMap { it.split(",") }.mapNotNull { it.trim().toLongOrNull() }.toSet()
        val size = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val current = page.coerceAtLeast(1)
        val condition = IssueSearchCondition(
            state = state, orderBy = sort, orderDir = order, filter = q.orEmpty(), pageNum = current - 1, milestoneId = milestoneId, labelIds = labelIds, authorId = authorId, assigneeId = assigneeId
        )
        val result = issueSearch.search(requireNotNull(target.project), condition, target.user, size)
        val items = result.items.map { json.issueSummary(it, it.authorId?.let { id -> users.findById(id).orElse(null) }) }
        return support.ok(linkedMapOf("items" to items, "page" to current, "pageSize" to size, "totalPages" to result.totalPageCount, "totalCount" to result.totalRows))
    }

    /** 토큰의 범위로 이 프로젝트를 볼 수 있는지이다: `repo`와 `read:repo`는 모두, `public_repo`는 공개(`PUBLIC`) 프로젝트만이다. */
    private fun visibleToToken(project: Project): Boolean {
        val scopes = (SecurityContextHolder.getContext().authentication as? YonaAuthentication)?.scopes ?: return false
        return ApiScopes.implies(scopes, ApiScopes.READ_REPO) || (ApiScopes.implies(scopes, ApiScopes.PUBLIC_REPO) && project.projectScope == ProjectScope.PUBLIC)
    }
}
