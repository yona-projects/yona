package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.ReviewVerdict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 풀 리퀘스트의 토론을 Magpie의 변경 요청 계약(`contract:change-request`, 로드맵 X-23)의 모양으로 모은다. 리뷰 스레드의 댓글은 `comment`, 리뷰는 판정에 따라
 * `approval`(승인), `change-request`(변경 요청), `review`(의견)이다. 해제한 리뷰와 임시 저장 댓글은 담지 않는다.
 */
@Service
@Transactional(readOnly = true)
class PullRequestDiscussionService(private val threads: dev.yona.domain.comment.CommentThreadRepository, private val reviews: PullRequestReviewRepository) {

    @jakarta.persistence.PersistenceContext
    private lateinit var em: jakarta.persistence.EntityManager

    /** 토론의 한 줄이다. [kind]는 `comment`, `review`, `approval`, `change-request`이다. */
    data class Entry(val author: String, val date: Date, val body: String, val kind: String)

    /** 토론 전체이다. [unresolvedCount]는 열린 리뷰 스레드 수이고 [participants]는 댓글이나 리뷰를 남긴 로그인 ID이다. */
    data class Discussion(val comments: List<Entry>, val participants: List<String>, val unresolvedCount: Int)

    fun discussion(pullRequest: PullRequest): Discussion {
        val id = requireNotNull(pullRequest.id)
        val visibleThreads = threads.findByPullRequestId(id)
        val fromThreads = visibleThreads.flatMap { thread -> thread.reviewComments.map { Entry(it.authorLoginId.orEmpty(), requireNotNull(it.createdDate), it.contents.orEmpty(), "comment") } }
        val fromReviews = reviews.findByPullRequestIdOrderByCreatedDateAscIdAsc(id).filter { it.dismissedDate == null }.map {
            Entry(it.authorLoginId.orEmpty(), requireNotNull(it.createdDate), it.contents.orEmpty(), when (it.verdict) {
                ReviewVerdict.APPROVED -> "approval"
                ReviewVerdict.CHANGES_REQUESTED -> "change-request"
                else -> "review"
            })
        }
        val entries = (fromThreads + fromReviews).sortedBy { it.date }
        return Discussion(entries, entries.map { it.author }.filter { it.isNotEmpty() }.distinct(), visibleThreads.count { it.state == dev.yona.domain.enumeration.ThreadState.OPEN })
    }

    /** [login]이 [since] 이상 [until] 미만에 리뷰한 풀 리퀘스트별 요약이다(처음 리뷰한 시각 순). */
    fun reviewsGiven(projectId: Long, login: String, since: Date, until: Date): List<ReviewActivity> {
        val reviewRows = em.createQuery(
            "select r from PullRequestReview r where r.pullRequest.toProject.id = :p and r.authorLoginId = :login and r.createdDate >= :since and r.createdDate < :until order by r.createdDate asc, r.id asc", PullRequestReview::class.java
        ).setParameter("p", projectId).setParameter("login", login).setParameter("since", since).setParameter("until", until).resultList
        return reviewRows.groupBy { requireNotNull(it.pullRequest) }.map { (pull, rows) ->
            val inline = em.createQuery(
                "select count(rc) from ReviewComment rc where rc.thread.pullRequest.id = :pr and rc.authorLoginId = :login and rc.createdDate >= :since and rc.createdDate < :until", Long::class.javaObjectType
            ).setParameter("pr", pull.id).setParameter("login", login).setParameter("since", since).setParameter("until", until).singleResult
            ReviewActivity(requireNotNull(pull.number), requireNotNull(rows.first().createdDate), rows.sumOf { it.contents.orEmpty().length }, inline.toInt(), pull.labels.map { it.name.orEmpty() }.sorted())
        }.sortedBy { it.firstReviewAt }
    }

    /** 사람이 한 풀 리퀘스트에 창 안에서 남긴 리뷰의 요약이다. [bodyLength]는 리뷰 본문 길이의 합이고 [inlineCommentCount]는 리뷰 스레드에 단 댓글 수이다. */
    data class ReviewActivity(val pullRequestNumber: Long, val firstReviewAt: Date, val bodyLength: Int, val inlineCommentCount: Int, val labels: List<String>)
}
