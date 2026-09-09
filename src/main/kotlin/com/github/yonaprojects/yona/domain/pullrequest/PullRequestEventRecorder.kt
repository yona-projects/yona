package com.github.yonaprojects.yona.domain.pullrequest

import com.github.yonaprojects.yona.domain.enumeration.EventType
import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

/**
 * yona `models/PullRequestEvent.java`의 `add()` 대응.
 * `IssueEvent`와 달리 값을 병합(A→B→C ⇒ A→C)하지 않고, PULL_REQUEST_REVIEW_STATE_CHANGED
 * 타입에 한해 같은 사용자가 30초 이내에 연속으로 리뷰 상태를 바꾸면 직전 이벤트를 삭제하고
 * 새 이벤트도 저장하지 않는다(둘 다 사라짐 — legacy `needToDeleteEvent`/`add()`의 실제 동작 그대로).
 * 그 외 이벤트 타입은 병합/취소 대상이 아니라 항상 그대로 저장한다.
 *
 * 저장되면 저장된 [PullRequestEvent]를, 상쇄되어 저장되지 않았으면 null을 반환한다.
 *
 * [meterRegistry]는 계측 지점(IssueEventRecorder.kt의 동일 설명 참고)으로, "새 이벤트 저장"
 * vs "직전 이벤트 상쇄" 비율을 outcome 태그로 카운팅한다.
 */
fun PullRequestEventRepository.recordWithDraftMerge(event: PullRequestEvent, meterRegistry: MeterRegistry): PullRequestEvent? {
    val draftSince = Instant.now().minus(DRAFT_WINDOW)
    val lastEvent = findFirstByPullRequestAndCreatedAfterOrderByCreatedDesc(event.pullRequest, draftSince)

    val needToDelete = lastEvent != null &&
        event.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
        lastEvent.eventType == EventType.PULL_REQUEST_REVIEW_STATE_CHANGED &&
        lastEvent.senderLoginId == event.senderLoginId

    val result = if (needToDelete) {
        delete(lastEvent!!)
        null
    } else {
        save(event)
    }

    meterRegistry.counter(
        "yona.event.draft_merge",
        "resourceType", "pull_request",
        "outcome", if (result == null) "merged_or_cancelled" else "saved"
    ).increment()

    return result
}
