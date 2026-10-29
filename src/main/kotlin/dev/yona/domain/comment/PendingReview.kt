package dev.yona.domain.comment

import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.AttachmentService
import dev.yona.domain.pullrequest.PullRequest
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.util.Date

/**
 * 임시 저장 리뷰의 임시 스레드와 임시 댓글을 읽는 유일한 경로이다(추가 개발 항목 X-26, `decisions.md` D-38). 엔티티에는 `pending_date is null` 조건이 항상 붙어
 * 임시 행이 어떤 읽기에도 보이지 않으므로, 작성자 본인의 임시 행은 이 클래스의 SQL로만 읽는다. 돌려주는 엔티티는 조건 없이 읽은 것이라 연관 컬렉션
 * (`reviewComments`)이 아니라 [threadComments]로 댓글을 읽어야 한다. 읽은 엔티티는 영속성 컨텍스트에 올라가며, 그 안에서는 아이디 조회(`find`)가
 * 숨김 조건 없이 캐시를 돌려준다. 요청마다 영속성 컨텍스트가 따로이고 이 경로는 작성자 본인의 요청에서만 쓰므로 다른 사용자에게 보이지는 않는다.
 */
@Service
class PendingReviewReader {

    @PersistenceContext
    private lateinit var em: EntityManager

    /** [author]가 [pullRequest]에 쌓아 둔 임시 댓글 수이다(임시 스레드의 댓글과 공개 스레드의 임시 답글을 모두 센다). */
    @Transactional(readOnly = true)
    fun count(pullRequest: PullRequest, author: User): Long =
        (em.createNativeQuery(
            "select count(*) from review_comment c join comment_thread t on t.id = c.thread_id " +
                "where t.pull_request_id = :pr and c.author_id = :author and c.pending_date is not null"
        ).setParameter("pr", pullRequest.id).setParameter("author", author.id).singleResult as Number).toLong()

    /** [author]의 임시 스레드(공개 스레드가 아니라 임시 댓글로 새로 시작한 스레드)를 만든 순서로 읽는다. */
    @Transactional(readOnly = true)
    fun threads(pullRequest: PullRequest, author: User): List<CommentThread> =
        em.createNativeQuery(
            "select * from comment_thread where pull_request_id = :pr and author_id = :author and pending_date is not null order by id", CommentThread::class.java
        ).setParameter("pr", pullRequest.id).setParameter("author", author.id).resultList.map { it as CommentThread }

    /** [author]가 공개 스레드에 단 임시 답글을 읽는다. */
    @Transactional(readOnly = true)
    fun replies(pullRequest: PullRequest, author: User): List<ReviewComment> =
        em.createNativeQuery(
            "select c.* from review_comment c join comment_thread t on t.id = c.thread_id " +
                "where t.pull_request_id = :pr and t.pending_date is null and c.author_id = :author and c.pending_date is not null order by c.id", ReviewComment::class.java
        ).setParameter("pr", pullRequest.id).setParameter("author", author.id).resultList.map { it as ReviewComment }

    /** [thread] 안의 임시 댓글이다(임시 스레드의 모든 댓글과 공개 스레드에 단 임시 답글). */
    @Transactional(readOnly = true)
    fun threadComments(thread: CommentThread): List<ReviewComment> =
        em.createNativeQuery("select * from review_comment where thread_id = :thread and pending_date is not null order by created_date, id", ReviewComment::class.java)
            .setParameter("thread", thread.id).resultList.map { it as ReviewComment }

    /** [author]의 임시 스레드 하나이다. 없거나 다른 사람의 것이면 `null`이다. */
    @Transactional(readOnly = true)
    fun thread(id: Long, pullRequest: PullRequest, author: User): CommentThread? =
        em.createNativeQuery(
            "select * from comment_thread where id = :id and pull_request_id = :pr and author_id = :author and pending_date is not null", CommentThread::class.java
        ).setParameter("id", id).setParameter("pr", pullRequest.id).setParameter("author", author.id).resultList.firstOrNull() as CommentThread?

    /** [author]의 임시 댓글 하나이다. 없거나 남의 것이거나 이미 공개된 댓글이면 `null`이다. */
    @Transactional(readOnly = true)
    fun comment(id: Long, author: User): ReviewComment? {
        // 댓글이 임시 스레드에 속하면 스레드가 숨겨져 있어 댓글을 읽을 때 연관을 찾지 못한다. 스레드를 먼저 읽어 영속성 컨텍스트에 올려 둔다
        em.createNativeQuery(
            "select t.* from comment_thread t join review_comment c on c.thread_id = t.id where c.id = :id and c.author_id = :author and c.pending_date is not null", CommentThread::class.java
        ).setParameter("id", id).setParameter("author", author.id).resultList
        return em.createNativeQuery("select * from review_comment where id = :id and author_id = :author and pending_date is not null", ReviewComment::class.java)
            .setParameter("id", id).setParameter("author", author.id).resultList.firstOrNull() as ReviewComment?
    }

    /** [id]가 (누구의 것이든) 아직 제출하지 않은 임시 스레드이면 `true`이다. */
    @Transactional(readOnly = true)
    fun isPendingThread(id: Long): Boolean =
        (em.createNativeQuery("select count(*) from comment_thread where id = :id and pending_date is not null").setParameter("id", id).singleResult as Number).toLong() > 0
}

