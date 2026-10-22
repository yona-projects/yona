package dev.yona.domain.notification

import dev.yona.access.ResourceLookup
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.user.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한 통의 메일로 보낼 알림 묶음이다. v1.6 `notification.MergedNotificationEvent`에 해당한다.
 *
 * @property main 제목, 보낸 사람, 이동 주소, 종류를 정하는 대표 이벤트
 * @property sources 문구를 만들 이벤트들. 메일 본문에는 `---` 구분선으로 이어 붙인다
 * @property receivers 받을 사용자. 대표 이벤트의 수신자와 다르게 나눈 경우에 따로 정한다
 */
class MergedNotification(
    val main: NotificationEvent,
    val sources: MutableList<NotificationEvent> = mutableListOf(main),
    var receivers: Set<User> = main.receivers.toSet()
)

/**
 * 메일을 보내기 전에 알림 이벤트를 묶는다. v1.6 `NotificationMail.mergeEvents`(`app/models/NotificationMail.java:196-282`)에 해당한다.
 *
 * 같은 사용자가 같은 이슈에서 **새 댓글을 달고 상태를 바꾼** 두 알림은 한 통의 메일로 보낸다(상태 변경이 대표이고 댓글 문구가 앞선다).
 * 두 알림의 수신자가 다르면 셋으로 나눈다: 둘 다 받는 사람은 합친 메일, 댓글만 받는 사람과 상태 변경만 받는 사람은 각자의 메일이다.
 */
@Service
class NotificationMerger(private val lookup: ResourceLookup) {

    /** 리소스와 보낸 사람이 같은지 비교하는 키이다. */
    private data class Key(val type: ResourceType?, val id: String?, val senderId: Long?)

    /**
     * [events]를 묶는다.
     *
     * @param events 만든 시각 오름차순으로 정렬된 이벤트
     * @return 묶은 결과. 묶는 중 오류가 나면 v1.6처럼 묶지 않은 채로 돌려준다
     */
    @Transactional(readOnly = true)
    fun merge(events: List<NotificationEvent>): List<MergedNotification> = try {
        mergeInternal(events)
    } catch (e: RuntimeException) {
        org.slf4j.LoggerFactory.getLogger(javaClass).warn("알림 이벤트를 묶지 못했다", e)
        events.map { MergedNotification(it) }
    }

    private fun mergeInternal(events: List<NotificationEvent>): List<MergedNotification> {
        val result = ArrayDeque<MergedNotification>()
        val stateChanged = HashMap<Key, MergedNotification>()
        // 최신 이벤트부터 거꾸로 훑으며 댓글에 대응하는 상태 변경을 찾는다.
        for (event in events.asReversed()) {
            if (event.eventType == EventType.ISSUE_STATE_CHANGED || event.eventType == EventType.REVIEW_THREAD_STATE_CHANGED) {
                val merged = MergedNotification(event)
                stateChanged[Key(event.resourceType, event.resourceId, event.senderId)] = merged
                result.addFirst(merged)
                continue
            }
            if (event.eventType == EventType.NEW_COMMENT || event.eventType == EventType.NEW_REVIEW_COMMENT) {
                val container = lookup.get(requireNotNull(event.resourceType), requireNotNull(event.resourceId)).container
                val state = stateChanged.remove(Key(container?.type, container?.id, event.senderId))
                if (state != null) {
                    val stateReceivers = state.receivers
                    val commentReceivers = event.receivers.toSet()
                    if (stateReceivers.ids() == commentReceivers.ids()) {
                        state.sources.add(0, event)
                        continue
                    }
                    val common = commentReceivers.filter { it.id in stateReceivers.ids() }.toSet()
                    result.addFirst(MergedNotification(state.main, mutableListOf(event, state.main), common))
                    result.addFirst(MergedNotification(event, mutableListOf(event), commentReceivers.filter { it !in common }.toSet()))
                    state.receivers = stateReceivers.filter { it.id !in common.ids() }.toSet()
                    continue
                }
            }
            result.addFirst(MergedNotification(event))
        }
        return result.toList()
    }

    private fun Set<User>.ids(): Set<Long?> = mapTo(HashSet()) { it.id }
}
