package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.IssueMassUpdateService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.MassEdit
import dev.yona.domain.issue.MilestoneService
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.NullProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Date

/**
 * 일괄 변경이다. v1.6 `IssueApp.massUpdate`(`app/controllers/IssueApp.java:389-470`)에 해당한다.
 * 이슈 목록의 일괄 변경 막대와 이슈 보기 오른쪽 창의 항목별 변경이 모두 이 주소로 요청한다.
 *
 * 이슈 번호는 `issues[0].id`처럼 보내고, 초안은 건너뛴다. 하나도 바꾸지 못했고 권한 때문에 거부된 이슈가 있으면 `403`이다.
 */
@Controller
@AnonymousCheck
class IssueMassUpdateController(
    private val projectService: ProjectService,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val massService: IssueMassUpdateService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val messages: MessageSource,
    private val clock: Clock
) {

    @PostMapping("/{user}/{project}/issues")
    @NullProjectCheck
    @Transactional
    fun massUpdate(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val ids = indexed(request, "issues", ".id").mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body("""{"issues":["This field is required"]}""")
        // 상태는 열거형 이름(`OPEN`, `CLOSED` 등) 그대로여야 한다. 소문자나 그 밖의 값은 v1.6 폼 바인딩이 `Invalid value`로 거절한다(빈 값은 통과)
        request.getParameter("state")?.takeIf { it.isNotEmpty() && State.entries.none { e -> e.name == it } }?.let {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body("""{"state":["Invalid value"]}""")
        }
        val dueChanged = request.getParameter("isDueDateChanged") == "true"
        var due: Date? = null
        try {
            request.getParameter("dueDate")?.takeIf { it.isNotEmpty() }?.let { due = SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false }.parse(it) }
        } catch (e: ParseException) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body("""{"dueDate":["Invalid value"]}""")
        }
        val edit = MassEdit(
            state = request.getParameter("state")?.let { v -> State.entries.firstOrNull { it.name == v } },
            assigneeId = request.getParameter("assignee.id")?.toLongOrNull(), milestoneId = request.getParameter("milestone.id")?.toLongOrNull(),
            dueDateChanged = dueChanged, dueDate = due,
            attachingLabelIds = list(request, "attachingLabelIds"), detachingLabelIds = list(request, "detachingLabelIds")
        )
        val delete = request.getParameter("delete") == "true"
        val viewer = currentUserProvider.get().user

        // v1.6는 폼이 만든 이슈 객체를 `issue.refresh()`하는데 없는 번호이면 예외로 500이었다(R-127). 아무것도 바꾸기 전에 404로 알린다
        val targets = ids.map { issues.findById(it).orElse(null) ?: return ErrorViews.notFound() }
        var updated = 0
        var rejected = 0
        for (issue in targets) {
            if (issue.isDraft == true) continue
            val resource = issue.asResource()
            if (delete) {
                if (viewer != null && accessControl.isAllowed(viewer, resource, Operation.DELETE)) { issueService.delete(issue, viewer, notify = false); continue }
                rejected++; continue
            }
            if (viewer == null || !accessControl.isAllowed(viewer, resource, Operation.UPDATE)) { rejected++; continue }
            massService.apply(issue, viewer, edit)
            updated++
        }
        if (updated == 0 && rejected > 0) return ErrorViews.forbidden("error.forbidden", views.of(project))

        if (HttpPreference.isJsonPreferred(request.getHeader("Accept"))) {
            val first = targets.firstOrNull()
            if (dueChanged && first != null) {
                val open = first.state == State.OPEN
                val overdue = open && first.dueDate?.before(Date.from(clock.instant())) == true
                val message = if (overdue) message("issue.dueDate.overdue") else until(open, first.dueDate)
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("""{"isOverDue":$overdue,"dueDateMsg":${if (message == null) "null" else quote(message)}}""")
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{}")
        }
        return RedirectSupport.see(request, response, request.getHeader("Referer") ?: "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/issues")
    }

    /** `이름[0].속성`, `이름[1].속성` 형태로 온 값을 순서대로 모은다. */
    private fun indexed(request: HttpServletRequest, prefix: String, suffix: String): List<String> =
        request.parameterMap.entries.mapNotNull { (key, values) ->
            Regex("^${Regex.escape(prefix)}\\[(\\d+)]${Regex.escape(suffix)}$").find(key)?.let { it.groupValues[1].toInt() to values.firstOrNull().orEmpty() }
        }.sortedBy { it.first }.map { it.second }

    /** `이름`, `이름[]`, `이름[0]` 중 어느 형태로 와도 값을 모은다. */
    private fun list(request: HttpServletRequest, name: String): List<Long> =
        (request.getParameterValues(name).orEmpty().toList() + request.getParameterValues("$name[]").orEmpty().toList() + indexed(request, name, ""))
            .mapNotNull { it.toLongOrNull() }

    /**
     * 오늘이면 `common.time.today`, 아니면 남았거나 지난 날수이다 (v1.6 `Issue.until`). 지났다는 판단(`isOverDueDate`)은 열린 이슈에만 있으므로, 닫힌 이슈의 지난
     * 마감일은 지난 날수가 아니라 오늘에서 마감일까지의 **부호 있는** 날수(`-3`)가 된다. v1.6이 그렇게 낸다.
     */
    private fun until(open: Boolean, due: Date?): String? {
        if (due == null) return null
        val dueDay = LocalDate.ofInstant(due.toInstant(), clock.zone)
        val today = LocalDate.now(clock)
        if (dueDay == today) return message("common.time.today")
        val days = if (open && clock.instant().isAfter(due.toInstant())) ChronoUnit.DAYS.between(dueDay, today) else ChronoUnit.DAYS.between(today, dueDay)
        return messages.getMessage("common.time.default.day", arrayOf(days), LocaleContextHolder.getLocale())
    }

    private fun message(key: String): String = messages.getMessage(key, null, key, LocaleContextHolder.getLocale()) ?: key

    private fun quote(text: String): String = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
