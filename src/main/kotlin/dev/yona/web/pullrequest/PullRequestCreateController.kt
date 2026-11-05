package dev.yona.web.pullrequest

import dev.yona.access.AccessControl
import dev.yona.domain.comment.CommitCommentRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestForm
import dev.yona.domain.pullrequest.PullRequestMergeProcessor
import dev.yona.domain.pullrequest.BackgroundJobRunner
import dev.yona.domain.pullrequest.PullRequestMerger
import dev.yona.domain.pullrequest.PullRequestService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.BranchService
import dev.yona.repository.CommitInfo
import dev.yona.repository.RepositoryService
import dev.yona.security.AvatarService
import dev.yona.security.CurrentUserProvider
import dev.yona.util.SiteUrls
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.TemplateHelper
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.access.IsOnlyGitAvailable
import dev.yona.web.code.CodePageAssembler
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.HtmlUtils

/** 프로젝트 선택지 하나이다. */
data class ProjectOption(val id: Long, val label: String, val selected: Boolean)

/** 브랜치 선택지 하나이다. */
data class BranchChoice(val name: String, val selected: Boolean)

/** 병합 결과 표의 커밋 한 줄이다. */
data class MergeCommitRow(
    val shortId: String, val commitUrl: String, val commentCount: Int, val shortMessageHtml: String, val hasMore: Boolean, val moreHtml: String,
    val date: String, val dateTitle: String, val authorEmail: String, val authorHref: String?, val authorName: String?, val avatarUrl: String
)

/**
 * 풀 리퀘스트 만들기이다. v1.6 `PullRequestApp.newPullRequestForm`, `mergeResult`, `newPullRequest`
 * (`app/controllers/PullRequestApp.java:134-316`)에 해당한다. 모두 로그인해야 하고 포크 만들기 권한이 필요하며 게스트는 쓸 수 없다(403).
 *
 * 폼은 보내는 프로젝트(내가 구성원인 것)와 받는 프로젝트(현재 프로젝트, 그 포크, 포크이면 원본)를 고르고 브랜치를 고르면 병합 결과(`mergeResult`)를 Ajax로
 * 가져와 제목과 본문을 채운다. 폼을 보낼 때 같은 조건의 열린 요청이 있으면 그 요청으로 이동하고, 없으면 만든 뒤 병합 검사를 시작한다.
 * 제목, 브랜치가 비어 있는 요청은 화면의 스크립트가 막으므로 직접 만든 요청에서만 닿는다([decisions.md] A-75).
 */
