package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.Operation
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentRepository
import com.github.yonaprojects.yona.domain.attachment.AttachmentService
import com.github.yonaprojects.yona.domain.support.MarkdownService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Controller
class MilestoneViewController(
    private val projectRepository: ProjectRepository,
    private val milestoneService: MilestoneService,
    private val milestoneRepository: MilestoneRepository,
    private val issueRepository: IssueRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val userRepository: UserRepository,
    private val attachmentRepository: AttachmentRepository,
    private val markdownService: MarkdownService,
    private val accessControl: AccessControl,
    private val attachmentService: AttachmentService
) {

    data class MilestoneViewDto(
        val milestone: Milestone,
        val openIssuesCount: Int,
        val closedIssuesCount: Int,
        val completionRate: Int,
        val openIssues: List<Issue>,
        val closedIssues: List<Issue>,
        val isOverdue: Boolean,
        val daysBetween: Long?
    )

    private fun toViewDto(milestone: Milestone): MilestoneViewDto {
        val allIssues = issueRepository.findByMilestone(milestone)
        // legacy Milestone.sortedByNumberOfIssue()/sortedByNumberOfOpenIssue()/
        // sortedByNumberOfClosedIssue() 대응 — 이슈 번호 내림차순.
        val openIssues = allIssues.filter { it.state == State.OPEN }.sortedByDescending { it.number }
        val closedIssues = allIssues.filter { it.state == State.CLOSED }.sortedByDescending { it.number }
        
        val total = openIssues.size + closedIssues.size
        val completionRate = if (total > 0) {
            (closedIssues.size * 100) / total
        } else {
            0
        }
        
        val isOverdue = milestone.dueDate?.let { it.isBefore(Instant.now()) } ?: false
        val daysBetween = milestone.dueDate?.let { dueDate ->
            val nowLocalDate = LocalDate.now(ZoneId.systemDefault())
            val dueLocalDate = LocalDate.ofInstant(dueDate, ZoneId.systemDefault())
            ChronoUnit.DAYS.between(nowLocalDate, dueLocalDate)
        }
        
        return MilestoneViewDto(
            milestone = milestone,
            openIssuesCount = openIssues.size,
            closedIssuesCount = closedIssues.size,
            completionRate = completionRate,
            openIssues = openIssues,
            closedIssues = closedIssues,
            isOverdue = isOverdue,
            daysBetween = daysBetween
        )
    }

    @GetMapping("/{owner}/{projectName}/milestones")
    fun listMilestones(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam(required = false, defaultValue = "open") state: String,
        @RequestParam(required = false, defaultValue = "dueDate") orderBy: String,
        @RequestParam(required = false, defaultValue = "asc") orderDir: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // 파라미터 값에 따라 이넘 조회
        val stateEnum = when (state.lowercase()) {
            "closed" -> State.CLOSED
            "all" -> State.ALL
            else -> State.OPEN
        }

        val milestones = milestoneService.getMilestones(project.id!!, stateEnum, orderBy, orderDir)
        var milestoneDtos = milestones.map { toViewDto(it) }

        // legacy Milestone.findMilestones()의 completionRate 정렬(Comparator, DB 컬럼이 아니라
        // 계산 필드라 조회 후 별도 정렬) 대응.
        if (orderBy == "completionRate") {
            milestoneDtos = if (orderDir.equals("desc", ignoreCase = true)) {
                milestoneDtos.sortedByDescending { it.completionRate }
            } else {
                milestoneDtos.sortedBy { it.completionRate }
            }
        }

        model.addAttribute("project", project)
        model.addAttribute("milestones", milestoneDtos)
        model.addAttribute("state", state)
        model.addAttribute("orderBy", orderBy)
        model.addAttribute("orderDir", orderDir)
        model.addAttribute("currentUser", loginUser)

        return "milestone/list"
    }

    @GetMapping("/{owner}/{projectName}/milestone/{id}")
    fun viewMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (!accessControl.isAllowed(loginUser, project, Operation.READ)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val milestone = milestoneService.getMilestone(id) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }
        if (milestone.project.id != project.id) {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }

        val dto = toViewDto(milestone)

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val attachId = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        val contentsHtml = milestone.contents?.let { markdownService.render(it, true, project) } ?: ""

        model.addAttribute("project", project)
        model.addAttribute("milestoneDto", dto)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("attachmentsJson", attachmentsJson)
        model.addAttribute("contentsHtml", contentsHtml)

        return "milestone/view"
    }

    @GetMapping("/{owner}/{projectName}/milestone/new", "/{owner}/{projectName}/newMilestoneForm", "/{owner}/{projectName}/newmilestoneform")
    fun createMilestoneForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        model.addAttribute("project", project)
        model.addAttribute("currentUser", loginUser)

        return "milestone/create"
    }

    @GetMapping("/{owner}/{projectName}/milestone/{id}/editform")
    fun editMilestoneForm(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser))) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val milestone = milestoneService.getMilestone(id) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }
        if (milestone.project.id != project.id) {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }

        val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
        val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
            val attachId = attach.id?.toString() ?: ""
            val mimeType = attach.mimeType ?: ""
            val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
            val url = "/files/${attach.id}"
            val size = attach.size?.toString() ?: "0"
            """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
        }

        model.addAttribute("project", project)
        model.addAttribute("milestone", milestone)
        model.addAttribute("currentUser", loginUser)
        model.addAttribute("attachmentsJson", attachmentsJson)

        return "milestone/edit"
    }

    @PostMapping("/{owner}/{projectName}/milestones")
    fun createMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @RequestParam title: String,
        @RequestParam(required = false) contents: String?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                model.addAttribute("project", project)
                return "error/forbidden"
            }

        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        // 1. 중복 제목 검증
        val isDuplicateTitle = milestoneRepository.findByProjectAndTitle(project, title) != null

        // 2. DueDate 날짜 끝 시간(23:59:59.999) 보정. legacy MilestoneApp.validateDueDate() 대응 —
        // 파싱에 실패하면 조용히 null로 저장하지 않고, 폼 바인딩 오류(hasErrors())로 전체 제출
        // 자체를 막던 것과 동일하게 저장을 막고 오류를 알린다.
        var dueDateError: String? = null
        val parsedDueDate = if (!dueDate.isNullOrBlank()) {
            try {
                val localDate = LocalDate.parse(dueDate)
                localDate.atTime(23, 59, 59, 999000000).atZone(ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                dueDateError = "milestone.error.duedateFormat"
                null
            }
        } else {
            null
        }

        if (isDuplicateTitle || dueDateError != null) {
            model.addAttribute("project", project)
            model.addAttribute("currentUser", loginUser)
            if (isDuplicateTitle) {
                model.addAttribute("titleError", "milestone.title.duplicated")
            }
            if (dueDateError != null) {
                model.addAttribute("dueDateError", dueDateError)
            }
            model.addAttribute("title", title)
            model.addAttribute("contents", contents)
            model.addAttribute("dueDate", dueDate)
            return "milestone/create"
        }

        val milestone = Milestone(
            title = title,
            contents = contents ?: "",
            dueDate = parsedDueDate,
            state = state,
            project = project
        )

        val savedMilestone = milestoneService.createMilestone(project.id!!, milestone)

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            // legacy Attachment.moveOnlySelected() 대응 — 소유권 검증 없이 요청받은 ID를 그대로
            // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮긴다.
            attachmentService.moveOnlySelected(
                fromType = ResourceType.NOT_A_RESOURCE,
                fromId = "",
                toType = ResourceType.MILESTONE,
                toId = savedMilestone.id.toString(),
                selectedIds = fileIds,
                moverLoginId = loginUser.loginId ?: ""
            )
        }

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/milestone/${savedMilestone.id}"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/edit")
    fun editMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        @RequestParam title: String,
        @RequestParam(required = false) contents: String?,
        @RequestParam(required = false) dueDate: String?,
        @RequestParam(required = false, defaultValue = "OPEN") state: State,
        @RequestParam(required = false) temporaryUploadFiles: String?,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"

        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                model.addAttribute("project", project)
                return "error/forbidden"
            }

        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }

        val original = milestoneService.getMilestone(id) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }

        // 1. 중복 제목 검증
        val isDuplicateTitle = original.title != title && milestoneRepository.findByProjectAndTitle(project, title) != null

        // 2. DueDate 날짜 끝 시간(23:59:59.999) 보정. legacy MilestoneApp.validateDueDate() 대응 —
        // 파싱에 실패하면 조용히 null로 저장하지 않고, 폼 바인딩 오류(hasErrors())로 전체 제출
        // 자체를 막던 것과 동일하게 저장을 막고 오류를 알린다.
        var dueDateError: String? = null
        val parsedDueDate = if (!dueDate.isNullOrBlank()) {
            try {
                val localDate = LocalDate.parse(dueDate)
                localDate.atTime(23, 59, 59, 999000000).atZone(ZoneId.systemDefault()).toInstant()
            } catch (e: Exception) {
                dueDateError = "milestone.error.duedateFormat"
                null
            }
        } else {
            null
        }

        if (isDuplicateTitle || dueDateError != null) {
            model.addAttribute("project", project)
            model.addAttribute("currentUser", loginUser)
            if (isDuplicateTitle) {
                model.addAttribute("titleError", "milestone.title.duplicated")
            }
            if (dueDateError != null) {
                model.addAttribute("dueDateError", dueDateError)
            }
            model.addAttribute("dueDate", dueDate)
            val dummyMilestone = Milestone(
                id = id,
                title = title,
                contents = contents,
                dueDate = parsedDueDate,
                state = state,
                project = project
            )
            model.addAttribute("milestone", dummyMilestone)

            // 기존 첨부파일 목록 전달
            val attachments = attachmentRepository.findByContainerTypeAndContainerId(ResourceType.MILESTONE, id.toString())
            val attachmentsJson = attachments.joinToString(prefix = "{\"attachments\":[", postfix = "]}", separator = ",") { attach ->
                val attachId = attach.id?.toString() ?: ""
                val mimeType = attach.mimeType ?: ""
                val name = attach.name.replace("\"", "\\\"").replace("\n", "\\n")
                val url = "/files/${attach.id}"
                val size = attach.size?.toString() ?: "0"
                """{"id":"$attachId","mimeType":"$mimeType","name":"$name","url":"$url","size":$size}"""
            }
            model.addAttribute("attachmentsJson", attachmentsJson)
            return "milestone/edit"
        }

        val updated = milestoneService.updateMilestone(
            milestoneId = id,
            title = title,
            contents = contents ?: "",
            dueDate = parsedDueDate,
            state = state
        )

        if (!temporaryUploadFiles.isNullOrBlank()) {
            val fileIds = temporaryUploadFiles.split(",").mapNotNull { it.trim().toLongOrNull() }
            // legacy Attachment.moveOnlySelected() 대응 — 소유권 검증 없이 요청받은 ID를 그대로
            // 재배선하지 않고, 실제로 이 로그인 사용자가 업로드한 임시 첨부만 옮긴다.
            attachmentService.moveOnlySelected(
                fromType = ResourceType.NOT_A_RESOURCE,
                fromId = "",
                toType = ResourceType.MILESTONE,
                toId = updated.id.toString(),
                selectedIds = fileIds,
                moverLoginId = loginUser.loginId ?: ""
            )
        }

        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/milestone/$id"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/open")
    fun openMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                model.addAttribute("project", project)
                return "error/forbidden"
            }
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }
        val milestone = milestoneService.getMilestone(id) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }
        if (milestone.project.id != project.id) {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }

        milestoneService.updateMilestone(
            milestoneId = id,
            title = milestone.title,
            contents = milestone.contents,
            dueDate = milestone.dueDate,
            state = State.OPEN
        )
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/milestone/$id"
    }

    @PostMapping("/{owner}/{projectName}/milestone/{id}/close")
    fun closeMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?,
        model: Model
    ): String {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return "error/404"
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: run {
                model.addAttribute("project", project)
                return "error/forbidden"
            }
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!) && !accessControl.isAllowedIfGroupMember(project, loginUser)) {
            model.addAttribute("project", project)
            return "error/forbidden"
        }
        val milestone = milestoneService.getMilestone(id) ?: run {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }
        if (milestone.project.id != project.id) {
            model.addAttribute("project", project)
            model.addAttribute("targetType", "milestone")
            return "error/notfound"
        }

        milestoneService.updateMilestone(
            milestoneId = id,
            title = milestone.title,
            contents = milestone.contents,
            dueDate = milestone.dueDate,
            state = State.CLOSED
        )
        return "redirect:/${owner.encodePathSegment()}/${projectName.encodePathSegment()}/milestone/$id"
    }

    @DeleteMapping("/{owner}/{projectName}/milestone/{id}")
    @ResponseBody
    fun deleteMilestone(
        @PathVariable owner: String,
        @PathVariable projectName: String,
        @PathVariable id: Long,
        authentication: Authentication?
    ): ResponseEntity<Void> {
        val project = projectRepository.findByOwnerAndNameOrPreviousPlace(owner, projectName).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        if (!projectUserRepository.existsByProjectIdAndUserId(project.id!!, loginUser.id!!)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
        val milestone = milestoneService.getMilestone(id)
            ?: return ResponseEntity.notFound().build()
        if (milestone.project.id != project.id) {
            return ResponseEntity.notFound().build()
        }
        
        milestoneService.deleteMilestone(id)
        
        return ResponseEntity.noContent()
            .header("Location", "/$owner/$projectName/milestones")
            .build()
    }
}
