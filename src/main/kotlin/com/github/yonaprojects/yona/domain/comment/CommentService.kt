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

    // sendNotificationMail: legacy IssueApp.saveComment()의
    // `isSelectedToSendNotificationMail() || !existingComment.isAuthoredBy(currentUser)` 그대로 —
    // 체크박스를 켠 작성자 본인이거나, 체크박스가 노출되지 않는 작성자 외 사용자(매니저 등)가 수정한 경우 알림 발행.
    fun updateIssueComment(
        commentId: Long,
        contents: String,
        author: User,
        sendNotificationMail: Boolean = false
    ): IssueComment

    fun deleteIssueComment(
        commentId: Long,
        author: User
    )

    fun updatePostingComment(
        commentId: Long,
        contents: String,
        author: User,
        sendNotificationMail: Boolean = false
    ): PostingComment

    fun deletePostingComment(
        commentId: Long,
        author: User
    )

    fun extractMentionedUsers(contents: String): Set<User>
}