@Controller
@AnonymousCheck
class PullRequestCreateController(
    private val projectService: ProjectService,
    private val projects: ProjectRepository,
    private val menuSettings: ProjectMenuSettingRepository,
    private val projectUsers: ProjectUserRepository,
    private val branches: BranchService,
    private val repositories: RepositoryService,
    private val merger: PullRequestMerger,
    private val pullRequestService: PullRequestService,
    private val processor: PullRequestMergeProcessor,
    private val runner: BackgroundJobRunner,
    private val commitComments: CommitCommentRepository,
    private val users: UserRepository,
    private val avatars: AvatarService,
    private val accessControl: AccessControl,
    private val postAttachments: PostAttachments,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val helper: TemplateHelper,
    private val siteUrls: SiteUrls,
    private val meta: PullRequestMetaAssembler,
    private val assigneeService: dev.yona.domain.pullrequest.PullRequestAssigneeService,
    private val labelService: dev.yona.domain.pullrequest.PullRequestLabelService,
    private val issueLabels: dev.yona.domain.issue.IssueLabelRepository
) {

    @GetMapping("/{user}/{project}/newPullRequestForm")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun form(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user)
        guestError(project, viewer)?.let { return it }

        val from = selectedProject(project, request.getParameter("fromProjectId"), false) ?: return ErrorViews.notFound()
        val to = selectedProject(project, request.getParameter("toProjectId"), true) ?: return ErrorViews.notFound()
        val fromBranches = branches.branches(from)
        val toBranches = branches.branches(to)
        if (fromBranches.isEmpty()) return ErrorViews.badRequest("error.pullRequest.empty.from.repository", views.of(from), "PULL_REQUEST")
        if (toBranches.isEmpty()) return ErrorViews.badRequest("error.pullRequest.empty.to.repository", views.of(to), "PROJECT_HOME")

        val fromBranch = request.getParameter("fromBranch").takeUnless { it.isNullOrBlank() } ?: fromBranches[0].name
        val toBranch = request.getParameter("toBranch").takeUnless { it.isNullOrBlank() } ?: repositories.defaultBranch(to)  // v1.6은 받는 프로젝트가 아니라 현재 프로젝트의 기본 브랜치를 썼다(R-42-4)
        val associated = associationProjects(project)
        val base = siteUrls.path(dev.yona.util.UrlPaths.projectPath(project.owner, project.name))
        return ModelAndView("pullrequest/create")
            .addObject("title", "title.newPullRequest")
            .addObject("projectView", views.of(project))
            .addObject("action", "$base/pullRequests")
            .addObject("mergeResultUrl", "$base/newPullRequest/mergeResult")
            .addObject("mentionUrl", "$base/mentionList")
            .addObject("fromProjects", associated.filter { projectUsers.existsByUserIdAndProjectId(requireNotNull(viewer.id), requireNotNull(it.id)) }.map { option(it, from) })
            .addObject("toProjects", associated.map { option(it, to) })
            .addObject("fromBranches", fromBranches.map { BranchChoice(it.name, it.name == fromBranch) })
            .addObject("toBranches", toBranches.map { BranchChoice(it.name, it.name == toBranch) })
            .addObject("state", "OPEN")
            .addObject("body", "")
            .addObject("assigneeArea", if (canTriage(viewer, to)) meta.formAssignee(to) else null)
            .addObject("labelArea", if (canTriage(viewer, to)) meta.formLabels(to) else null)
            // 라벨 색 CSS는 받는 프로젝트의 것이어야 한다(라벨은 받는 프로젝트의 것이다)
            .addObject("labelsCssUrl", "${siteUrls.path(dev.yona.util.UrlPaths.projectPath(to.owner, to.name))}/issue/labels.css")
    }

    @GetMapping("/{user}/{project}/newPullRequest/mergeResult")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    @Transactional(readOnly = true)
    @IsOnlyGitAvailable
    fun mergeResult(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user)
        guestError(project, viewer)?.let { return it }

        val from = selectedProject(project, request.getParameter("fromProjectId"), false) ?: return ErrorViews.notFound()
        val to = selectedProject(project, request.getParameter("toProjectId"), true) ?: return ErrorViews.notFound()
        val fromBranch = request.getParameter("fromBranch").takeUnless { it.isNullOrBlank() } ?: branches.branches(from).first().name
        val toBranch = request.getParameter("toBranch").takeUnless { it.isNullOrBlank() } ?: branches.branches(to).first().name
        val pullRequest = PullRequest(fromProject = from, toProject = to, fromBranch = fromBranch, toBranch = toBranch)

        val attempt = merger.attempt(pullRequest)
        val (title, body) = suggest(attempt.commits)
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store")
        return ModelAndView("pullrequest/partial-merge-result")
            .addObject("commitCount", attempt.commits.size)
            .addObject("suggestedTitle", title)
            .addObject("suggestedBody", body)
            .addObject("conflict", attempt.conflict)
            .addObject("rows", attempt.commits.map { row(it, from) })
    }

    @PostMapping("/{user}/{project}/pullRequests")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.FORK)
    @Transactional
    @IsOnlyGitAvailable
    fun create(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = requireNotNull(currentUserProvider.get().user)
        guestError(project, viewer)?.let { return it }

        val title = request.getParameter("title")
        val fromBranch = request.getParameter("fromBranch")
        val toBranch = request.getParameter("toBranch")
        if (title.isNullOrEmpty() || fromBranch.isNullOrEmpty() || toBranch.isNullOrEmpty() || title.length > MAX_TITLE) {
            return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
        }
        val from = request.getParameter("fromProjectId")?.toLongOrNull()?.let { projects.findById(it).orElse(null) }
        val to = request.getParameter("toProjectId")?.toLongOrNull()?.let { projects.findById(it).orElse(null) }
        if (from == null || to == null) return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()

        // 담당자와 라벨은 받는 프로젝트에 수정 권한이 있는 사람만 고를 수 있다(추가 항목 X-14, D-34 20번). 권한이 없으면 보낸 값은 무시한다
        var assignee: User? = null
        var labels = emptyList<dev.yona.domain.issue.IssueLabel>()
        if (canTriage(viewer, to)) {
            assignee = request.getParameter("assigneeLoginId")?.takeIf { it.isNotBlank() }?.let { login ->
                users.findByLoginId(login)?.takeIf { who -> assigneeService.candidates(to, viewer).any { it.id == who.id } } ?: return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
            }
            labels = request.getParameterValues("labelIds").orEmpty().filter { it.isNotBlank() }.map { raw ->
                raw.toLongOrNull()?.let { issueLabels.findById(it).orElse(null) }?.takeIf { it.project?.id == to.id } ?: return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
            }
        }

        val creation = pullRequestService.create(PullRequestForm(title, body, from, to, fromBranch, toBranch), viewer)
        val pullRequest = creation.pullRequest
        val location = "${dev.yona.util.UrlPaths.projectPath(pullRequest.toProject?.owner, pullRequest.toProject?.name)}/pullRequest/${pullRequest.number}"
        if (!creation.created) return RedirectSupport.see(request, response, siteUrls.path(location))

        val flash = postAttachments.attach(request, viewer, ResourceType.PULL_REQUEST, requireNotNull(pullRequest.id))
        if (assignee != null) assigneeService.set(pullRequest, assignee, viewer)
        if (labels.isNotEmpty()) labelService.change(pullRequest, labels, emptyList(), viewer)
        val pullRequestId = requireNotNull(pullRequest.id)
        val senderId = requireNotNull(viewer.id)
        runner.run("merging of pull request $pullRequestId", { processor.start(pullRequestId) }, { processor.process(pullRequestId, senderId, true) })
        return RedirectSupport.see(request, response, siteUrls.path(location), flash)
    }

    // --- 도우미 ---

    /** [viewer]가 받는 프로젝트 [to]에서 풀 리퀘스트의 담당자와 라벨을 정할 수 있는지이다(수정 권한. 아직 요청이 없으므로 작성자는 따지지 않는다). */
    private fun canTriage(viewer: User, to: Project): Boolean =
        accessControl.isAllowed(viewer, dev.yona.access.ProjectResource("new", ResourceType.PULL_REQUEST, to), dev.yona.domain.enumeration.Operation.UPDATE)

    /** 게스트(구성원이 아닌 로그인 사용자)는 풀 리퀘스트 화면을 쓸 수 없다. 403 오류 화면을 돌려준다 (v1.6 `validateBeforePullRequest`). */
    private fun guestError(project: Project, viewer: User): ModelAndView? =
        if (projectService.isGuest(project, viewer)) {
            ErrorViews.badRequest("Guest is not allowed this request", views.of(project), "PROJECT_HOME").also { it.status = HttpStatus.FORBIDDEN }
        } else null

    /** 현재 프로젝트, 그 포크, 포크이면 원본(코드와 풀 리퀘스트 메뉴를 켠 경우)이다 (v1.6 `Project.getAssociationProjects`). */
    private fun associationProjects(project: Project): List<Project> {
        val result = mutableListOf(project)
        result += projects.findByOriginalProjectId(requireNotNull(project.id))
        val original = project.originalProject
        if (original != null && usesPullRequest(original)) result += original
        return result
    }

    private fun usesPullRequest(project: Project): Boolean {
        val menu = menuSettings.findFirstByProjectId(requireNotNull(project.id))
        return menu?.code == true && menu.pullRequest == true
    }

    /**
     * 선택한 프로젝트이다. 번호를 주지 않으면 보내는 쪽은 현재 프로젝트, 받는 쪽은 포크이고 원본이 풀 리퀘스트를 받으면 원본이다 (v1.6 `getSelectedProject`).
     */
    private fun selectedProject(project: Project, projectId: String?, isToProject: Boolean): Project? {
        if (!projectId.isNullOrEmpty() && projectId.all { it.isDigit() }) return projects.findById(projectId.toLong()).orElse(null)
        val original = project.originalProject
        return if (isToProject && original != null && usesPullRequest(original)) original else project
    }

    private fun option(project: Project, selected: Project) = ProjectOption(requireNotNull(project.id), "${project.owner} / ${project.name}", project.id == selected.id)

    /**
     * 커밋에서 제목과 본문을 제안한다 (v1.6 `suggestTitleAndBodyFromDiffCommit`). 커밋이 하나이면 첫 줄이 제목이고 나머지가 본문이다.
     * 둘 이상이면 제목은 비우고 각 커밋의 첫 줄을 본문으로 나열한다. 없으면 둘 다 비운다.
     */
    private fun suggest(commits: List<CommitInfo>): Pair<String?, String?> = when {
        commits.isEmpty() -> null to null
        commits.size == 1 -> {
            val lines = commits[0].message.split("\n").dropLastWhile { it.isEmpty() }
            if (lines.size > 1) lines[0] to lines.drop(1).joinToString("\n").trim() else lines.firstOrNull() to ""
        }
        else -> null to commits.joinToString("\n") { it.message.split("\n").firstOrNull().orEmpty() }
    }

    private fun row(commit: CommitInfo, from: Project): MergeCommitRow {
        val user = users.findByEmail(commit.authorEmail)
        val short = commit.message.split("\n")[0]
        val lines = commit.message.split("\n").dropLastWhile { it.isEmpty() }
        val more = lines.size > 1
        return MergeCommitRow(
            shortId = commit.shortId, commitUrl = siteUrls.path("${dev.yona.util.UrlPaths.projectPath(from.owner, from.name)}/commit/${commit.id}"),
            commentCount = commitComments.countByProjectIdAndCommitId(requireNotNull(from.id), commit.id),
            shortMessageHtml = HtmlUtils.htmlEscape(short), hasMore = more, moreHtml = HtmlUtils.htmlEscape(commit.message.replace(short + "\n", "")),
            date = helper.agoOrDateString(commit.authorDate), dateTitle = helper.dateString(commit.authorDate), authorEmail = commit.authorEmail,
            authorHref = user?.let { siteUrls.path("/${it.loginId}") }, authorName = user?.name,
            avatarUrl = user?.let { avatars.avatarUrl(it) } ?: avatars.gravatarUrl(commit.authorEmail, AVATAR_SIZE)
        )
    }

    private companion object {
        const val MAX_TITLE = 255
        const val AVATAR_SIZE = 32
    }
}
