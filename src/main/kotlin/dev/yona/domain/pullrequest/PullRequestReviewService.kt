package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ReviewVerdict
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 리뷰 판정 제출 결과이다. */
enum class ReviewResult {
    /** 저장했다 */
    SUBMITTED,

    /** 작성자는 자기 풀 리퀘스트를 승인하거나 변경을 요청할 수 없다 */
    AUTHOR_NOT_ALLOWED,

    /** 승인과 변경 요청은 열린 풀 리퀘스트에만 할 수 있다 */
    NOT_OPEN,

    /** 댓글과 변경 요청은 본문이 필요하다 */
    BODY_REQUIRED
}

/** 리뷰 해제 결과이다. */
enum class DismissResult { DISMISSED, NOT_FOUND, ALREADY_DISMISSED, MESSAGE_REQUIRED }

/**
 * 풀 리퀘스트 리뷰 판정을 제출하고 해제한다. v1.6 `ReviewApp.review`, `unreview`(`app/controllers/ReviewApp.java`)의 "리뷰 완료와 취소"를
 * GitHub 방식의 리뷰(Comment, Approve, Request changes) 하나로 합쳤다(추가 개발 항목 X-12, `decisions.md` D-35). 권한 확인은 호출하는 쪽이 한다.
 */
@Service
class PullRequestReviewService(
    private val pullRequests: PullRequestRepository,
    private val reviews: PullRequestReviewRepository,
    private val users: UserRepository,
    private val events: PullRequestEventService
) {

    companion object {
        /** 새 커밋으로 시스템이 승인을 해제할 때 쓰는 사유이다. 화면은 해제한 사람이 없으면 이 값 대신 현지화한 문구를 보인다. */
        const val STALE_REASON = "New commits were pushed"
    }

    /**
     * 리뷰 판정을 제출한다(추가 개발 항목 X-12, `decisions.md` D-35). 권한 확인은 호출하는 쪽이 한다.
     * 작성자는 승인과 변경 요청을 할 수 없고(댓글은 가능), 승인과 변경 요청은 열린 요청에만 하며, 댓글과 변경 요청은 본문이 필요하다.
     * 저장하면 리뷰어별 최신 판정에 맞게 승인 집합(`pull_request_reviewers`)을 고치고 타임라인 이벤트를 남긴다.
     */
    @Transactional
    fun submit(pullRequest: PullRequest, reviewer: User, verdict: ReviewVerdict, contents: String?): ReviewResult {
        val author = pullRequest.contributor?.id == reviewer.id
        if (author && verdict != ReviewVerdict.COMMENTED) return ReviewResult.AUTHOR_NOT_ALLOWED
        if (verdict != ReviewVerdict.COMMENTED && pullRequest.state != State.OPEN) return ReviewResult.NOT_OPEN
        if (verdict != ReviewVerdict.APPROVED && contents.isNullOrBlank()) return ReviewResult.BODY_REQUIRED
        val review = reviews.save(
            PullRequestReview(
                pullRequest = pullRequest, authorId = reviewer.id, authorLoginId = reviewer.loginId, verdict = verdict,
                contents = contents?.takeIf { it.isNotBlank() }, createdDate = Date()
            )
        )
        pullRequest.reviewHistory.add(review)
        syncApprovers(pullRequest)
        events.addReviewSubmitted(reviewer, pullRequest, review)
        return ReviewResult.SUBMITTED
    }

    /**
     * 리뷰를 해제한다. 해제한 리뷰는 댓글로 본다. 사유가 필요하고 다른 풀 리퀘스트의 리뷰나 이미 해제한 리뷰는 해제할 수 없다.
     * 권한(쓰기 권한자)은 호출하는 쪽이 확인한다.
     */
    @Transactional
    fun dismiss(pullRequest: PullRequest, review: PullRequestReview, actor: User, message: String?): DismissResult {
        if (review.pullRequest?.id != pullRequest.id) return DismissResult.NOT_FOUND
        if (review.dismissedDate != null) return DismissResult.ALREADY_DISMISSED
        if (message.isNullOrBlank()) return DismissResult.MESSAGE_REQUIRED
        review.dismissedDate = Date(); review.dismissedByLoginId = actor.loginId; review.dismissedReason = message
        reviews.save(review)
        syncApprovers(pullRequest)
        events.addReviewDismissed(actor, pullRequest, review)
        return DismissResult.DISMISSED
    }

    /**
     * 새 커밋이 올라와 현재 승인을 모두 해제한다(프로젝트 설정 `isDismissingStaleApprovals`가 켜진 경우 호출한다). 해제한 사람은 비우고 사유는 [STALE_REASON]이다.
     * 변경 요청과 댓글은 해제하지 않는다. 해제한 승인 수를 돌려준다.
     */
    @Transactional
    fun dismissStaleApprovals(pullRequest: PullRequest): Int {
        val approvals = pullRequest.latestReviews.values.filter { it.effectiveVerdict == ReviewVerdict.APPROVED }
        approvals.forEach {
            it.dismissedDate = Date(); it.dismissedByLoginId = null; it.dismissedReason = STALE_REASON
            reviews.save(it)
            events.addReviewDismissed(null, pullRequest, it)
        }
        if (approvals.isNotEmpty()) syncApprovers(pullRequest)
        return approvals.size
    }

    /**
     * 승인 집합을 리뷰어별 최신 판정에 맞춘다. 판정 이력이 있는 리뷰어만 다루며(보정 전의 옛 행은 건드리지 않는다) 작성자는 넣지 않는다.
     */
    private fun syncApprovers(pullRequest: PullRequest) {
        val latest = pullRequest.latestReviews
        val approverIds = latest.filterValues { it.effectiveVerdict == ReviewVerdict.APPROVED }.keys.filterNotNull().filter { it != pullRequest.contributor?.id }.toSet()
        pullRequest.reviewers.removeAll { it.id != null && latest.containsKey(it.id) && it.id !in approverIds }
        approverIds.filter { id -> pullRequest.reviewers.none { it.id == id } }.forEach { id -> users.findById(id).ifPresent { pullRequest.reviewers.add(it) } }
        pullRequests.save(pullRequest)
    }
}
