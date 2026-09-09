package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.support.CodeRange
import com.github.yonaprojects.yona.domain.user.User

interface CodeReviewService {
    fun createReviewComment(
        project: Project,
        pullRequest: PullRequest?,
        commitId: String?,
        contents: String,
        codeRange: CodeRange?,
        threadId: Long?,
        currentUser: User
    ): ReviewComment

    fun deleteReviewComment(commentId: Long, currentUser: User)

    fun createCommitComment(
        project: Project,
        commitId: String,
        contents: String,
        path: String?,
        line: Int?,
        side: CodeRange.Side?,
        currentUser: User
    ): CommitComment

    fun deleteCommitComment(commentId: Long, currentUser: User)

    fun updateThreadState(threadId: Long, state: CommentThread.ThreadState, currentUser: User): CommentThread

    fun addReviewer(pullRequestId: Long, reviewerId: Long)

    fun removeReviewer(pullRequestId: Long, reviewerId: Long)

    // yona CodeCommentThread.isOutdated() 대응.
    fun isThreadOutdated(threadId: Long): Boolean
}
