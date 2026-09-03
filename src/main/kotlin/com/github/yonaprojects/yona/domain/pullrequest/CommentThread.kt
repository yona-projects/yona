package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.UserIdent
import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "comment_thread")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
abstract class CommentThread(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Embedded
    @AttributeOverrides(
        AttributeOverride(name = "id", column = Column(name = "author_id")),
        AttributeOverride(name = "loginId", column = Column(name = "author_login_id")),
        AttributeOverride(name = "name", column = Column(name = "author_name"))
    )
    var author: UserIdent? = null,

    @Enumerated(EnumType.STRING)
    var state: ThreadState = ThreadState.OPEN,

    var createdDate: Instant = Instant.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id")
    var pullRequest: PullRequest? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    var project: Project? = null,

    @OneToMany(mappedBy = "thread", cascade = [CascadeType.ALL], orphanRemoval = true)
    var reviewComments: MutableList<ReviewComment> = mutableListOf(),

    var prevCommitId: String = "",
    var commitId: String? = null
) {
    enum class ThreadState {
        OPEN, CLOSED
    }

    fun isOnPullRequest(): Boolean = pullRequest != null

    fun addComment(reviewComment: ReviewComment) {
        reviewComments.add(reviewComment)
        reviewComment.thread = this
    }

    fun removeComment(reviewComment: ReviewComment) {
        reviewComments.remove(reviewComment)
        reviewComment.thread = null
    }

    fun getFirstReviewComment(): ReviewComment {
        return reviewComments.minByOrNull { it.createdDate }
            ?: throw IllegalStateException("This thread has no ReviewComment.")
    }

    fun getChildCommentsSizeToString(): String {
        return if (reviewComments.size > 1) (reviewComments.size - 1).toString() else ""
    }

    fun hasChildComments(): Boolean = reviewComments.size > 1
}
