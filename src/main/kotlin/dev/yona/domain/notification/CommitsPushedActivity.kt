package dev.yona.domain.notification

import dev.yona.domain.project.Project
import dev.yona.domain.user.User

/**
 * 커밋이 푸시되었음을 알리는 애플리케이션 이벤트이다. v1.6 `NotificationEvent.afterNewCommits`를 부르던 지점(`CommitsNotificationActor`)에서 발행한다.
 *
 * @property commits 푸시로 들어온 커밋의 (짧은 해시, 짧은 메시지, 전체 해시)
 * @property refNames 푸시한 ref 이름
 */
data class CommitsPushedActivity(val project: Project, val sender: User, val commits: List<Triple<String, String, String>>, val refNames: List<String>)
