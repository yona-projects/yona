package com.github.yonaprojects.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PullRequestCommitRepository : JpaRepository<PullRequestCommit, Long> {

    fun findByPullRequestAndState(
        pullRequest: PullRequest,
        state: PullRequestCommit.State
    ): List<PullRequestCommit>

    fun findFirstByPullRequestAndCommitIdOrderByCreatedDesc(
        pullRequest: PullRequest,
        commitId: String
    ): PullRequestCommit?

    fun findByPullRequest(pullRequest: PullRequest): List<PullRequestCommit>
}
