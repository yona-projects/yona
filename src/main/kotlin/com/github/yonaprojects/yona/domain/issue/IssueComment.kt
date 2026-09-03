package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.support.Comment
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "issue_comment")
class IssueComment(
    id: Long? = null,
    contents: String = "",
    createdDate: Instant? = null,
    authorId: Long? = null,
    authorLoginId: String? = null,
    authorName: String? = null,
    projectId: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id", nullable = false)
    var issue: Issue,

    // yona IssueComment.java:47-51 parentComment 대응. legacy는 @OneToOne으로 선언돼 있지만 Ebean은
    // 이를 DB 유니크 제약으로 강제하지 않아 실제로는 여러 댓글이 같은 parentComment.id를 공유할 수
    // 있었다(같은 파일의 형제 댓글 조회 쿼리 eq("parentComment.id", parentComment.id)가 이를 전제).
    // Hibernate/JPA에서 @OneToOne을 그대로 쓰면 parent_comment_id에 실제 유니크 제약이 생성되어
    // "한 부모에 답글 2개"가 DB 레벨에서 막혀 legacy의 실제 동작과 달라지므로 @ManyToOne으로 이식한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    var parentComment: IssueComment? = null,

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "issue_comment_voter",
        joinColumns = [JoinColumn(name = "issue_comment_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var voters: MutableSet<User> = mutableSetOf()
) : Comment(
    id = id,
    contents = contents,
    createdDate = createdDate,
    authorId = authorId,
    authorLoginId = authorLoginId,
    authorName = authorName,
    projectId = projectId
)
