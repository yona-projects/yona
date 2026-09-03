package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.vcs.GitCommit
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.enumeration.State

class PullRequestMergeResult(
    var gitCommits: List<GitCommit> = emptyList(),
    var newCommits: List<PullRequestCommit> = emptyList(),
    var pullRequest: PullRequest
) {
    fun hasDiffCommits(): Boolean {
        return gitCommits.isNotEmpty()
    }

    fun conflicts(): Boolean {
        return pullRequest.isConflict == true
    }

    fun setConflictStateOfPullRequest() {
        pullRequest.isConflict = true
    }

    fun setResolvedStateOfPullRequest() {
        pullRequest.isConflict = false
    }

    fun setMergedStateOfPullRequest(receiver: User) {
        pullRequest.isConflict = false
        pullRequest.state = State.MERGED
        pullRequest.receiver = receiver
    }
}
