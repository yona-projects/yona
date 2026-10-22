package dev.yona.domain.notification

import dev.yona.domain.enumeration.RequestState
import dev.yona.domain.project.Organization
import dev.yona.domain.user.User

/**
 * 조직 가입 요청이 처리되었음을 알리는 애플리케이션 이벤트이다.
 * v1.6 `NotificationEvent.afterOrganizationMemberRequest(organization, user, state)`를 부르던 지점에서 발행한다.
 * 알림을 옮기는 단계에서 수신기를 추가한다([ProjectMemberRequestEvent]와 같은 방식, A-25).
 *
 * @property organization 대상 조직
 * @property user 가입을 요청했거나 승인된 사용자
 * @property state 요청 처리 상태
 * @property actor 처리한 사용자. 익명일 수도 있다
 */
data class OrganizationMemberRequestEvent(val organization: Organization, val user: User, val state: RequestState, val actor: User?)
