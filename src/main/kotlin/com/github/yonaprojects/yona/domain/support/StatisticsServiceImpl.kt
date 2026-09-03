package com.github.yonaprojects.yona.domain.support

import com.github.yonaprojects.yona.web.UserStatisticsResponse
import java.lang.Long as JLong
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StatisticsServiceImpl(
    @PersistenceContext
    private val entityManager: EntityManager
) : StatisticsService {

    @Transactional(readOnly = true)
    override fun getUserStatistics(userId: Long): UserStatisticsResponse {
        val issueCount = entityManager.createQuery(
            "select count(i) from Issue i where i.authorId = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val postingCount = entityManager.createQuery(
            "select count(p) from Posting p where p.authorId = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val assignedIssueCount = entityManager.createQuery(
            "select count(i) from Issue i where i.assignee.user.id = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val issueCommentCount = entityManager.createQuery(
            "select count(ic) from IssueComment ic where ic.authorId = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val postingCommentCount = entityManager.createQuery(
            "select count(pc) from PostingComment pc where pc.authorId = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val issueVoterCount = entityManager.createQuery(
            "select count(i) from Issue i join i.voters v where v.id = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        val issueCommentVoterCount = entityManager.createQuery(
            "select count(ic) from IssueComment ic join ic.voters v where v.id = :userId",
            JLong::class.java
        ).setParameter("userId", userId).singleResult.toLong()

        return UserStatisticsResponse(
            issue = issueCount,
            posting = postingCount,
            assignedIssue = assignedIssueCount,
            issueComment = issueCommentCount,
            postingComment = postingCommentCount,
            issueVoter = issueVoterCount,
            issueCommentVoter = issueCommentVoterCount
        )
    }
}
