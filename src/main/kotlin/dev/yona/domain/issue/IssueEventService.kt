package dev.yona.domain.issue

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.EventType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 이슈 타임라인 이벤트(`issue_event`)를 기록한다. v1.6 `IssueEvent.add`, `addWithoutSkipEvent`
 * (`app/models/IssueEvent.java:63-140`)에 해당한다.
 *
 * 같은 이슈에서 같은 사용자가 같은 종류를 [YonaProperties.issueEventDraftTime](기본 30초) 안에 연달아 바꾸면 이벤트를 합친다.
 * `A → B`, `B → C`는 `A → C`로 합치고, `A → B`, `B → A`는 둘 다 지운다. 라벨과 공유자처럼 중간 값이 의미 있는 이벤트는
 * `skipWaypoints = false`로 기록해 되돌리는 경우에만 지운다.
 */
@Service
class IssueEventService(private val events: IssueEventRepository, private val properties: YonaProperties) {

    /**
     * 이벤트를 기록한다.
     *
     * @param skipWaypoints `true`이면 연달아 바꾼 중간 값을 건너뛰어 합친다
     */
    @Transactional
    fun record(issue: Issue, type: EventType, oldValue: String?, newValue: String?, senderLoginId: String?, skipWaypoints: Boolean = true) {
        var old = oldValue
        val since = Date(System.currentTimeMillis() - properties.issueEventDraftTime.toMillis())
        val last = events.findFirstByIssueIdAndCreatedAfterOrderByIdDesc(requireNotNull(issue.id), since)
        if (last != null && last.eventType == type && last.senderLoginId == senderLoginId) {
            if (skipWaypoints) {
                // A -> B, B -> C ==> A -> C. A -> B, B -> A ==> 둘 다 지운다.
                old = last.oldValue
                events.delete(last)
                if (old == newValue) return
            } else if (old == last.newValue && newValue == last.oldValue) {
                events.delete(last)
                return
            }
        }
        events.save(IssueEvent(created = Date(), senderLoginId = senderLoginId, issue = issue, eventType = type, oldValue = old, newValue = newValue))
    }
}
