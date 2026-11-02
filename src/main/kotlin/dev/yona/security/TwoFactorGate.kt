package dev.yona.security

import dev.yona.domain.twofactor.KnownDeviceService
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * 요청이 2단계 인증을 거치지 않고 사용자로 인정받아도 되는지 판정한다(추가 개발 항목 X-2, `decisions.md` D-69). 2단계 인증을 켜지 않았거나, 요청에 그 사용자의 유효한 알려진 기기
 * 쿠키([KnownDeviceService.COOKIE])가 있으면 통과한다. 로그인 입구, 세션 시작, 로그인 유지 쿠키 확인이 같은 규칙을 쓴다.
 */
@Component
class TwoFactorGate(private val twoFactor: TwoFactorService, private val devices: KnownDeviceService) {

    /** [user]가 이 요청에서 2단계 없이 통과하면 `true`이다. */
    fun passes(user: User, request: HttpServletRequest): Boolean =
        !twoFactor.isEnabled(user) || devices.isKnown(user, request.cookies?.firstOrNull { it.name == KnownDeviceService.COOKIE }?.value)
}
