package dev.yona.web.code

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.access.ResourceLookup
import dev.yona.access.asResource
import dev.yona.domain.comment.CodeRange
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.comment.ReviewCommentService
import dev.yona.domain.comment.ReviewLinks
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.project.ProjectService
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.repository.CommitHistoryService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.access.IsCreatable
import dev.yona.domain.vcs.VcsCapability
import dev.yona.web.access.RequiresVcs
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriUtils
import tools.jackson.databind.json.JsonMapper

/**
 * 코드 리뷰 댓글의 등록, 스레드 열기와 닫기, 댓글 삭제이다. v1.6 `CodeHistoryApp.newComment`, `PullRequestApp.newComment`
 * (`app/controllers/PullRequestApp.java:566-640`), `CommentThreadApp`, `CommentApp.delete`에 해당한다.
 *
 * - 댓글 등록: 내용(`contents`)이 있어야 하고 `thread.id`가 있으면 그 스레드에 이어 단다. 없으면 코드 범위(`path`, `startSide`, `startLine`, `startColumn`,
 *   `endSide`, `endLine`, `endColumn`)가 있으면 범위 스레드, 없으면 파일 단위 스레드를 만든다. 끝나면 댓글 위치로 303 이동한다.
 * - 스레드 열기와 닫기: 각각 다시 열기, 닫기 권한이 필요하다. 성공하면 빈 `200`이다.
 * - 댓글 삭제: 커밋 코멘트와 리뷰 댓글만 지울 수 있다. 지울 권한이 필요하다.
 *
 * Subversion 프로젝트의 커밋 코멘트는 [CommitCommentService]로 만들고 `commit/{id}/comments/{id}/delete`로 지운다.
 */
