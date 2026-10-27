package dev.yona.domain.pullrequest

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.State
import dev.yona.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/** 풀 리퀘스트 이벤트 저장소이다. */
interface PullRequestEventRepository : JpaRepository<PullRequestEvent, Long> {

    /** 풀 리퀘스트의 이벤트 가운데 [since] 이후에 생긴 가장 최근 것이다 (v1.6 `getLatestEventInDraftTime`). */
    fun findFirstByPullRequestIdAndCreatedAfterOrderByCreatedDescIdDesc(pullRequestId: Long, since: Date): PullRequestEvent?

    /** 풀 리퀘스트의 이벤트를 발생 순서로 조회한다 (v1.6 `pullRequestEvents`는 `created` 오름차순). */
    fun findByPullRequestIdOrderByCreatedAscIdAsc(pullRequestId: Long): List<PullRequestEvent>
}

/**
 * 풀 리퀘스트 이벤트(타임라인)를 기록한다. v1.6 `PullRequestEvent`의 `add…` 정적 메서드들(`app/models/PullRequestEvent.java:61-135`)에 해당한다.
 *
 * 같은 사용자가 리뷰 상태(`PULL_REQUEST_REVIEW_STATE_CHANGED`)를 설정한 시간(`notificationDraftTime`) 안에 다시 바꾸면 새로 쌓지 않고 직전 것을
 * 지운다(리뷰했다가 취소한 흔적을 남기지 않는다).
 */
@Service
class PullRequestEventService(private val events: PullRequestEventRepository, private val properties: YonaProperties) {

    /** 알림 이벤트에서 파생된 이벤트(새 풀 리퀘스트, 상태 변경, 리뷰 상태 변경)를 기록한다. */
    @Transactional
    fun addFromNotification(pullRequest: PullRequest, sender: User, type: EventType, oldValue: String?, newValue: String?) {
        val event = PullRequestEvent(
            created = Date(), senderLoginId = sender.loginId, pullRequest = pullRequest, eventType = type, oldValue = oldValue, newValue = newValue
        )
        val last = events.findFirstByPullRequestIdAndCreatedAfterOrderByCreatedDescIdDesc(
            requireNotNull(pullRequest.id), Date(System.currentTimeMillis() - properties.notificationDraftTime.toMillis())
        )
        if (last != null && type == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED && last.eventType == type && last.senderLoginId == event.senderLoginId) {
            events.delete(last)
        } else {
            events.save(event)
        }
    }

    /** 풀 리퀘스트의 타임라인 이벤트를 모두 지운다. */
    @Transactional
    fun deleteAll(pullRequestId: Long) {
        events.deleteAll(events.findByPullRequestIdOrderByCreatedAscIdAsc(pullRequestId))
    }

    /** 상태가 바뀐 이벤트를 기록한다 (v1.6 `addStateEvent`). */
    @Transactional
    fun addState(sender: User, pullRequest: PullRequest, state: State) {
        events.save(PullRequestEvent(created = Date(), senderLoginId = sender.loginId, pullRequest = pullRequest, eventType = EventType.PULL_REQUEST_STATE_CHANGED, newValue = state.state))
    }

    /** 병합 상태(충돌, 해결, 병합)가 바뀐 이벤트를 기록한다 (v1.6 `addMergeEvent`). */
    @Transactional
    fun addMerge(sender: User, type: EventType, state: State, pullRequest: PullRequest) {
        events.save(PullRequestEvent(created = Date(), senderLoginId = sender.loginId, pullRequest = pullRequest, eventType = type, newValue = state.state))
    }

    /** 새 커밋이 생긴 이벤트를 기록한다. 새 값은 새 커밋 행의 번호를 쉼표로 이은 것이다 (v1.6 `addCommitEvents`). */
    @Transactional
    fun addCommits(sender: User, pullRequest: PullRequest, commits: List<PullRequestCommit>, oldValue: String?) {
        events.save(
            PullRequestEvent(
                created = Date(), senderLoginId = sender.loginId, pullRequest = pullRequest, eventType = EventType.PULL_REQUEST_COMMIT_CHANGED,
                newValue = commits.joinToString(DELIMITER) { requireNotNull(it.id).toString() }, oldValue = oldValue
            )
        )
    }

    companion object {
        /** 값 목록의 구분자이다 (v1.6 `PullRequest.DELIMETER`). */
        const val DELIMITER = ","
    }
}
