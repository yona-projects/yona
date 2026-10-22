package dev.yona.domain.pullrequest

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.user.User

/**
 * 풀 리퀘스트가 만들어졌음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewPullRequest`를 부르던 지점에서 발행한다.
 * 알림 서브시스템이 받아 알림 이벤트와 메일을 처리한다(D-14).
 */
data class PullRequestCreatedActivity(val pullRequest: PullRequest, val actor: User)

/**
 * 풀 리퀘스트의 상태, 커밋, 병합 상태가 바뀌었음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterPullRequestUpdated`,
 * `afterPullRequestCommitChanged`, `afterMerge`를 부르던 지점에서 발행한다.
 *
 * @property type `PULL_REQUEST_STATE_CHANGED`(값은 상태 이름 소문자), `PULL_REQUEST_COMMIT_CHANGED`(값은 쓰지 않는다),
 *   `PULL_REQUEST_MERGED`(새 값은 `conflict`, `resolved`, `merged`)
 */
data class PullRequestChangedActivity(val pullRequest: PullRequest, val type: EventType, val oldValue: String?, val newValue: String?, val actor: User)

/**
 * 풀 리퀘스트의 리뷰 상태가 바뀌었음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterReviewed`를 부르던 지점에서 발행한다.
 *
 * @property action 리뷰를 마쳤으면 `DONE`, 취소했으면 `CANCEL`
 */
data class PullRequestReviewedActivity(val pullRequest: PullRequest, val action: dev.yona.domain.enumeration.PullRequestReviewAction, val actor: User)
