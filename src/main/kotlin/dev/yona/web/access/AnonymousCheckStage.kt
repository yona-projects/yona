package dev.yona.web.access

import dev.yona.config.YonaProperties
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

/**
 * [AnonymousCheck] 한 개를 실행해 익명 사용자를 로그인 폼으로 보낸다.
 * v1.6 `actions.AnonymousCheckAction`(`app/actions/AnonymousCheckAction.java:42-62`)과 같다.
 *
 * 어노테이션을 어떤 순서로 실행하는지는 [AccessChainInterceptor]가 정한다. 메서드와 클래스에 모두 붙었으면 둘 다 실행한다.
 */
@Component
class AnonymousCheckStage(
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties
) {

    /** 통과하면 `true`, 로그인 폼으로 보냈으면 응답을 쓰고 `false`이다. */
    fun run(check: AnonymousCheck, request: HttpServletRequest, response: HttpServletResponse): Boolean {
        val loginRequired = !properties.allowsAnonymousAccess || check.requiresLogin
        if (!loginRequired || !currentUserProvider.get().isAnonymous) return true

        val flash = if (check.displaysFlashMessage) mapOf(RedirectSupport.WARNING to "user.login.alert") else emptyMap()
        val redirect = RedirectSupport.see(request, response, "/users/loginform?redirectUrl=${request.requestURI}", flash)
        response.status = redirect.statusCode.value()
        response.setHeader(HttpHeaders.LOCATION, redirect.headers.getFirst(HttpHeaders.LOCATION))
        return false
    }
}
