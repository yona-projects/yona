package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
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
 * 라벨과 마일스톤을 고치고 지우는 REST이다(로드맵 X-29c, `decisions.md` D-47). 만들기는 v1.6에서 옮긴 [ExternalProjectApiController]에 이미 있다.
 * 경로는 v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사에 GitHub의 자원 경로를 얹은 것이고 토큰 전용이다(X-5).
 *
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). 고치기는 `UPDATE`, 지우기는 `DELETE`이고 웹 화면과 같은 판정을 따른다
 * (라벨과 마일스톤은 프로젝트 구성원이 고치고 지운다).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalProjectWriteApiController(
    private val projectService: ProjectService,
    private val labels: IssueLabelRepository,
    private val labelService: IssueLabelService,
    private val issueService: IssueService,
    private val milestones: MilestoneRepository,
    private val milestoneService: MilestoneService,
    private val projectJson: ProjectJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    // --- 라벨 ---

    /**
     * 라벨을 고친다. 본문의 `labelName`, `labelColor`, `category`(분류 이름) 중 보낸 항목만 바꾼다. 분류가 없으면 단일 선택이 아닌 분류로 새로 만든다.
     * 값이 비면 400, 이름이 255자를 넘으면 400, 같은 분류에 같은 이름의 다른 라벨이 있으면 409이다. 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "patchLabel", tags = [ApiTags.LABEL], summary = "라벨 고치기", description = "`labelName`, `labelColor`, `category` 중 보낸 항목만 바꾼다. 분류가 없으면 단일 선택이 아닌 분류로 새로 만든다. 값이 비면 400이고 같은 분류에 같은 이름의 다른 라벨이 있으면 409이다. 구성원이 고친다.")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = LabelPatchRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 라벨", content = [Content(schema = Schema(implementation = LabelResult::class))])
    @ApiResponse(responseCode = "400", description = "보낸 항목이 없거나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 라벨이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 분류에 같은 이름이 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("$PROJECT/labels/{labelId}")
    @Transactional
    fun patchLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable labelId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (project, label) = findLabel(owner, projectName, labelId) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, label.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!json.has("labelName") && !json.has("labelColor") && !json.has("category")) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: labelName, labelColor, category")
        val name = if (json.has("labelName")) text(json.get("labelName"), MAX_LENGTH) ?: return requests.message(HttpStatus.BAD_REQUEST, "labelName must be 1 to $MAX_LENGTH characters") else label.name.orEmpty()
        val color = if (json.has("labelColor")) text(json.get("labelColor"), null) ?: return requests.message(HttpStatus.BAD_REQUEST, "labelColor must not be empty") else label.color.orEmpty()
        val categoryName = if (json.has("category")) text(json.get("category"), MAX_LENGTH) ?: return requests.message(HttpStatus.BAD_REQUEST, "category must be 1 to $MAX_LENGTH characters") else null
        val existing = categoryName?.let { labelService.findCategory(project, it) } ?: label.category.takeIf { categoryName == null }
        if (existing != null) {
            val same = labelService.findLabel(project, existing, name)
            if (same != null && same.id != label.id) return requests.message(HttpStatus.CONFLICT, "a label with the same name exists in the category")
        }
        label.name = name
        label.color = color
        label.category = existing ?: labelService.createCategory(project, requireNotNull(categoryName), false)
        labels.save(label)
        return ok(linkedMapOf("result" to projectJson.label(label)))
    }

    /** 라벨을 지운다. 이슈와 게시글과 풀 리퀘스트에 붙은 것도 함께 떼고, 남은 라벨이 없는 분류도 지운다. 성공하면 204이다. */
    @ApiOperation(operationId = "deleteLabel", tags = [ApiTags.LABEL], summary = "라벨 지우기", description = "라벨을 지운다. 이슈와 게시글과 풀 리퀘스트에 붙은 것도 함께 떼고, 남은 라벨이 없는 분류도 지운다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 라벨이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/labels/{labelId}")
    @Transactional
    fun deleteLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable labelId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val (_, label) = findLabel(owner, projectName, labelId) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, label.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        issueService.deleteLabel(label)
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 마일스톤 ---

    /**
     * 마일스톤을 고친다. 본문의 `title`, `description`, `due_on`(`null`이면 지움), `state`(`open`/`closed`) 중 보낸 항목만 바꾼다. 마감일은 그날의 마지막 초로 맞춘다.
     * 제목이 비거나, 상태나 마감일을 읽을 수 없으면 400, 바꾼 제목이 다른 마일스톤과 같으면 409이다. 결과: `{"result": {...}}`.
     */
    @ApiOperation(operationId = "patchMilestone", tags = [ApiTags.MILESTONE], summary = "마일스톤 고치기", description = "`title`, `description`, `due_on`(`null`이면 지움), `state` 중 보낸 항목만 바꾼다. 마감일은 그날의 마지막 초로 맞춘다. 제목이 비거나 상태와 마감일을 읽을 수 없으면 400, 바꾼 제목이 다른 마일스톤과 같으면 409이다.")
    @BodyDoc(description = "바꿀 항목", required = true, content = [Content(schema = Schema(implementation = MilestonePatchRequest::class))])
    @ApiResponse(responseCode = "200", description = "고친 마일스톤", content = [Content(schema = Schema(implementation = MilestoneResult::class))])
    @ApiResponse(responseCode = "400", description = "보낸 항목이 없거나 값이 틀렸다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "404", description = "프로젝트나 마일스톤이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @ApiResponse(responseCode = "409", description = "같은 제목의 마일스톤이 있다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @PatchMapping("$PROJECT/milestones/{milestoneId}")
    @Transactional
    fun patchMilestone(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable milestoneId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (project, milestone) = findMilestone(owner, projectName, milestoneId) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, milestone.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!json.has("title") && !json.has("description") && !json.has("due_on") && !json.has("state")) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: title, description, due_on, state")
        val title = if (json.has("title")) text(json.get("title"), null) ?: return requests.message(HttpStatus.BAD_REQUEST, "title must not be empty") else milestone.title.orEmpty()
        val state = if (json.has("state")) when (json.get("state").asString("").lowercase()) {
            "open" -> State.OPEN
            "closed" -> State.CLOSED
            else -> return requests.message(HttpStatus.BAD_REQUEST, "state must be open or closed")
        } else milestone.state ?: State.OPEN
        val dueDate = if (json.has("due_on")) {
            val node = json.get("due_on")
            if (node.isNull) null else ApiDueDates.parse(node.asString("")) ?: return requests.message(HttpStatus.BAD_REQUEST, "due_on must be a date such as 2030-01-02")
        } else milestone.dueDate
        val contents = if (json.has("description")) json.get("description").let { if (it.isNull) "" else it.asString("") } else milestone.contents
        if (title != milestone.title && !milestoneService.isUniqueTitle(project, title)) return requests.message(HttpStatus.CONFLICT, "a milestone with the same title exists")
        milestoneService.update(milestone, title, contents, state, dueDate)
        return ok(linkedMapOf("result" to projectJson.milestone(milestone)))
    }

    /** 마일스톤을 지운다. 속한 이슈의 마일스톤은 비워진다. 성공하면 204이다. */
    @ApiOperation(operationId = "deleteMilestone", tags = [ApiTags.MILESTONE], summary = "마일스톤 지우기", description = "마일스톤을 지운다. 속한 이슈의 마일스톤은 비워진다.")
    @ApiResponse(responseCode = "204", description = "지웠다.")
    @ApiResponse(responseCode = "404", description = "프로젝트나 마일스톤이 없다.", content = [Content(schema = Schema(ref = "#/components/schemas/ErrorResponse"))])
    @DeleteMapping("$PROJECT/milestones/{milestoneId}")
    @Transactional
    fun deleteMilestone(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable milestoneId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val (_, milestone) = findMilestone(owner, projectName, milestoneId) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, milestone.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        milestoneService.delete(milestone)
        return ResponseEntity.noContent().build<Void>()
    }

    /** 문자열 값이다. 앞뒤 공백을 지우고 비었거나 [max]를 넘으면 `null`이다. */
    private fun text(node: tools.jackson.databind.JsonNode, max: Int?): String? =
        node.takeIf { !it.isNull }?.asString("")?.trim()?.takeIf { it.isNotEmpty() && (max == null || it.length <= max) }

    private fun findLabel(owner: String, projectName: String, id: Long): Pair<Project, IssueLabel>? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        val label = labels.findById(id).orElse(null)?.takeIf { it.project?.id == project.id } ?: return null
        return project to label
    }

    private fun findMilestone(owner: String, projectName: String, id: Long): Pair<Project, Milestone>? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        val milestone = milestones.findById(id).orElse(null)?.takeIf { it.project?.id == project.id } ?: return null
        return project to milestone
    }

    private fun ok(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        const val MAX_LENGTH = 255
    }
}
