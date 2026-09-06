package com.github.yonaprojects.yona.domain.pullrequest

import org.springframework.data.jpa.repository.JpaRepository

interface PullRequestReviewRepository : JpaRepository<PullRequestReview, Long> {
    fun findByPullRequestOrderByCreatedDateAsc(pullRequest: PullRequest): List<PullRequestReview>
    fun findByPullRequestIdOrderByCreatedDateAsc(pullRequestId: Long): List<PullRequestReview>
}
