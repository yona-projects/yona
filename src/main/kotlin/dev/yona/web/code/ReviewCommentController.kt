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
import dev.yona.web.access.IsCreatable
import dev.yona.web.access.IsOnlyGitAvailable
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
    private val commitComments: dev.yona.domain.comment.CommitCommentRepository
) {

    @PostMapping("/{user}/{project}/commit/{commitId}/comments")
    @IsCreatable(ResourceType.COMMIT_COMMENT)
    @Transactional
    fun newCommitComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable commitId: String, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("error.validation", views.of(project), "CODE")
        if (history.commit(project, commitId) == null) return ErrorViews.notFound("error.notfound", views.of(project), null)
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
        return RedirectSupport.see(request, response, "/${project.owner}/${project.name}/commit/$commitId#comment-${comment.id}")
    }

    /** Subversion 커밋 코멘트를 지우고 커밋 보기로 돌아간다 (v1.6 `CodeHistoryApp.deleteComment`). 지울 권한이 있어야 한다. */
    @DeleteMapping("/{user}/{project}/commit/{commitId}/comments/{id}/delete")
    @Transactional
    fun deleteCommitComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable commitId: String, @PathVariable id: Long, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val comment = commitComments.findById(id).orElse(null) ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.COMMIT_COMMENT.resource)
        if (!accessControl.isAllowed(currentUserProvider.get().user, lookup.get(ResourceType.COMMIT_COMMENT, id.toString()), Operation.DELETE)) {
            return ErrorViews.forbidden("error.forbidden", views.of(project))
        }
        service.deleteCommitComment(requireNotNull(comment.id))
        return RedirectSupport.see(request, response, "/$owner/$name/commit/$commitId")
    }

    @PostMapping("/{user}/{project}/pullRequest/{pullRequestId}/comments")
    @IsCreatable(ResourceType.REVIEW_COMMENT)
    @Transactional
    @IsOnlyGitAvailable
    fun newPullRequestComment(
        @PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable pullRequestId: Long, request: HttpServletRequest, response: HttpServletResponse
    ): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val contents = request.getParameter("contents")
        if (contents.isNullOrEmpty()) return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")
        val pullRequest = pullRequests.findById(pullRequestId).orElse(null) ?: return ErrorViews.notFound("error.notfound", views.of(project), null)

        if (!threadBelongs(request, project)) return ErrorViews.badRequest("error.validation", views.of(project), "PULL_REQUEST")

        val author = requireNotNull(currentUserProvider.get().user)
        // v1.6는 `commitId`를 쿼리 문자열 인자(`commitId: String ?= null`)로만 받는다. 폼 본문의 같은 이름 값(전체 변경 화면이 숨겨 보낸다)은 쓰지 않는다.
        val commitId = UriComponentsBuilder.newInstance().query(request.queryString).build().queryParams.getFirst("commitId")?.takeIf { it.isNotEmpty() }?.let { UriUtils.decode(it, Charsets.UTF_8) }
        val comment = service.commentOnPullRequest(project, pullRequest, commitId, author, contents, threadId(request), range(request))
        return RedirectSupport.see(request, response, links.comment(comment))
    }

    @PostMapping("/threads/{id}/open")
    @Transactional
    fun open(@PathVariable id: Long): ResponseEntity<Void> = updateState(id, ThreadState.OPEN)

    @PostMapping("/threads/{id}/close")
    @Transactional
    fun close(@PathVariable id: Long): ResponseEntity<Void> = updateState(id, ThreadState.CLOSED)

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
    private fun threadBelongs(request: HttpServletRequest, project: dev.yona.domain.project.Project): Boolean {
        val raw = request.getParameter("thread.id") ?: return true
        val id = raw.toLongOrNull() ?: return true
        return threads.findById(id).orElse(null)?.project?.id == project.id
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