@Controller
@AnonymousCheck
class ReviewCommentController(
    private val projectService: ProjectService,
    private val history: CommitHistoryService,
    private val service: ReviewCommentService,
    private val links: ReviewLinks,
    private val pullRequests: PullRequestRepository,
    private val threads: CommentThreadRepository,
    private val reviewComments: ReviewCommentRepository,
    private val lookup: ResourceLookup,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val views: ProjectViewFactory,
    private val commitCommentService: dev.yona.domain.comment.CommitCommentService,
    private val commitComments: dev.yona.domain.comment.CommitCommentRepository,
    private val postAttachments: dev.yona.web.PostAttachments,
    private val siteUrls: dev.yona.util.SiteUrls,
    private val pendingReader: dev.yona.domain.comment.PendingReviewReader,
    private val markdown: dev.yona.markdown.ProjectMarkdownRenderer
) {

    @PostMapping("/{user}/{project}/commit/{commitId}/comments")
    @IsCreatable(ResourceType.COMMIT_COMMENT)
    @Transactional
    fun newCommitComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable commitId: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("error.validation", views.of(project), "PROJECT_HOME")  // v1.6 newComment는 render(키, project)라 PROJECT_HOME이 활성화된다
        if (history.commit(project, commitId) == null) return ErrorViews.notFound("error.notfound", views.of(project), request.requestURI)
        if (project.vcs == "Subversion") return newSvnComment(project, commitId, contents, request, response)

        if (!threadBelongs(request, project)) return ErrorViews.badRequest("error.validation", views.of(project), "CODE")

        val author = requireNotNull(currentUserProvider.get().user)
        val comment = service.commentOnCommit(project, commitId, author, contents, threadId(request), range(request))
        return RedirectSupport.see(request, response, links.comment(comment))
    }

    /**
     * Subversion 프로젝트의 커밋 코멘트를 만든다 (v1.6 `CodeHistoryApp.newSVNComment`). 커밋(리소스 `프로젝트번호:리비전`) 아래에 코멘트를 만들 수 있어야 한다.
     * 파일 경로, 줄, 쪽은 요청에 있으면 함께 저장한다. 끝나면 코멘트 위치로 303 이동한다.
     */
    private fun newSvnComment(project: dev.yona.domain.project.Project, commitId: String, contents: String, request: HttpServletRequest, response: HttpServletResponse): Any {
        val author = currentUserProvider.get().user
        val container = ProjectResource("${project.id}:$commitId", ResourceType.COMMIT, project)
        if (!accessControl.isResourceCreatable(author, container, ResourceType.COMMIT_COMMENT)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val side = request.getParameter("side")?.let { value -> Side.entries.firstOrNull { it.name == value } }
        val comment = commitCommentService.add(
            project, commitId, requireNotNull(author), contents, request.getParameter("path")?.takeIf { it.isNotEmpty() }, request.getParameter("line")?.toIntOrNull(), side
        )
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/commit/$commitId#comment-${comment.id}")
    }

    /** Subversion 커밋 코멘트를 지우고 커밋 보기로 돌아간다 (v1.6 `CodeHistoryApp.deleteComment`). 지울 권한이 있어야 한다. */
    @DeleteMapping("/{user}/{project}/commit/{commitId}/comments/{id}/delete")
    @IsAllowed(value = Operation.DELETE, resourceType = ResourceType.COMMIT_COMMENT)
    @Transactional
    fun deleteCommitComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable commitId: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        if (!service.deleteCommitCommentIn(project, commitId, id)) return ErrorViews.notFound()
        return RedirectSupport.see(request, response, "${dev.yona.util.UrlPaths.projectPath(owner, name)}/commit/$commitId")
    }

    @PostMapping("/{user}/{project}/pullRequest/{pullRequestId}/comments")
    @IsCreatable(ResourceType.REVIEW_COMMENT)
    @Transactional
    @RequiresVcs(VcsCapability.PULL_REQUESTS)
    fun newPullRequestComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable pullRequestId: Long, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("error.validation", views.of(project), "PROJECT_HOME")  // v1.6 newComment는 메뉴 종류 없이 render(키, project)를 불러 PROJECT_HOME이 활성화된다
        val pullRequest = pullRequests.findById(pullRequestId).orElse(null) ?: return ErrorViews.notFound("error.notfound", views.of(project), request.requestURI)

        if (!threadBelongs(request, project, pullRequest)) return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")

        val author = requireNotNull(currentUserProvider.get().user)
        // v1.6는 `commitId`를 쿼리 문자열 인자(`commitId: String ?= null`)로만 받는다. 폼 본문의 같은 이름 값(전체 변경 화면이 숨겨 보낸다)은 쓰지 않는다.
        val commitId = UriComponentsBuilder.newInstance().query(request.queryString).build().queryParams.getFirst("commitId")?.takeIf { it.isNotEmpty() }?.let { UriUtils.decode(it, Charsets.UTF_8) }
        // `pending=true`는 "리뷰 시작"과 "리뷰에 추가" 단추이다(추가 개발 항목 X-26, `decisions.md` D-38). 알림은 리뷰를 제출할 때 하나로 나간다
        val comment = try {
            service.commentOnPullRequest(project, pullRequest, commitId, author, contents, threadId(request), range(request), pending = request.getParameter("pending") == "true")
        } catch (e: IllegalArgumentException) {
            return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
        }
        val flash = postAttachments.attach(request, author, ResourceType.REVIEW_COMMENT, requireNotNull(comment.id))
        // v1.6 newComment: 쿼리의 commitId가 있을 때만 커밋 하나의 변경 화면(specificChange), 아니면 전체 변경 화면(pullRequestChanges)이다
        val base = "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/pullRequest/${pullRequest.number}/changes"
        val view = if (commitId != null) "$base/${dev.yona.util.UrlPaths.encode(commitId)}" else base
        return RedirectSupport.see(request, response, siteUrls.path("$view#comment-${comment.id}"), flash)
    }

    @PostMapping("/threads/{id}/open")
    @Transactional
    fun open(@PathVariable id: Long): ResponseEntity<Void> = updateState(id, ThreadState.OPEN)

    @PostMapping("/threads/{id}/close")
    @Transactional
    fun close(@PathVariable id: Long): ResponseEntity<Void> = updateState(id, ThreadState.CLOSED)

    /**
     * 리뷰 댓글의 내용을 고친다(GitHub 기준, `decisions.md` D-38 7번). 본문 `contents`가 필요하고 응답은 `{"id", "edited": true, "bodyHtml"}`이다.
     * 공개된 댓글은 프로젝트 구성원이면 남의 것도 고칠 수 있고(`Operation.UPDATE`, GitHub 방식인 D-48. 지우기만 작성자와 관리자로 제한한다), 임시 댓글은 작성자만 고치며 남에게는 없는 댓글(404)이다.
     */
    @PostMapping("/comments/review_comment/{id}/edit")
    @AnonymousCheck(requiresLogin = true)
    @Transactional
    fun edit(@PathVariable id: Long, request: HttpServletRequest): ResponseEntity<Any> {
        val user = requireNotNull(currentUserProvider.get().user)
        val visible = reviewComments.findById(id).orElse(null)
        val comment = if (visible != null) {
            if (!accessControl.isAllowed(user, visible.asResource(), Operation.UPDATE)) return ResponseEntity.status(403).build()
            visible
        } else {
            pendingReader.comment(id, user) ?: return ResponseEntity.notFound().build()
        }
        val contents = request.getParameter("contents")
        if (contents.isNullOrBlank()) return ResponseEntity.badRequest().build()
        service.edit(comment, contents)
        val project = comment.thread?.project
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .body(mapOf("id" to id, "edited" to true, "bodyHtml" to markdown.render(contents, requireNotNull(project), null)))
    }

    @DeleteMapping("/comments/{type}/{id}")
    @Transactional
    fun delete(@PathVariable type: String, @PathVariable id: String): ResponseEntity<Void> {
        val resourceType = ResourceType.entries.firstOrNull { it.resource == type && it != ResourceType.NOT_A_RESOURCE } ?: return ResponseEntity.badRequest().build()
        val resource = try { lookup.get(resourceType, id) } catch (e: RuntimeException) { return ResponseEntity.badRequest().build() }
        if (!accessControl.isAllowed(currentUserProvider.get().user, resource, Operation.DELETE)) return ResponseEntity.status(403).build()
        when (resource.type) {
            ResourceType.REVIEW_COMMENT -> service.delete(reviewComments.findById(id.toLong()).get())
            ResourceType.COMMIT_COMMENT -> service.deleteCommitComment(id.toLong())
            else -> return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok().build()
    }

    private fun updateState(id: Long, state: ThreadState): ResponseEntity<Void> {
        val thread = threads.findById(id).orElse(null) ?: return ResponseEntity.notFound().build()
        val operation = if (state == ThreadState.OPEN) Operation.REOPEN else Operation.CLOSE
        val user = currentUserProvider.get().user
        if (!accessControl.isAllowed(user, thread.asResource(), operation)) return ResponseEntity.status(403).build()
        service.updateState(thread, state, requireNotNull(user))
        return ResponseEntity.ok().build()
    }

    /** `thread.id`를 주었다면 같은 프로젝트의 스레드여야 한다. 다른 프로젝트의 스레드에 이어 다는 것은 직접 만든 요청에서만 닿는다 ([decisions.md] A-80). */
    private fun threadBelongs(request: HttpServletRequest, project: dev.yona.domain.project.Project, pullRequest: dev.yona.domain.pullrequest.PullRequest? = null): Boolean {
        val raw = request.getParameter("thread.id") ?: return true
        val id = raw.toLongOrNull() ?: return true
        // 내 임시 스레드(X-26, D-38)는 엔티티 조회에 보이지 않으므로 임시 스레드 읽기 경로로도 찾는다
        val visible = threads.findById(id).orElse(null)?.project?.id == project.id
        if (visible) return true
        val viewer = currentUserProvider.get().user ?: return false
        return pullRequest != null && pendingReader.thread(id, pullRequest, viewer)?.project?.id == project.id
    }

    private fun threadId(request: HttpServletRequest): Long? = request.getParameter("thread.id")?.toLongOrNull()

    /** 요청의 코드 범위이다. 시작과 끝의 줄, 열이 모두 숫자로 있어야 범위이고, 아니면 `null`(파일 단위)이다. */
    private fun range(request: HttpServletRequest): CodeRange? {
        fun number(key: String) = request.getParameter(key)?.toIntOrNull()
        fun side(key: String) = request.getParameter(key)?.let { value -> Side.entries.firstOrNull { it.name == value } }
        return CodeRange(
            request.getParameter("path"), side("startSide"), number("startLine") ?: return null, number("startColumn") ?: return null,
            side("endSide"), number("endLine") ?: return null, number("endColumn") ?: return null
        )
    }
}
