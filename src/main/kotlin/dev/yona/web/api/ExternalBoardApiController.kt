package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.ExternalPosting
import dev.yona.domain.board.ExternalPostingService
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.PostAttachments
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.access.IsCreatable
import dev.yona.web.error.ErrorViews
import dev.yona.web.project.ProjectViewFactory
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 외부 도구(마이그레이션)가 쓰는 게시판 REST API이다. v1.6 `BoardApi.newPostings`, `newPostingComment`(`app/controllers/api/BoardApi.java:70-190`)에 해당한다.
 * 가져오기용이라 알림은 보내지 않는다. 마이그레이션 화면이 세션 로그인으로 쓰므로 현재 사용자(세션 또는 토큰)가 만들 권한을 가져야 한다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalBoardApiController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val service: ExternalPostingService,
    private val apiUsers: ApiUsers,
    private val requests: ApiRequests,
    private val users: UserRepository,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider,
    private val attachments: PostAttachments,
    private val views: ProjectViewFactory
) {

    /**
     * 게시글 여러 개를 만든다. 본문: `{"posts": [{"title", "body", "author", "createdAt", "updatedAt", "number", "temporaryUploadFiles"}]}`.
     * 항목마다 `{"status": 201, "location": 주소}`를 모아 `201`로 답한다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts")
    @IsCreatable(ResourceType.BOARD_POST)
    @Transactional
    fun newPostings(@PathVariable owner: String, @PathVariable projectName: String, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val postsNode = json.findValue("posts")
        if (postsNode == null || !postsNode.isArray) return requests.message(HttpStatus.BAD_REQUEST, "No posts key exists or value wasn't array!")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val created = postsNode.toList().map { node ->
            val title = node.findValue("title")?.asString() ?: return requests.message(HttpStatus.BAD_REQUEST, "title is required")
            val author = findAuthor(node.findValue("author"), viewer)
            val posting = service.create(
                project, author,
                ExternalPosting(title, node.findValue("body")?.asString().orEmpty(), parseDate(node.findValue("createdAt")), parseDate(node.findValue("updatedAt")), node.findValue("number")?.asLong(0)?.takeIf { it > 0 })
            )
            attachments.attachIds(viewer, ids(node.findValue("temporaryUploadFiles")), ResourceType.BOARD_POST, requireNotNull(posting.id))
            linkedMapOf("status" to 201, "location" to "/${project.owner}/${project.name}/post/${posting.number}")
        }
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(created)
    }

    /**
     * 게시글에 댓글을 단다. 본문: `{"body", "createdAt", "author", "temporaryUploadFiles"}`. `{"status": 201, "location": 주소}`를 `201`로 돌려준다.
     */
    @PostMapping("/-_-api/v1/owners/{owner}/projects/{projectName}/posts/{number}/comments")
    @IsCreatable(ResourceType.NONISSUE_COMMENT)
    @Transactional
    fun newPostingComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = requests.json(request) ?: return ResponseEntity.badRequest().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("Expecting Json data")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (!accessControl.isResourceCreatable(viewer, posting.asResource(), ResourceType.NONISSUE_COMMENT)) return ErrorViews.forbidden("error.forbidden", views.of(project))
        val author = findAuthor(json.findValue("author"), requireNotNull(viewer))
        val comment = service.addComment(posting, author, json.findValue("body")?.asString().orEmpty(), parseDate(json.findValue("createdAt")))
        attachments.attachIds(viewer, ids(json.findValue("temporaryUploadFiles")), ResourceType.NONISSUE_COMMENT, requireNotNull(comment.id))
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
            .body(linkedMapOf("status" to 201, "location" to "/${project.owner}/${project.name}/post/${posting.number}#comment-${comment.id}"))
    }

    private fun parseDate(node: JsonNode?): Date? {
        if (node == null) return null
        return try { SimpleDateFormat("yyyy-MM-dd a hh:mm:ss Z", Locale.ENGLISH).parse(node.asString()) } catch (e: ParseException) { null }
    }

    private fun ids(node: JsonNode?): List<String> = if (node == null || !node.isArray) emptyList() else node.toList().map { it.asString("") }

    /** 작성자를 정한다. `author`가 있으면 이메일로 찾고 없으면 사용자를 만든다. 없으면 요청한 사용자이다. 찾지도 만들지도 못하면 익명 사용자(번호 -1)이다. */
    private fun findAuthor(node: JsonNode?, viewer: User): User {
        if (node == null) return viewer
        val email = node.findValue("email")?.asString() ?: return apiUsers.anonymous("")
        return apiUsers.findAuthor(email)
    }
}
