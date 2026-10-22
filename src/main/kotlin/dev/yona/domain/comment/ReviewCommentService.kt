package dev.yona.domain.comment

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.Side
import dev.yona.domain.enumeration.ThreadState
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.project.Project
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import dev.yona.repository.CommitHistoryService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 코드 리뷰 댓글이 달린 코드 범위이다. 시작과 끝의 줄, 열은 모두 있어야 범위로 본다 (v1.6 `CodeRange`의 `@Required`).
 * 면(`Side`)은 없어도 된다.
 */
data class CodeRange(
    val path: String?, val startSide: Side?, val startLine: Int, val startColumn: Int, val endSide: Side?, val endLine: Int, val endColumn: Int
)

/** 코드 리뷰 댓글을 새로 달았음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewCommitComment`, `afterNewComment`를 부르던 지점에서 발행한다. */
data class ReviewCommentedActivity(val comment: ReviewComment, val pullRequest: PullRequest?, val commitId: String?, val actor: User)

/** 코드 리뷰 스레드의 상태가 바뀌었음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterStateChanged(ThreadState, CommentThread)`. */
data class ReviewThreadStateActivity(val thread: CommentThread, val previous: ThreadState, val actor: User)

/**
 * 코드 리뷰 댓글과 스레드의 생성, 상태 변경, 삭제를 담당한다. v1.6 `CodeHistoryApp.newComment`, `PullRequestApp.newComment`,
 * `CommentThreadApp.updateState`, `CommentApp.delete`와 `ReviewComment.delete`에 해당한다. 권한 확인은 호출하는 쪽이 한다.
 *
 * 스레드를 지정하지 않으면 새 스레드를 만든다. 코드 범위가 있으면 범위 스레드, 없으면 파일 단위 스레드이다. 스레드는 열린 상태로 만들며
 * 커밋 작성자가 알려진 사용자이면 코드 작성자(`codeAuthors`)에 넣는다. 스레드의 마지막 댓글을 지우면 스레드도 지운다.
 */
@Service
class ReviewCommentService(
    private val threads: CommentThreadRepository,
    private val reviewComments: ReviewCommentRepository,
    private val commitComments: CommitCommentRepository,
    private val history: CommitHistoryService,
    private val userLookup: UserLookup,
    private val attachments: AttachmentService,
    private val pullRequests: PullRequestRepository,
    private val publisher: ApplicationEventPublisher
) {

    /**
     * 커밋에 코드 리뷰 댓글을 단다. [threadId]가 있으면 그 스레드에 이어 달고, 없으면 새 스레드(`prevCommitId`는 `null`)를 만든다.
     * 첨부 중 작성자의 임시 첨부를 모두 댓글로 옮긴다. 새 코드 리뷰 댓글 알림을 내보낸다.
     */
    @Transactional
    fun commentOnCommit(project: Project, commitId: String, author: User, contents: String, threadId: Long?, range: CodeRange?): ReviewComment {
        val comment = newComment(author, contents)
        comment.thread = threadId?.let { threads.findById(it).orElse(null) } ?: newThread(project, commitId, null, range, author, comment.createdDate)
        return save(comment, author).also { publisher.publishEvent(ReviewCommentedActivity(it, null, commitId, author)) }
    }

    /**
     * 풀 리퀘스트에 코드 리뷰 댓글을 단다. [commitId]가 없으면 풀 리퀘스트 전체 변경(병합 기준 커밋 사이)에 단 것이고, 있으면 그 커밋 하나에 단 것이다.
     * 새 스레드는 풀 리퀘스트에 연결한다. 새 코드 리뷰 댓글 알림을 내보낸다.
     */
    @Transactional
    fun commentOnPullRequest(project: Project, pullRequest: PullRequest, commitId: String?, author: User, contents: String, threadId: Long?, range: CodeRange?): ReviewComment {
        val comment = newComment(author, contents)
        val existing = threadId?.let { threads.findById(it).orElse(null) }
        comment.thread = existing ?: run {
            val targetCommit = commitId ?: pullRequest.mergedCommitIdTo
            val previous = if (commitId != null) "" else pullRequest.mergedCommitIdFrom
            newThread(project, requireNotNull(targetCommit), previous, range, author, comment.createdDate).also { it.pullRequest = pullRequest }
        }
        return save(comment, author).also { publisher.publishEvent(ReviewCommentedActivity(it, pullRequest, commitId, author)) }
    }

    /**
     * 스레드를 열거나 닫는다. 바뀐 상태가 같아도 v1.6처럼 알림을 내보낸다.
     */
    @Transactional
    fun updateState(thread: CommentThread, state: ThreadState, actor: User) {
        val previous = thread.state ?: ThreadState.OPEN
        thread.state = state
        threads.save(thread)
        try {
            publisher.publishEvent(ReviewThreadStateActivity(thread, previous, actor))
        } catch (e: Exception) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("Failed to send a notification for a change of review thread's state", e)
        }
    }

    /** 코드 리뷰 댓글을 지운다. 스레드의 마지막 댓글이면 스레드도 지운다 (v1.6 `ReviewComment.delete`). */
    @Transactional
    fun delete(comment: ReviewComment) {
        val thread = comment.thread
        thread?.reviewComments?.remove(comment)
        reviewComments.delete(comment)
        reviewComments.flush()
        if (thread != null && reviewComments.countByThreadId(requireNotNull(thread.id)) == 0L) {
            thread.pullRequest = null
            threads.delete(thread)
        }
    }

    /** 커밋 코멘트(Subversion 프로젝트의 커밋 댓글)를 지운다. */
    @Transactional
    fun deleteCommitComment(id: Long) {
        commitComments.deleteById(id)
    }

    private fun newComment(author: User, contents: String) = ReviewComment(
        contents = contents, createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name
    )

    private fun save(comment: ReviewComment, author: User): ReviewComment {
        val thread = requireNotNull(comment.thread)
        if (thread.id == null) threads.save(thread)
        val saved = reviewComments.save(comment)
        thread.reviewComments.add(saved)
        attachments.moveAll(ResourceType.USER, requireNotNull(author.id), ResourceType.REVIEW_COMMENT, requireNotNull(saved.id))
        return saved
    }

    private fun newThread(project: Project, commitId: String, previous: String?, range: CodeRange?, author: User, created: Date?): CommentThread {
        val thread: CommentThread = if (range != null) {
            CodeCommentThread(
                prevCommitId = previous, commitId = commitId, path = range.path, startSide = range.startSide, startLine = range.startLine, startColumn = range.startColumn,
                endSide = range.endSide, endLine = range.endLine, endColumn = range.endColumn
            ).also { code ->
                history.commit(project, commitId)?.let { userLookup.findByEmail(it.authorEmail) }?.let { code.codeAuthors.add(it) }
            }
        } else {
            NonRangedCodeCommentThread(prevCommitId = previous, commitId = commitId)
        }
        thread.project = project
        thread.state = ThreadState.OPEN
        thread.createdDate = created
        thread.authorId = author.id
        thread.authorLoginId = author.loginId
        thread.authorName = author.name
        return thread
    }
}
