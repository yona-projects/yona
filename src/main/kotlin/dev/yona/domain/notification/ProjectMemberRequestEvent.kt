package dev.yona.domain.notification

import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.project.Project
import dev.yona.domain.user.User

/**
 * 프로젝트 가입 요청이 처리되었음을 알리는 애플리케이션 이벤트이다.
 * v1.6 `NotificationEvent.afterMemberRequest(project, user, state)`를 부르던 지점에서 발행한다.
 *
 * 알림 서브시스템(알림 이벤트 저장, 메일 발송)이 이 이벤트를 받아 처리한다. 비동기 처리는 Spring 이벤트와
 * `@Async`로 한다는 결정(D-14)에 따른 연결 지점이며, 알림을 옮기는 단계에서 수신기를 추가한다.
 *
 * @property project 대상 프로젝트
 * @property user 가입을 요청했거나 승인된 사용자
 * @property state 요청 처리 상태
 * @property actor 처리한 사용자(현재 로그인한 사용자). 익명일 수도 있다
 */
data class ProjectMemberRequestEvent(val project: Project, val user: User, val state: RequestState, val actor: User?)
