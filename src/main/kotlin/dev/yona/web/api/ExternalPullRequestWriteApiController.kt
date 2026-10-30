package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.AcceptResult
import dev.yona.domain.pullrequest.MergeMethod
import dev.yona.domain.pullrequest.PullRequestForm
import dev.yona.domain.pullrequest.PullRequestMergeProcessor
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.enumeration.ResourceType
import dev.yona.repository.BranchService
import dev.yona.web.PostAttachments
import dev.yona.domain.pullrequest.AssignResult
import dev.yona.domain.pullrequest.LabelResult
import dev.yona.domain.pullrequest.PullRequestAssigneeService
import dev.yona.domain.pullrequest.PullRequestLabelService
import dev.yona.domain.pullrequest.PullRequestReviewRequestService
import dev.yona.domain.pullrequest.PullRequestWorkflow
import dev.yona.domain.pullrequest.ReviewRequestResult
import dev.yona.domain.pullrequest.merged
import dev.yona.domain.pullrequest.open
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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
 * 외부용 풀 리퀘스트 REST API의 쓰기이다(로드맵 X-15, `decisions.md` D-39). 화면과 같은 동작(닫기, 다시 열기, 병합, 출발 브랜치 지우기와 되살리기, 담당자, 라벨, 리뷰 요청)을
 * 같은 서비스와 같은 권한(`AccessControl`)으로 노출하고, 성공하면 바뀐 풀 리퀘스트 전체(`GET .../pullrequests/{number}`의 모양)를 돌려준다.
 * 상태가 맞지 않아 할 수 없는 일은 409, 입력이 잘못되면 400이다. 풀 리퀘스트 만들기와 고치기도 이 컨트롤러가 한다. 병합은 `method` 필드를 받지만 지금은 `merge`만 받는다(스쿼시와 리베이스는 로드맵 X-28).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalPullRequestWriteApiController(
    private val support: PullRequestApiSupport,
    private val workflow: PullRequestWorkflow,
    private val assignees: PullRequestAssigneeService,
    private val labelService: PullRequestLabelService,
    private val reviewRequests: PullRequestReviewRequestService,
    private val issueLabels: IssueLabelRepository,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val pullRequestService: PullRequestService,
    private val branches: BranchService,
    private val processor: PullRequestMergeProcessor,
    private val runner: dev.yona.domain.pullrequest.BackgroundJobRunner,
    private val attachments: PostAttachments
) {

    private companion object {
        const val BASE = "/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}"
        const val MAX_TITLE = 255
    }

    /**
     * 풀 리퀘스트를 만든다. 경로의 프로젝트가 받는 프로젝트이다. 본문 `{"title", "body", "fromProject": {"owner", "name"}, "fromBranch", "toBranch", "assignees", "labelIds",
     * "temporaryUploadFiles"}`이고 브랜치는 `master`나 `refs/heads/master`로 적는다. 응답은 201과 풀 리퀘스트(상세 모양)이며 `Location`에 화면 주소가 있다.
     *
     * 화면과 같은 규칙이다: 제목은 필수이고 255자까지, 출발 프로젝트를 쓸 수 있어야 하며(게스트는 403) 같은 브랜치 쌍의 열린 풀 리퀘스트가 있으면 409와
     * `{"pullRequest": {"number", "url"}}`를 돌려준다(화면은 그 요청으로 이동한다). 이 API는 더 엄격하게 출발 프로젝트가 받는 프로젝트 자신이거나 그 포크(또는 원본)여야 하고
     * 두 브랜치가 실제로 있어야 한다. 담당자와 라벨은 받는 프로젝트를 고칠 수 있는 사람만 정한다(D-34 20번, 아니면 403).
     */
    @ApiOperation(operationId = "createPullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 만들기", description = "경로의 프로젝트가 받는 프로젝트이다. 제목은 필수이고 255자까지, 두 브랜치가 실제로 있어야 하며 보내는 프로젝트는 받는 프로젝트 자신이거나 그 포크나 원본이어야 한다. 같은 브랜치 쌍의 열린 풀 리퀘스트가 있으면 409이다. 담당자와 라벨은 받는 프로젝트를 고칠 수 있는 사람만 정한다(아니면 403). `Location` 헤더에 화면 주소가 있다.")
    @BodyDoc(description = "만들 풀 리퀘스트", required = true, content = [Content(schema = Schema(implementation = PullRequestCreateRequest::class))])
    @ApiResponse(responseCode = "201", description = "만든 풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "제목, 프로젝트, 브랜치, 담당자, 라벨이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 브랜치 쌍의 열린 풀 리퀘스트가 이미 있다.", content = [Content(schema = Schema(implementation = PullRequestConflict::class))])
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests")
    @Transactional
    fun create(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, null, request)
        target.error?.let { return it }
        val to = requireNotNull(target.project)
        val viewer = requireNotNull(target.user)
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        val title = body.get("title")?.asString()
        if (title.isNullOrEmpty() || title.length > MAX_TITLE) return support.badRequest("title is required and must be at most $MAX_TITLE characters")
        val fromNode = body.get("fromProject")
        val from = if (fromNode == null) to else projectService.findByOwnerAndProjectName(fromNode.get("owner")?.asString().orEmpty(), fromNode.get("name")?.asString().orEmpty()) ?: return support.badRequest("no fromProject by request")
        if (!isAssociated(from, to)) return support.badRequest("fromProject must be this project or its fork or original")
        if (!accessControl.isAllowed(viewer, from.asResource(), Operation.READ) || projectService.isGuest(from, viewer) || !accessControl.isProjectResourceCreatable(viewer, from, ResourceType.FORK)) {
            return requests.message(HttpStatus.FORBIDDEN, "forbidden")
        }
        val fromBranch = branchName(body.get("fromBranch")?.asString(), from) ?: return support.badRequest("no fromBranch by request")
        val toBranch = branchName(body.get("toBranch")?.asString(), to) ?: return support.badRequest("no toBranch by request")
        val triage = accessControl.isAllowed(viewer, dev.yona.access.ProjectResource("new", ResourceType.PULL_REQUEST, to), Operation.UPDATE)
        val logins = body.get("assignees")?.toList().orEmpty().mapNotNull { it.asString() }.filter { it.isNotBlank() }
        val labelIds = body.get("labelIds")?.toList().orEmpty().map { it.asLong(0) }
        if ((logins.isNotEmpty() || labelIds.isNotEmpty()) && !triage) return requests.message(HttpStatus.FORBIDDEN, "forbidden to set assignees or labels")
        if (logins.size > PullRequestAssigneeService.MAX_ASSIGNEES) return support.badRequest("too many assignees")
        val assignee = logins.firstOrNull()?.let { login -> candidate(login, to, viewer) }
        if (logins.isNotEmpty() && assignee == null) return support.badRequest("not an assignable user: ${logins.first()}")
        val labels = labelsOf(labelIds, to)
        if (labels == null) return support.badRequest("no label of this project by the given ids")

        val creation = pullRequestService.create(PullRequestForm(title, body.get("body")?.asString().orEmpty(), from, to, fromBranch, toBranch), viewer)
        val pullRequest = creation.pullRequest
        if (!creation.created) {
            return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(linkedMapOf("message" to "a pull request for the same branches is already open", "pullRequest" to linkedMapOf("number" to pullRequest.number, "url" to json.of(pullRequest)["url"])))
        }
        attachments.attachIds(viewer, ids(body.get("temporaryUploadFiles")), ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id))
        if (assignee != null) assignees.set(pullRequest, assignee, viewer)
        if (labels.isNotEmpty()) labelService.change(pullRequest, labels, emptyList(), viewer)
        val pullRequestId = requireNotNull(pullRequest.id)
        val senderId = requireNotNull(viewer.id)
        runner.run("merging of pull request $pullRequestId", { processor.start(pullRequestId) }, { processor.process(pullRequestId, senderId, true) })
        val node = json.of(pullRequest)
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).header("Location", node["url"].toString()).body(node)
    }

    /**
     * 제목, 본문, 출발 브랜치, 받는 브랜치를 고친다(주어진 것만). 화면과 같은 규칙이다: 제목은 비울 수 없고 255자까지이며, 브랜치 쌍이 바뀌어 같은 쌍의 다른 열린 풀 리퀘스트와
     * 겹치면 409이다. 브랜치는 실제로 있어야 하고(400) 아무것도 주지 않으면 400이다. 첨부는 `temporaryUploadFiles`로 더한다. 출발 프로젝트는 바꿀 수 없다.
     */
    @ApiOperation(operationId = "updatePullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 수정", description = "제목, 본문, 보내는 브랜치, 받는 브랜치 중 주어진 것만 고친다. 제목은 비울 수 없고 브랜치는 실제로 있어야 하며(400) 바뀐 브랜치 쌍이 다른 열린 풀 리퀘스트와 겹치면 409이다. 아무것도 주지 않으면 400이다.")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = PullRequestPatchRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "제목이나 브랜치가 틀렸거나 바꿀 항목이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 브랜치 쌍의 다른 열린 풀 리퀘스트가 있다.", content = [Content(schema = Schema(implementation = PullRequestConflict::class))])
    @PatchMapping("$BASE")
    @Transactional
    fun update(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val to = requireNotNull(target.project)
        val from = requireNotNull(pullRequest.fromProject)
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        if (listOf("title", "body", "fromBranch", "toBranch", "temporaryUploadFiles").none { body.has(it) }) return support.badRequest("nothing to update")
        val title = if (body.has("title")) body.get("title").asString() else pullRequest.title
        if (title.isNullOrEmpty() || title.length > MAX_TITLE) return support.badRequest("title is required and must be at most $MAX_TITLE characters")
        val fromBranch = if (body.has("fromBranch")) branchName(body.get("fromBranch").asString(), from) ?: return support.badRequest("no fromBranch by request") else requireNotNull(pullRequest.fromBranch)
        val toBranch = if (body.has("toBranch")) branchName(body.get("toBranch").asString(), to) ?: return support.badRequest("no toBranch by request") else requireNotNull(pullRequest.toBranch)
        val text = if (body.has("body")) body.get("body").asString().orEmpty() else pullRequest.body.orEmpty()
        if (fromBranch != pullRequest.fromBranch || toBranch != pullRequest.toBranch) {
            val duplicate = pullRequestService.findDuplicate(PullRequestForm(title, text, from, to, fromBranch, toBranch))
            if (duplicate != null && duplicate.id != pullRequest.id) {
                return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                    .body(linkedMapOf("message" to "a pull request for the same branches is already open", "pullRequest" to linkedMapOf("number" to duplicate.number, "url" to json.of(duplicate)["url"])))
            }
        }
        val user = requireNotNull(target.user)
        workflow.update(pullRequest, title, text, fromBranch, toBranch)
        attachments.attachIds(user, ids(body.get("temporaryUploadFiles")), ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id))
        return support.ok(json.of(pullRequest))
    }

    /** [login]이 [to]의 담당자 후보이면 그 사용자이고 아니면 `null`이다. */
    private fun candidate(login: String, to: Project, viewer: dev.yona.domain.user.User): dev.yona.domain.user.User? {
        val who = users.findByLoginId(login) ?: return null
        return who.takeIf { assignees.candidates(to, viewer).any { c -> c.id == who.id } }
    }

    /** [ids]의 라벨이 모두 [to]의 것이면 그 라벨들이고, 하나라도 없거나 다른 프로젝트의 것이면 `null`이다. */
    private fun labelsOf(ids: List<Long>, to: Project): List<dev.yona.domain.issue.IssueLabel>? {
        val found = ids.mapNotNull { issueLabels.findById(it).orElse(null) }
        return found.takeIf { it.size == ids.size && it.all { label -> label.project?.id == to.id } }
    }

    /** `master`나 `refs/heads/master`를 저장 형식(`refs/heads/...`)으로 바꾼다. 그 프로젝트에 없는 브랜치이면 `null`이다. */
    private fun branchName(raw: String?, project: Project): String? {
        if (raw.isNullOrBlank()) return null
        val full = if (raw.startsWith("refs/")) raw else "refs/heads/$raw"
        return full.takeIf { name -> branches.branches(project).any { it.name == name } }
    }

    /** 출발 프로젝트가 받는 프로젝트 자신이거나 그 포크이거나 원본이면 `true`이다 (v1.6 `Project.getAssociationProjects`). */
    private fun isAssociated(from: Project, to: Project): Boolean =
        from.id == to.id || from.originalProject?.id == to.id || to.originalProject?.id == from.id

    private fun ids(node: JsonNode?): List<String> = if (node == null || !node.isArray) emptyList() else node.toList().map { it.asString() }.filter { it.isNotBlank() }

    /** 풀 리퀘스트를 닫는다. 병합된 것은 409이고 이미 닫힌 것은 그대로 성공이다. */
    @ApiOperation(operationId = "closePullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 닫기", description = "풀 리퀘스트를 닫는다. 병합된 것은 409이고 이미 닫힌 것은 그대로 성공이다.")
    @ApiResponse(responseCode = "200", description = "닫은 풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이미 병합되었다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/close")
    @Transactional
    fun close(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.CLOSE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        if (pullRequest.merged) return support.conflict("pull request is already merged")
        if (pullRequest.open) workflow.close(pullRequest, requireNotNull(target.user))
        return support.ok(json.of(pullRequest))
    }

    /** 닫힌 풀 리퀘스트를 다시 연다. 이미 열렸거나 병합된 것은 409이다. */
    @ApiOperation(operationId = "reopenPullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 다시 열기", description = "닫힌 풀 리퀘스트를 다시 연다. 이미 열렸거나 병합된 것은 409이다.")
    @ApiResponse(responseCode = "200", description = "다시 연 풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "이미 열렸거나 병합되었다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/reopen")
    @Transactional
    fun reopen(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.REOPEN)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        if (!workflow.reopen(pullRequest, requireNotNull(target.user))) return support.conflict("pull request is not closed")
        return support.ok(json.of(pullRequest))
    }

    /**
     * 병합한다. 본문 `{"method": "merge" | "squash" | "rebase", "commitTitle": ..., "commitMessage": ...}`는 모두 생략할 수 있다(로드맵 X-28, `decisions.md` D-41).
     * 방식을 생략하면 프로젝트의 기본 방식이고, 모르는 값은 400 `Unsupported merge method`, 프로젝트가 허용하지 않은 방식은 400 `Merge method not allowed`이다.
     * 제목과 본문은 병합 커밋과 스쿼시 커밋에만 쓰고 리베이스는 무시한다. 리베이스는 원래 커밋을 그대로 옮기며, 한 커밋에서라도 충돌하거나 병합 커밋이 섞여 있으면 409이다.
     * 열려 있지 않거나, 병합 중이거나, 충돌하거나, 리뷰 조건을 채우지 못했으면 409이다. 프로젝트 관리자와 사이트 관리자는 화면처럼 리뷰 조건을 건너뛴다.
     * v1.6처럼 병합 중 표시를 먼저 저장하므로 이 메서드에는 트랜잭션을 걸지 않는다(`refactoring-backlog.md` R-76).
     */
    @ApiOperation(operationId = "mergePullRequest", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 병합", description = "본문은 모두 생략할 수 있다. 방식을 생략하면 프로젝트의 기본 방식이고, 모르는 값이나 프로젝트가 허용하지 않은 방식은 400이다. 제목과 본문은 병합 커밋과 스쿼시 커밋에만 쓴다. 리베이스는 원래 커밋을 그대로 옮기며 충돌하거나 병합 커밋이 섞여 있으면 409이다. 열려 있지 않거나 병합 중이거나 충돌하거나 규칙 묶음의 리뷰 조건과 푸시 제한에 걸리면 409이다. 프로젝트 관리자와 사이트 관리자는 규칙 묶음의 우회 대상일 때 리뷰 조건을 건너뛴다.")
    @BodyDoc(description = "병합 방식과 메시지", required = false, content = [Content(schema = Schema(implementation = MergeRequest::class))])
    @ApiResponse(responseCode = "200", description = "병합한 풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "모르는 병합 방식이거나 허용하지 않은 방식이다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "열려 있지 않거나 병합 중이거나 충돌하거나 병합 조건을 채우지 못했다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/merge")
    fun merge(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.ACCEPT)
        target.error?.let { return it }
        val body = requests.json(request)
        val methodName = body?.get("method")?.takeUnless { it.isNull }?.asString()
        val method = MergeMethod.of(methodName)
        if (methodName != null && method == null) return support.badRequest("Unsupported merge method: $methodName")
        val pullRequest = requireNotNull(target.pullRequest)
        if (!pullRequest.open) return support.conflict("pull request is not open")
        if (pullRequest.isMerging == true) return support.conflict("pull request is being merged")
        if (pullRequest.isConflict == true) return support.conflict("pull request has conflicts")
        val user = requireNotNull(target.user)
        val title = body?.get("commitTitle")?.takeUnless { it.isNull }?.asString()
        val message = body?.get("commitMessage")?.takeUnless { it.isNull }?.asString()
        val targetProject = requireNotNull(target.project)
        val bypass: (List<dev.yona.domain.project.Ruleset>) -> Boolean = { blocking -> accessControl.canBypass(user, targetProject, blocking) }
        val result = workflow.accept(pullRequest, user, bypass, method, title, message)
        return when (result) {
            AcceptResult.MERGED -> support.ok(json.of(pullRequest))
            AcceptResult.METHOD_NOT_ALLOWED -> support.badRequest("Merge method not allowed: $methodName")
            AcceptResult.CONFLICT -> support.conflict("pull request has conflicts")
            AcceptResult.NOT_REVIEWED -> support.conflict("not enough review approvals")
            AcceptResult.UPDATES_RESTRICTED -> support.conflict("updates to the target branch are restricted by a ruleset")
        }
    }

    /** 병합된 풀 리퀘스트의 출발 브랜치를 지운다(화면의 "브랜치 삭제"). */
    @ApiOperation(operationId = "deletePullRequestFromBranch", tags = [ApiTags.PULL_REQUEST], summary = "보내는 브랜치 지우기", description = "병합된 풀 리퀘스트의 보내는 브랜치를 지운다(화면의 \"브랜치 삭제\"). 규칙 묶음이 삭제를 막은 브랜치는 지울 수 없다.")
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$BASE/fromBranch")
    @Transactional
    fun deleteFromBranch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        workflow.deleteFromBranch(pullRequest)
        return support.ok(json.of(pullRequest))
    }

    /** 지운 출발 브랜치를 되살린다. */
    @ApiOperation(operationId = "restorePullRequestFromBranch", tags = [ApiTags.PULL_REQUEST], summary = "보내는 브랜치 되살리기", description = "지운 보내는 브랜치를 되살린다.")
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/fromBranch/restore")
    @Transactional
    fun restoreFromBranch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        workflow.restoreFromBranch(pullRequest)
        return support.ok(json.of(pullRequest))
    }

    /** 담당자를 지정한다. 본문 `{"assignees": ["로그인ID"]}`이고 비우면 해제한다(지금은 1명까지). 없는 사용자와 후보가 아닌 사람은 400이다. */
    @ApiOperation(operationId = "setPullRequestAssignees", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 담당자 정하기", description = "담당자를 지정한다. 비우면 해제한다(지금은 한 명까지). 없는 사용자와 후보가 아닌 사람은 400이다.")
    @BodyDoc(description = "담당자", required = true, content = [Content(schema = Schema(implementation = PullRequestAssigneesRequest::class))])
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "없는 사용자이거나 후보가 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/assignees")
    @Transactional
    fun setAssignees(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val node: JsonNode = requests.json(request)?.get("assignees")?.takeIf { it.isArray } ?: return support.badRequest("assignees must be an array")
        val logins = node.toList().mapNotNull { it.asString() }.filter { it.isNotBlank() }
        if (logins.size > PullRequestAssigneeService.MAX_ASSIGNEES) return support.badRequest("too many assignees")
        val assignee = logins.firstOrNull()?.let { users.findByLoginId(it) ?: return support.badRequest("no user by $it") }
        val pullRequest = requireNotNull(target.pullRequest)
        if (assignees.set(pullRequest, assignee, requireNotNull(target.user)) == AssignResult.NOT_CANDIDATE) return support.badRequest("not an assignable user")
        return support.ok(json.of(pullRequest))
    }

    /**
     * 라벨을 바꾼다. 본문 `{"attachingLabelIds": [...], "detachingLabelIds": [...]}`(둘 다 생략할 수 있다)이다. 없는 라벨이나 받는 프로젝트의 라벨이 아닌 것은
     * 400이고 아무것도 바꾸지 않는다.
     */
    @ApiOperation(operationId = "changePullRequestLabels", tags = [ApiTags.PULL_REQUEST], summary = "풀 리퀘스트 라벨 바꾸기", description = "붙일 라벨과 뗄 라벨의 번호를 보낸다(둘 다 생략할 수 있다). 없는 라벨이나 받는 프로젝트의 라벨이 아니면 400이고 아무것도 바꾸지 않는다.")
    @BodyDoc(description = "붙이고 뗄 라벨", required = true, content = [Content(schema = Schema(implementation = PullRequestLabelsRequest::class))])
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "없는 라벨이거나 받는 프로젝트의 라벨이 아니다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/labels")
    @Transactional
    fun changeLabels(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        fun labelsOf(key: String): List<dev.yona.domain.issue.IssueLabel>? = body.get(key)?.toList().orEmpty().map { issueLabels.findById(it.asLong(0)).orElse(null) ?: return null }
        val attach = labelsOf("attachingLabelIds") ?: return support.badRequest("no label by attachingLabelIds")
        val detach = labelsOf("detachingLabelIds") ?: return support.badRequest("no label by detachingLabelIds")
        val pullRequest = requireNotNull(target.pullRequest)
        if (labelService.change(pullRequest, attach, detach, requireNotNull(target.user)) == LabelResult.WRONG_PROJECT) return support.badRequest("label belongs to another project")
        return support.ok(json.of(pullRequest))
    }

    /** 리뷰를 요청한다. 본문 `{"reviewer": "로그인ID"}`이다. 없는 사용자, 후보가 아닌 사람(작성자, 프로젝트나 조직에 속하지 않은 사람), 열려 있지 않은 풀 리퀘스트는 400이다. */
    @ApiOperation(operationId = "requestPullRequestReview", tags = [ApiTags.PULL_REQUEST], summary = "리뷰 요청하기", description = "사용자에게 리뷰를 요청한다. 없는 사용자, 후보가 아닌 사람(작성자, 프로젝트나 조직에 속하지 않은 사람), 열려 있지 않은 풀 리퀘스트는 400이다.")
    @BodyDoc(description = "리뷰어", required = true, content = [Content(schema = Schema(implementation = ReviewRequestRequest::class))])
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "400", description = "리뷰를 요청할 수 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PostMapping("$BASE/requestedreviewers")
    @Transactional
    fun requestReview(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val login = requests.json(request)?.get("reviewer")?.asString()?.takeIf { it.isNotBlank() } ?: return support.badRequest("reviewer is required")
        val reviewer = users.findByLoginId(login) ?: return support.badRequest("no user by $login")
        val pullRequest = requireNotNull(target.pullRequest)
        val result = reviewRequests.request(pullRequest, reviewer, requireNotNull(target.user))
        if (result == ReviewRequestResult.NOT_CANDIDATE || result == ReviewRequestResult.NOT_OPEN) return support.badRequest("cannot request a review from $login")
        return support.ok(json.of(pullRequest))
    }

    /** 대기 중인 리뷰 요청을 취소한다. 그 사용자가 없으면 404이고 대기 중이 아니면 그대로 성공이다. */
    @ApiOperation(operationId = "cancelPullRequestReviewRequest", tags = [ApiTags.PULL_REQUEST], summary = "리뷰 요청 취소", description = "대기 중인 리뷰 요청을 취소한다. 그 사용자가 없으면 404이고 대기 중이 아니면 그대로 성공이다.")
    @ApiResponse(responseCode = "200", description = "풀 리퀘스트", content = [Content(schema = Schema(implementation = PullRequestDetail::class))])
    @ApiResponse(responseCode = "404", description = "풀 리퀘스트나 사용자가 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$BASE/requestedreviewers/{reviewer}")
    @Transactional
    fun cancelReviewRequest(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable reviewer: String, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.UPDATE)
        target.error?.let { return it }
        val user = users.findByLoginId(reviewer) ?: return requests.message(HttpStatus.NOT_FOUND, "no user by $reviewer")
        val pullRequest = requireNotNull(target.pullRequest)
        reviewRequests.cancel(pullRequest, user, requireNotNull(target.user))
        return support.ok(json.of(pullRequest))
    }
}
