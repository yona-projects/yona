package com.github.yonaprojects.yona.domain.user

import com.github.yonaprojects.yona.domain.enumeration.State
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface FavoriteIssueRepository : JpaRepository<FavoriteIssue, Long> {
    fun findByUserIdAndIssueId(userId: Long, issueId: Long): Optional<FavoriteIssue>
    fun findByUserId(userId: Long): List<FavoriteIssue>
    fun findByIssueId(issueId: Long): List<FavoriteIssue>

    @Query("SELECT COUNT(fi) FROM FavoriteIssue fi WHERE fi.user.id = :userId AND fi.issue.state = :state")
    fun countByUserIdAndIssueState(@Param("userId") userId: Long, @Param("state") state: State): Int
}
