package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEdit
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.issue.IssueSharerService
import dev.yona.domain.misc.WatchService
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 이슈의 Yona 고유 쓰기 기능을 토큰으로 쓰는 REST이다(로드맵 X-29d, `decisions.md` D-47): 공유자, 가중치, 지켜보기. 웹 화면에만 있던
 * `.../issue/{n}/share`, `upvoteWeight`, `downvoteWeight`, `/watch`의 토큰 버전이고 GitHub에는 없는 기능이라 Yona 방식으로 만들었다.
 * 경로는 v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사에 자원 경로를 얹은 것이다(X-5).
 *
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). 공유자와 가중치는 이슈를 고칠 수 있어야 하고(`UPDATE`),
 * 지켜보기는 읽을 수 있으면 된다(`READ`). 웹 화면과 같은 판정이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalIssueExtrasApiController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val sharers: IssueSharerRepository,
    private val sharerService: IssueSharerService,
    private val watchService: WatchService,
    private val users: UserRepository,
    private val issueJson: IssueJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    // --- 공유자 ---

    /** 이슈의 공유자 목록이다. 추가한 순서이고 `{"loginId", "name"}`을 담는다. 읽을 수 있어야 한다. 결과: `{"result": [...]}`. */
    @ApiOperation(operationId = "listIssueSharers", tags = [ApiTags.ISSUE], summary = "이슈 공유자 목록", description = "이슈를 공유받은 사용자를 추가한 순서로 돌려준다. 읽을 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "공유자 목록", content = [Content(schema = Schema(implementation = SharerListResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("$PROJECT/issues/{number}/sharers")
    @Transactional(readOnly = true)
    fun listSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ok(linkedMapOf("result" to sharersOf(issue)))
    }

    /**
     * 공유자를 더한다. 본문 `{"sharers": [로그인 ID], "projects": [프로젝트 번호]}` 중 하나 이상이다. 프로젝트를 주면 그 구성원이 모두 공유자가 된다(웹의
     * "프로젝트로 공유"와 같다). 없는 사용자나 읽을 수 없는 프로젝트가 하나라도 있으면 아무것도 바꾸지 않고 400이다. 이슈를 고칠 수 있어야 한다.
     * 결과는 바뀐 뒤의 공유자 목록이다.
     */
    @ApiOperation(operationId = "addIssueSharers", tags = [ApiTags.ISSUE], summary = "이슈 공유자 더하기", description = "사용자(`sharers`)나 프로젝트(`projects`, 그 구성원이 모두)를 공유자로 더한다. 없는 사용자나 읽을 수 없는 프로젝트가 하나라도 있으면 아무것도 바꾸지 않고 400이다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "공유자", required = true, content = [Content(schema = Schema(implementation = SharersRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 뒤의 공유자 목록", content = [Content(schema = Schema(implementation = SharerListResult::class))])
    @ApiResponse(responseCode = "400", description = "`sharers`와 `projects`가 모두 없거나 없는 사용자와 프로젝트가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/issues/{number}/sharers")
    @Transactional
    fun addSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any = changeSharers(owner, projectName, number, request, true)

    /** 공유자를 뺀다. 본문은 [addSharers]와 같다. 공유자가 아니어도 그대로 성공한다. */
    @ApiOperation(operationId = "removeIssueSharers", tags = [ApiTags.ISSUE], summary = "이슈 공유자 빼기", description = "공유자를 뺀다. 본문은 더하기와 같다. 공유자가 아니어도 성공한다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "뺄 공유자", required = true, content = [Content(schema = Schema(implementation = SharersRequest::class))])
    @ApiResponse(responseCode = "200", description = "바뀐 뒤의 공유자 목록", content = [Content(schema = Schema(implementation = SharerListResult::class))])
    @ApiResponse(responseCode = "400", description = "`sharers`와 `projects`가 모두 없거나 없는 사용자와 프로젝트가 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/issues/{number}/sharers")
    @Transactional
    fun removeSharers(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any = changeSharers(owner, projectName, number, request, false)

    private fun changeSharers(owner: String, projectName: String, number: Long, request: HttpServletRequest, add: Boolean): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val loginIds = list(json, "sharers") { it.asString("").takeIf { text -> text.isNotBlank() } } ?: return requests.message(HttpStatus.BAD_REQUEST, "sharers must be a list of login ids")
        val projectIds = list(json, "projects") { if (it.isNumber) it.asLong() else null } ?: return requests.message(HttpStatus.BAD_REQUEST, "projects must be a list of project numbers")
        if (loginIds.isEmpty() && projectIds.isEmpty()) return requests.message(HttpStatus.BAD_REQUEST, "sharers or projects is required")
        loginIds.firstOrNull { users.findByLoginIdIgnoreCase(it) == null }?.let { return requests.message(HttpStatus.BAD_REQUEST, "no user by request: $it") }
        projectIds.firstOrNull { id -> projects.findById(id).orElse(null)?.let { accessControl.isAllowed(user, it.asResource(), Operation.READ) } != true }
            ?.let { return requests.message(HttpStatus.BAD_REQUEST, "no project by request: $it") }
        loginIds.forEach { sharerService.changeByUser(issue, user, it, add, user.lang) }
        projectIds.forEach { sharerService.changeByProject(issue, user, it, add) }
        return ok(linkedMapOf("result" to sharersOf(issue)))
    }

    /** 본문의 [key] 항목을 목록으로 읽는다. 없으면 빈 목록이고, 배열이 아니거나 [convert]가 `null`을 돌려주는 값이 있으면 `null`이다. */
    private fun <T : Any> list(json: JsonNode, key: String, convert: (JsonNode) -> T?): List<T>? {
        if (!json.has(key)) return emptyList()
        val node = json.get(key)
        if (!node.isArray) return null
        val converted = node.toList().map(convert)
        return if (converted.any { it == null }) null else converted.filterNotNull().distinct()
    }

    private fun sharersOf(issue: Issue): List<Map<String, Any?>> =
        sharers.findByIssueId(requireNotNull(issue.id)).sortedBy { it.id }.map { linkedMapOf("loginId" to it.loginId, "name" to it.user?.name) }

    // --- 가중치 ---

    /** 이슈의 가중치(중요도)를 올리거나 내린다. 본문 `{"delta": 1}` 또는 `{"delta": -1}`만 받는다(웹의 위, 아래 단추와 같다). 이슈를 고칠 수 있어야 한다. 결과: `{"result": {"weight": 새 값}}`. */
    @ApiOperation(operationId = "adjustIssueWeight", tags = [ApiTags.ISSUE], summary = "이슈 가중치 올리고 내리기", description = "중요도(가중치)를 1 올리거나 내린다. 웹 화면의 위, 아래 단추와 같다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "올릴지 내릴지", required = true, content = [Content(schema = Schema(implementation = WeightRequest::class))])
    @ApiResponse(responseCode = "200", description = "새 가중치", content = [Content(schema = Schema(implementation = WeightResult::class))])
    @ApiResponse(responseCode = "400", description = "`delta`가 1이나 -1이 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/issues/{number}/weight")
    @Transactional
    fun weight(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val delta = json.get("delta")?.takeIf { it.isNumber }?.asInt()?.takeIf { it == 1 || it == -1 } ?: return requests.message(HttpStatus.BAD_REQUEST, "delta must be 1 or -1")
        return ok(linkedMapOf("result" to linkedMapOf("weight" to issueService.adjustWeight(issue, delta))))
    }

    // --- 지켜보기 ---

    /** 이슈를 지켜본다. 이미 지켜보고 있어도 그대로 성공한다. 읽을 수 있어야 한다. 결과: `{"result": {"watching": true}}`. */
    @ApiOperation(operationId = "watchIssue", tags = [ApiTags.ISSUE], summary = "이슈 지켜보기", description = "토큰 주인이 이슈를 지켜본다. 이미 지켜보고 있어도 성공한다. 읽을 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "지켜보는 상태", content = [Content(schema = Schema(implementation = WatchingResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/issues/{number}/watchers")
    @Transactional
    fun watch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        watchService.watch(user, ResourceType.ISSUE_POST, requireNotNull(issue.id).toString())
        return ok(linkedMapOf("result" to linkedMapOf("watching" to true)))
    }

    /** 이슈 지켜보기를 그만둔다. 이미 그만둔 상태여도 그대로 성공한다. 읽을 수 있어야 한다. 결과: `{"result": {"watching": false}}`. */
    @ApiOperation(operationId = "unwatchIssue", tags = [ApiTags.ISSUE], summary = "이슈 지켜보기 그만두기", description = "지켜보기를 그만둔다. 이미 그만둔 상태여도 성공한다. 읽을 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "지켜보는 상태", content = [Content(schema = Schema(implementation = WatchingResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/issues/{number}/watchers")
    @Transactional
    fun unwatch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        watchService.unwatch(user, ResourceType.ISSUE_POST, requireNotNull(issue.id).toString())
        return ok(linkedMapOf("result" to linkedMapOf("watching" to false)))
    }

    // --- 옮기기 ---

    /**
     * 이슈를 다른 프로젝트로 옮긴다(로드맵 X-37d, `decisions.md` D-52). 본문 `{"targetOwner", "targetProject"}`이다. 웹의 이슈 수정(`targetProjectId`)과 같은 서비스를 쓴다: 이슈와
     * 하위 이슈가 대상 프로젝트의 다음 번호를 받고, 마일스톤은 지워지고, 댓글이 따라가며, 라벨은 옮기는 사람이 대상 프로젝트의 구성원이면 같은 분류와 이름으로 옮겨지고(없으면
     * 만든다) 아니면 지워지며, 이동 이벤트(`ISSUE_MOVED`)가 남는다. 이슈를 고칠 수 있어야 하고 대상 프로젝트에 이슈를 만들 수 있어야 한다(403). 같은 프로젝트는 400, 없는 프로젝트는
     * 404이다. 결과: `{"result": {...}}`(옮긴 뒤의 이슈).
     */
    @ApiOperation(operationId = "transferIssue", tags = [ApiTags.ISSUE], summary = "이슈를 다른 프로젝트로 옮기기", description = "이슈와 하위 이슈가 대상 프로젝트의 다음 번호를 받고, 마일스톤은 지워지고, 댓글이 따라가며, 라벨은 옮기는 사람이 대상 프로젝트의 구성원이면 같은 분류와 이름으로 옮겨지고 아니면 지워진다. 이동 이벤트가 남는다. 이슈를 고칠 수 있어야 하고 대상 프로젝트에 이슈를 만들 수 있어야 한다. 웹의 이슈 수정에서 대상 프로젝트를 고르는 것과 같다.")
    @BodyDoc(description = "옮길 프로젝트", required = true, content = [Content(schema = Schema(implementation = IssueTransferRequest::class))])
    @ApiResponse(responseCode = "200", description = "옮긴 뒤의 이슈(새 번호, 새 프로젝트)", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "400", description = "대상이 없거나 같은 프로젝트이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "이슈나 대상 프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$PROJECT/issues/{number}/transfer")
    @Transactional
    fun transfer(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val targetOwner = json.get("targetOwner")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "targetOwner is required")
        val targetName = json.get("targetProject")?.takeIf { it.isString }?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "targetProject is required")
        val target = projectService.findByOwnerAndProjectName(targetOwner, targetName) ?: return requests.message(HttpStatus.NOT_FOUND, "no project by request")
        if (target.id == issue.project?.id) return requests.message(HttpStatus.BAD_REQUEST, "the issue is already in the project")
        if (!accessControl.isProjectResourceCreatable(user, target, ResourceType.ISSUE_POST)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val edit = IssueEdit(
            title = issue.title.orEmpty(), body = issue.body.orEmpty(), dueDate = issue.dueDate, state = issue.state, draft = issue.isDraft == true, publish = false, notifyMail = false,
            parentIssueId = issue.parent?.id, changeParent = false, assigneeLoginId = issue.assignee?.user?.loginId, milestoneId = issue.milestone?.id, labelIds = issue.labels.mapNotNull { it.id }.toSet()
        )
        val moved = issueService.update(issue, user, edit, target)
        return ok(linkedMapOf("result" to issueJson.of(moved, request)))
    }

    private fun find(owner: String, projectName: String, number: Long): Issue? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        return issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
    }

    private fun ok(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
    }
}