/**
 * 임시 저장 리뷰의 줄 댓글을 지우고, 버리고, 제출(공개)한다. 권한 확인은 호출하는 쪽이 한다. 모두 엔티티의 숨김 조건을 타지 않는 SQL이며 행 수와 관계없이
 * 정해진 개수의 문장으로 끝난다(`decisions.md` D-38: 동시 제출이 몰려도 쓰기가 행마다 늘지 않게 한다).
 *
 * 한 사람이 한 풀 리퀘스트에 가지는 임시 리뷰는 하나이며, 그 사람이 그 풀 리퀘스트에 임시로 단 댓글 전체이다.
 */
@Service
class PendingReviewService(private val attachments: AttachmentService) {

    @PersistenceContext
    private lateinit var em: EntityManager

    /**
     * [author]의 임시 댓글 [commentId] 하나를 지운다. 공개 댓글이나 남의 댓글이나 다른 풀 리퀘스트의 댓글이면 `false`이다.
     * 임시 스레드의 마지막 댓글을 지우면 스레드도 지운다.
     */
    @Transactional
    fun deleteComment(pullRequest: PullRequest, author: User, commentId: Long): Boolean {
        em.flush()
        val row = em.createNativeQuery(
            "select c.thread_id, t.pending_date from review_comment c join comment_thread t on t.id = c.thread_id " +
                "where c.id = :id and c.author_id = :author and c.pending_date is not null and t.pull_request_id = :pr"
        ).setParameter("id", commentId).setParameter("author", author.id).setParameter("pr", pullRequest.id).resultList.firstOrNull() as Array<*>? ?: return false
        val threadId = (row[0] as Number).toLong()
        attachments.deleteAll(ResourceType.REVIEW_COMMENT, commentId)
        em.createNativeQuery("delete from review_comment where id = :id").setParameter("id", commentId).executeUpdate()
        val remaining = (em.createNativeQuery("select count(*) from review_comment where thread_id = :t").setParameter("t", threadId).singleResult as Number).toLong()
        if (row[1] != null && remaining == 0L) {
            em.createNativeQuery("delete from comment_thread_n4user where comment_thread_id = :t").setParameter("t", threadId).executeUpdate()
            em.createNativeQuery("delete from comment_thread where id = :t").setParameter("t", threadId).executeUpdate()
        }
        return true
    }

    /** [author]의 임시 댓글과 임시 스레드를 모두 지운다(리뷰 버리기). 지운 댓글 수를 돌려준다. */
    @Transactional
    fun discard(pullRequest: PullRequest, author: User): Int {
        em.flush()
        val ids = pendingCommentIds(pullRequest, author)
        if (ids.isEmpty()) return 0
        ids.forEach { attachments.deleteAll(ResourceType.REVIEW_COMMENT, it) }
        em.createNativeQuery("delete from review_comment where id in (:ids)").setParameter("ids", ids).executeUpdate()
        val threadIds = em.createNativeQuery("select id from comment_thread where pull_request_id = :pr and author_id = :author and pending_date is not null")
            .setParameter("pr", pullRequest.id).setParameter("author", author.id).resultList.map { (it as Number).toLong() }
        if (threadIds.isNotEmpty()) {
            em.createNativeQuery("delete from comment_thread_n4user where comment_thread_id in (:ids)").setParameter("ids", threadIds).executeUpdate()
            em.createNativeQuery("delete from comment_thread where id in (:ids)").setParameter("ids", threadIds).executeUpdate()
        }
        return ids.size
    }

    /**
     * [author]의 임시 댓글을 모두 공개한다(리뷰 제출). 공개된 댓글의 번호를 만든 순서로 돌려준다. 공개 스레드에 단 답글은 작성 시각을 제출 시각으로 바꿔
     * 그 스레드의 맨 뒤에 붙인다. 알림은 호출하는 쪽(`PullRequestReviewService.submit`)이 리뷰 하나로 묶어 보낸다.
     */
    @Transactional
    fun publish(pullRequest: PullRequest, author: User): List<Long> {
        em.flush()
        val ids = pendingCommentIds(pullRequest, author)
        if (ids.isEmpty()) return emptyList()
        val now = Timestamp(Date().time)
        em.createNativeQuery(
            "update review_comment set pending_date = null, created_date = :now where pending_date is not null and author_id = :author " +
                "and thread_id in (select id from comment_thread where pull_request_id = :pr and pending_date is null)"
        ).setParameter("now", now).setParameter("author", author.id).setParameter("pr", pullRequest.id).executeUpdate()
        em.createNativeQuery("update comment_thread set pending_date = null where pull_request_id = :pr and author_id = :author and pending_date is not null")
            .setParameter("pr", pullRequest.id).setParameter("author", author.id).executeUpdate()
        em.createNativeQuery(
            "update review_comment set pending_date = null where pending_date is not null and author_id = :author " +
                "and thread_id in (select id from comment_thread where pull_request_id = :pr)"
        ).setParameter("author", author.id).setParameter("pr", pullRequest.id).executeUpdate()
        return ids
    }

    private fun pendingCommentIds(pullRequest: PullRequest, author: User): List<Long> =
        em.createNativeQuery(
            "select c.id from review_comment c join comment_thread t on t.id = c.thread_id " +
                "where t.pull_request_id = :pr and c.author_id = :author and c.pending_date is not null order by c.id"
        ).setParameter("pr", pullRequest.id).setParameter("author", author.id).resultList.map { (it as Number).toLong() }
}
