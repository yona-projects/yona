package com.github.yonaprojects.yona.domain.notification

import com.github.yonaprojects.yona.domain.board.PostingCommentRepository
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.enumeration.ResourceType
import com.github.yonaprojects.yona.domain.issue.IssueCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import org.springframework.stereotype.Component

/**
 * yona models/NotificationMail.java의 mergeEvents() 대응.
 *
 * 이벤트를 리소스+발신자로 묶어(legacy EventHashKey 대응, [MergeKey]), 상태변경
 * (ISSUE_STATE_CHANGED/REVIEW_THREAD_STATE_CHANGED)과 그 직후 같은 사람이 남긴 댓글
 * (NEW_COMMENT/NEW_REVIEW_COMMENT)을 하나로 합치거나(수신자 집합이 같을 때), 수신자 집합이
 * 다르면 세 갈래(교집합 전용/댓글 전용/상태변경 전용)로 쪼갠다. [events]는 created 오름차순으로
 * 주어져야 하며(legacy `NotificationMail t2.notificationEvent.created ASC` 정렬과 동일),
 * 내부적으로는 legacy와 동일하게 최신 이벤트부터 역순으로 순회한다.
 *
 * yona는 ISSUE_STATE_CHANGED류 이벤트에 `ISSUE_STATE` 같은 세분화된 resourceType을 쓰지만,
 * legacy는 이 모두를 `issue.asResource()`(=ISSUE_POST)로 취급하므로,
 * 댓글의 컨테이너 키와 맞아떨어지도록 [selfMergeKey]에서 ISSUE_POST로 정규화한다.
 *
 * NEW_REVIEW_COMMENT(컨테이너=COMMENT_THREAD, [ReviewComment.thread])도 legacy `event.getResource()
 * .getContainer()`와 동일하게 [containerMergeKey]에서 정규화한다. REVIEW_THREAD_STATE_CHANGED
 * 이벤트는 [CodeReviewServiceImpl]이 이미 `resourceType=COMMENT_THREAD`로 발행하므로 [selfMergeKey]가
 * 별도 정규화 없이 그대로 키로 쓸 수 있다. NEW_COMMENT로 발행되는 COMMIT_COMMENT는 legacy에서도
 * "커밋 상태변경" 개념 자체가 없어 대응되는 self-key가 결코 존재하지 않으므로(항상 미스) 컨테이너 키를
 * 계산해도 동작에 차이가 없어 정규화하지 않는다.
 */
@Component
class NotificationEventMerger(
    private val issueCommentRepository: IssueCommentRepository,
    private val postingCommentRepository: PostingCommentRepository,
    private val reviewCommentRepository: ReviewCommentRepository
) {
    private data class MergeKey(val resourceType: ResourceType, val resourceId: String, val senderId: Long?)

    fun mergeEvents(events: List<NotificationEvent>): List<MergedNotificationEvent> {
        val result = ArrayDeque<MergedNotificationEvent>()
        val stateChangedEvents = HashMap<MergeKey, MergedNotificationEvent>()

        for (event in events.asReversed()) {
            if (event.eventType == EventType.ISSUE_STATE_CHANGED || event.eventType == EventType.REVIEW_THREAD_STATE_CHANGED) {
                val stateChangedEvent = MergedNotificationEvent(event)
                selfMergeKey(event)?.let { stateChangedEvents[it] = stateChangedEvent }
                result.addFirst(stateChangedEvent)
                continue
            }

            if (event.eventType == EventType.NEW_COMMENT || event.eventType == EventType.NEW_REVIEW_COMMENT) {
                val containerKey = containerMergeKey(event)
                val stateChangedEvent = containerKey?.let { stateChangedEvents.remove(it) }

                if (stateChangedEvent != null) {
                    val stateReceivers = stateChangedEvent.receivers
                    val commentReceivers = event.receivers

                    if (stateReceivers == commentReceivers) {
                        // a-b. 수신자가 완전히 같으면 댓글을 상태변경 이벤트의 메시지 소스 맨 앞에 끼워넣고,
                        // 댓글 자체는 별도 이벤트로 추가하지 않는다(병합됨).
                        stateChangedEvent.messageSources.add(0, event)
                        continue
                    } else {
                        // 수신자 집합이 다르면 세 갈래로 쪼갠다: 교집합(둘 다 받아야 할 사람)/댓글 전용/상태변경 전용.
                        val intersect = stateReceivers.intersect(commentReceivers)

                        val merged = MergedNotificationEvent(
                            stateChangedEvent.main,
                            listOf(event) + stateChangedEvent.messageSources
                        )
                        merged.setReceivers(intersect)
                        result.addFirst(merged)

                        val commentOnly = MergedNotificationEvent(event)
                        commentOnly.setReceivers(commentReceivers - intersect)
                        result.addFirst(commentOnly)

                        stateChangedEvent.setReceivers(stateReceivers - intersect)
                        continue
                    }
                }
            }

            result.addFirst(MergedNotificationEvent(event))
        }

        return result.toList()
    }

    private fun selfMergeKey(event: NotificationEvent): MergeKey? {
        return when (event.eventType) {
            EventType.ISSUE_STATE_CHANGED -> MergeKey(ResourceType.ISSUE_POST, event.resourceId, event.senderId)
            EventType.REVIEW_THREAD_STATE_CHANGED -> MergeKey(event.resourceType, event.resourceId, event.senderId)
            else -> null
        }
    }

    private fun containerMergeKey(event: NotificationEvent): MergeKey? {
        return when (event.resourceType) {
            ResourceType.ISSUE_COMMENT -> {
                val id = event.resourceId.toLongOrNull() ?: return null
                val comment = issueCommentRepository.findById(id).orElse(null) ?: return null
                MergeKey(ResourceType.ISSUE_POST, comment.issue.id.toString(), event.senderId)
            }
            ResourceType.NONISSUE_COMMENT -> {
                val id = event.resourceId.toLongOrNull() ?: return null
                val comment = postingCommentRepository.findById(id).orElse(null) ?: return null
                MergeKey(ResourceType.BOARD_POST, comment.posting.id.toString(), event.senderId)
            }
            ResourceType.REVIEW_COMMENT -> {
                val id = event.resourceId.toLongOrNull() ?: return null
                val comment = reviewCommentRepository.findById(id).orElse(null) ?: return null
                val threadId = comment.thread?.id ?: return null
                MergeKey(ResourceType.COMMENT_THREAD, threadId.toString(), event.senderId)
            }
            else -> null
        }
    }
}
