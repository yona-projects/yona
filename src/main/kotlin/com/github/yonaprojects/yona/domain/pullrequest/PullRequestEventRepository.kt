package com.github.yonaprojects.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface PullRequestEventRepository : JpaRepository<PullRequestEvent, Long> {
    fun findByPullRequestOrderByCreatedAsc(pullRequest: PullRequest): List<PullRequestEvent>

    fun findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(pullRequest: PullRequest, created: Instant): PullRequestEvent?
}
