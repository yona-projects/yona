package dev.yona.domain.pullrequest

import dev.yona.access.AccessControl
import dev.yona.access.asResource
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 리뷰 요청과 취소의 결과이다. */
enum class ReviewRequestResult {
    /** 요청했거나 취소했다 */
    CHANGED,

    /** 이미 대기 중인 사람을 또 요청했거나, 대기 중이 아닌 사람의 요청을 취소했다. 아무것도 바꾸지 않았다 */
    UNCHANGED,

    /** 요청 대상이 될 수 없다(작성자, 프로젝트를 읽을 수 없는 사람, 활성이 아닌 사용자) */
    NOT_CANDIDATE,

    /** 열린 풀 리퀘스트에만 요청할 수 있다 */
    NOT_OPEN
}

/**
 * 풀 리퀘스트의 리뷰를 사람에게 요청하고 취소한다. v1.6에 없는 추가 항목이다(로드맵 X-27, `decisions.md` D-37). 권한 확인(풀 리퀘스트 수정 권한)은 호출하는 쪽이 한다.
 *
 * 대기 중인 요청만 연결 테이블 `pull_request_review_request`에 저장하며([PullRequest.reviewRequests]) 이력은 타임라인 이벤트가 남긴다.
 * 요청 대상은 그 프로젝트를 읽을 수 있는 활성 사용자이고 작성자는 제외한다. 이미 리뷰한 사람도 다시 요청할 수 있다(GitHub의 다시 요청).
 * 요청하면 타임라인에 기록하고 요청받은 사람에게 알림과 웹훅을 보낸다. 취소는 타임라인에만 남긴다. 리뷰를 제출하면 [fulfil]이 요청을 말없이 지운다.
 * 새 커밋으로 오래된 승인이 해제되어도 자동으로 다시 요청하지 않는다.
 */
@Service
class PullRequestReviewRequestService(
    private val pullRequests: PullRequestRepository,
    private val users: UserRepository,
    private val events: PullRequestEventService,
    private val publisher: ApplicationEventPublisher,
    private val accessControl: AccessControl
) {

    companion object {
        /** 후보 검색이 돌려주는 최대 사람 수이다. */
        const val MAX_CANDIDATES = 20

        /** 검색어에 맞는 사용자를 한 번에 읽는 수이다. 읽을 수 없는 사람을 거르고도 [MAX_CANDIDATES]명이 남도록 넉넉히 읽는다. */
        private const val SEARCH_BATCH = 200
    }

    /** [reviewer]에게 리뷰를 요청한다. */
    @Transactional
    fun request(pullRequest: PullRequest, reviewer: User, actor: User): ReviewRequestResult {
        if (pullRequest.state != State.OPEN) return ReviewRequestResult.NOT_OPEN
        if (!isCandidate(pullRequest, reviewer)) return ReviewRequestResult.NOT_CANDIDATE
        if (pullRequest.reviewRequests.any { it.id == reviewer.id }) return ReviewRequestResult.UNCHANGED
        pullRequest.reviewRequests.add(reviewer)
        pullRequests.save(pullRequest)
        events.addReviewRequest(actor, pullRequest, requireNotNull(reviewer.loginId), requested = true)
        publisher.publishEvent(PullRequestChangedActivity(pullRequest, EventType.PULL_REQUEST_REVIEW_REQUESTED, null, reviewer.loginId, actor))
        return ReviewRequestResult.CHANGED
    }

    /** 대기 중인 [reviewer]의 요청을 취소한다. 알림은 보내지 않고 타임라인에만 남긴다. */
    @Transactional
    fun cancel(pullRequest: PullRequest, reviewer: User, actor: User): ReviewRequestResult {
        if (!pullRequest.reviewRequests.removeIf { it.id == reviewer.id }) return ReviewRequestResult.UNCHANGED
        pullRequests.save(pullRequest)
        events.addReviewRequest(actor, pullRequest, requireNotNull(reviewer.loginId), requested = false)
        return ReviewRequestResult.CHANGED
    }

    /** 리뷰를 제출한 [reviewer]의 대기 중인 요청을 말없이 지운다(리뷰 이벤트가 이미 타임라인에 있다). */
    @Transactional
    fun fulfil(pullRequest: PullRequest, reviewer: User) {
        if (pullRequest.reviewRequests.removeIf { it.id == reviewer.id }) pullRequests.save(pullRequest)
    }

    /** [user]가 이 풀 리퀘스트의 리뷰 요청을 받을 수 있는지이다: 활성 사용자이고 작성자가 아니며 프로젝트를 읽을 수 있다. */
    @Transactional(readOnly = true)
    fun isCandidate(pullRequest: PullRequest, user: User): Boolean {
        if (user.state != UserState.ACTIVE) return false
        if (user.id == pullRequest.contributor?.id) return false
        return accessControl.isAllowed(user, requireNotNull(pullRequest.toProject).asResource(), Operation.READ)
    }

    /**
     * 리뷰를 요청할 수 있는 사람을 이름이나 로그인 ID로 찾는다(최대 [MAX_CANDIDATES]명). 빈 검색어는 목록을 보이지 않는다.
     * 이미 대기 중인 사람은 뺀다. [viewer]는 후보에 넣지 않는 기준이 아니라(자기 자신도 요청받을 수 있다) 호출 맥락을 남기는 용도이다.
     */
    @Transactional(readOnly = true)
    fun candidates(pullRequest: PullRequest, query: String, viewer: User?): List<User> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val pending = pullRequest.reviewRequests.mapNotNull { it.id }.toSet()
        val found = users.search(trimmed, PageRequest.of(0, SEARCH_BATCH)).content
        return found.filter { it.id !in pending && isCandidate(pullRequest, it) }.take(MAX_CANDIDATES)
    }

}
