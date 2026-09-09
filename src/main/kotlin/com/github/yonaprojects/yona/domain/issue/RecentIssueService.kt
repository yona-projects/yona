package com.github.yonaprojects.yona.domain.issue

import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// yona RecentIssue의 addVisitIssueHistory/addVisitPostingHistory 대응.
// 사용자별 최근 방문 이슈/게시글을 issueId 또는 postingId로 중복 제거하고,
// MAX_RECENT_LIST_PER_USER(100)를 넘으면 가장 오래된 항목부터 제거한다.
@Service
class RecentIssueService(
    private val recentIssueRepository: RecentIssueRepository
) {
    companion object {
        const val MAX_RECENT_LIST_PER_USER = 100
    }

    @Transactional
    fun recordIssueVisit(user: User, issue: Issue) {
        val userId = user.id ?: return
        val issueId = issue.id ?: return
        val project = issue.project

        recentIssueRepository.findByUserIdAndIssueId(userId, issueId).ifPresent {
            recentIssueRepository.delete(it)
        }

        recentIssueRepository.save(
            RecentIssue(
                userId = userId,
                issueId = issueId,
                title = issue.title,
                url = "/${project.owner}/${project.name}/issue/${issue.number}",
                createdDate = Instant.now()
            )
        )

        deleteOldestIfOverflow(userId)
    }

    @Transactional
    fun recordPostingVisit(user: User, posting: Posting) {
        val userId = user.id ?: return
        val postingId = posting.id ?: return
        val project = posting.project

        recentIssueRepository.findByUserIdAndPostingId(userId, postingId).ifPresent {
            recentIssueRepository.delete(it)
        }

        recentIssueRepository.save(
            RecentIssue(
                userId = userId,
                postingId = postingId,
                title = posting.title,
                url = "/${project.owner}/${project.name}/post/${posting.number}",
                createdDate = Instant.now()
            )
        )

        deleteOldestIfOverflow(userId)
    }

    fun getRecentIssues(user: User): List<RecentIssue> {
        val userId = user.id ?: return emptyList()
        return recentIssueRepository.findByUserIdOrderByIdDesc(userId)
    }

    // yona RecentIssue.deleteAll(user) 대응. 회원 탈퇴/계정 삭제 시 방문 이력을 정리한다.
    @Transactional
    fun deleteAll(user: User) {
        val userId = user.id ?: return
        recentIssueRepository.deleteByUserId(userId)
    }

    private fun deleteOldestIfOverflow(userId: Long) {
        val recentList = recentIssueRepository.findByUserIdOrderByIdDesc(userId)
        if (recentList.size > MAX_RECENT_LIST_PER_USER) {
            val toDelete = recentList.sortedBy { it.id }.take(recentList.size - MAX_RECENT_LIST_PER_USER)
            toDelete.forEach { recentIssueRepository.delete(it) }
        }
    }
}
