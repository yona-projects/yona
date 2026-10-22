package dev.yona.domain.notification

import dev.yona.domain.user.Email

/**
 * 보조 이메일의 확인 메일을 보내야 함을 알리는 애플리케이션 이벤트이다.
 * v1.6 `Email.sendValidationEmail`이 확인 주소를 메일로 보내던 지점이다. 메일 서브시스템을 옮길 때 수신기를 추가한다(A-30).
 *
 * @property email 확인할 보조 이메일(토큰이 정해진 상태)
 * @property confirmPath 확인 주소의 경로. `/user/email/confirm/{id}/{token}`
 */
data class EmailValidationRequestedEvent(val email: Email, val confirmPath: String)
