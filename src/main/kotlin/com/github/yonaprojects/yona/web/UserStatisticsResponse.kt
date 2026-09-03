package com.github.yonaprojects.yona.web

data class UserStatisticsResponse(
    val issue: Long,
    val posting: Long,
    val assignedIssue: Long,
    val issueComment: Long,
    val postingComment: Long,
    val issueVoter: Long,
    val issueCommentVoter: Long
)
