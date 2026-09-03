package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.UserIdent
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "commit_comment")
class CommitComment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    var path: String? = null,
    var line: Int? = null,

    @Enumerated(EnumType.STRING)
    var side: CodeRange.Side? = null,

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

    var commitId: String = ""
) {
    fun hasLocation(): Boolean = !path.isNullOrBlank() && line != null
}
