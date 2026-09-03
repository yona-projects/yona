package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.user.UserIdent
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "review_comment")
class ReviewComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(length = 1_000_000, nullable = false)
    var contents: String = "",

    var createdDate: Instant = Instant.now(),

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "author_id")),
        AttributeOverride(name = "loginId", column = Column(name = "author_login_id")),
        AttributeOverride(name = "name", column = Column(name = "author_name"))
    )
    var author: UserIdent? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_id")
    var thread: CommentThread? = null
)
