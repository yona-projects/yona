package com.github.yonaprojects.yona.domain.comment

import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.board.PostingComment
import com.github.yonaprojects.yona.domain.user.User

interface CommentService {
    fun createIssueComment(
        issueId: Long,
        contents: String,
        author: User,
        parentCommentId: Long? = null
    ): IssueComment

    fun createPostingComment(
        postingId: Long,
        contents: String,
        author: User,
        parentCommentId: Long? = null
    ): PostingComment

    fun updateIssueComment(
        commentId: Long,
        contents: String,
        author: User
    ): IssueComment

    fun deleteIssueComment(
        commentId: Long,
        author: User
    )

    fun updatePostingComment(
        commentId: Long,
        contents: String,
        author: User
    ): PostingComment

    fun deletePostingComment(
        commentId: Long,
        author: User
    )

    fun extractMentionedUsers(contents: String): Set<User>
}
