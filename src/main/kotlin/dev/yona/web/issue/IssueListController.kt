package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueExcelExporter
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssuePage
import dev.yona.domain.issue.IssueSearchCondition
import dev.yona.domain.issue.IssueSearchService
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.issue.MilestoneStatus
import dev.yona.domain.issue.MilestoneStatusService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserDisplay
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.ContentDispositions
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import org.apache.tika.Tika

/**
 * 프로젝트의 이슈 목록이다. v1.6 `IssueApp.issues`(`app/controllers/IssueApp.java:113-247`)에 해당한다.
 *
 * `format`이 `html`(기본)이면 화면, `pjax`이거나 PJAX 요청(`X-PJAX`)이면 목록 조각, Ajax 요청(`X-Requested-With`)이면 JSON,
 * `xls`이면 엑셀이다. 한 쪽은 15개이고 `itemsPerPage`로 최대 45개까지 바꿀 수 있다.
 */
@Controller
@AnonymousCheck
class IssueListController(
    private val projectService: ProjectService,
    private val searchService: IssueSearchService,
    private val listService: IssueListService,
    private val assembler: IssueListAssembler,
    private val labelService: IssueLabelService,
    private val milestoneService: MilestoneService,
    private val milestoneStatuses: MilestoneStatusService,
    private val views: ProjectViewFactory,
    private val accessControl: AccessControl,
    private val avatars: AvatarService,
    private val currentUserProvider: CurrentUserProvider,
    private val recentVisits: RecentVisitService,
    private val excelExporter: IssueExcelExporter,
    private val formOptions: IssueFormOptions
) {

    @GetMapping("/{user}/{project}/issues")
    @IsAllowed(Operation.READ)
    @Transactional
    fun issues(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val pageNum = request.getParameter("pageNum")?.toIntOrNull() ?: 1
        val condition = IssueConditionBinder.bind(request, maxOf(pageNum - 1, 0)) ?: return ErrorViews.badRequest()

        var format = request.getParameter("format") ?: "html"
        if (request.getHeader("X-Requested-With") == "XMLHttpRequest") format = if (request.getHeader("X-PJAX").toBoolean()) "pjax" else "json"

        if (format == EXCEL_EXT) return excel(project, condition, viewer)
        val page = searchService.search(project, condition, viewer, itemsPerPage(request))
        return when (format) {
            "pjax" -> {
                response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
                listModel("issue/partial-list-wrap :: listWrap", project, condition, page, viewer, request)
            }
            "json" -> json(project, page, request.getParameter("exceptId"))
            else -> {
                viewer?.let { recentVisits.record(it, project) }
                listModel("issue/list", project, condition, page, viewer, request)
            }
        }
    }

    /** 한 쪽의 이슈 수이다. `itemsPerPage`를 숫자로 받으면 쓰되 최대 45개이다. */
    private fun itemsPerPage(request: HttpServletRequest): Int =
        minOf(request.getParameter("itemsPerPage")?.toIntOrNull() ?: ITEMS_PER_PAGE, ITEMS_PER_PAGE_MAX)

    /** 조건에 맞는 모든 이슈를 쪽 나누기 없이 엑셀로 내려준다. */
    private fun excel(project: Project, condition: IssueSearchCondition, viewer: User?): Any {
        val data = excelExporter.export(searchService.findAll(project, condition, viewer))
        val filename = "${project.name}_issues_${SimpleDateFormat("yyyyMMddHHmm").format(Date())}.$EXCEL_EXT"
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, Tika().detect(filename))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; " + ContentDispositions.encodeFilename(filename))
            .body(data)
    }

    private fun json(project: Project, page: IssuePage, exceptIdParam: String?): Any {
        val exceptId = if (exceptIdParam.isNullOrEmpty()) -1L else exceptIdParam.toLongOrNull()
            ?: return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(emptyMap<String, Any>())
        val body = linkedMapOf<String, Map<String, Any?>>()
        for (issue in page.items) {
            val number = issue.number ?: continue
            if (number == exceptId) continue
            body[issue.id.toString()] = linkedMapOf(
                "id" to number, "title" to issue.title, "state" to issue.state?.name, "createdDate" to issue.createdDate?.toString(),
                "link" to "/${project.owner}/${project.name}/issue/$number"
            )
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)
    }

    /** 목록 화면과 PJAX 조각이 함께 쓰는 모델을 만든다. */
    private fun listModel(view: String, project: Project, condition: IssueSearchCondition, page: IssuePage, viewer: User?, request: HttpServletRequest): ModelAndView {
        val projectView = views.of(project)
        val items = assembler.assemble(project, page.items, viewer)
        val count = { c: IssueSearchCondition -> searchService.count(project, c, viewer) }
        val showDrafts = page.pageIndex == 0 && !condition.hasCondition() && condition.state != State.CLOSED.state
        val drafts = if (showDrafts) assembler.assemble(project, listService.drafts(project, viewer?.loginId), viewer) else emptyList()
        val labels = labelService.labels(project)
        val mav = ModelAndView(view)
            .addObject("title", "menu.issue")
            .addObject("projectView", projectView)
            .addObject("cond", condition)
            .addObject("items", items)
            .addObject("drafts", drafts)
            .addObject("pageIndex", page.pageIndex)
            .addObject("totalPageCount", page.totalPageCount)
            .addObject("openCount", count(condition.copy(state = State.OPEN.state)))
            .addObject("closedCount", count(condition.copy(state = State.CLOSED.state)))
            .addObject("quick", quickSearch(condition, viewer, count))
            .addObject("excelUrl", excelUrl(request))
            .addObject("search", searchForm(project, condition, viewer, labels))
            .addObject("canMassUpdate", projectView.projectMember)
        if (projectView.projectMember) mav.addObject("massUpdate", formOptions.massUpdate(project, viewer, labels))
        return mav
    }

    private fun excelUrl(request: HttpServletRequest): String {
        val query = request.queryString.orEmpty().split("&").filter { it.isNotEmpty() && !it.startsWith("pageNum=") } + "format=xls"
        return request.requestURI + "?" + query.joinToString("&")
    }

    /** 왼쪽의 빠른 검색(전체, 내게 할당, 내가 작성, 내가 댓글)과 각 개수이다. */
    private fun quickSearch(condition: IssueSearchCondition, viewer: User?, count: (IssueSearchCondition) -> Long): QuickSearch {
        val base = condition.copy(assigneeId = null, authorId = null, commenterId = null)
        val me = viewer?.id
        return QuickSearch(
            everyone = count(base),
            assignedToMe = me?.let { count(base.copy(assigneeId = it)) },
            authoredByMe = me?.let { count(base.copy(authorId = it)) },
            commentedByMe = me?.let { count(base.copy(commenterId = it)) },
            meId = me
        )
    }

    /** 왼쪽 상세 검색 폼의 선택지이다. */
    private fun searchForm(project: Project, condition: IssueSearchCondition, viewer: User?, labels: List<IssueLabel>): SearchForm {
        val language = viewer?.lang
        val option = { user: User -> UserOption(requireNotNull(user.id), user.name.orEmpty(), UserDisplay.of(user, language), user.loginId.orEmpty(), avatars.avatarUrl(user)) }
        val authors = listService.authors(project, viewer).map(option)
        val assignees = listService.assignees(project, viewer).map(option)
        val assignable = listService.assignableUsers(project, viewer, accessControl.isSiteManager(viewer))
        val milestones = milestoneService.list(project, State.ALL, "dueDate", false)
        val openMilestones = milestones.filter { it.state == State.OPEN }
        val closedMilestones = milestones.filter { it.state == State.CLOSED }
        val selectedMilestone: MilestoneStatus? = condition.milestoneId?.takeIf { it != IssueSearchCondition.NO_VALUE }
            ?.let { id -> milestones.firstOrNull { it.id == id } }?.let { milestoneStatuses.statusOf(it) }
        return SearchForm(
            authors = authors, assignees = assignees,
            // 작성자 선택지의 "내가 쓴 이슈"는 프로젝트나 조직 구성원에게만 보인다.
            showAuthoredByMe = viewer != null && assignable.any { it.id == viewer.id },
            hasMilestones = milestones.isNotEmpty(),
            openMilestones = openMilestones.map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), it.state?.name.orEmpty()) },
            closedMilestones = closedMilestones.map { MilestoneOption(requireNotNull(it.id), it.title.orEmpty(), it.state?.name.orEmpty()) },
            selectedMilestone = selectedMilestone,
            labels = formOptions.labelGroups(labels), canManageLabels = accessControl.isProjectResourceCreatable(viewer, project, ResourceType.ISSUE_LABEL),
            hasLabels = labels.isNotEmpty(), selectedLabelIds = java.util.LinkedHashSet(condition.labelIds), manager = viewer?.let { v -> views.of(project).manager } ?: false
        )
    }

    data class QuickSearch(val everyone: Long, val assignedToMe: Long?, val authoredByMe: Long?, val commentedByMe: Long?, val meId: Long?)
    data class SearchForm(
        val authors: List<UserOption>, val assignees: List<UserOption>, val showAuthoredByMe: Boolean, val hasMilestones: Boolean,
        val openMilestones: List<MilestoneOption>, val closedMilestones: List<MilestoneOption>, val selectedMilestone: MilestoneStatus?,
        val labels: List<LabelGroupOption>, val canManageLabels: Boolean, val hasLabels: Boolean, val selectedLabelIds: Set<Long>, val manager: Boolean
    )

    private companion object {
        const val ITEMS_PER_PAGE = 15
        const val ITEMS_PER_PAGE_MAX = 45
        const val EXCEL_EXT = "xls"
    }
}

