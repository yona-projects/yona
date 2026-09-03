package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.user.UserIdent
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import java.time.Instant

@Entity
@DiscriminatorValue("non_ranged")
class NonRangedCodeCommentThread(
    id: Long? = null,
    author: UserIdent? = null,
    state: ThreadState = ThreadState.OPEN,
    createdDate: Instant = Instant.now(),
    pullRequest: PullRequest? = null,
    project: Project? = null,
    reviewComments: MutableList<ReviewComment> = mutableListOf(),
    prevCommitId: String = "",
    commitId: String? = null
) : CommentThread(
    id, author, state, createdDate, pullRequest, project, reviewComments, prevCommitId, commitId
) {
    fun isOnChangesOfPullRequest(): Boolean = isOnPullRequest() && !commitId.isNullOrEmpty()
}
