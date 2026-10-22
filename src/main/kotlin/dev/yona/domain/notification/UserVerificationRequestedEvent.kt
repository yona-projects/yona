package dev.yona.domain.notification

import dev.yona.domain.user.User
import dev.yona.domain.user.UserVerification

/**
 * 가입한 사용자에게 이메일 인증 메일을 보내야 함을 알리는 애플리케이션 이벤트이다.
 * v1.6 `UserApp.sendMailAfterUserCreation`이 인증 링크(`/verify/{loginId}/{verificationCode}`)와 계정 안내를
 * 담아 메일을 보내던 지점이다. 메일 서브시스템을 옮길 때 수신기를 추가한다(A-25와 같은 방식).
 *
 * @property user 가입한 사용자
 * @property verification 인증 코드를 담은 인증 정보
 */
data class UserVerificationRequestedEvent(val user: User, val verification: UserVerification)
