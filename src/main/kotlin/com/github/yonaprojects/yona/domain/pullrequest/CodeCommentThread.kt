package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserIdent
import jakarta.persistence.*
import java.time.Instant

@Entity
@DiscriminatorValue("ranged")
class CodeCommentThread(
    id: Long? = null,
    author: UserIdent? = null,
    state: ThreadState = ThreadState.OPEN,
    createdDate: Instant = Instant.now(),
    pullRequest: PullRequest? = null,
    project: Project? = null,
    reviewComments: MutableList<ReviewComment> = mutableListOf(),
    prevCommitId: String = "",
    commitId: String? = null,

    @Embedded
    var codeRange: CodeRange = CodeRange(),

    @ManyToMany(cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "code_comment_thread_author",
        joinColumns = [JoinColumn(name = "thread_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")]
    )
    var codeAuthors: MutableList<User> = mutableListOf()
) : CommentThread(
    id, author, state, createdDate, pullRequest, project, reviewComments, prevCommitId, commitId
) {
    fun isCommitComment(): Boolean = prevCommitId.isEmpty()

    fun isOnChangesOfPullRequest(): Boolean = isOnPullRequest() && !commitId.isNullOrEmpty()

    fun isOnAllChangesOfPullRequest(): Boolean = isOnChangesOfPullRequest() && prevCommitId.isNotEmpty()
}
