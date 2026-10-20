package com.github.yonaprojects.yona.domain.webhook

import java.time.Instant

// VCS-neutral push payload shared by repository event listeners and webhooks.
data class PushedVcsCommit(
    val id: String,
    val message: String,
    val authorName: String,
    val authorEmail: String?,
    val timestamp: Instant?,
    val committerName: String = authorName,
    val committerEmail: String? = authorEmail
)

data class PushedVcsCommits(
    val commits: List<PushedVcsCommit>,
    val refNames: List<String>
)
