package dev.yona.web.issue

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.config.YonaProperties
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.IssueService
import dev.yona.domain.issue.NewIssue
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.user.User
import dev.yona.repository.RepositoryFileReader
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 이슈 등록 폼과 등록이다. v1.6 `IssueApp.newIssueForm`, `newIssueFormByComment`, `newDirectIssueForm`, `newDirectMyIssueForm`,
 * `newIssue`(`app/controllers/IssueApp.java:311-387, 562-660`)에 해당한다.
 *
 * 폼은 로그인한 사용자가 프로젝트에 이슈를 만들 권한이 있을 때 열린다. 등록은 멀티파트 폼이다.
 */
@Controller
@AnonymousCheck
class IssueCreateController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val issueService: IssueService,
    private val formSupport: IssueFormSupport,
    private val fileReader: RepositoryFileReader,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val postAttachments: PostAttachments,
    private val recentProjects: RecentProjectRepository,
    private val comments: IssueCommentRepository,
    private val properties: YonaProperties,
    private val flashTranslator: FlashMessageTranslator,
    private val issueComments: dev.yona.domain.issue.IssueCommentService,
    private val messages: org.springframework.context.MessageSource
) {

    @GetMapping("/{user}/{project}/issueform")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.ISSUE_POST)
    @Transactional(readOnly = true)
    fun newIssueForm(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        @RequestParam(name = "parentIssueId", required = false) parentIssueId: String?,
        @RequestParam(name = "commentId", required = false) commentId: String?
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val template = fileReader.readText(project, "ISSUE_TEMPLATE.md")?.ifBlank { null }.orEmpty()
        return createView(project, "title.newIssue", template, parentIssueId?.toLongOrNull(), commentId, emptyMap(), fromGlobalMenu = false)
    }

    /** 최근 방문한 프로젝트에 이슈를 만드는 폼이다. 방문한 프로젝트가 없으면 안내와 함께 첫 화면으로 보낸다. */
    @GetMapping("/user/issues/new")
    @Transactional(readOnly = true)
    fun newDirectIssueForm(
        @RequestParam(name = "commentId", defaultValue = "-1") commentId: Long,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val viewer = currentUserProvider.get().user
        val project = viewer?.let { recentProjects.findByUserIdOrderByIdDesc(requireNotNull(it.id)).firstOrNull()?.project }
            ?: return noProject(request, response)
        if (commentId != -1L) return formByComment(project, commentId, request)
        val template = fileReader.readText(project, "ISSUE_TEMPLATE.md")?.ifBlank { null }.orEmpty()
        return createView(project, "title.newIssue", template, null, null, emptyMap(), fromGlobalMenu = true)
    }

    /** 내 프로젝트(`inbox`, `_private`, 최근 만든 비공개, 최근 만든 공개 순)에 이슈를 만드는 폼이다. */
    @GetMapping("/user/issues/new/mine")
    @Transactional(readOnly = true)
    fun newDirectMyIssueForm(request: HttpServletRequest, response: HttpServletResponse): Any {
        val viewer = currentUserProvider.get().user ?: return noProject(request, response)
        val login = viewer.loginId.orEmpty()
        val project = projectService.findByOwnerAndProjectName(login, "inbox")
            ?: projectService.findByOwnerAndProjectName(login, "_private")
            ?: projects.findByOwnerIgnoreCaseAndProjectScopeOrderByCreatedDateDesc(login, ProjectScope.PRIVATE).firstOrNull()
            ?: projects.findByOwnerIgnoreCaseAndProjectScopeOrderByCreatedDateDesc(login, ProjectScope.PUBLIC).firstOrNull()
            ?: return noProject(request, response)
        val template = fileReader.readText(project, "ISSUE_TEMPLATE.md")?.ifBlank { null }.orEmpty()
        return createView(project, "title.newIssue", template, null, null, emptyMap(), fromGlobalMenu = true)
    }

    @PostMapping("/{user}/{project}/issues/latest")
    @IsCreatable(ResourceType.ISSUE_POST)
    @Transactional
    fun newIssue(
        @PathVariable("user") owner: String,
        @PathVariable("project") name: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        val pathProject = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return ErrorViews.forbidden()
        val errors = linkedMapOf<String, MutableList<String>>()
        val title = request.getParameter("title")
        if (title.isNullOrEmpty()) errors.getOrPut("title") { mutableListOf() } += "error.required"
        else if (title.length > MAX_TITLE) errors.getOrPut("title") { mutableListOf() } += "error.maxLength"
        val dueDate = parseDate(request.getParameter("dueDate")) { errors.getOrPut("dueDate") { mutableListOf() } += "error.invalid.java.util.Date" }
        if (errors.isNotEmpty()) {
            val template = fileReader.readText(pathProject, "ISSUE_TEMPLATE.md")?.ifBlank { null }.orEmpty()
            return createView(pathProject, "error.validation", template, null, null, errors, fromGlobalMenu = false).also { it.status = org.springframework.http.HttpStatus.BAD_REQUEST }
        }

        var project = pathProject
        val targetId = request.getParameter("targetProjectId")
        if (!targetId.isNullOrEmpty()) {
            val target = targetId.toLongOrNull()?.let { projects.findById(it).orElse(null) }
            if (target == null) {
                val mav = createView(pathProject, "title.newIssue", null, null, null, emptyMap(), fromGlobalMenu = false)
                mav.addObject("flashMessages", flashTranslator.translate(mapOf(RedirectSupport.WARNING to "error.notfound.project")))
                return mav.also { it.status = org.springframework.http.HttpStatus.BAD_REQUEST }
            }
            if (!accessControl.isProjectResourceCreatable(viewer, target, ResourceType.ISSUE_POST)) {
                return ErrorViews.forbidden("error.forbidden", formSupport.projectView(target))
            }
            project = target
        }

        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()
        val labelIds = if (project.id == pathProject.id) {
            request.getParameterValues("labelIds").orEmpty().filter { it.isNotEmpty() }.mapNotNull { it.toLongOrNull() }.toSet()
        } else emptySet()
        val issue = issueService.create(
            project, viewer,
            NewIssue(
                title = requireNotNull(title), body = body, dueDate = dueDate, draft = request.getParameter("isDraft").toBooleanLenient(),
                parentIssueId = request.getParameter("parentIssueId")?.toLongOrNull(), assigneeLoginId = request.getParameter("assigneeLoginId"),
                milestoneId = request.getParameter("milestoneId")?.toLongOrNull(), labelIds = labelIds
            )
        )
        val flash = postAttachments.attach(request, viewer, ResourceType.ISSUE_POST, requireNotNull(issue.id))
        addReferComment(request, viewer, project, issue)
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/issue/${issue.number}", flash)
    }

    // --- 도우미 ---

    /**
     * 댓글에서 만든 이슈이면 원 댓글의 이슈에 `파생 이슈: 새 이슈 주소` 댓글을 그 댓글의 한 줄 댓글로 단다
     * (v1.6 `IssueApp.newIssue` 끝의 `newReferComment`). 초안이면 달지 않고, 댓글을 쓸 수 없으면 아무것도 하지 않는다.
     */
    private fun addReferComment(request: HttpServletRequest, viewer: User, project: Project, issue: dev.yona.domain.issue.Issue) {
        val referId = request.getParameter("referCommentId")?.toLongOrNull() ?: return
        if (issue.isDraft == true) return
        val parent = comments.findById(referId).orElse(null) ?: return
        val origin = parent.issue ?: return
        if (!accessControl.isResourceCreatable(viewer, origin.asResource(), ResourceType.ISSUE_COMMENT)) return
        val scheme = properties.scheme.ifEmpty { "http" }
        val host = request.getHeader("Host") ?: "${request.serverName}:${request.serverPort}"
        val context = if (properties.applicationContext.isEmpty()) "" else "/" + properties.applicationContext.trim('/')
        val content = messages.getMessage("issue.derived", null, org.springframework.context.i18n.LocaleContextHolder.getLocale()) +
            ": $scheme://$host$context/${project.owner}/${project.name}/issue/${issue.number}"
        issueComments.add(origin, viewer, content, referId, false)
    }

    private fun formByComment(project: Project, commentId: Long, request: HttpServletRequest): Any {
        val comment = comments.findById(commentId).orElse(null) ?: return ErrorViews.notFound()
        val scheme = properties.scheme.ifEmpty { "http" }
        val host = request.getHeader("Host") ?: "${request.serverName}:${request.serverPort}"
        val context = if (properties.applicationContext.isEmpty()) "" else "/" + properties.applicationContext.trim('/')
        val issue = comment.issue
        val url = "$scheme://$host$context/${issue?.project?.owner}/${issue?.project?.name}/issue/${issue?.number}#comment-${comment.id}"
        val reference = "${comment.contents}\n\n_Originally posted by @${comment.authorLoginId} in $url"
        return createView(project, "title.newIssue", reference, null, commentId.toString(), emptyMap(), fromGlobalMenu = true)
    }

    private fun noProject(request: HttpServletRequest, response: HttpServletResponse): Any =
        RedirectSupport.see(request, response, "/", mapOf(RedirectSupport.WARNING to "project.is.empty"))

    private fun createView(
        project: Project, title: String, bodyValue: String?, parentIssueId: Long?, commentId: String?, errors: Map<String, List<String>>, fromGlobalMenu: Boolean
    ): ModelAndView {
        val viewer = currentUserProvider.get().user
        val mav = ModelAndView("issue/create")
            .addObject("title", title)
            .addObject("bodyValue", if (parentIssueId != null) "" else bodyValue)
            .addObject("commentId", commentId)
            .addObject("errors", errors)
            .addObject("fromGlobalMenu", fromGlobalMenu)
        return formSupport.fill(mav, project, viewer, null, parentIssueId)
    }

    private fun parseDate(value: String?, onError: () -> Unit): Date? {
        if (value.isNullOrEmpty()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd").apply { isLenient = false }.parse(value)
        } catch (e: ParseException) {
            onError(); null
        }
    }

    private fun String?.toBooleanLenient(): Boolean = this?.lowercase() in setOf("true", "on", "yes", "1")

    private companion object {
        const val MAX_TITLE = 255
    }
}