/**
 * 요청의 검색 조건을 읽는다. v1.6의 폼 묶기(`SearchCondition` 바인딩)와 `LabelApp.getLabelIds`에 해당한다.
 * 숫자 항목이 숫자가 아니면 `null`을 돌려준다(400으로 알린다).
 */
object IssueConditionBinder {

    fun bind(request: HttpServletRequest, pageIndex: Int): IssueSearchCondition? {
        fun long(name: String): Result<Long?> = runCatching { request.getParameter(name)?.takeIf { it.isNotEmpty() }?.toLong() }
        val milestone = long("milestoneId"); val author = long("authorId"); val assignee = long("assigneeId")
        val commenter = long("commenterId"); val sharer = long("sharerId"); val favorite = long("favoriteId"); val mention = long("mentionId")
        val labelIds = runCatching { request.getParameterValues("labelIds").orEmpty().filter { it.isNotEmpty() }.map { it.toLong() }.toSet() }
        val dueDate = runCatching {
            request.getParameter("dueDate")?.takeIf { it.isNotEmpty() }?.let { SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false }.parse(it) }
        }
        val parsed = listOf(milestone, author, assignee, commenter, sharer, favorite, mention, labelIds, dueDate)
        if (parsed.any { it.isFailure }) return null
        val defaults = IssueSearchCondition()
        return IssueSearchCondition(
            state = request.getParameter("state") ?: defaults.state,
            orderBy = request.getParameter("orderBy")?.takeIf { it.isNotBlank() } ?: defaults.orderBy,
            orderDir = request.getParameter("orderDir") ?: defaults.orderDir,
            filter = request.getParameter("filter") ?: defaults.filter,
            pageNum = pageIndex,
            commentedCheck = request.getParameter("commentedCheck")?.lowercase() in setOf("true", "on", "yes", "1"),
            milestoneId = milestone.getOrNull(), labelIds = labelIds.getOrThrow(), authorId = author.getOrNull(), assigneeId = assignee.getOrNull(),
            commenterId = commenter.getOrNull(), sharerId = sharer.getOrNull(), favoriteId = favorite.getOrNull(), mentionId = mention.getOrNull(),
            dueDate = dueDate.getOrNull()
        )
    }
}
