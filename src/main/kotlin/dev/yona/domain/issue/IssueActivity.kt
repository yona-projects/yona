package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.user.User

/**
 * 이슈가 만들어졌음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewIssue`를 부르던 지점에서 발행한다.
 * 초안은 발행하지 않는다. 알림 서브시스템이 이 이벤트를 받아 알림 이벤트와 메일, 웹훅을 처리한다(D-14).
 *
 * @property issue 만들어진 이슈
 * @property actor 만든 사용자
 */
data class IssueCreatedActivity(val issue: Issue, val actor: User)

/**
 * 이슈의 상태, 담당자, 본문, 마일스톤, 라벨, 공유자 등이 바뀌었음을 알리는 애플리케이션 이벤트이다.
 * v1.6 `NotificationEvent.afterStateChanged`, `afterAssigneeChanged`, `afterIssueBodyChanged`, `afterMilestoneChanged`,
 * `afterIssueLabelChanged`, `afterIssueSharerChanged`, `afterIssueMoved`를 부르던 지점에서 발행한다.
 *
 * @property type 바뀐 종류
 * @property oldValue 이전 값(상태는 `open`처럼 소문자 이름, 담당자는 로그인 ID 등). 없으면 `null`
 * @property newValue 바뀐 값
 * @property actor 바꾼 사용자
 */
data class IssueChangedActivity(val issue: Issue, val type: EventType, val oldValue: String?, val newValue: String?, val actor: User?)

/**
 * 이슈가 지워졌음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterResourceDeleted`를 부르던 지점에서 발행한다.
 * 지워진 뒤에는 이슈를 읽을 수 없으므로 알림에 필요한 값을 함께 담는다. 초안은 발행하지 않는다.
 */
data class IssueDeletedActivity(val projectId: Long, val issueNumber: Long, val title: String?, val body: String?, val actor: User)
