package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import java.time.Instant

/**
 * yona notification/MergedNotificationEvent.java 대응 (P1-27). [INotificationEvent]를 구현해
 * NotificationEvent(단일 이벤트)와 다형적으로 다뤄진다 — legacy도 두 클래스 모두
 * INotificationEvent를 구현해 호출부(발송 파이프라인)가 "단일 이벤트인지 병합 이벤트인지"를
 * 신경 쓰지 않고 같은 계약으로 다루도록 돼 있다.
 *
 * [main]은 title/sender/resourceType/resourceId/eventType/created 등 "대표 속성"의 출처이고,
 * [messageSources]는 실제 메일 본문에 합쳐질 개별 이벤트들의 순서 목록이다(legacy와 동일하게
 * 렌더링 시 "\n\n---\n\n"로 join, [NotificationMessageResolver.getMessage] 참고). [receivers]는
 * 병합 과정에서 재계산된 값이 있으면 그것을, 없으면 main의 원래 수신자 집합을 그대로 쓴다
 * (legacy MergedNotificationEvent.findReceivers() 대응).
 */
class MergedNotificationEvent private constructor(
    val main: NotificationEvent,
    val messageSources: MutableList<NotificationEvent>,
    private var receiversOverride: Set<User>?
) : INotificationEvent {
    constructor(main: NotificationEvent) : this(main, mutableListOf(main), null)
    constructor(main: NotificationEvent, messageSources: List<NotificationEvent>) : this(main, messageSources.toMutableList(), null)

    override val senderId: Long? get() = main.senderId
    override val title: String get() = main.title
    override val eventType: EventType get() = main.eventType
    override val resourceType: ResourceType get() = main.resourceType
    override val resourceId: String get() = main.resourceId
    override val created: Instant? get() = main.created

    override val receivers: Set<User>
        get() = receiversOverride ?: main.receivers

    // yona MergedNotificationEvent.setReceivers() 대응. INotificationEvent에는 없다(NotificationEvent
    // 쪽 설명 참고) — 이 메서드는 구체 타입(MergedNotificationEvent)으로 호출하는 기존 호출부
    // (NotificationEventMerger 등)에서 계속 그대로 쓰인다.
    fun setReceivers(receivers: Set<User>) {
        receiversOverride = receivers
    }
}
