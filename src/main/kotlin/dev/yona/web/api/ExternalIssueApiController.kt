package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.descrKey
import dev.yona.domain.issue.ExternalIssue
import dev.yona.domain.issue.ExternalIssueService
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueCommentService
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssuePatch
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.yona.web.api.docs.*
import io.swagger.v3.oas.annotations.Operation as ApiOperation
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.parameters.RequestBody as BodyDoc

/**
 * 외부 도구(마이그레이션, 자동화)가 쓰는 이슈 REST API이다. v1.6 `IssueApi`의 `getIssue`, `newIssues`, `updateIssue`, `updateIssueState`,
 * `newIssueComment`, `updateIssueLabel`, `imports`(`app/controllers/api/IssueApi.java:58-235, 246-476, 515-535, 637-672`)에 해당한다.
 *
 * 인증은 `Authorization: token <값>` 헤더의 개인 토큰이다. 이슈 만들기, 라벨 바꾸기, 가져오기는 마이그레이션 화면이 세션 로그인으로도 쓴다.
 * 토큰으로만 인증하는 경로(조회, 수정, 상태 변경, 토큰 댓글)는 v1.6처럼 개인 토큰이 유효하면 프로젝트 권한을 확인하지 않는다.
 * 토큰은 사용자가 외부 도구에 맡긴 보안 자격이라고 보기 때문이다([decisions.md] D-20). 세션이나 토큰 어느 쪽으로도 부르는 라벨 바꾸기와
 * 글 가져오기는 v1.6에 인증 확인이 없었으므로 프로젝트 권한을 확인한다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalIssueApiController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val issueEvents: IssueEventRepository,
    private val postings: PostingRepository,
    private val comments: IssueCommentRepository,
    private val labels: IssueLabelRepository,
    private val labelService: IssueLabelService,
    private val issueService: IssueService,
    private val milestones: MilestoneRepository,
    private val commentService: IssueCommentService,
    private val service: ExternalIssueService,
    private val issueJson: IssueJson,
    private val apiUsers: ApiUsers,
    private val requests: ApiRequests,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val attachments: PostAttachments,
    private val messages: MessageSource,
    private val views: dev.yona.web.project.ProjectViewFactory
) {

    // --- 조회 ---

    /** 이슈 하나와 그 이벤트를 JSON으로 돌려준다. 토큰이 필요하다. */
    @ApiOperation(operationId = "getIssue", tags = [ApiTags.ISSUE], summary = "이슈 한 건 조회", description = "이슈의 본문, 담당자, 라벨, 마일스톤, 첨부, 댓글(한 줄 댓글 포함), 이벤트를 돌려준다. 이슈를 읽을 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "이슈와 이벤트", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional(readOnly = true)
    fun getIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return requests.message(HttpStatus.BAD_REQUEST, "no project by request")
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return requests.message(HttpStatus.BAD_REQUEST, "no issue by request")
        // 토큰은 범위와 사용자의 실제 권한의 교집합만 할 수 있다(X-29a a0, `decisions.md` D-36 4번). v1.6은 권한을 보지 않았다(D-20, 대체)
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return result(HttpStatus.OK, issue, user, request)
    }

    // --- 등록 ---

    /**
     * 이슈 여러 개를 만든다. 본문: `{"issues": [...], "sendNotification": 불리언}`. 항목마다 `{"status": 201, "location": 주소}`를 담아 `201`로 답한다.
     * `number`를 주면 그 번호로 만들고(이슈 가져오기), `author`는 이메일로 찾고 없으면 익명 사용자(번호 -1)로 기록한다.
     */
    @ApiOperation(operationId = "createIssues", tags = [ApiTags.ISSUE], summary = "이슈 여러 개 만들기(가져오기 포함)", description = "이슈를 한 번에 여러 개 만든다. `number`를 주면 그 번호로 만들고(이슈 가져오기), `author`는 이메일로 찾으며 없으면 익명 사용자(번호 -1)로 기록한다. 알림은 `sendNotification`이 `true`일 때만 보낸다. 항목마다 만든 이슈의 주소를 모아 `201`로 답한다. 이슈를 만들 수 있어야 한다.")
    @BodyDoc(description = "만들 이슈 목록", required = true, content = [Content(schema = Schema(implementation = IssuesCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "항목마다 만든 이슈의 주소", content = [Content(array = ArraySchema(schema = Schema(implementation = CreatedLocation::class)))])
    @ApiResponse(responseCode = "400", description = "`issues`가 배열이 아니거나 `title`이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues")
    @IsCreatable(ResourceType.ISSUE_POST)
    @Transactional
    fun newIssues(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issuesNode = json.findValue("issues")
        if (issuesNode == null || !issuesNode.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No issues key exists or value wasn't array!")
        val notify = json.findValue("sendNotification")?.asBoolean(false) == true
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")

        val created = issuesNode.toList().map { node ->
            val title = node.findValue("title")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "title is required")
            val author = findAuthor(node.findValue("author"), viewer)
            val issue = service.create(
                project, author,
                ExternalIssue(
                    title = title, body = node.findValue("body")?.asString().orEmpty(), state = state(node), createdDate = parseDate(node.findValue("createdAt")),
                    updatedDate = parseDate(node.findValue("updatedAt")), assignee = assigneeByEmail(node.findValue("assignees")), milestoneTitle = node.findValue("milestoneTitle")?.asString(),
                    dueDate = parseDate(node.findValue("dueDate")), labels = labelPairs(node.findValue("labels")), number = node.findValue("number")?.asLong(0)?.takeIf { it > 0 }
                ),
                notify, viewer
            )
            attachments.attachIds(viewer, ids(node.findValue("temporaryUploadFiles")), ResourceType.ISSUE_POST, requireNotNull(issue.id))
            linkedMapOf("status" to 201, "location" to dev.yona.util.UrlPaths.withContext(request.contextPath, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}"))
        }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(created)
    }

    // --- 수정 ---

    /** 이슈의 제목, 본문, 마일스톤, 상태, 담당자를 한꺼번에 바꾼다. 상태와 담당자가 바뀌면 이벤트를 남긴다. 토큰이 필요하다. */
    @ApiOperation(operationId = "replaceIssue", tags = [ApiTags.ISSUE], summary = "이슈 수정(전체, v1.6 호환)", description = "제목, 본문, 마일스톤, 상태, 담당자를 한꺼번에 바꾼다. 네 항목 `title`, `body`, `state`, `assignees`가 모두 있어야 한다. 상태나 담당자가 바뀌면 이벤트를 남긴다. 일부만 바꾸려면 `PATCH`를 쓴다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "바꿀 값", required = true, content = [Content(schema = Schema(implementation = IssueUpdateRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "400", description = "필수 항목이 빠졌다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PutMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional
    fun updateIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val title = json.findValue("title")?.asString()
        val body = json.findValue("body")?.asString()
        val assignees = json.findValue("assignees")
        if (title == null || body == null || json.findValue("state") == null || assignees == null) return requests.message(HttpStatus.BAD_REQUEST, "title, body, state and assignees are required")
        service.update(issue, user, title, body, json.findValue("milestoneTitle")?.asString(), state(json), assigneeByEmail(assignees))
        return result(HttpStatus.OK, issue, user, request)
    }

    /**
     * 이슈를 부분 수정한다. 본문의 `title`, `body`, `state`(`open`/`closed`), `milestone`(번호, `null`이면 지움), `assignees`(로그인 ID 목록, 담당자는 한 명까지이고 빈 목록이면 지움),
     * `labels`(라벨 번호 목록, 빈 목록이면 모두 뗌) 중 보낸 항목만 바꾼다(로드맵 X-29a, `decisions.md` D-47). `state`만 보내는 v1.6 호출은 그대로 동작한다(`state`가 `OPEN`이 아니면
     * 닫힘). 항목 하나라도 틀리면 아무것도 바꾸지 않고 400이다. 이슈를 고칠 수 있어야 한다. 토큰이 필요하다.
     */
    @ApiOperation(operationId = "patchIssue", tags = [ApiTags.ISSUE], summary = "이슈 부분 수정", description = "본문의 `title`, `body`, `state`, `milestone`, `assignees`, `labels` 중 보낸 항목만 바꾼다. `state`만 보내는 v1.6 호출도 그대로 동작한다(`state`가 `open`이 아니면 닫힘). 항목 하나라도 틀리면 아무것도 바꾸지 않고 400이다. 이슈를 고칠 수 있어야 한다(구성원은 남의 이슈도 고친다).")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = IssuePatchRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "400", description = "보낸 항목이 없거나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional
    fun patchIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val keys = PATCH_FIELDS.filter { json.has(it) }
        if (keys.isEmpty()) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: title, body, state, milestone, assignees, labels")
        if (keys == listOf("state")) {
            service.updateState(issue, user, state(json))
            return result(HttpStatus.OK, issue, user, request)
        }
        val patch = when (val parsed = parsePatch(json, requireNotNull(issue.project))) {
            is PatchParse.Bad -> return requests.message(HttpStatus.BAD_REQUEST, parsed.message)
            is PatchParse.Ok -> parsed.patch
        }
        service.patch(issue, user, patch)
        return result(HttpStatus.OK, issue, user, request)
    }

    /** 부분 수정의 본문을 검사해 [IssuePatch]로 만든다. 틀린 항목이 하나라도 있으면 그 이유를 돌려준다. */
    private fun parsePatch(json: JsonNode, project: Project): PatchParse {
        val title = if (json.has("title")) json.get("title").asString("").takeIf { it.isNotBlank() } ?: return PatchParse.Bad("title must not be empty") else null
        val body = if (json.has("body")) json.get("body").let { if (it.isNull) "" else it.asString("") } else null
        val state = if (json.has("state")) when (json.get("state").asString("").lowercase()) {
            "open" -> State.OPEN
            "closed" -> State.CLOSED
            else -> return PatchParse.Bad("state must be open or closed")
        } else null
        var milestoneId: Long? = null
        if (json.has("milestone")) {
            val node = json.get("milestone")
            if (!node.isNull) {
                milestoneId = if (node.isNumber) node.asLong() else return PatchParse.Bad("milestone must be a number or null")
                if (milestones.findById(milestoneId).orElse(null)?.project?.id != project.id) return PatchParse.Bad("no milestone by request")
            }
        }
        var assignee: String? = null
        if (json.has("assignees")) {
            val node = json.get("assignees")
            if (!node.isArray || node.size() > 1) return PatchParse.Bad("assignees must be a list with at most one login id")
            if (node.size() == 1) {
                assignee = node.get(0).asString("").takeIf { it.isNotBlank() } ?: return PatchParse.Bad("assignees must contain a login id")
                if (users.findByLoginId(assignee) == null) return PatchParse.Bad("no user by request: $assignee")
            }
        }
        var labelIds: Set<Long> = emptySet()
        if (json.has("labels")) {
            val node = json.get("labels")
            if (!node.isArray || node.toList().any { !it.isNumber }) return PatchParse.Bad("labels must be a list of label numbers")
            labelIds = node.toList().map { it.asLong() }.toSet()
            if (labelService.findByIds(project, labelIds).size != labelIds.size) return PatchParse.Bad("no label by request")
            if (issueService.violatesExclusiveCategory(labelIds)) return PatchParse.Bad("labels of an exclusive category cannot be combined")
        }
        return PatchParse.Ok(
            IssuePatch(
                title = title, body = body, state = state, milestoneSet = json.has("milestone"), milestoneId = milestoneId,
                assigneeSet = json.has("assignees"), assigneeLoginId = assignee, labelsSet = json.has("labels"), labelIds = labelIds
            )
        )
    }

    private sealed interface PatchParse {
        data class Ok(val patch: IssuePatch) : PatchParse
        data class Bad(val message: String) : PatchParse
    }

    // --- 댓글 ---

    /**
     * 이슈에 댓글을 단다. 인증은 항상 토큰이고(`/-_-api/v1`은 토큰 전용, X-5) 흐름은 본문 모양으로 가린다. 본문에 `comment`가 있으면 `{"comment": 내용}`을 받아
     * 이슈 결과를 `201`로 돌려주고(토큰 흐름), 없으면 `{"body", "createdAt", "author", "temporaryUploadFiles"}`를 받아 `{"status": 201, "location": 주소}`로 답한다
     * (가져오기 흐름. 가져오기용이라 알림은 보내지 않는다). 두 흐름의 요청과 응답 모양은 v1.6 그대로이다.
     */
    @ApiOperation(operationId = "createIssueComment", tags = [ApiTags.ISSUE_COMMENT], summary = "이슈 댓글 달기", description = "본문에 `comment`가 있으면 토큰 주인이 다는 일반 흐름이다(응답은 이슈 전체와 `201`). 없으면 가져오기 흐름으로 `body`, `author`, `createdAt`, `temporaryUploadFiles`를 쓰고 만든 댓글의 주소를 `201`로 답하며 알림은 보내지 않는다. 댓글을 만들 수 있어야 한다.")
    @BodyDoc(description = "댓글 내용", required = true, content = [Content(schema = Schema(implementation = IssueCommentCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "일반 흐름은 이슈 전체(`result`), 가져오기 흐름은 만든 댓글의 주소(`status`, `location`)", content = [Content(schema = Schema(oneOf = [IssueResult::class, CreatedLocation::class]))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments")
    @Transactional
    fun newIssueComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val project = requireNotNull(issue.project)
        // v1.6은 `Authorization` 헤더의 유무로 두 흐름을 가렸다(헤더가 있으면 토큰 흐름, 없으면 세션 흐름). `/-_-api/v1`이 토큰 전용이 되었으므로(X-5, D-36)
        // 인증은 항상 토큰이고 흐름은 본문 모양으로 가린다: `comment`가 있으면 토큰 흐름(응답 `{"result": …}`), 없으면 가져오기 흐름(본문 `body`, `author`, `createdAt`, 응답 `{"status", "location"}`)
        if (json.has("comment")) {
            val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
            val text = json.findValue("comment")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "comment is required")
            if (!accessControl.isResourceCreatable(user, issue.asResource(), ResourceType.ISSUE_COMMENT)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
            service.addComment(issue, user, text, null)
            return result(HttpStatus.CREATED, issue, user, request)
        }
        val viewer = currentUserProvider.get().user
        if (!accessControl.isResourceCreatable(viewer, issue.asResource(), ResourceType.ISSUE_COMMENT)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val author = findAuthor(json.findValue("author"), requireNotNull(viewer))
        val comment = service.addComment(issue, author, json.findValue("body")?.asString().orEmpty(), parseDate(json.findValue("createdAt")))
        attachments.attachIds(viewer, ids(json.findValue("temporaryUploadFiles")), ResourceType.ISSUE_COMMENT, requireNotNull(comment.id))
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(linkedMapOf("status" to 201, "location" to dev.yona.util.UrlPaths.withContext(request.contextPath, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issue/${issue.number}#comment-${comment.id}")))
    }

    // --- 삭제와 댓글, 라벨, 담당자 (X-29a, D-47) ---

    /**
     * 이슈를 지운다. 이슈를 지울 수 있는 사용자(작성자나 프로젝트 관리자)여야 하고, 다른 사람이 쓴 댓글이 있으면 지울 수 없어 409이다(v1.6 `canBeDeleted`). 성공하면 204이다.
     */
    @ApiOperation(operationId = "deleteIssue", tags = [ApiTags.ISSUE], summary = "이슈 삭제", description = "이슈를 지운다. 이슈를 지울 수 있는 사용자(작성자나 프로젝트 관리자)여야 한다. 다른 사람이 쓴 댓글이 있으면 지울 수 없어 409이다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "다른 사람이 쓴 댓글이 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}")
    @Transactional
    fun deleteIssue(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!issueService.canBeDeleted(issue)) return requests.message(HttpStatus.CONFLICT, "the issue has comments written by others")
        issueService.delete(issue, user)
        return ResponseEntity.noContent().build<Void>()
    }

    /** 이슈의 댓글 목록이다. 오래된 순서이고 한 줄 댓글은 `childComments`로 묶인다. 이슈를 읽을 수 있어야 한다. 결과: `{"result": [...]}`. */
    @ApiOperation(operationId = "listIssueComments", tags = [ApiTags.ISSUE_COMMENT], summary = "이슈 댓글 목록", description = "이슈의 댓글을 오래된 순서로 돌려준다. 한 줄 댓글은 부모 댓글의 `childComments`로 묶인다. 이슈를 읽을 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "댓글 목록", content = [Content(schema = Schema(implementation = CommentListResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @GetMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments")
    @Transactional(readOnly = true)
    fun listComments(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to issueJson.commentsOf(issue)))
    }

    /** 댓글을 고친다. 본문 `{"body": 내용}`이다. 구성원은 남의 댓글도 고치고(D-48), 내용이 비면 400이다. 결과: `{"result": {"id", "body", ...}}`. */
    @ApiOperation(operationId = "editIssueComment", tags = [ApiTags.ISSUE_COMMENT], summary = "이슈 댓글 고치기", description = "댓글 내용을 바꾼다. 구성원은 남의 댓글도 고친다. 내용이 비면 400이다.")
    @BodyDoc(description = "새 내용", required = true, content = [Content(schema = Schema(implementation = CommentBodyRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 댓글", content = [Content(schema = Schema(implementation = CommentEditedResult::class))])
    @ApiResponse(responseCode = "400", description = "내용이 비었다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트, 이슈, 댓글이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId}")
    @Transactional
    fun editComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = comments.findById(commentId).orElse(null)?.takeIf { it.issue?.id == issue.id } ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, comment.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val text = json.get("body")?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "body is required")
        commentService.update(comment, user, text, false, false)
        val node = linkedMapOf<String, Any?>(
            "id" to comment.id, "body" to comment.contents, "createdAt" to issueJson.iso(comment.createdDate),
            "author" to linkedMapOf("loginId" to comment.authorLoginId, "name" to comment.authorName)
        )
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node))
    }

    /** 댓글을 지운다. 작성자와 프로젝트 관리자만 지운다(D-48). 성공하면 204이다. */
    @ApiOperation(operationId = "deleteIssueComment", tags = [ApiTags.ISSUE_COMMENT], summary = "이슈 댓글 삭제", description = "댓글을 지운다. 작성자와 프로젝트 관리자만 지운다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "프로젝트, 이슈, 댓글이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/comments/{commentId}")
    @Transactional
    fun deleteComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = comments.findById(commentId).orElse(null)?.takeIf { it.issue?.id == issue.id } ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, comment.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        commentService.delete(comment)
        return ResponseEntity.noContent().build<Void>()
    }

    /**
     * 이슈에 라벨을 더한다. 본문 `{"labels": [라벨 번호]}`이다. 이미 붙은 라벨은 그대로 두고, 이 프로젝트의 것이 아니거나 같은 분류(배타)의 라벨이 겹치면 400이다.
     * 이슈를 고칠 수 있어야 한다. 결과는 이슈 JSON이다.
     */
    @ApiOperation(operationId = "addIssueLabels", tags = [ApiTags.ISSUE], summary = "이슈에 라벨 더하기", description = "본문의 라벨 번호를 이미 붙은 라벨에 더한다. 이 프로젝트의 라벨이 아니거나 같은 배타 분류의 라벨이 겹치면 400이다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "더할 라벨 번호", required = true, content = [Content(schema = Schema(implementation = LabelIdsRequest::class))])
    @ApiResponse(responseCode = "200", description = "라벨이 붙은 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "400", description = "라벨 번호가 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/labels")
    @Transactional
    fun addLabels(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val node = json.get("labels")
        if (node == null || !node.isArray || node.size() == 0 || node.toList().any { !it.isNumber }) return requests.message(HttpStatus.BAD_REQUEST, "labels must be a non-empty list of label numbers")
        val added = node.toList().map { it.asLong() }.toSet()
        val project = requireNotNull(issue.project)
        if (labelService.findByIds(project, added).size != added.size) return requests.message(HttpStatus.BAD_REQUEST, "no label by request")
        val union = issue.labels.mapNotNull { it.id }.toSet() + added
        if (issueService.violatesExclusiveCategory(union)) return requests.message(HttpStatus.BAD_REQUEST, "labels of an exclusive category cannot be combined")
        service.patch(issue, user, IssuePatch(labelsSet = true, labelIds = union))
        return result(HttpStatus.OK, issue, user, request)
    }

    /** 이슈에서 라벨 하나를 뗀다. 붙어 있지 않아도 그대로 성공한다. 이슈를 고칠 수 있어야 한다. 결과는 이슈 JSON이다. */
    @ApiOperation(operationId = "removeIssueLabel", tags = [ApiTags.ISSUE], summary = "이슈에서 라벨 떼기", description = "라벨 하나를 뗀다. 붙어 있지 않아도 성공한다. 이슈를 고칠 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "라벨을 뗀 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/labels/{labelId}")
    @Transactional
    fun removeLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable labelId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        service.patch(issue, user, IssuePatch(labelsSet = true, labelIds = issue.labels.mapNotNull { it.id }.toSet() - labelId))
        return result(HttpStatus.OK, issue, user, request)
    }

    /**
     * 담당자를 정한다. 본문 `{"assignees": [로그인 ID]}`이다. Yona의 이슈는 담당자가 한 명이라 정확히 한 명을 받고(아니면 400), 이미 담당자가 있으면 바꾼다. 없는 사용자는 400이다.
     * 바뀌면 변경 이벤트를 남긴다. 이슈를 고칠 수 있어야 한다. 결과는 이슈 JSON이다.
     */
    @ApiOperation(operationId = "setIssueAssignee", tags = [ApiTags.ISSUE], summary = "이슈 담당자 정하기", description = "담당자를 정한다. Yona의 이슈는 담당자가 한 명이라 정확히 한 명을 받고, 이미 담당자가 있으면 바꾼다. 없는 사용자는 400이다. 바뀌면 변경 이벤트를 남긴다. 이슈를 고칠 수 있어야 한다.")
    @BodyDoc(description = "담당자", required = true, content = [Content(schema = Schema(implementation = AssigneesRequest::class))])
    @ApiResponse(responseCode = "200", description = "담당자가 바뀐 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "400", description = "담당자가 한 명이 아니거나 없는 사용자이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignees")
    @Transactional
    fun setAssignee(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val node = json.get("assignees")
        if (node == null || !node.isArray || node.size() != 1) return requests.message(HttpStatus.BAD_REQUEST, "assignees must be a list with exactly one login id")
        val loginId = node.get(0).asString("").takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "assignees must contain a login id")
        if (users.findByLoginId(loginId) == null) return requests.message(HttpStatus.BAD_REQUEST, "no user by request: $loginId")
        service.patch(issue, user, IssuePatch(assigneeSet = true, assigneeLoginId = loginId))
        return result(HttpStatus.OK, issue, user, request)
    }

    /** 담당자를 뺀다. 담당자가 없어도 성공한다. 이슈를 고칠 수 있어야 한다. 결과는 이슈 JSON이다. */
    @ApiOperation(operationId = "clearIssueAssignee", tags = [ApiTags.ISSUE], summary = "이슈 담당자 빼기", description = "담당자를 뺀다. 담당자가 없어도 성공한다. 이슈를 고칠 수 있어야 한다.")
    @ApiResponse(responseCode = "200", description = "담당자를 뺀 이슈", content = [Content(schema = Schema(implementation = IssueResult::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/{number}/assignees")
    @Transactional
    fun clearAssignee(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        service.patch(issue, user, IssuePatch(assigneeSet = true, assigneeLoginId = null))
        return result(HttpStatus.OK, issue, user, request)
    }

    // --- 라벨 ---

    /** 이슈의 라벨을 본문의 라벨 번호 배열로 바꾼다. 이슈를 고칠 수 있어야 한다. 결과: `{"id": 소유자, "labels": 개수}`. */
    @ApiOperation(operationId = "replaceIssueLabels", tags = [ApiTags.ISSUE], summary = "이슈 라벨 바꾸기(v1.6 호환)", description = "이슈의 라벨을 본문의 라벨 번호 배열(예: `[\"1\",\"2\"]`)로 통째로 바꾼다. 이슈를 고칠 수 있어야 한다. 하나씩 더하고 떼려면 `POST .../labels`와 `DELETE .../labels/{labelId}`를 쓴다.")
    @BodyDoc(description = "라벨 번호 배열(문자열이나 숫자)", required = true, content = [Content(array = ArraySchema(schema = Schema(type = "string", example = "1")))])
    @ApiResponse(responseCode = "200", description = "바뀐 라벨 수", content = [Content(schema = Schema(implementation = IssueLabelsChanged::class))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 이슈가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issuelabel/{number}")
    @Transactional
    fun updateIssueLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Expecting Json data")
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val issue = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val found = json.toList().mapNotNull { it.asString("").toLongOrNull()?.let { id -> labels.findById(id).orElse(null) } }.toSet()
        service.replaceLabels(issue, found)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("id" to issue.project?.owner, "labels" to issue.labels.size))
    }

    // --- 가져오기 ---

    /**
     * 게시글을 이슈로 옮긴다. `postNumber`로 글을 고른다. 글의 댓글과 첨부가 이슈로 옮겨지고 글은 지워진다. 결과: `{"number": 새 이슈 번호}`.
     * 글을 지울 수 있고 이슈를 만들 수 있어야 한다.
     */
    @ApiOperation(operationId = "importPostAsIssue", tags = [ApiTags.ISSUE], summary = "게시글을 이슈로 옮기기", description = "`postNumber`로 고른 게시글을 이슈로 옮긴다. 글의 댓글과 첨부가 이슈로 옮겨지고 글은 지워진다. 글을 지울 수 있고 이슈를 만들 수 있어야 한다.")
    @io.swagger.v3.oas.annotations.Parameter(name = "postNumber", description = "옮길 게시글 번호", required = true, `in` = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY)
    @ApiResponse(responseCode = "200", description = "새 이슈 번호", content = [Content(schema = Schema(implementation = NumberResponse::class))])
    @ApiResponse(responseCode = "400", description = "`postNumber`가 숫자가 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 게시글이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/issues/imports")
    @Transactional
    fun imports(@PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) postNumber: String?): Any {
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val number = postNumber?.toLongOrNull() ?: return ResponseEntity.badRequest().build<Void>()
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        if (!accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_POST) || !accessControl.isAllowed(viewer, posting.asResource(), Operation.DELETE)) {
            return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        }
        val issue = service.importFromPosting(posting)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("number" to issue.number))
    }

    // --- 도우미 ---

    private companion object {
        /** 부분 수정이 받는 항목 이름이다. */
        val PATCH_FIELDS = listOf("title", "body", "state", "milestone", "assignees", "labels")
    }

    private fun find(owner: String, projectName: String, number: Long): Issue? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        return issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
    }

    /** `{"result": 이슈 + 이벤트}` 응답이다. 이벤트가 하나도 없으면 `events` 키를 뺀다. */
    private fun result(status: HttpStatus, issue: Issue, viewer: User, request: HttpServletRequest): ResponseEntity<*> {
        val node = LinkedHashMap(issueJson.of(issue, request))
        val events = issueEvents.findByIssueIdOrderByCreatedAscIdAsc(requireNotNull(issue.id))
        if (events.isNotEmpty()) {
            node["events"] = events.map { event ->
                val sender = event.senderLoginId?.let { users.findByLoginId(it) }
                linkedMapOf(
                    "id" to event.id, "createdDate" to issueJson.iso(event.created), "eventType" to event.eventType?.name,
                    "eventDescription" to event.eventType?.let { messages.getMessage(it.descrKey, null, LocaleContextHolder.getLocale()) },
                    "oldValue" to event.oldValue, "newValue" to event.newValue,
                    "actor" to linkedMapOf("name" to sender?.let { UserDisplay.pureName(it, viewer.lang) }, "loginId" to sender?.loginId, "englishName" to sender?.englishName)
                )
            }
        }
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node))
    }

    /** `state`가 없으면 열림, `OPEN`(대소문자 무시)이면 열림, 그 밖은 닫힘이다 (v1.6 `findIssueState`). */
    private fun state(json: JsonNode): State {
        val node = json.findValue("state") ?: return State.OPEN
        return if (node.asString("").equals("OPEN", ignoreCase = true)) State.OPEN else State.CLOSED
    }

    /** `yyyy-MM-dd a hh:mm:ss Z` 형식(영어)의 시각이다. 없거나 읽을 수 없으면 `null`이다 (v1.6 `parseDateString`). */
    private fun parseDate(node: JsonNode?): Date? {
        if (node == null) return null
        return try { SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(node.asString()) } catch (e: ParseException) { null }
    }

    /** 담당자 배열의 첫 항목을 이메일로 찾는다 (v1.6 `findAssginee`). 없으면 `null`이다. */
    private fun assigneeByEmail(node: JsonNode?): User? {
        if (node == null || !node.isArray || node.size() == 0) return null
        val email = node.get(0).findValue("email")?.asString() ?: return null
        return apiUsers.lookup(email)
    }

    private fun labelPairs(node: JsonNode?): List<Pair<String, String>> {
        if (node == null || !node.isArray) return emptyList()
        return node.toList().mapNotNull { n ->
            val name = n.findValue("labelName")?.asString() ?: return@mapNotNull null
            val category = n.findValue("category")?.asString() ?: return@mapNotNull null
            name to category
        }
    }

    private fun ids(node: JsonNode?): List<String> = if (node == null || !node.isArray) emptyList() else node.toList().map { it.asString("") }

    /**
     * 작성자를 정한다. `author`가 있으면 이메일로 찾고 없으면 사용자를 만든다. `author`가 없으면 요청한 사용자이다 (v1.6 `findAuthor`).
     * 찾지도 만들지도 못하면 익명 사용자(번호 -1)이다.
     */
    private fun findAuthor(node: JsonNode?, viewer: User): User {
        if (node == null) return viewer
        val email = node.findValue("email")?.asString() ?: return apiUsers.anonymous("")
        return apiUsers.findAuthor(email)
    }
}
