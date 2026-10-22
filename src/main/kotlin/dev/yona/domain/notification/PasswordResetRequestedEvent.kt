package dev.yona.domain.notification

import dev.yona.domain.user.User

/**
 * 비밀번호 재설정 메일을 보내야 함을 알리는 애플리케이션 이벤트이다.
 * v1.6 `PasswordResetApp.sendPasswordResetMail`이 재설정 주소(`/resetPassword?s={token}`)를 메일로 보내던 지점이다.
 * 메일 서브시스템을 옮길 때 수신기를 추가한다(A-25와 같은 방식).
 *
 * @property user 비밀번호를 재설정할 사용자
 * @property token 재설정 주소에 담을 값
 */
data class PasswordResetRequestedEvent(val user: User, val token: String)
