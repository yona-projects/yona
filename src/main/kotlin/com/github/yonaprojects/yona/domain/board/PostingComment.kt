package com.github.yonaprojects.yona.domain.board

import com.github.yonaprojects.yona.domain.support.Comment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "posting_comment")
class PostingComment(
    id: Long? = null,
    contents: String = "",
    createdDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    projectId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    var posting: Posting,

    // yona PostingComment.java:27-28 parentComment 대응. IssueComment.kt와 동일한 사유로
    // (legacy Ebean @OneToOne은 DB 유니크 제약을 강제하지 않아 형제 댓글이 실제로 존재할 수 있었음)
    // Hibernate에서 legacy 동작과 동등하게 만들기 위해 @ManyToOne으로 이식한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    var parentComment: PostingComment? = null
) : Comment(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    projectId = projectId
)
