package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

private val DRAFT_WINDOW: Duration = Duration.ofSeconds(30)

// v1.6 NotificationEvent가 webhookRequest(...)를 호출하던 이벤트(알림 저장·수신자 유무와 무관). 저장하지 않은 이벤트는 이 목록만 웹훅으로 넘긴다.
// 라벨·공유자·게시글 본문 변경 같은 이벤트는 v1.6에서 웹훅 대상이 아니었고, RESOURCE_DELETED는 v1.6이 이슈에만 보냈으며 삭제된 리소스는
// 리스너가 조회하지 못한다. PR 병합(PULL_REQUEST_STATE_CHANGED→MERGED)은 PullRequestServiceImpl이 동기로 직접 보낸다.
private val WEBHOOK_EVENT_TYPES: Set<EventType> = setOf(
    EventType.NEW_ISSUE, EventType.NEW_COMMENT, EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED,
    EventType.ISSUE_MILESTONE_CHANGED, EventType.ISSUE_BODY_CHANGED, EventType.ISSUE_MOVED, EventType.NEW_POSTING,
    EventType.NEW_PULL_REQUEST, EventType.PULL_REQUEST_COMMIT_CHANGED, EventType.NEW_REVIEW_COMMENT,
    EventType.PULL_REQUEST_REVIEW_STATE_CHANGED, EventType.COMMENT_UPDATED
)

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
    private val meterRegistry: MeterRegistry,
    // v1.6 NotificationEvent.filterReceivers() 대응 — 읽을 수 없는 수신자 제거.
    private val receiverFilter: NotificationReceiverFilter,
    // v1.6 webhookRequest(...)는 알림 저장/수신자 유무와 무관하게 호출됐다. 저장하지 않은 이벤트(수신자 없음·상쇄)는 여기서 직접
    // publish해 WebhookNotificationEventListener가 받게 한다(저장된 이벤트는 호출자가 publish한다).
    private val eventPublisher: org.springframework.context.ApplicationEventPublisher
) {
    @Transactional
    fun record(event: NotificationEvent, skipWaypoint: Boolean = true, publishWhenUnsaved: Boolean = true): NotificationEvent? {
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
                    publishUnsaved(event, publishWhenUnsaved)
                    return null
                }
            } else if (event.oldValue == lastEvent.newValue && event.newValue == lastEvent.oldValue) {
                notificationMailRepository.findByNotificationEvent(lastEvent)?.let { notificationMailRepository.delete(it) }
                notificationEventRepository.delete(lastEvent)
                publishUnsaved(event, publishWhenUnsaved)
                return null
            }
        }

        // 읽을 수 없는 수신자(멘션으로 추가된 비멤버 등)와 loginId 없는 수신자를 제거한다 — legacy filterReceivers().
        receiverFilter.filter(event)

        if (event.receivers.isEmpty()) {
            publishUnsaved(event, publishWhenUnsaved)
            return null
        }

        val saved = notificationEventRepository.save(event)
        notificationMailRepository.save(NotificationMail(notificationEvent = saved))
        return saved
    }

    private fun publishUnsaved(event: NotificationEvent, enabled: Boolean) {
        if (enabled && event.eventType in WEBHOOK_EVENT_TYPES) eventPublisher.publishEvent(event)
    }
}
