package com.github.yonaprojects.yona.domain.watch

import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.user.User

interface WatchService {
    fun watch(user: User, resourceType: ResourceType, resourceId: String)
    fun unwatch(user: User, resourceType: ResourceType, resourceId: String)

    // yona ResourcePersistAdapter.java의 postDelete()(deleteRelatedWatch/deleteRelatedUnwatch)
    // 대응. Ebean의 전역 BeanPersistAdapter 훅과 달리 JPA에는 동등한 범용 엔티티 삭제 리스너가
    // 없어, 리소스 삭제 시 attachmentService.deleteAll()과 동일한 위치에서 명시적으로 호출한다.
    fun deleteAll(resourceType: ResourceType, resourceId: String)
    fun isWatching(user: User, resourceType: ResourceType, resourceId: String): Boolean
    fun findWatchers(resourceType: ResourceType, resourceId: String): Set<User>
    fun findUnwatchers(resourceType: ResourceType, resourceId: String): Set<User>

    // eventType: yona Watch.filterReceivers()의 UserProjectNotification 뮤트 필터 대응. 프로젝트를
    // 통해서만(명시적 리소스 감시/기본 감시자가 아님) 감시 중인 사용자에게만 적용된다.
    fun findActualWatchers(
        baseWatchers: Set<User>,
        resourceType: ResourceType,
        resourceId: String,
        projectId: Long?,
        allowedWatchersOnly: Boolean = true,
        eventType: EventType? = null
    ): Set<User>

    // yona NotificationEvent.getMandatoryReceivers()의 UserProjectNotification 규칙 — 해당 이벤트를 옵트인(allowed=true)한 사용자를
    // 수신자에 더하고, 거부(allowed=false)한 사용자는 작성자·담당자여도 뺀다(findEventWatchersByEventType/findEventUnwatchersByEventType).
    // 글·댓글의 "필수 수신자" 흐름(상태/담당자/마일스톤/본문 변경, 게시글 수정, 댓글)에서만 쓴다.
    fun applyEventPreferences(receivers: Set<User>, projectId: Long?, eventType: EventType): Set<User>
}
