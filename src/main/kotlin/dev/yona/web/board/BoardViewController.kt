package dev.yona.web.board

import dev.yona.access.asResource
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.issue.RecentIssueService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.project.RecentVisitService
import dev.yona.repository.ProjectReadmeService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.HttpPreference
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsAllowed
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.ModelAndView

/**
 * 게시글 보기이다. v1.6 `BoardApp.post`(`app/controllers/BoardApp.java:151-182`)에 해당한다.
 * `Accept`에 JSON이 들어 있으면 같은 글을 JSON으로 돌려준다(제목, 종류, 작성자, 작성 시각, 본문, 첨부, 댓글).
 */
@Controller
@AnonymousCheck
class BoardViewController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val assembler: BoardViewAssembler,
    private val attachmentJson: dev.yona.web.AttachmentJson,
    private val readme: ProjectReadmeService,
    private val recentVisits: RecentVisitService,
    private val recentIssues: RecentIssueService,
    private val views: ProjectViewFactory,
    private val currentUserProvider: CurrentUserProvider,
    private val siteUrls: dev.yona.util.SiteUrls
) {

    @GetMapping("/{user}/{project}/post/{number:[0-9]+}")
    @IsAllowed(value = Operation.READ, resourceType = ResourceType.BOARD_POST)
    @Transactional
    fun post(@PathVariable("user") owner: String, @PathVariable("project") name: String, @PathVariable("number") number: Long, request: HttpServletRequest): Any {
        val project = projectService.findByOwnerAndProjectName(owner, name) ?: return ErrorViews.notFound()
        val post = postings.findByProjectIdAndNumber(requireNotNull(project.id), number)
            ?: return ErrorViews.notFound("error.notfound", views.of(project), ResourceType.BOARD_POST.resource)
        val viewer = currentUserProvider.get().user
        val body = if (post.readme == true && project.vcs == "GIT") readme.readFirstFound(project).orEmpty() else post.body

        if (request.getHeader("Accept").orEmpty().contains("application/json")) {
            val comments = postingComments.findByPostingIdOrderByCreatedDateAscIdAsc(requireNotNull(post.id)).map { comment ->
                val node = linkedMapOf<String, Any?>(
                    "id" to comment.id, "type" to ResourceType.NONISSUE_COMMENT.name, "authorId" to comment.authorLoginId, "authorName" to comment.authorName,
                    "created_at" to comment.createdDate?.time, "body" to comment.contents
                )
                attachmentJson.list(ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id)).takeIf { it.isNotEmpty() }?.let { node["attachments"] = it }
                node
            }
            val json = linkedMapOf<String, Any?>(
                "title" to post.title, "type" to ResourceType.BOARD_POST.name, "author" to post.authorLoginId, "authorName" to post.authorName,
                "created_at" to post.createdDate?.time, "body" to body, "attachments" to attachmentJson.list(ResourceType.BOARD_POST, requireNotNull(post.id)), "comments" to comments
            )
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(json)
        }

        recentVisits.visits(viewer, project)
        recentIssues.visits(viewer, post)
        recentVisits.markIssueOrPostingVisited(viewer, requireNotNull(post.id))
        val detail = assembler.assemble(project, post, viewer)
        val title = post.title.orEmpty() + " |:| " + body.orEmpty().take(200)
        return ModelAndView("board/view")
            .addObject("projectView", views.of(project))
            .addObject("post", detail)
            .addObject("title", title)
            .addObject("listUrl", listUrl(request, "${dev.yona.util.UrlPaths.projectPath(project.owner, project.name)}/posts"))
    }

    /**
     * 목록으로 돌아가는 주소이다. `Referer`가 이 프로젝트의 게시판 목록이면 그 주소(검색 조건과 쪽 포함)를, 아니면 목록의 기본 주소를 돌려준다
     * (v1.6 `TemplateHelper.urlToList`).
     */
    private fun listUrl(request: HttpServletRequest, default: String): String {
        val referer = request.getHeader("Referer") ?: return default
        return siteUrls.urlToList(referer, default)
    }
}
