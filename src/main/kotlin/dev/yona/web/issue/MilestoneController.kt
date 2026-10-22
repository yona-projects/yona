package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSearchCondition
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.issue.MilestoneStatus
import dev.yona.domain.issue.MilestoneStatusService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.markdown.ProjectMarkdownRenderer
import dev.yona.security.CurrentUserProvider
import dev.yona.web.AttachmentListing
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 마일스톤이다. v1.6 `MilestoneApp`(`app/controllers/MilestoneApp.java`)에 해당한다.
 *
 * 목록과 보기는 프로젝트를 읽을 수 있는 사용자, 만들기는 프로젝트 구성원, 고치기, 열기, 닫기, 지우기는 마일스톤의 수정, 삭제
 * 권한이 있는 사용자(프로젝트 구성원)가 할 수 있다. 보기 화면의 이슈 목록은 이슈 화면을 옮길 때 더한다.
 */
@Controller
@AnonymousCheck
class MilestoneController(
    private val projectService: ProjectService,
    private val milestoneService: MilestoneService,
    private val milestones: MilestoneRepository,
    private val statuses: MilestoneStatusService,
    private val issues: IssueRepository,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val recentVisits: RecentVisitService,
    private val markdown: ProjectMarkdownRenderer,
    private val attachmentListing: AttachmentListing,
    private val postAttachments: PostAttachments,
    private val attachments: dev.yona.domain.misc.AttachmentService,
    private val flashTranslator: FlashMessageTranslator,
    private val searchService: IssueSearchService,
    private val assembler: IssueListAssembler,
    private val formOptions: IssueFormOptions,
    private val labelService: IssueLabelService
) {

    // --- 목록 ---

    @GetMapping("/{user}/{project}/milestones")
    @IsAllowed(Operation.READ)
    @Transactional(readOnly = true)
    fun milestones(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @RequestParam(name = "state", defaultValue = "open") state: String,
        @RequestParam(name = "orderBy", defaultValue = "dueDate") orderBy: String,
        @RequestParam(name = "orderDir", defaultValue = "asc") orderDir: String
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val found = milestoneService.list(project, stateOf(state), orderBy, orderDir == "desc")
        val rows = found.map { milestone ->
            val id = requireNotNull(milestone.id)
            MilestoneRow(
                status = statuses.statusOf(milestone),
                openIssues = issues.findByMilestoneIdAndStateOrderById(id, State.OPEN).map { IssueRow.of(it) },
                closedIssues = issues.findByMilestoneIdAndStateOrderById(id, State.CLOSED).map { IssueRow.of(it) }
            )
        }
        return ModelAndView("milestone/list")
            .addObject("title", "${project.name} - milestone")
            .addObject("projectView", views.of(project))
            .addObject("rows", rows)
            .addObject("state", state)
            .addObject("orderBy", orderBy)
            .addObject("orderDir", orderDir)
            .addObject("canCreate", accessControl.isProjectResourceCreatable(currentUserProvider.get().user, project, ResourceType.MILESTONE))
    }

    // --- 만들기 ---

    @GetMapping("/{user}/{project}/newMilestoneForm")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.MILESTONE)
    @Transactional(readOnly = true)
    fun newMilestoneForm(@PathVariable("user") owner: String, @PathVariable("project") name: String): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        return createView(project, MilestoneInput(), emptyMap())
    }

    @PostMapping("/{user}/{project}/milestones")
    @IsCreatable(ResourceType.MILESTONE)
    @Transactional
    fun newMilestone(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val input = MilestoneInput.of(request)
        val result = validate(project, input, checkTitle = true)
        if (result.errors.isNotEmpty()) return createView(project, input, result.errors, result.flashKey)

        val milestone = milestoneService.create(project, requireNotNull(input.title), input.contents, requireNotNull(result.state), result.dueDate)
        val flash = currentUserProvider.get().user?.let { postAttachments.attach(request, it, ResourceType.MILESTONE, requireNotNull(milestone.id)) }.orEmpty()
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/milestone/${milestone.id}", flash)
    }

    // --- 수정 ---

    @GetMapping("/{user}/{project}/milestone/{id}/editform")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(Operation.UPDATE, ResourceType.MILESTONE)
    @Transactional(readOnly = true)
    fun editMilestoneForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val milestone = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        val input = MilestoneInput(milestone.title, milestone.contents, milestone.dueDate?.let { DATE_FORMAT().format(it) }, milestone.state?.name)
        return editView(project, milestone, input, emptyMap())
    }

    @PostMapping("/{user}/{project}/milestone/{id}/edit")
    @IsAllowed(Operation.UPDATE, ResourceType.MILESTONE)
    @Transactional
    fun editMilestone(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @PathVariable("id") id: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val original = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        val input = MilestoneInput.of(request)
        // 제목을 바꾸지 않았으면 중복 검사를 하지 않는다.
        val result = validate(project, input, checkTitle = original.title != input.title)
        if (result.errors.isNotEmpty()) return editView(project, original, input, result.errors, result.flashKey)

        // 본문이 한도를 넘어 폼 값을 읽지 못했으면 내용이 오지 않는다.
        if (input.contents == null) return ErrorViews.requestTextEntityTooLarge()
        milestoneService.update(original, requireNotNull(input.title), input.contents, requireNotNull(result.state), result.dueDate)
        currentUserProvider.get().user?.let { attachments.moveAll(ResourceType.USER, requireNotNull(it.id), ResourceType.MILESTONE, id) }
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/milestone/$id")
    }

    // --- 삭제, 열기, 닫기 ---

    @DeleteMapping("/{user}/{project}/milestone/{id}/delete")
    @IsAllowed(Operation.DELETE, ResourceType.MILESTONE)
    @Transactional
    fun deleteMilestone(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @PathVariable("id") id: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val milestone = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        if (milestone.project?.id != project.id) return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build<Void>()
        milestoneService.delete(milestone)
        val list = "/${project.owner}/${project.name}/milestones"
        if (request.getHeader("X-Requested-With") == "XMLHttpRequest") {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, list).build<Void>()
        }
        return RedirectSupport.see(request, response, list)
    }

    @PostMapping("/{user}/{project}/milestone/{id}/open")
    @IsAllowed(Operation.UPDATE, ResourceType.MILESTONE)
    @Transactional
    fun open(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val milestone = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        milestoneService.open(milestone)
        return RedirectSupport.see(request, response, "/$owner/$name/milestone/$id")
    }

    @PostMapping("/{user}/{project}/milestone/{id}/close")
    @IsAllowed(Operation.UPDATE, ResourceType.MILESTONE)
    @Transactional
    fun close(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("id") id: Long, request: HttpServletRequest, response: HttpServletResponse): Any {
        val milestone = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        milestoneService.close(milestone)
        return RedirectSupport.see(request, response, "/$owner/$name/milestone/$id")
    }

    // --- 보기 ---

    @GetMapping("/{user}/{project}/milestone/{id}")
    @IsAllowed(Operation.READ, ResourceType.MILESTONE)
    @Transactional
    fun milestone(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @PathVariable("id") id: Long,
        @RequestParam(name = "state", required = false) state: String?
    ): Any {
        val project = findProject(owner, name) ?: return ErrorViews.notFound()
        val milestone = milestones.findById(id).orElse(null) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        viewer?.let { recentVisits.record(it, project) }
        val resource = milestone.asResource()
        val contents = milestone.contents
        val issueState = stateOf(state)
        val shown = when (issueState) {
            State.OPEN -> State.OPEN
            State.CLOSED -> State.CLOSED
            else -> null
        }
        val allIssues = issues.findByMilestoneIdOrderByNumberDesc(id)
        val counts = listOf(State.OPEN, State.CLOSED, State.ALL).associateWith {
            searchService.count(project, IssueSearchCondition(state = it.state, milestoneId = id), viewer)
        }
        val items = assembler.assemble(project, allIssues.filter { shown == null || it.state == shown }, viewer)
        return ModelAndView("milestone/view")
            .addObject("title", milestone.title)
            .addObject("projectView", views.of(project))
            .addObject("milestone", milestone)
            .addObject("status", statuses.statusOf(milestone))
            .addObject("issueState", issueState)
            .addObject("issueCounts", counts.mapKeys { it.key.state })
            .addObject("items", items)
            .addObject("massUpdate", formOptions.massUpdate(project, viewer, labelService.labels(project)))
            .addObject("contentsHtml", if (contents.isNullOrEmpty()) null else markdown.render(contents, project))
            .addObject("attachmentsJson", if (contents.isNullOrEmpty()) null else attachmentListing.fileListJson(ResourceType.MILESTONE, id))
            .addObject("canDelete", accessControl.isAllowed(viewer, resource, Operation.DELETE))
            .addObject("canUpdate", accessControl.isAllowed(viewer, resource, Operation.UPDATE))
    }

    // --- 도우미 ---

    private fun findProject(owner: String, name: String): Project? = projectService.findByOwnerAndProjectName(owner, name)

    /** 이름으로 상태를 찾는다. 알 수 없는 이름은 열림이다 (v1.6 `State.getValue`). */
    private fun stateOf(value: String?): State = State.entries.firstOrNull { it.state == value } ?: State.OPEN

    private fun validate(project: Project, input: MilestoneInput, checkTitle: Boolean): ValidationResult {
        val errors = linkedMapOf<String, MutableList<String>>()
        var flashKey: String? = null
        if (input.title.isNullOrEmpty()) errors.getOrPut("title") { mutableListOf() } += "error.required"
        val state = input.state?.let { value -> State.entries.firstOrNull { it.name == value } }
        if (input.state.isNullOrEmpty()) errors.getOrPut("state") { mutableListOf() } += "error.required"
        else if (state == null) errors.getOrPut("state") { mutableListOf() } += "error.invalid"

        var dueDate: Date? = null
        if (!input.dueDate.isNullOrEmpty()) {
            try {
                dueDate = DATE_FORMAT().apply { isLenient = false }.parse(input.dueDate)
            } catch (e: ParseException) {
                errors.getOrPut("dueDate") { mutableListOf() } += "error.invalid.java.util.Date"
                flashKey = "milestone.error.duedateFormat"
            }
        }
        if (checkTitle && !milestoneService.isUniqueTitle(project, input.title)) {
            errors.getOrPut("title") { mutableListOf() } += "milestone.title.duplicated"
            flashKey = "milestone.title.duplicated"
        }
        return ValidationResult(errors, flashKey, state, dueDate)
    }

    private fun createView(project: Project, input: MilestoneInput, errors: Map<String, List<String>>, flashKey: String? = null): ModelAndView =
        formView("milestone/create", "title.newMilestone", project, input, errors, flashKey)
            .addObject("openChecked", true)

    private fun editView(project: Project, milestone: Milestone, input: MilestoneInput, errors: Map<String, List<String>>, flashKey: String? = null): ModelAndView =
        formView("milestone/edit", "title.editMilestone", project, input, errors, flashKey)
            .addObject("milestone", milestone)
            // 오류로 다시 보여 줄 때 제목과 내용은 고치기 전의 값이다.
            .addObject("titleValue", milestone.title)
            .addObject("contentsValue", milestone.contents)

    private fun formView(view: String, title: String, project: Project, input: MilestoneInput, errors: Map<String, List<String>>, flashKey: String?): ModelAndView {
        val mav = ModelAndView(view)
            .addObject("title", title)
            .addObject("projectView", views.of(project))
            .addObject("input", input)
            .addObject("errors", errors)
            .addObject("titleValue", input.title)
            .addObject("contentsValue", input.contents)
            .addObject("signedIn", currentUserProvider.get().user != null)
        if (flashKey != null) mav.addObject("flashMessages", flashTranslator.translate(mapOf(RedirectSupport.WARNING to flashKey)))
        return mav
    }

    /** 폼으로 받은 값이다. 비어 있어도 문자열이고, 보내지 않은 값은 `null`이다. */
    data class MilestoneInput(val title: String? = null, val contents: String? = null, val dueDate: String? = null, val state: String? = null) {
        companion object {
            fun of(request: HttpServletRequest) = MilestoneInput(
                request.getParameter("title"), request.getParameter("contents"), request.getParameter("dueDate"), request.getParameter("state")
            )
        }
    }

    private data class ValidationResult(val errors: Map<String, List<String>>, val flashKey: String?, val state: State?, val dueDate: Date?)

    /** 목록의 마일스톤 한 줄이다. */
    data class MilestoneRow(val status: MilestoneStatus, val openIssues: List<IssueRow>, val closedIssues: List<IssueRow>)

    /** 목록에 보일 이슈이다. */
    data class IssueRow(val number: Long, val title: String, val assigneeName: String?, val labels: List<LabelRow>) {
        companion object {
            fun of(issue: dev.yona.domain.issue.Issue) = IssueRow(
                issue.number ?: 0, issue.title.orEmpty(), issue.assignee?.user?.name,
                issue.labels.sortedWith(compareBy({ it.category?.name }, { it.name })).map { LabelRow(requireNotNull(it.id), it.category?.id, it.name.orEmpty()) }
            )
        }
    }

    data class LabelRow(val id: Long, val categoryId: Long?, val name: String)

    private companion object {
        fun DATE_FORMAT() = SimpleDateFormat("yyyy-MM-dd")
    }
}
