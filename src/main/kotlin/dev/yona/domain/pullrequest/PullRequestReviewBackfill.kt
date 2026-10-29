package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.user.User
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 기존 데이터를 새 리뷰 구조로 이어 준다(추가 개발 항목 X-12, `decisions.md` D-35 13번). v1.6의 승인 집합(`pull_request_reviewers`)에는 있으나
 * 리뷰 판정 테이블(`pull_request_review`)에 그 사용자의 유효한 승인이 없는 행마다 승인(`APPROVED`) 리뷰를 만든다. 서버를 시작할 때와 데이터를 가져온 뒤에 돈다.
 * 그래서 DB를 그대로 쓰면서 프로그램만 바꾼 경우와 이관한 경우가 같은 결과를 얻는다.
 *
 * 멱등하다. 승인 리뷰가 이미 있는 행은 건너뛰므로 몇 번을 돌려도 같다. 승인 시각은 그 사용자가 그 요청에서 마지막으로 리뷰를 마친 옛 이벤트
 * (`PULL_REQUEST_REVIEW_STATE_CHANGED`, 값 `DONE`)의 시각이고, 없으면 풀 리퀘스트를 만든 시각이다. 본문은 비운다.
 */
@Component
class PullRequestReviewBackfill(
    private val entityManager: EntityManager,
    private val reviews: PullRequestReviewRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        backfill()
    }

    /** 빠진 승인 리뷰를 만들고 만든 건수를 돌려준다. */
    @Transactional
    fun backfill(): Int {
        val members = entityManager.createQuery("select pr.id, u.id from PullRequest pr join pr.reviewers u", Array<Any>::class.java).resultList
        if (members.isEmpty()) return 0
        val approved = entityManager.createQuery(
            "select r.pullRequest.id, r.authorId from PullRequestReview r where r.verdict = :verdict and r.dismissedDate is null and r.pullRequest.id is not null", Array<Any>::class.java
        ).setParameter("verdict", ReviewVerdict.APPROVED).resultList.map { it[0] as Long to it[1] as Long? }.toSet()

        var created = 0
        for (row in members) {
            val pullRequestId = row[0] as Long
            val userId = row[1] as Long
            if (pullRequestId to userId in approved) continue
            val user = entityManager.find(User::class.java, userId) ?: continue
            val pullRequest = entityManager.find(PullRequest::class.java, pullRequestId) ?: continue
            reviews.save(
                PullRequestReview(
                    pullRequest = pullRequest, authorId = userId, authorLoginId = user.loginId, verdict = ReviewVerdict.APPROVED,
                    createdDate = lastApprovedAt(pullRequestId, user.loginId) ?: pullRequest.created ?: Date()
                )
            )
            created++
        }
        if (created > 0) log.info("Created {} approval reviews from the existing reviewers", created)
        return created
    }

    private fun lastApprovedAt(pullRequestId: Long, loginId: String?): Date? {
        if (loginId == null) return null
        return entityManager.createQuery(
            "select e.created from PullRequestEvent e where e.pullRequest.id = :pr and e.eventType = :type and e.senderLoginId = :login and e.newValue = 'DONE' order by e.created desc, e.id desc",
            Date::class.java
        ).setParameter("pr", pullRequestId).setParameter("type", EventType.PULL_REQUEST_REVIEW_STATE_CHANGED).setParameter("login", loginId).setMaxResults(1).resultList.firstOrNull()
    }
}
