package com.github.yonaprojects.yona.domain.issue

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface RecentIssueRepository : JpaRepository<RecentIssue, Long> {
    fun findByUserIdOrderByIdDesc(userId: Long): List<RecentIssue>
    fun findByUserIdAndIssueId(userId: Long, issueId: Long): Optional<RecentIssue>
    fun findByUserIdAndPostingId(userId: Long, postingId: Long): Optional<RecentIssue>
    fun deleteByUserId(userId: Long)
}
