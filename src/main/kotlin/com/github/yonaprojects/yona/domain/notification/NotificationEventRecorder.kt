package com.github.yonaprojects.yona.domain.notification

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

/**
 * yona `models/NotificationEvent.java`의 `add()`/`addWithoutSkipEvent()` 대응.
 *
 * 두 가지 역할을 한 번에 한다(legacy와 동일하게 원자적으로 처리):
 * 1. draft-time 병합/취소 — 같은 리소스(resourceType+resourceId)에 같은 사용자가 [DRAFT_WINDOW]
 *    이내에 같은 타입의 이벤트를 연속으로 남기면 병합하거나 상쇄한다(IssueEvent/PullRequestEvent와
 *    동일한 패턴). `skipWaypoint=true`(`add()`)는 A→B→C를 A→C로 병합하고
 *    A→B→A는 완전히 상쇄한다. `skipWaypoint=false`(`addWithoutSkipEvent()`)는 중간 지점은
 *    남기되 정확히 되돌아오는 경우만 상쇄한다.
 * 2. 저장 시 [NotificationMail] 마커를 함께 만들어 붙인다 — legacy의
 *    `event.notificationMail = new NotificationMail(); ...; event.save()`(OneToOne cascade로
 *    한 번에 저장됨) 대응. 이 마커가 곧 "발송 대기 큐"이며, [NotificationMailDigestScheduler]가
 *    주기적으로 이 큐를 읽어 실제 메일을 보낸다.
 *
 * 수신자가 비어있으면(legacy `if (event.receivers.isEmpty()) return`) 아무 것도 저장하지 않는다.
 */
@Component
class NotificationEventRecorder(
    private val notificationEventRepository: NotificationEventRepository,
    private val notificationMailRepository: NotificationMailRepository,
    // record()는 전체 알림이 거치는 단일 지점이라
    // 여기 하나만 계측해도 시스템 전체 알림 활동량을 eventType/resourceType별로 볼 수 있다.
    private val meterRegistry: MeterRegistry
) {
    @Transactional
    fun record(event: NotificationEvent, skipWaypoint: Boolean = true): NotificationEvent? {
        meterRegistry.counter(
            "yona.notification.events",
            "eventType", event.eventType.name,
            "resourceType", event.resourceType.name
        ).increment()

        val draftSince = Instant.now().minus(DRAFT_WINDOW)
        val lastEvent = notificationEventRepository
            .findFirstByResourceTypeAndResourceIdAndCreatedAfterOrderByIdDesc(event.resourceType, event.resourceId, draftSince)

        if (lastEvent != null && lastEvent.eventType == event.eventType && lastEvent.senderId == event.senderId) {
            if (skipWaypoint) {
                event.oldValue = lastEvent.oldValue
                notificationMailRepository.findByNotificationEvent(lastEvent)?.let { notificationMailRepository.delete(it) }
                notificationEventRepository.delete(lastEvent)
                if (event.oldValue == event.newValue) {
                    return null
                }
            } else if (event.oldValue == lastEvent.newValue && event.newValue == lastEvent.oldValue) {
                notificationMailRepository.findByNotificationEvent(lastEvent)?.let { notificationMailRepository.delete(it) }
                notificationEventRepository.delete(lastEvent)
                return null
            }
        }

        if (event.receivers.isEmpty()) {
            return null
        }

        val saved = notificationEventRepository.save(event)
        notificationMailRepository.save(NotificationMail(notificationEvent = saved))
        return saved
    }
}
