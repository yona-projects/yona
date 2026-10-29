package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingCommentService
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectService
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest

/**
 * 게시판 화면이 쓰는 JSON API이다. v1.6 `BoardApi`(`app/controllers/api/BoardApi.java`)의 라벨, 본문 갱신, 댓글 갱신에 해당한다.
 * 다른 사람이 먼저 고친 본문을 덮어쓰려 하면 `409`이다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class BoardApiController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val postingService: PostingService,
    private val commentService: PostingCommentService,
    private val labels: IssueLabelRepository,
    private val issueJson: IssueJson,
    private val accessControl: AccessControl,
    private val currentUserProvider: CurrentUserProvider
) {
    private val mapper = JsonMapper.builder().build()

    /**
     * 글의 라벨을 본문의 번호 배열(`["1","2"]`)로 바꾸고 `{"id": 소유자, "labels": 개수}`를 돌려준다.
     * v1.6은 권한을 확인하지 않았으나 화면은 수정할 수 있는 사용자에게만 선택기를 보이므로 수정 권한을 요구한다([decisions.md] A-24).
     */
    @PostMapping("/{owner}/{projectName}/post/{number}/label")
    @Transactional
    fun updatePostLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val json = body(request) ?: return ResponseEntity.badRequest().body("Expecting Json data")
        val (project, posting) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val viewer = currentUserProvider.get().user
        if (viewer == null || !accessControl.isAllowed(viewer, posting.asResource(), Operation.UPDATE)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build<Void>()
        val ids = mutableListOf<Long>()
        for (node in json) ids += node.asString("").toLongOrNull() ?: return ResponseEntity.badRequest().body("Wrong label id")
        val found = labels.findAllById(ids.toSet()).toMutableSet()
        if (found.size != ids.toSet().size) return ResponseEntity.badRequest().body("Wrong label id")
        postingService.replaceLabels(posting, found)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("id" to project.owner, "labels" to posting.labels.size))
    }

    /** 체크리스트 갱신으로 글 본문을 바꾼다. 본문: `{"content", "original"}`. `original`이 저장된 본문과 다르면 `409`이다. */
    @PatchMapping("/{owner}/{projectName}/post/{number}/content")
    @Transactional
    fun updatePostingContent(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val viewer = currentUserProvider.get().user ?: return message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, posting) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(viewer, posting.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (modifiedByOthers(posting.body.orEmpty(), json.findValue("original")?.asString("").orEmpty())) return conflicted(posting.body.orEmpty())
        postingService.replaceBody(posting, json.findValue("content")?.asString("").orEmpty())
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(issueJson.ofPosting(posting))
    }

    /** 체크리스트 갱신으로 댓글 내용을 바꾼다. v1.6은 권한을 충돌보다 먼저 확인한다. */
    @PatchMapping("/{owner}/{projectName}/post/{number:[0-9]+}/comment/{commentId}")
    @Transactional
    fun updatePostingComment(
        @PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest
    ): Any {
        val viewer = currentUserProvider.get().user ?: return message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = body(request) ?: return message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val (_, posting) = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = postingComments.findById(commentId).orElse(null)?.takeIf { it.posting?.id == posting.id } ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(viewer, comment.asResource(), Operation.UPDATE)) return message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (modifiedByOthers(comment.contents.orEmpty(), json.findValue("original")?.asString("").orEmpty())) return conflicted(comment.contents.orEmpty())
        commentService.replaceContents(comment, json.findValue("content")?.asString("").orEmpty())
        val node = linkedMapOf<String, Any?>("id" to comment.id, "contents" to comment.contents, "createdDate" to issueJson.iso(comment.createdDate))
        node["author"] = linkedMapOf("id" to viewer.id, "loginId" to viewer.loginId, "name" to viewer.name)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("result" to node))
    }

    private fun find(owner: String, projectName: String, number: Long): Pair<Project, Posting>? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        val posting = postings.findByProjectIdAndNumber(requireNotNull(project.id), number) ?: return null
        return project to posting
    }

    private fun body(request: HttpServletRequest): JsonNode? {
        if (request.contentType?.contains("json", ignoreCase = true) != true) return null
        return try { mapper.readTree(request.inputStream) } catch (e: Exception) { null }
    }

    private fun message(status: HttpStatus, text: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to text))

    private fun conflicted(stored: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to "Already modified by someone.", "storedContent" to stored))

    private fun modifiedByOthers(current: String, fromView: String): Boolean =
        sha1(current.replace("\r", "").trim()) != sha1(fromView.replace("\r", "").trim())

    private fun sha1(text: String): String = MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
