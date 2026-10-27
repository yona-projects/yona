package dev.yona.domain.notification

import dev.yona.access.AccessControl
import dev.yona.access.GlobalResource
import dev.yona.access.ResourceLookup
import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.Operation
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 새로 만들 알림 이벤트이다. v1.6 `NotificationEvent`의 `createFrom`과 각 `after…`가 채우던 값이다.
 *
 * @property sender 이벤트를 일으킨 사용자
 * @property resourceType 이벤트가 걸린 리소스 종류
 * @property resourceId 그 리소스의 번호
 * @property receivers 받을 사용자(저장하기 전에 읽기 권한과 프로젝트 알림 설정으로 거른다)
 */
data class NewNotification(
    val sender: User, val resourceType: ResourceType, val resourceId: String, val eventType: EventType, val title: String?,
    val oldValue: String?, val newValue: String?, val receivers: Collection<User>
)

/**
 * 알림 이벤트를 저장한다. v1.6 `NotificationEvent.add`, `addWithoutSkipEvent`, `filterReceivers`, `deleteBy`
 * (`app/models/NotificationEvent.java:400-520`)에 해당한다. 이벤트마다 메일로 보낼 대기열 행(`notification_mail`)도 함께 만든다.
 */
@Service
class NotificationEvents(
    private val events: NotificationEventRepository,
    private val mails: NotificationMailRepository,
    private val watches: WatchRepository,
    private val unwatches: UnwatchRepository,
    private val projectNotifications: UserProjectNotificationRepository,
    private val lookup: ResourceLookup,
    private val accessControl: AccessControl,
    private val properties: YonaProperties,
    private val exclusions: NotificationExclusions
) {

    /**
     * 이벤트를 저장한다. 같은 리소스의 직전 이벤트가 설정한 시간(`notificationDraftTime`) 안에 같은 사용자가 만든 같은 종류이면 합친다.
     * A→B 다음에 B→C이면 A→C 하나가 되고, B→A이면 둘 다 없어진다. 거른 수신자가 없으면 저장하지 않는다.
     *
     * @return 저장한 이벤트. 저장하지 않았으면 `null`
     */
    @Transactional
    fun add(note: NewNotification): NotificationEvent? {
        var oldValue = note.oldValue
        lastEvent(note)?.takeIf { sameUserAndType(note, it) }?.let { last ->
            // 직전이 A -> B이고 지금이 B -> C이면 A -> C로 합친다.
            oldValue = last.oldValue
            delete(last)
            // A -> B 다음의 B -> A는 둘 다 지운다.
            if (oldValue == note.newValue) return null
        }
        val excluded = exclusions.current()
        val receivers = filterReceivers(note).filter { it.id !in excluded }
        if (receivers.isEmpty()) return null
        return save(note, oldValue, receivers)
    }

    /**
     * 리소스에 걸린 [eventType] 이벤트의 새 값을 바꾼다. 받은 메일의 본문을 정리한 뒤 알림 내용을 맞출 때 쓴다.
     * 이벤트를 저장하지 않았으면(받을 사람이 없었으면) 아무 일도 하지 않는다.
     */
    @Transactional
    fun replaceNewValue(type: ResourceType, resourceId: String, eventType: EventType, newValue: String?) {
        events.findByResourceTypeAndResourceId(type, resourceId).filter { it.eventType == eventType }.forEach {
            it.newValue = newValue
            events.save(it)
        }
    }

    /**
     * 연달아 일어난 변경을 합치지 않는 방식이다(공유자, 라벨 변경). 직전 이벤트의 값을 정확히 되돌릴 때만 직전 이벤트를 지우고 저장하지 않는다.
     * 수신자는 공유자를 추가하는 이벤트(`ISSUE_SHARER_CHANGED`에서 이전 값이 비고 새 값이 있는 경우)일 때만 거른다.
     */
    @Transactional
    fun addWithoutSkip(note: NewNotification): NotificationEvent? {
        lastEvent(note)?.takeIf { sameUserAndType(note, it) && note.oldValue == it.newValue && note.newValue == it.oldValue }?.let {
            delete(it)
            return null
        }
        val addingSharer = note.eventType == EventType.ISSUE_SHARER_CHANGED && note.oldValue.isNullOrBlank() && !note.newValue.isNullOrBlank()
        val receivers = if (addingSharer) filterReceivers(note) else note.receivers.toList()
        if (receivers.isEmpty()) return null
        return save(note, note.oldValue, receivers)
    }

    /** 리소스에 걸린 이벤트와 대기열을 모두 지운다. */
    @Transactional
    fun deleteBy(type: ResourceType, resourceId: String) {
        events.findByResourceTypeAndResourceId(type, resourceId).forEach { delete(it) }
    }

    private fun lastEvent(note: NewNotification): NotificationEvent? =
        events.findFirstByResourceIdAndResourceTypeAndCreatedAfterOrderByIdDesc(note.resourceId, note.resourceType, Date(System.currentTimeMillis() - properties.notificationDraftTime.toMillis()))

    private fun sameUserAndType(note: NewNotification, last: NotificationEvent) = last.eventType == note.eventType && last.senderId == note.sender.id

    private fun save(note: NewNotification, oldValue: String?, receivers: Collection<User>): NotificationEvent {
        val event = events.save(
            NotificationEvent(
                title = note.title, senderId = note.sender.id, created = Date(), resourceType = note.resourceType, resourceId = note.resourceId,
                eventType = note.eventType, oldValue = oldValue, newValue = note.newValue, receivers = LinkedHashSet(receivers)
            )
        )
        mails.save(NotificationMail(notificationEvent = event))
        return event
    }

    private fun delete(event: NotificationEvent) {
        mails.deleteAll(mails.findByNotificationEventId(requireNotNull(event.id)))
        events.delete(event)
        events.flush()
    }

    /**
     * 수신자 중 아이디가 없거나, 이벤트의 리소스를 읽을 수 없거나, 프로젝트를 구독 중이면서 이 종류의 알림을 끈 사용자를 뺀다
     * (프로젝트를 구독하지 않으면 설정과 관계없이 남긴다). 프로젝트에 속하지 않은 리소스(조직 등)는 거르지 않는다.
     */
    private fun filterReceivers(note: NewNotification): List<User> {
        val resource = lookup.get(note.resourceType, note.resourceId)
        val project: Project = (if (resource is GlobalResource) null else resource.project) ?: return note.receivers.toList()
        val projectId = requireNotNull(project.id)
        return note.receivers.filter { receiver ->
            val userId = receiver.id
            when {
                receiver.loginId == null || userId == null -> false
                !accessControl.isAllowed(receiver, resource, Operation.READ) -> false
                !isWatching(userId, projectId) -> true
                else -> projectNotifications.findByUserIdAndProjectIdAndNotificationType(userId, projectId, note.eventType).firstOrNull()?.allowed != false
            }
        }
    }

    private fun isWatching(userId: Long, projectId: Long): Boolean =
        watches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.PROJECT, projectId.toString()) &&
            !unwatches.existsByUserIdAndResourceTypeAndResourceId(userId, ResourceType.PROJECT, projectId.toString())
}
