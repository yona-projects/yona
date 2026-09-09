package com.github.yonaprojects.yona.domain.issue

import io.micrometer.core.instrument.MeterRegistry
import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

/**
 * yona `models/IssueEvent.java`의 `add()`/`addWithoutSkipEvent()` 대응.
 * 같은 이슈에 대해 같은 사용자가 [DRAFT_WINDOW] 이내에 같은 타입의 이벤트를 연속으로 남기면
 * 타임라인 잡음을 줄이기 위해 병합하거나 상쇄한다.
 *
 * - [skipWaypoint]=true(`add()`): A→B, B→C ⇒ A→C로 병합(중간 지점 B는 생략).
 *   A→B, B→A처럼 값이 되돌아오면 두 이벤트 모두 남기지 않는다.
 * - [skipWaypoint]=false(`addWithoutSkipEvent()`): 중간 지점은 그대로 남기되,
 *   정확히 값이 되돌아오는 경우(A→B, B→A)만 두 이벤트 모두 상쇄한다.
 *
 * 저장되면 저장된 [IssueEvent]를, 상쇄되어 저장되지 않았으면 null을 반환한다.
 *
 * [meterRegistry]는 "새 이벤트 저장" vs "직전 이벤트 병합/상쇄" 비율을 outcome 태그로 카운팅하는
 * 계측 지점이다. 확장 함수라 Spring 빈이 아니므로
 * DI 컨테이너 대신 파라미터로 주입받는다(호출부인 IssueServiceImpl/IssueShareServiceImpl이
 * 생성자로 주입받은 걸 그대로 전달).
 */
fun IssueEventRepository.recordWithDraftMerge(event: IssueEvent, skipWaypoint: Boolean, meterRegistry: MeterRegistry): IssueEvent? {
    val draftSince = Instant.now().minus(DRAFT_WINDOW)
    val lastEvent = findFirstByIssueAndCreatedAfterOrderByIdDesc(event.issue, draftSince)

    val result: IssueEvent? = if (lastEvent != null &&
        lastEvent.eventType == event.eventType &&
        lastEvent.senderLoginId == event.senderLoginId
    ) {
        if (skipWaypoint) {
            event.oldValue = lastEvent.oldValue
            delete(lastEvent)
            if (event.oldValue == event.newValue) {
                null
            } else {
                save(event)
            }
        } else if (event.oldValue == lastEvent.newValue && event.newValue == lastEvent.oldValue) {
            delete(lastEvent)
            null
        } else {
            save(event)
        }
    } else {
        save(event)
    }

    meterRegistry.counter(
        "yona.event.draft_merge",
        "resourceType", "issue",
        "outcome", if (result == null) "merged_or_cancelled" else "saved"
    ).increment()

    return result
}
