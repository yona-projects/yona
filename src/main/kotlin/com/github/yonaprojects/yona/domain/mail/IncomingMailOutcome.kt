package com.github.yonaprojects.yona.domain.mail

sealed class IncomingMailOutcome {
    data class IssueCreated(val issueId: Long, val owner: String, val projectName: String) : IncomingMailOutcome()
    data class IssueCommentCreated(val commentId: Long, val issueId: Long) : IncomingMailOutcome()
    data class PostingCommentCreated(val commentId: Long, val postingId: Long) : IncomingMailOutcome()
    // yona EmailHandler.getThreads()의 COMMENT_THREAD/REVIEW_COMMENT 분기 대응 (P1-30)
    data class ReviewCommentCreated(val commentId: Long, val threadId: Long) : IncomingMailOutcome()
    data class CommitCommentCreated(val commentId: Long) : IncomingMailOutcome()
    data class Rejected(val reason: String) : IncomingMailOutcome()
    object Duplicate : IncomingMailOutcome()
    object UnknownSender : IncomingMailOutcome()
    // yona EmailHandler.getHelpMessage()/reply() 대응 (P1-31)
    object HelpRequested : IncomingMailOutcome()
}
