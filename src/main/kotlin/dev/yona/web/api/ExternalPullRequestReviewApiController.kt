package dev.yona.web.api

import dev.yona.access.AccessControl
import dev.yona.access.ProjectResource
import dev.yona.access.asResource
import dev.yona.domain.comment.CodeRange
import dev.yona.domain.comment.CommentThreadRepository
import dev.yona.domain.comment.PendingReviewReader
import dev.yona.domain.comment.PendingReviewService
import dev.yona.domain.comment.ReviewCommentRepository
import dev.yona.domain.comment.ReviewCommentService
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.pullrequest.DismissResult
import dev.yona.domain.pullrequest.PullRequestReviewRepository
import dev.yona.domain.pullrequest.PullRequestReviewService
import dev.yona.domain.pullrequest.ReviewResult
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import tools.jackson.databind.JsonNode

/**
 * 외부용 풀 리퀘스트 REST API의 리뷰이다(로드맵 X-15, `decisions.md` D-39). 리뷰 제출과 해제, 줄 댓글(임시 저장 포함) 달기와 고치기와 지우기, 임시 리뷰 버리기,
 * 스레드 열기와 닫기를 화면과 같은 서비스와 권한으로 노출한다. 임시 댓글은 호출한 사용자 본인에게만 보이고 리뷰를 제출하면 한꺼번에 공개된다(`decisions.md` D-38).
 */
