package dev.yona.security

import dev.yona.domain.user.User
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

/**
 * 사용자를 로그인된 상태로 만든다. v1.6 `UserApp.addUserInfoToSession`에 해당한다.
 *
 * 로그인 전에 쓰던 세션 식별자를 버려 세션 고정 공격을 막고, 인증 정보를 세션에 저장한다.
 * 잠긴 사용자는 로그인시키지 않는다.
 */
@Component
class LoginSessionService {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    /** [user]를 현재 요청의 세션에 로그인시킨다. 잠겼거나 지워진 사용자이면 아무것도 하지 않는다. */
    fun start(request: HttpServletRequest, response: HttpServletResponse, user: User) {
        if (user.state.isLockedOut()) return
        if (request.getSession(false) != null) request.changeSessionId()
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = YonaAuthentication.of(user)
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
    }

    /** 세션의 인증 정보와 remember-me 쿠키를 지운다 (v1.6 `UserApp.processLogout`). */
    fun end(request: HttpServletRequest, response: HttpServletResponse) {
        SecurityContextHolder.clearContext()
        request.getSession(false)?.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        YonaAuthenticationFilter.discardRememberMeCookie(response)
    }
}
