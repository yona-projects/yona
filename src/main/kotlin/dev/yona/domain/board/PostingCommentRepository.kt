package dev.yona.domain.board

import org.springframework.data.jpa.repository.JpaRepository

/** 게시글 댓글 저장소이다. */
interface PostingCommentRepository : JpaRepository<PostingComment, Long> {

    /** 게시글의 댓글을 작성 시각 순으로 조회한다 (v1.6 `Posting.getComments`, `Comment.comparator`). */
    fun findByPostingIdOrderByCreatedDateAscIdAsc(postingId: Long): List<PostingComment>

    /** 게시글의 댓글 수이다. */
    fun countByPostingId(postingId: Long): Long

    /** 댓글에 달린 한 줄 댓글을 조회한다 (v1.6 `PostingComment.getChildComments`). */
    fun findByParentCommentIdOrderByIdAsc(parentCommentId: Long): List<PostingComment>
}
