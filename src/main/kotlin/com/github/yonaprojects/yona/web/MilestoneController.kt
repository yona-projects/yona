package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

@RestController
@RequestMapping("/api/projects/{projectId}/milestones")
class MilestoneController(
    private val milestoneService: MilestoneService,
    private val milestoneRepository: MilestoneRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val accessControl: AccessControl
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    private fun checkReadPermission(project: Project, user: User?): Boolean {
        return accessControl.isAllowed(user, project, Operation.READ)
    }

    @GetMapping
    fun getMilestones(
        @PathVariable projectId: Long,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        authentication: Authentication?
    ): ResponseEntity<List<Milestone>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val milestones = milestoneService.getMilestones(projectId, state)
        return ResponseEntity.ok(milestones)
    }

    @GetMapping("/{milestoneId}")
    fun getMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication)
        if (!checkReadPermission(project, user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(milestone)
    }

    @PostMapping
    fun createMilestone(
        @PathVariable projectId: Long,
        @RequestBody request: CreateMilestoneRequest,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.MILESTONE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val milestone = Milestone(
            title = request.title,
            contents = request.contents,
            dueDate = request.dueDate,
            state = request.state ?: State.OPEN,
            project = project
        )

        val saved = milestoneService.createMilestone(projectId, milestone)
        return ResponseEntity.status(HttpStatus.CREATED).body(saved)
    }

    // yona controllers/api/MilestoneApi.java:29-50 newMilestone() 대응 (P1-129). GitHub 이슈 임포트 [GL-controllers_api_MilestoneApi-001;GL-controllers_api_MilestoneApi-002]
    // 등에서 쓰는 벌크 마일스톤 생성 API 전체가 yona에 없었음 — 단건 생성 API(createMilestone())만
    // 있었고, 그마저도 MilestoneServiceImpl.createMilestone()이 state를 항상 OPEN으로 강제해
    // 임포트 시 CLOSED 상태를 그대로 들여올 수 없어 리포지토리를 직접 써서 우회한다.
    @PostMapping("/bulk")
    fun bulkCreateMilestones(
        @PathVariable projectId: Long,
        @RequestBody request: BulkCreateMilestonesRequest,
        authentication: Authentication?
    ): ResponseEntity<List<Map<String, Any?>>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isProjectResourceCreatable(user, project, ResourceType.MILESTONE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val createdMilestones = request.milestones.map { createMilestoneNode(it, project, milestoneRepository) }
        return ResponseEntity.status(HttpStatus.CREATED).body(createdMilestones)
    }

    @PutMapping("/{milestoneId}")
    fun updateMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        @RequestBody request: UpdateMilestoneRequest,
        authentication: Authentication?
    ): ResponseEntity<Milestone> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, milestone, Operation.UPDATE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val updated = milestoneService.updateMilestone(
            milestoneId = milestoneId,
            title = request.title,
            contents = request.contents,
            dueDate = request.dueDate,
            state = request.state ?: State.OPEN
        )

        return ResponseEntity.ok(updated)
    }

    @DeleteMapping("/{milestoneId}")
    fun deleteMilestone(
        @PathVariable projectId: Long,
        @PathVariable milestoneId: Long,
        authentication: Authentication?
    ): ResponseEntity<Map<String, String>> {
        val project = projectRepository.findById(projectId).orElse(null)
            ?: return ResponseEntity.notFound().build()

        val milestone = milestoneService.getMilestone(milestoneId)
            ?: return ResponseEntity.notFound().build()

        if (milestone.project.id != project.id) {
            return ResponseEntity.badRequest().build()
        }

        val user = getLoginUser(authentication) ?: return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        if (!accessControl.isAllowed(user, project, milestone, Operation.DELETE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        milestoneService.deleteMilestone(milestoneId)
        return ResponseEntity.ok(mapOf("status" to "success"))
    }

    data class CreateMilestoneRequest(
        val title: String,
        val contents: String?,
        val dueDate: Instant?,
        val state: State?
    )

    data class UpdateMilestoneRequest(
        val title: String,
        val contents: String?,
        val dueDate: Instant?,
        val state: State?
    )

    data class BulkCreateMilestonesRequest(val milestones: List<BulkMilestoneItem>)

    data class BulkMilestoneItem(
        val title: String? = null,
        val description: String? = null,
        val due_on: String? = null,
        val state: String? = null
    )
}

// yona MilestoneApi.java:52-68 createMilestoneNode() 대응. 제목이 이미 존재하면(프로젝트 내
// 유일해야 함) 생성하지 않고 입력값과 메시지를 그대로 돌려주고, 성공하면 MigrationApp.
// getMilestoneNode()와 동일한 형식({id, title, state, description, due_on})으로 응답한다.
// MilestoneController.bulkCreateMilestones()(projectId 기반)와 MilestoneApiController(legacy
// owner/projectName 기반, P2-58)가 로직 중복 없이 함께 쓰도록 top-level 함수로 분리했다.
internal fun createMilestoneNode(
    item: MilestoneController.BulkMilestoneItem,
    project: Project,
    milestoneRepository: MilestoneRepository
): Map<String, Any?> {
    val title = item.title ?: "No title"
    if (milestoneRepository.findByProjectAndTitle(project, title) != null) {
        return mapOf("milestone" to item, "message" to "이미 존재하는 마일스톤 제목입니다.")
    }

    val state = if (item.state?.equals("closed", ignoreCase = true) == true) State.CLOSED else State.OPEN
    val milestone = Milestone(
        title = title,
        contents = item.description ?: "",
        project = project,
        dueDate = parseDueOn(item.due_on),
        state = state
    )
    val saved = milestoneRepository.save(milestone)

    return mapOf(
        "id" to saved.id,
        "title" to saved.title,
        "state" to saved.state.state(),
        "description" to saved.contents,
        "due_on" to saved.dueDate?.toString()
    )
}

// yona utils/JodaDateUtil.java:84-92 lastSecondOfDay() 대응. 날짜만 오든 전체 ISO 일시가 오든
// 유연하게 파싱해 그날의 23:59:59로 정규화한다.
internal fun parseDueOn(dueOn: String?): Instant? {
    if (dueOn.isNullOrBlank()) return null
    val localDate = try {
        OffsetDateTime.parse(dueOn).toLocalDate()
    } catch (e: Exception) {
        LocalDate.parse(dueOn)
    }
    return localDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant()
}