@Controller
@AnonymousCheck
@ResponseBody
class ExternalPullRequestReviewApiController(
    private val support: PullRequestApiSupport,
    private val reviewService: PullRequestReviewService,
    private val reviewRepository: PullRequestReviewRepository,
    private val comments: ReviewCommentService,
    private val commentRepository: ReviewCommentRepository,
    private val threads: CommentThreadRepository,
    private val pendingReader: PendingReviewReader,
    private val pendingReview: PendingReviewService,
    private val accessControl: AccessControl,
    private val requests: ApiRequests,
    private val json: PullRequestJson,
    private val attachments: dev.yona.web.PostAttachments
) {

    private companion object {
        const val BASE = "/-_-api/v1/owners/{owner}/projects/{projectName}/pullrequests/{number}"
    }

    /**
     * 리뷰를 제출한다. 본문 `{"verdict": "APPROVED|CHANGES_REQUESTED|COMMENTED", "body": 요약}`이고 응답은 201 `{"review": {...}, "publishedComments": 개수}`이다.
     * 내 임시 줄 댓글이 있으면 함께 공개되며(그러면 `body`는 생략할 수 있다) 알림은 리뷰 하나로 나간다. 모르는 판정, 빠진 본문, 닫힌 요청의 승인과 변경 요청은 400,
     * 승인과 변경 요청에 받아들일 권한이 없거나 작성자이면 403이다.
     */
    @PostMapping("$BASE/reviews")
    @Transactional
    fun submit(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        val verdict = body.get("verdict")?.asString()?.let { raw -> ReviewVerdict.entries.firstOrNull { it.name == raw } } ?: return support.badRequest("verdict must be APPROVED, CHANGES_REQUESTED or COMMENTED")
        val pullRequest = requireNotNull(target.pullRequest)
        val viewer = requireNotNull(target.user)
        val resource = ProjectResource(requireNotNull(pullRequest.id).toString(), ResourceType.PULL_REQUEST, requireNotNull(target.project), authorId = pullRequest.contributor?.id)
        if (verdict != ReviewVerdict.COMMENTED && !accessControl.isAllowed(viewer, resource, Operation.ACCEPT)) return requests.message(HttpStatus.FORBIDDEN, "forbidden")
        val pendingCount = pendingReader.count(pullRequest, viewer)
        return when (reviewService.submit(pullRequest, viewer, verdict, body.get("body")?.asString())) {
            ReviewResult.SUBMITTED -> {
                val review = pullRequest.reviewHistory.filter { it.authorId == viewer.id }.maxWithOrNull(compareBy({ it.createdDate }, { it.id }))
                ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("review" to review?.let { json.review(it) }, "publishedComments" to pendingCount))
            }
            ReviewResult.AUTHOR_NOT_ALLOWED -> requests.message(HttpStatus.FORBIDDEN, "the author cannot approve or request changes on their own pull request")
            ReviewResult.NOT_OPEN -> support.badRequest("pull request is not open")
            ReviewResult.BODY_REQUIRED -> support.badRequest("body is required")
        }
    }

    /** 리뷰를 해제한다. 본문 `{"message": 사유}`(필수)이고 받아들일 권한이 필요하다. 없는 리뷰는 404, 사유가 없거나 이미 해제했으면 400이다. 응답은 `{"review": {...}}`이다. */
    @PostMapping("$BASE/reviews/{reviewId}/dismissals")
    @Transactional
    fun dismiss(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable reviewId: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request, Operation.ACCEPT)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val review = reviewRepository.findById(reviewId).orElse(null) ?: return requests.message(HttpStatus.NOT_FOUND, "no review by request")
        return when (reviewService.dismiss(pullRequest, review, requireNotNull(target.user), requests.json(request)?.get("message")?.asString())) {
            DismissResult.DISMISSED -> support.ok(linkedMapOf("review" to json.review(review)))
            DismissResult.NOT_FOUND -> requests.message(HttpStatus.NOT_FOUND, "no review by request")
            DismissResult.ALREADY_DISMISSED -> support.badRequest("review is already dismissed")
            DismissResult.MESSAGE_REQUIRED -> support.badRequest("message is required")
        }
    }

    /**
     * 줄 댓글을 단다. 본문 `{"body", "path", "startSide", "startLine", "startColumn", "endSide", "endLine", "endColumn", "commitId", "threadId", "pending", "temporaryUploadFiles"}`이다(첨부는 `/-_-api/v1/files`로 먼저 올린 번호).
     * `threadId`가 있으면 그 스레드에 이어 달고, 없으면 새 스레드를 만든다(줄 범위를 모두 주면 줄 스레드, 아니면 변경 전체 스레드). `pending: true`는 "리뷰 시작/리뷰에 추가"이며
     * 내 임시 스레드의 답글은 항상 임시이다. 응답은 201 `{"comment": {...}, "threadId": 번호}`이다.
     */
    @PostMapping("$BASE/comments")
    @Transactional
    fun comment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val project = requireNotNull(target.project)
        val viewer = requireNotNull(target.user)
        if (!accessControl.isProjectResourceCreatable(viewer, project, ResourceType.REVIEW_COMMENT)) return requests.message(HttpStatus.FORBIDDEN, "forbidden")
        val body = requests.json(request) ?: return support.badRequest("Expecting Json data")
        val text = body.get("body")?.asString()?.takeIf { it.isNotBlank() } ?: return support.badRequest("body is required")
        val pullRequest = requireNotNull(target.pullRequest)
        val threadId = body.get("threadId")?.asLong(0)?.takeIf { it > 0 }
        if (threadId != null && threads.findById(threadId).orElse(null)?.pullRequest?.id != pullRequest.id && pendingReader.thread(threadId, pullRequest, viewer) == null) {
            return requests.message(HttpStatus.NOT_FOUND, "no thread by request")
        }
        val comment = try {
            comments.commentOnPullRequest(project, pullRequest, body.get("commitId")?.asString()?.takeIf { it.isNotEmpty() }, viewer, text, threadId, range(body), pending = body.get("pending")?.asBoolean(false) == true)
        } catch (e: IllegalArgumentException) {
            return requests.message(HttpStatus.NOT_FOUND, "no thread by request")
        }
        attachments.attachIds(viewer, body.get("temporaryUploadFiles")?.takeIf { it.isArray }?.toList().orEmpty().map { it.asString() }.filter { it.isNotBlank() }, ResourceType.REVIEW_COMMENT, requireNotNull(comment.id))
        return ResponseEntity.status(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(linkedMapOf("comment" to json.comment(comment), "threadId" to comment.thread?.id))
    }

    /** 댓글을 고친다. 본문 `{"body"}`이다. 공개 댓글은 작성자와 지울 권한이 있는 사람이, 임시 댓글은 작성자만 고친다(남에게는 404). 응답은 `{"comment": {...}}`이다. */
    @PatchMapping("$BASE/comments/{commentId}")
    @Transactional
    fun editComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val viewer = requireNotNull(target.user)
        val pullRequest = requireNotNull(target.pullRequest)
        val comment = findComment(commentId, pullRequest.id, viewer) ?: return requests.message(HttpStatus.NOT_FOUND, "no comment by request")
        if (!comment.isPending && !accessControl.isAllowed(viewer, comment.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "forbidden")
        val text = requests.json(request)?.get("body")?.asString()?.takeIf { it.isNotBlank() } ?: return support.badRequest("body is required")
        return support.ok(linkedMapOf("comment" to json.comment(comments.edit(comment, text))))
    }

    /** 댓글을 지운다. 공개 댓글은 작성자와 지울 권한이 있는 사람이, 임시 댓글은 작성자만 지운다(남에게는 404). 스레드의 마지막 댓글이면 스레드도 지운다. */
    @DeleteMapping("$BASE/comments/{commentId}")
    @Transactional
    fun deleteComment(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable commentId: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val viewer = requireNotNull(target.user)
        val pullRequest = requireNotNull(target.pullRequest)
        val comment = findComment(commentId, pullRequest.id, viewer) ?: return requests.message(HttpStatus.NOT_FOUND, "no comment by request")
        if (comment.isPending) {
            pendingReview.deleteComment(pullRequest, viewer, commentId)
        } else {
            if (!accessControl.isAllowed(viewer, comment.asResource(), Operation.DELETE)) return requests.message(HttpStatus.FORBIDDEN, "forbidden")
            comments.delete(comment)
        }
        return support.ok(linkedMapOf("deleted" to true))
    }

    /** 내 임시 리뷰(임시 댓글과 임시 스레드 전체)를 버린다. 응답은 `{"discarded": 지운 댓글 수}`이다. */
    @DeleteMapping("$BASE/pendingreview")
    @Transactional
    fun discard(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        return support.ok(linkedMapOf("discarded" to pendingReview.discard(requireNotNull(target.pullRequest), requireNotNull(target.user))))
    }

    /** 스레드를 닫는다(`close`). 닫을 권한은 화면과 같다. 이 풀 리퀘스트의 스레드가 아니면 404이다. 응답은 `{"thread": {...}}`이다. */
    @PostMapping("$BASE/threads/{threadId}/close")
    @Transactional
    fun closeThread(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable threadId: Long, request: HttpServletRequest): Any =
        changeThread(owner, projectName, number, threadId, ThreadState.CLOSED, request)

    /** 닫힌 스레드를 다시 연다. */
    @PostMapping("$BASE/threads/{threadId}/open")
    @Transactional
    fun openThread(@PathVariable owner: String, @PathVariable projectName: String, @PathVariable number: Long, @PathVariable threadId: Long, request: HttpServletRequest): Any =
        changeThread(owner, projectName, number, threadId, ThreadState.OPEN, request)

    private fun changeThread(owner: String, projectName: String, number: Long, threadId: Long, state: ThreadState, request: HttpServletRequest): Any {
        val target = support.target(owner, projectName, number, request)
        target.error?.let { return it }
        val pullRequest = requireNotNull(target.pullRequest)
        val thread = threads.findById(threadId).orElse(null)?.takeIf { it.pullRequest?.id == pullRequest.id } ?: return requests.message(HttpStatus.NOT_FOUND, "no thread by request")
        val operation = if (state == ThreadState.OPEN) Operation.REOPEN else Operation.CLOSE
        if (!accessControl.isAllowed(requireNotNull(target.user), thread.asResource(), operation)) return requests.message(HttpStatus.FORBIDDEN, "forbidden")
        comments.updateState(thread, state, requireNotNull(target.user))
        return support.ok(linkedMapOf("thread" to json.thread(thread, thread.reviewComments)))
    }

    /** 이 풀 리퀘스트의 댓글이다. 공개 댓글이거나 내 임시 댓글이고, 다른 풀 리퀘스트의 댓글이거나 남의 임시 댓글이면 `null`이다. */
    private fun findComment(id: Long, pullRequestId: Long?, viewer: dev.yona.domain.user.User): dev.yona.domain.comment.ReviewComment? {
        commentRepository.findById(id).orElse(null)?.let { return it.takeIf { c -> c.thread?.pullRequest?.id == pullRequestId } }
        return pendingReader.comment(id, viewer)?.takeIf { it.thread?.pullRequest?.id == pullRequestId }
    }

    /** 줄 범위이다. 시작과 끝의 줄과 열이 모두 숫자로 있어야 범위이고 아니면 `null`(변경 전체)이다. */
    private fun range(body: JsonNode): CodeRange? {
        fun number(key: String) = body.get(key)?.takeIf { it.isNumber }?.asInt()
        fun side(key: String) = body.get(key)?.asString()?.let { value -> Side.entries.firstOrNull { it.name == value } }
        return CodeRange(body.get("path")?.asString(), side("startSide"), number("startLine") ?: return null, number("startColumn") ?: return null, side("endSide"), number("endLine") ?: return null, number("endColumn") ?: return null)
    }
}
