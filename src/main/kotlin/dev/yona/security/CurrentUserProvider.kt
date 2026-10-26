package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder

/**
 * 현재 요청의 사용자를 알려 주는 서비스이다. v1.6 `UserApp.currentUser()`의 대응이다.
 *
 * 인증 정보는 [YonaAuthenticationFilter]가 세션, remember-me 쿠키, API 토큰 순으로 확인해 보안 컨텍스트에
 * 채워 둔다. 여기서는 그 사용자 번호로 DB의 최신 정보를 읽고, 같은 요청 안에서는 한 번만 읽는다.
 *
 * 세션이나 쿠키로 식별된 사용자가 그 사이 잠겼으면 v1.6처럼 익명으로 취급한다
 * (`getUserFromSession`이 LOCKED를 무효 세션으로 처리). API 토큰으로 식별된 사용자는 상태를 보지 않는다.
 */
@Component
class CurrentUserProvider(private val users: UserRepository) {

    fun get(): CurrentUser {
        val attributes = RequestContextHolder.getRequestAttributes()
        (attributes?.getAttribute(CACHE_KEY, RequestAttributes.SCOPE_REQUEST) as? CurrentUser)?.let { return it }

        val current = resolve()
        attributes?.setAttribute(CACHE_KEY, current, RequestAttributes.SCOPE_REQUEST)
        return current
    }

    private fun resolve(): CurrentUser {
        val authentication = SecurityContextHolder.getContext().authentication as? YonaAuthentication
            ?: return CurrentUser.ANONYMOUS
        val user = users.findById(authentication.principal.userId).orElse(null) ?: return CurrentUser.ANONYMOUS
        if (user.state.isLockedOut() && !authentication.viaToken) return CurrentUser.ANONYMOUS
        return CurrentUser(user)
    }

    private companion object {
        const val CACHE_KEY = "dev.yona.security.CurrentUser"
    }
}
