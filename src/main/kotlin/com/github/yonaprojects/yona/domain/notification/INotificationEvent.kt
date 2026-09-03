package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User
import java.time.Instant

// yona notification/INotificationEvent.java 대응. NotificationEvent(단일 이벤트)와
// MergedNotificationEvent(병합 이벤트) 둘 다 이 인터페이스를 구현해 다형적으로 다뤄진다 —
// 이전에는 MergedNotificationEvent가 이 인터페이스 없이 NotificationEvent를 그냥 감싸는 단순
// 래퍼 클래스였으나(사용자 지적으로 되돌림), yona처럼 두 구현체가 공통 계약을 갖도록 정정한다.
//
// yona INotificationEvent의 getMessage(Lang)/getPlainMessage(Lang)/getUrlToView()/resourceExists()는
// Ebean active-record 모델이 정적 파인더(예: Resource.exists(...))로 직접 DB를 조회하는 메서드였다.
// yona는 그 책임을 이미 별도 Spring 서비스(NotificationMessageResolver/NotificationUrlResolver 등,
// P1-27)로 분리해뒀고 엔티티에 리포지토리를 주입하는 건 JPA 관례에 맞지 않으므로, 이 인터페이스에는
// "이벤트 자신이 들고 있는 상태"만 남기고 메시지/URL 해석은 계속 이 인터페이스를 받는 외부 서비스가
// 담당한다(NotificationMessageResolver.getMessage(NotificationEvent|MergedNotificationEvent, ...)
// 오버로드가 이미 legacy MergedNotificationEvent.getMessage()의 "\n\n---\n\n" join 동작을 그대로 재현).
interface INotificationEvent {
    val senderId: Long?
    val title: String
    val eventType: EventType
    val resourceType: ResourceType
    val resourceId: String
    val created: Instant?

    // yona INotificationEvent.findReceivers() 대응. setReceivers()는 인터페이스에 넣지 않았다 —
    // NotificationEvent의 receivers는 JPA @ManyToMany 프로퍼티라 Kotlin이 이미 setReceivers(Set)
    // 합성 setter를 만드는데, 별도 override fun setReceivers(...)를 추가하면 JVM 소거 후
    // 시그니처가 겹쳐 컴파일이 깨진다(둘 다 setReceivers(Set)). NotificationEvent를 다루는 기존
    // 코드는 어차피 전부 `event.receivers = x` 프로퍼티 대입만 쓰고 있어 실질적인 손실이 없고,
    // MergedNotificationEvent는 자신만의 setReceivers(receivers: Set<User>) 메서드를 여전히
    // 그대로 노출한다(구체 타입으로 호출하는 NotificationEventMerger 등 기존 호출부는 변경 없음).
    val receivers: Set<User>
}
