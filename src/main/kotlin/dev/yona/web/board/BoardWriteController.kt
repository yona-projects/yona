package dev.yona.web.board

import dev.yona.web.AjaxRequests
import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingService
import dev.yona.domain.board.PostForm
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.RecentIssueService
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.User
import dev.yona.io.LineEnding
import dev.yona.repository.OnlineCommit
import dev.yona.repository.ProjectReadmeService
import dev.yona.repository.RepositoryFileReader
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.web.access.NullProjectCheck
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.util.UriUtils

/**
 * 게시글 작성 폼, 작성, 수정 폼, 수정, 삭제이다. v1.6 `BoardApp`(`app/controllers/BoardApp.java:132-340`)과
 * `AbstractPostingApp.editPosting`, `delete`에 해당한다.
 *
 * 작성 폼은 게시글 말고도 저장소 파일 편집에 쓰인다. 쿼리 문자열 `readme`, `issueTemplate`, `path`(와 `branch`, `edit`)가 있으면
 * 각각 README, 이슈 템플릿, 지정한 파일의 내용으로 채워지고, 저장하면 글이 아니라 저장소에 커밋된다(온라인 커밋).
 */
@Controller
@AnonymousCheck
class BoardWriteController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val postingService: PostingService,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val projectUsers: ProjectUserRepository,
    private val readmeService: ProjectReadmeService,
    private val fileReader: RepositoryFileReader,
    private val onlineCommit: OnlineCommit,
    private val postAttachments: PostAttachments,
    private val views: ProjectViewFactory
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // --- 작성 ---

    @GetMapping("/{user}/{project}/postform")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsCreatable(ResourceType.BOARD_POST)
    @Transactional(readOnly = true)
    fun newPostForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        var prepared = ""
        // v1.6 projectHasReadme는 README.md/readme.md만 확인하고, 읽을 때는 네 가지 이름 중 처음 찾은 파일을 읽는다
        if (request.getParameter("readme") != null && readmeService.fileName(project) != null) prepared = readmeService.readFirstFound(project).orEmpty()
        if (request.getParameter("issueTemplate") != null) prepared = fileReader.readText(project, "ISSUE_TEMPLATE.md")?.ifBlank { null }.orEmpty()
        if (request.getParameter("path") != null) {
            if (!accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT)) return ErrorViews.forbidden("error.forbidden", views.of(project))
            prepared = onlineCommit.readTextFile(project, request.getParameter("branch").orEmpty(), request.getParameter("path")).orEmpty()
        }
        return createView(project, viewer, "post.new", prepared, emptyMap(), request)
    }

    @PostMapping("/{user}/{project}/posts")
    @IsCreatable(ResourceType.BOARD_POST)
    @Transactional
    fun newPost(@PathVariable("user") owner: String, @PathVariable("project") name: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return ErrorViews.forbidden()
        val errors = validate(request)
        // 오류 화면에 제출한 제목과 본문을 되돌려 준다. v1.6은 본문을 비우고(`preparedPostBody`는 오류 때 "") 제목도 비워서 오류가 난 사용자가 긴 글을 잃었다(board-F5)
        if (errors.isNotEmpty()) return createView(project, viewer, "error.validation", request.getParameter("body").orEmpty(), errors, request, request.getParameter("title")).also { it.status = HttpStatus.BAD_REQUEST }
        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()
        val title = request.getParameter("title")
        val form = PostForm(title, body, checked(request, "notice") && allowedToNotice(viewer, project), checked(request, "readme"))

        if (form.readme) {
            val existing = postings.findFirstByProjectIdAndReadmeTrue(requireNotNull(project.id))
            if (existing != null) return edit(project, existing, viewer, request, response)
            commitRootFile(project, viewer, "README.md", form)
        }
        if (request.getParameter("issueTemplate") == "true") {
            commitRootFile(project, viewer, "ISSUE_TEMPLATE.md", form)
            return RedirectSupport.see(request, response, dev.yona.util.UrlPaths.projectPath(project.owner, project.name))
        }
        val path = request.getParameter("path")
        if (!path.isNullOrEmpty() && projectUsers.existsByUserIdAndProjectId(requireNotNull(viewer.id), requireNotNull(project.id))) {
            val branch = request.getParameter("branch").orEmpty()
            onlineCommit.commitTextFile(project, viewer, branch, path, LineEnding.change(body, request.getParameter("lineEnding")), title)
            return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/code/${UriUtils.encodePathSegment(branch, Charsets.UTF_8)}/${dev.yona.util.UrlPaths.encodeEach(path)}")
        }

        // 공지는 공지 권한이 있는 사용자만 지정한다. v1.6 `newPost`는 폼 값을 권한 검사 없이 저장했다(화면에서만 숨겼다). 보안 보강이다(A-459)
        val posting = postingService.create(project, viewer, form)
        val flash = postAttachments.attach(request, viewer, ResourceType.BOARD_POST, requireNotNull(posting.id))
        return RedirectSupport.see(request, response, if (form.readme) dev.yona.util.UrlPaths.projectPath(project.owner, project.name) else "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/post/${posting.number}", flash)
    }

    // --- 수정 ---

    @GetMapping("/{user}/{project}/post/{number:[0-9]+}/editform")
    @NullProjectCheck
    @Transactional(readOnly = true)
    fun editPostForm(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.BOARD_POST.resource)
        if (!accessControl.isAllowed(viewer, posting.asResource(), Operation.READ)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val body = if (posting.readme == true && dev.yona.domain.vcs.VcsCapabilities.supports(project.vcs, dev.yona.domain.vcs.VcsCapability.README_POSTING)) readmeService.readFirstFound(project) else posting.body
        return editView(project, viewer, posting, "post.modify", emptyMap(), allowedToNotice(viewer, project), body.orEmpty(), posting.title.orEmpty())
    }

    @PostMapping("/{user}/{project}/post/{number:[0-9]+}/edit")
    @NullProjectCheck
    @Transactional
    fun editPost(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.BOARD_POST.resource)
        // 읽을 수 없는 글은 입력 검사 전에 막는다. 검사 뒤에 막으면 오류 화면에 저장된 제목과 본문이 실린다(v1.6은 검사 뒤였다. 보안 보강, A-459)
        if (!accessControl.isAllowed(viewer, posting.asResource(), Operation.READ)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val errors = validate(request)
        if (errors.isNotEmpty()) {
            val noticeAllowed = accessControl.isProjectResourceCreatable(viewer, project, ResourceType.BOARD_NOTICE)
            // 제출한 값을 되돌려 준다(v1.6은 저장된 제목과 본문으로 되돌려 입력을 잃었다, board-F5)
            return editView(project, viewer, posting, "error.validation", errors, noticeAllowed, request.getParameter("body") ?: posting.body.orEmpty(), request.getParameter("title") ?: posting.title.orEmpty()).also { it.status = HttpStatus.BAD_REQUEST }
        }
        if (viewer == null || !accessControl.isAllowed(viewer, posting.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        return edit(project, posting, viewer, request, response)
    }

    /** 폼 값으로 [posting]을 고친다. 새 글 작성에서 README 글이 이미 있을 때도 이 처리를 쓴다 (v1.6 `BoardApp.editPost`). */
    private fun edit(project: Project, posting: Posting, viewer: User, request: HttpServletRequest, response: HttpServletResponse): Any {
        if (!accessControl.isAllowed(viewer, posting.asResource(), Operation.UPDATE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val body = request.getParameter("body") ?: return ErrorViews.requestTextEntityTooLarge()
        // 공지 권한이 없으면 보낸 값을 무시하고 지금 값을 유지한다(A-459)
        val notice = if (allowedToNotice(viewer, project)) checked(request, "notice") else posting.notice == true
        val form = PostForm(request.getParameter("title"), body, notice, checked(request, "readme"))
        if (form.readme) {
            commitRootFile(project, viewer, "README.md", form)
            postingService.unmarkOtherReadme(project, requireNotNull(posting.number))
        }
        postingService.update(posting, viewer, form, request.getParameter("notificationMail") == "yes")
        val flash = postAttachments.attach(request, viewer, ResourceType.BOARD_POST, requireNotNull(posting.id))
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/post/${posting.number}", flash)
    }

    // --- 삭제 ---

    @DeleteMapping("/{user}/{project}/post/{number:[0-9]+}/delete")
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.BOARD_POST)
    @Transactional
    fun deletePost(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long,
        request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return ErrorViews.forbidden()
        if (!accessControl.isAllowed(viewer, posting.asResource(), Operation.DELETE)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        postingService.delete(posting, viewer)
        val location = "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/posts"
        if (AjaxRequests.isXhr(request)) return ResponseEntity.status(HttpStatus.NO_CONTENT).header(HttpHeaders.LOCATION, location).build<Void>()
        return RedirectSupport.see(request, response, location)
    }

    // --- 도우미 ---

    private fun validate(request: HttpServletRequest): Map<String, List<String>> {
        val title = request.getParameter("title")
        return when {
            title.isNullOrEmpty() -> mapOf("title" to listOf("error.required"))
            title.length > MAX_TITLE -> mapOf("title" to listOf("error.maxLength"))
            else -> emptyMap()
        }
    }

    /** 체크박스 값이다. 켜 둔 상자는 `on`(또는 `true`)로 온다. */
    private fun checked(request: HttpServletRequest, name: String): Boolean = request.getParameter(name)?.lowercase() in setOf("on", "true", "yes", "1")

    /** 저장소 `HEAD` 최상위 파일을 커밋한다. 실패는 기록만 하고 넘어간다 (v1.6 `commitReadmeFile`, `commitIssueTemplateFile`). */
    private fun commitRootFile(project: Project, viewer: User, fileName: String, form: PostForm) {
        try {
            onlineCommit.commitRootFile(project, viewer, fileName, form.body, form.title)
        } catch (e: Exception) {
            log.error("Online commit failed: {} {}", project.name, fileName, e)
        }
    }

    /**
     * 공지를 올릴 수 있는지이다. 화면이 공지 선택을 보이는 조건과 같은 [AccessControl.isProjectResourceCreatable]를 쓴다.
     *
     * v1.6은 글쓰기 화면은 이 조건(조직 관리자와 그룹 구성원 포함)으로 선택을 보이고, 저장과 수정은 `ProjectUser.isAllowedToNotice`
     * (프로젝트 직접 구성원만)로 판단해 조직 관리자가 공지를 켜도 조용히 무시되었다(board-F4). 보이는 선택이 동작하도록 하나로 맞췄다.
     */
    private fun allowedToNotice(viewer: User?, project: Project): Boolean =
        accessControl.isProjectResourceCreatable(viewer, project, ResourceType.BOARD_NOTICE)

    private fun createView(project: Project, viewer: User?, title: String, prepared: String, errors: Map<String, List<String>>, request: HttpServletRequest?, submittedTitle: String? = null): ModelAndView {
        val query = { key: String -> request?.getParameter(key) }
        val readme = request?.getParameter("readme") != null
        val issueTemplate = query("issueTemplate")
        val path = query("path")
        return ModelAndView("board/create")
            .addObject("title", title)
            .addObject("projectView", views.of(project))
            .addObject("errors", errors)
            .addObject("titleValue", if (submittedTitle != null) submittedTitle else if (readme) "Update README.md" else if (issueTemplate != null) "ISSUE_TEMPLATE.md: Project Issue Template" else "")
            .addObject("issueTemplate", issueTemplate.orEmpty())
            .addObject("hasIssueTemplate", issueTemplate != null)
            .addObject("branch", query("branch").orEmpty())
            .addObject("path", path.orEmpty())
            .addObject("hasPath", path != null)
            .addObject("isEdit", query("edit") != null)
            .addObject("readmeRequested", readme)
            .addObject("preparedBody", prepared)
            .addObject("lineEnding", LineEnding.find(prepared).name)
            .addObject("allowedToNotice", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.BOARD_NOTICE))
            .addObject("canCommit", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT))
            .addObject("isGit", dev.yona.domain.vcs.VcsCapabilities.supports(project.vcs, dev.yona.domain.vcs.VcsCapability.README_POSTING))
    }

    private fun editView(project: Project, viewer: User?, posting: Posting, title: String, errors: Map<String, List<String>>, allowedToNotice: Boolean, body: String, titleValue: String): ModelAndView =
        ModelAndView("board/edit")
            .addObject("title", title)
            .addObject("projectView", views.of(project))
            .addObject("errors", errors)
            .addObject("number", posting.number)
            .addObject("postId", posting.id)
            .addObject("titleValue", titleValue)
            .addObject("bodyValue", body)
            .addObject("notice", posting.notice == true)
            .addObject("readme", posting.readme == true)
            .addObject("allowedToNotice", allowedToNotice)
            .addObject("canCommit", accessControl.isProjectResourceCreatable(viewer, project, ResourceType.COMMIT))
            .addObject("isGit", dev.yona.domain.vcs.VcsCapabilities.supports(project.vcs, dev.yona.domain.vcs.VcsCapability.README_POSTING))
            .addObject("canUpdate", accessControl.isAllowed(viewer, posting.asResource(), Operation.UPDATE))
            .addObject("authoredByViewer", viewer != null && posting.authorLoginId == viewer.loginId)

    private companion object {
        const val MAX_TITLE = 255
    }
}
