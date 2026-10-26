package dev.yona.domain.comment

import org.springframework.data.jpa.repository.JpaRepository

/** 코드 리뷰 스레드의 댓글 저장소이다. */
interface ReviewCommentRepository : JpaRepository<ReviewComment, Long> {

    /** 스레드에 달린 댓글 수이다 (v1.6 `ReviewComment.findByThread(...).isEmpty()`). */
    fun countByThreadId(threadId: Long): Long

    /** 스레드의 첫 댓글이다. 작성 시각이 가장 이른 댓글이다 (v1.6 `CommentThread.getFirstReviewComment`). */
    fun findFirstByThreadIdOrderByCreatedDateAscIdAsc(threadId: Long): ReviewComment?
}
