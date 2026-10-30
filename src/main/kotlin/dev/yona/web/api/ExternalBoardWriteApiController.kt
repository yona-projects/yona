package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostForm
import dev.yona.domain.board.PostingCommentRepository
import dev.yona.domain.board.PostingCommentService
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.board.PostingSearchCondition
import dev.yona.domain.board.PostingSearchService
import dev.yona.domain.board.PostingService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.issue.IssueLabelService
import dev.yona.domain.issue.IssueService
import dev.yona.domain.project.ProjectService
import dev.yona.domain.user.UserRepository
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * 게시판의 조회, 수정, 삭제, 댓글, 라벨 REST이다(로드맵 X-29b, `decisions.md` D-47). 경로는 v1.6의 `/-_-api/v1/owners/{owner}/projects/{projectName}` 접두사에
 * GitHub의 자원 경로를 얹은 것이고 토큰 전용이다(X-5). 게시글 만들기와 댓글 달기, 구독자 조회는 v1.6에서 옮긴 [ExternalBoardApiController]가 맡는다.
 *
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). 읽기는 `READ`, 수정과 라벨은 `UPDATE`, 삭제는 `DELETE`이다.
 * 권한 모델은 GitHub 방식이다(D-48): 구성원은 남의 글과 댓글도 고치지만 지우는 것은 작성자와 관리자만 한다.
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalBoardWriteApiController(
    private val projectService: ProjectService,
    private val postings: PostingRepository,
    private val postingComments: PostingCommentRepository,
    private val postingService: PostingService,
    private val commentService: PostingCommentService,
    private val search: PostingSearchService,
    private val labelService: IssueLabelService,
    private val issueService: IssueService,
    private val users: UserRepository,
    private val issueJson: IssueJson,
    private val projectJson: ProjectJson,
    private val requests: ApiRequests,
    private val accessControl: AccessControl
) {

    // --- 조회 ---

    /**
     * 게시글 목록이다. 공지도 함께 나온다. 쿼리: `q`(제목이나 본문), `labelIds`(쉼표로 구분, 하나라도 붙은 글), `sort`(`createdDate` 기본, `updatedDate`, `number`, `title`,
     * `numOfComments`), `order`(`desc` 기본, `asc`), `page`(1부터), `pageSize`(1~100, 기본 15). 응답은 `{"items": [...], "page", "pageSize", "totalPages", "totalCount"}`이다.
     * 프로젝트를 읽을 수 있어야 한다.
     */
    @GetMapping("$PROJECT/posts")
    @Transactional(readOnly = true)
    fun list(
        @PathVariable owner: String, @PathVariable projectName: String, @RequestParam(required = false) q: String?, @RequestParam(defaultValue = "createdDate") sort: String,
        @RequestParam(defaultValue = "desc") order: String, @RequestParam(defaultValue = "1") page: Int, @RequestParam(required = false) pageSize: Int?, request: HttpServletRequest
    ): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, project.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (sort !in PostingSearchService.ORDERABLE) return requests.message(HttpStatus.BAD_REQUEST, "sort must be one of ${PostingSearchService.ORDERABLE.joinToString(", ")}")
        if (order != "asc" && order != "desc") return requests.message(HttpStatus.BAD_REQUEST, "order must be asc or desc")
        val labelIds = request.getParameterValues("labelIds").orEmpty().flatMap { it.split(",") }.mapNotNull { it.trim().toLongOrNull() }.toSet()
        val size = (pageSize ?: DEFAULT_PAGE_SIZE).coerceIn(1, MAX_PAGE_SIZE)
        val current = page.coerceAtLeast(1)
        val result = search.search(project, PostingSearchCondition(orderBy = sort, orderDir = order, filter = q.orEmpty(), pageNum = current - 1, labelIds = labelIds), size, withNotices = true)
        val items = result.items.map { projectJson.postingSummary(it, it.authorId?.let { id -> users.findById(id).orElse(null) }) }
        return ok(linkedMapOf("items" to items, "page" to current, "pageSize" to size, "totalPages" to result.totalPageCount, "totalCount" to result.totalRows))
    }

    /** 게시글 한 건이다. 본문, 라벨, 첨부, 댓글을 담는다. 읽을 수 있어야 한다. 결과: `{"result": {...}}`. */
    @GetMapping("$PROJECT/posts/{number:[0-9]+}")
    @Transactional(readOnly = true)
    fun get(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ok(linkedMapOf("result" to issueJson.postingDetail(posting)))
    }

    // --- 수정과 삭제 ---

    /**
     * 게시글을 부분 수정한다. 본문의 `title`, `body` 중 보낸 항목만 바꾼다. 공지와 README 표시는 그대로 둔다. 제목이 비면 400이다. 본문이 바뀌면 변경 이력이 쌓인다.
     * 게시글을 고칠 수 있어야 한다(구성원은 남의 글도 고친다, D-48). 결과: `{"result": {...}}`.
     */
    @PatchMapping("$PROJECT/posts/{number:[0-9]+}")
    @Transactional
    fun patch(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        if (!json.has("title") && !json.has("body")) return requests.message(HttpStatus.BAD_REQUEST, "no updatable field: title, body")
        val title = if (json.has("title")) json.get("title").asString("").takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "title must not be empty") else posting.title.orEmpty()
        val body = if (json.has("body")) json.get("body").let { if (it.isNull) "" else it.asString("") } else posting.body.orEmpty()
        postingService.update(posting, user, PostForm(title, body, posting.notice == true, posting.readme == true), false)
        return ok(linkedMapOf("result" to issueJson.postingDetail(posting)))
    }

    /** 게시글을 지운다. 작성자와 프로젝트 관리자만 지운다(D-48). 댓글과 첨부도 함께 지워진다. 성공하면 204이다. */
    @DeleteMapping("$PROJECT/posts/{number:[0-9]+}")
    @Transactional
    fun delete(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        postingService.delete(posting, user)
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 댓글 ---

    /** 게시글의 댓글 목록이다. 오래된 순서이고 한 줄 댓글은 `childComments`로 묶인다. 읽을 수 있어야 한다. 결과: `{"result": [...]}`. */
    @GetMapping("$PROJECT/posts/{number:[0-9]+}/comments")
    @Transactional(readOnly = true)
    fun listComments(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.READ)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        return ok(linkedMapOf("result" to issueJson.commentsOf(posting)))
    }

    /** 댓글을 고친다. 본문 `{"body": 내용}`이다. 구성원은 남의 댓글도 고치고(D-48) 내용이 비면 400이다. 결과: `{"result": {"id", "body", ...}}`. */
    @PatchMapping("$PROJECT/posts/{number:[0-9]+}/comments/{commentId}")
    @Transactional
    fun editComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = postingComments.findById(commentId).orElse(null)?.takeIf { it.posting?.id == posting.id } ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, comment.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val text = json.get("body")?.asString("")?.takeIf { it.isNotBlank() } ?: return requests.message(HttpStatus.BAD_REQUEST, "body is required")
        commentService.update(comment, user, text, false)
        val node = linkedMapOf<String, Any?>(
            "id" to comment.id, "body" to comment.contents, "createdAt" to issueJson.iso(comment.createdDate),
            "author" to linkedMapOf("loginId" to comment.authorLoginId, "name" to comment.authorName)
        )
        return ok(linkedMapOf("result" to node))
    }

    /** 댓글을 지운다. 작성자와 프로젝트 관리자만 지우고(D-48) 글의 댓글 수가 줄어든다. 성공하면 204이다. */
    @DeleteMapping("$PROJECT/posts/{number:[0-9]+}/comments/{commentId}")
    @Transactional
    fun deleteComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        val comment = postingComments.findById(commentId).orElse(null)?.takeIf { it.posting?.id == posting.id } ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, comment.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        commentService.delete(comment)
        return ResponseEntity.noContent().build<Void>()
    }

    // --- 라벨 ---

    /**
     * 게시글에 라벨을 더한다. 본문 `{"labels": [라벨 번호]}`이다. 이미 붙은 라벨은 그대로 두고, 이 프로젝트의 것이 아니거나 같은 분류(배타)의 라벨이 겹치면 400이다.
     * 게시글을 고칠 수 있어야 한다. 결과: `{"result": {...}}`.
     */
    @PostMapping("$PROJECT/posts/{number:[0-9]+}/labels")
    @Transactional
    fun addLabels(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val json = requests.json(request) ?: return requests.message(HttpStatus.BAD_REQUEST, "Expecting Json data")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        val node = json.get("labels")
        if (node == null || !node.isArray || node.size() == 0 || node.toList().any { !it.isNumber }) return requests.message(HttpStatus.BAD_REQUEST, "labels must be a non-empty list of label numbers")
        val added = node.toList().map { it.asLong() }.toSet()
        val project = requireNotNull(posting.project)
        val found = labelService.findByIds(project, added)
        if (found.size != added.size) return requests.message(HttpStatus.BAD_REQUEST, "no label by request")
        val union = posting.labels.mapNotNull { it.id }.toSet() + added
        if (issueService.violatesExclusiveCategory(union)) return requests.message(HttpStatus.BAD_REQUEST, "labels of an exclusive category cannot be combined")
        postingService.setLabels(posting, posting.labels.toSet() + found)
        return ok(linkedMapOf("result" to issueJson.postingDetail(posting)))
    }

    /** 게시글에서 라벨 하나를 뗀다. 붙어 있지 않아도 그대로 성공한다. 게시글을 고칠 수 있어야 한다. 결과: `{"result": {...}}`. */
    @DeleteMapping("$PROJECT/posts/{number:[0-9]+}/labels/{labelId}")
    @Transactional
    fun removeLabel(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable labelId: Long, request: HttpServletRequest): Any {
        val user = requests.tokenUser(request) ?: return requests.message(HttpStatus.UNAUTHORIZED, "unauthorized request")
        val posting = find(owner, projectName, number) ?: return ErrorViews.notFound()
        if (!accessControl.isAllowed(user, posting.asResource(), Operation.UPDATE)) return requests.message(HttpStatus.FORBIDDEN, "Forbidden request")
        postingService.setLabels(posting, posting.labels.filter { it.id != labelId }.toSet())
        return ok(linkedMapOf("result" to issueJson.postingDetail(posting)))
    }

    private fun find(owner: String, projectName: String, number: Long): Posting? {
        val project = projectService.findByOwnerAndProjectName(owner, projectName) ?: return null
        return postings.findByProjectIdAndNumber(requireNotNull(project.id), number)
    }

    private fun ok(body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> = ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body)

    private companion object {
        const val PROJECT = "/-_-api/v1/owners/{owner}/projects/{projectName}"
        const val DEFAULT_PAGE_SIZE = 15
        const val MAX_PAGE_SIZE = 100
    }
}
