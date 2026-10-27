package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.access.assigneeAsResource
import dev.yona.access.asResource
import dev.yona.access.milestoneAsResource
import dev.yona.access.stateAsResource
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEdit
import dev.yona.domain.issue.IssueListService
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.NullProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 이슈 수정 폼과 수정이다. v1.6 `IssueApp.editIssueForm`, `editIssue`(`app/controllers/IssueApp.java:662-680, 735-850`)와
 * `AbstractPostingApp.editPosting`에 해당한다.
 *
 * 폼은 이슈를 읽을 수 있으면 열리고(수정 권한이 없으면 저장 단추가 없다), 수정은 이슈 수정 권한이 필요하다. 수정 폼은 멀티파트이다.
 * 폼의 값을 모두 반영하므로, 보내지 않은 마일스톤은 마일스톤 없음이다(마일스톤 메뉴를 끈 프로젝트에서도 마찬가지이다).
 */
@Controller
@AnonymousCheck
class IssueEditController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val issues: IssueRepository,
    private val issueService: IssueService,
    private val listService: IssueListService,
    private val formSupport: IssueFormSupport,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val postAttachments: PostAttachments,
    private val views: ProjectViewFactory,
    private val flashTranslator: FlashMessageTranslator
) {

    @GetMapping("/{user}/{project}/issue/{number:[0-9]+}/editform")
    @NullProjectCheck
    @Transactional(readOnly = true)
    fun editIssueForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        // v1.6는 `notFound(ErrorViews.Forbidden.render("error.notfound", project))`라 403 화면 모양에 404 상태이다
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.forbidden("error.notfound", views.of(project)).also { it.status = HttpStatus.NOT_FOUND }
        if (!accessControl.isAllowed(viewer, issue.asResource(), Operation.READ)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        return editView(project, issue, viewer, "title.editIssue", emptyMap(), null)
    }

    @PostMapping("/{user}/{project}/issue/{number:[0-9]+}/edit")
    @NullProjectCheck
    @Transactional
    fun editIssue(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val issue = issues.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.ISSUE_POST.resource)

        val errors = linkedMapOf<String, MutableList<String>>()
        val title = request.getParameter("title")
        if (title.isNullOrEmpty()) errors.getOrPut("title") { mutableListOf() } += "error.required"
        else if (title.length > MAX_TITLE) errors.getOrPut("title") { mutableListOf() } += "error.maxLength"
        var dueDate: Date? = null
        request.getParameter("dueDate")?.takeIf { it.isNotEmpty() }?.let {
            try { dueDate = SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false }.parse(it) } catch (e: ParseException) { errors.getOrPut("dueDate") { mutableListOf() } += "error.invalid.java.util.Date" }
        }
        if (errors.isNotEmpty()) {
            return editView(project, issue, viewer, "error.validation", errors, null).also { it.status = HttpStatus.BAD_REQUEST }
        }
        if (viewer == null || !accessControl.isAllowed(viewer, issue.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", views.of(project))

        var target: Project? = null
        val targetId = request.getParameter("targetProjectId")
        if (!targetId.isNullOrEmpty()) {
            target = targetId.toLongOrNull()?.let { projects.findById(it).orElse(null)}
            if (target == null) {
                return editView(project, issue, viewer, "error.validation", emptyMap(), "error.notfound.project").also { it.status = HttpStatus.BAD_REQUEST }
            }
            if (!accessControl.isProjectResourceCreatable(viewer, target, ResourceType.ISSUE_POST)) return ErrorViews.forbidden("error.forbidden", views.of(target))
        }
        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()

        val labelIds = request.getParameterValues("labelIds").orEmpty().filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }.toSet()
        if (issueService.violatesExclusiveCategory(labelIds)) {
            return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8))
                .body("This category does not allow an issue to have two or more labels of the category")
        }

        val edit = IssueEdit(
            title = requireNotNull(title), body = body, dueDate = dueDate, state = request.getParameter("state")?.let { v -> State.entries.firstOrNull { it.name == v } },
            draft = request.getParameter("isDraft").yes(), publish = request.getParameter("isPublish").yes(), notifyMail = request.getParameter("notificationMail") == "yes",
            parentIssueId = request.getParameter("parentIssueId")?.toLongOrNull(), changeParent = !targetId.isNullOrEmpty(),
            assigneeLoginId = request.getParameter("assigneeLoginId"), milestoneId = request.getParameter("milestoneId")?.toLongOrNull(), labelIds = labelIds
        )
        val updated = issueService.update(issue, viewer, edit, target)
        val flash = postAttachments.attach(request, viewer, ResourceType.ISSUE_POST, requireNotNull(updated.id))
        val place = requireNotNull(updated.project)
        return RedirectSupport.see(request, response, "/${place.owner}/${place.name}/issue/${updated.number}", flash)
    }

    private fun editView(project: Project, issue: Issue, viewer: User?, title: String, errors: Map<String, List<String>>, flashKey: String?): ModelAndView {
        val resource = issue.asResource()
        val mav = ModelAndView("issue/edit")
            .addObject("title", title)
            .addObject("errors", errors)
            .addObject("issueNumber", issue.number)
            .addObject("issueId", issue.id)
            .addObject("draft", issue.isDraft == true)
            .addObject("titleValue", issue.title)
            .addObject("bodyValue", issue.body)
            .addObject("authorId", issue.authorId)
            .addObject("dueDateValue", issue.dueDate?.let { SimpleDateFormat("yyyy-MM-dd").format(it) })
            .addObject("stateName", issue.state?.name)
            .addObject("assigneeLoginId", issue.assignee?.user?.loginId)
            .addObject("milestoneId", issue.milestone?.id)
            .addObject("hasChild", listService.parentsWithChildren(listOf(requireNotNull(issue.id))).isNotEmpty())
            .addObject("canUpdate", accessControl.isAllowed(viewer, resource, Operation.UPDATE))
            .addObject("canUpdateState", accessControl.isAllowed(viewer, issue.stateAsResource(), Operation.UPDATE))
            .addObject("canUpdateAssignee", accessControl.isAllowed(viewer, issue.assigneeAsResource(), Operation.UPDATE))
            .addObject("canUpdateMilestone", accessControl.isAllowed(viewer, issue.milestoneAsResource(), Operation.UPDATE))
            .addObject("authoredByViewer", viewer != null && issue.authorLoginId == viewer.loginId)
            .addObject("allMilestones", true)
        if (flashKey != null) mav.also { dev.yona.web.RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to flashKey)) }
        val filled = formSupport.fill(mav, project, viewer, issue.id, issue.parent?.id, issue.labels.mapNotNull { it.id }.toSet())
        return filled
    }

    private fun String?.yes(): Boolean = this?.lowercase() in setOf("true", "on", "yes", "1")

    private companion object {
        const val MAX_TITLE = 255
    }
}
