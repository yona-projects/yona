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

    // sendNotificationMail: legacy commentUpdateForm.scala.html의 "알림 메일 받기" 체크박스
    // 대응(P3-50) — 작성자 본인이 수정하면서 체크박스를 켠 경우, 또는 작성자가 아닌 다른 사람이
    // (그 체크박스 자체가 노출되지 않는 매니저 등이) 수정한 경우에 알림을 발행한다
    // (IssueApp.saveComment()의 `isSelectedToSendNotificationMail() || !existingComment.
    // isAuthoredBy(currentUser)` 그대로).
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
