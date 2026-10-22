package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.PullRequestReviewAction
import dev.yona.domain.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 풀 리퀘스트의 리뷰(승인 표시)를 더하고 뺀다. v1.6 `ReviewApp.review`, `unreview`(`app/controllers/ReviewApp.java`)에 해당한다.
 * 권한 확인은 호출하는 쪽이 한다.
 *
 * 리뷰를 바꾸면 리뷰 상태 알림을 내보내고 타임라인에 기록한다. 같은 사람이 짧은 시간 안에 리뷰와 취소를 반복하면 타임라인에는 남지 않는다.
 */
@Service
class PullRequestReviewService(
    private val pullRequests: PullRequestRepository,
    private val events: PullRequestEventService,
    private val publisher: ApplicationEventPublisher
) {

    /** [user]를 리뷰어로 더한다. 이미 있으면 저장하지 않지만 알림은 v1.6처럼 내보낸다. */
    @Transactional
    fun review(pullRequest: PullRequest, user: User) {
        if (pullRequest.reviewers.none { it.id == user.id }) {
            pullRequest.reviewers.add(user)
            pullRequests.save(pullRequest)
        }
        notify(pullRequest, PullRequestReviewAction.DONE, user)
    }

    /** [user]를 리뷰어에서 뺀다. 리뷰어가 아니어도 v1.6처럼 알림을 내보낸다. */
    @Transactional
    fun unreview(pullRequest: PullRequest, user: User) {
        pullRequest.reviewers.removeAll { it.id == user.id }
        pullRequests.save(pullRequest)
        notify(pullRequest, PullRequestReviewAction.CANCEL, user)
    }

    private fun notify(pullRequest: PullRequest, action: PullRequestReviewAction, user: User) {
        publisher.publishEvent(PullRequestReviewedActivity(pullRequest, action, user))
        events.addFromNotification(pullRequest, user, EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, action.opposite.name, action.name)
    }
}
