package com.github.yonaprojects.yona.domain.webhook

import java.time.Instant

// VCS-neutral push payload for commits that are not JGit/hg4j objects (SVN revisions, and commits
// imported from another server). Produces the same JSON shape as PushedCommits/PushedHgCommits.
data class PushedVcsCommit(
    val id: String,
    val message: String,
    val authorName: String,
    val authorEmail: String?,
    val timestamp: Instant
)

data class PushedVcsCommits(
    val commits: List<PushedVcsCommit>,
    val refNames: List<String>
)
