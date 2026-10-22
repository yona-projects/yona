package dev.yona.web.access

import dev.yona.config.YonaProperties
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * [AnonymousCheck]가 붙은 요청을 가로채 익명 사용자를 로그인 폼으로 보낸다.
 * v1.6 `actions.AnonymousCheckAction`(`app/actions/AnonymousCheckAction.java:42-62`)과 같다.
 *
 * 메서드의 애너테이션이 클래스의 애너테이션보다 우선한다.
 */
@Component
class AnonymousCheckInterceptor(
    private val currentUserProvider: CurrentUserProvider,
    private val properties: YonaProperties
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val check = (handler as? HandlerMethod)?.let {
            AnnotatedElementUtils.findMergedAnnotation(it.method, AnonymousCheck::class.java)
                ?: AnnotatedElementUtils.findMergedAnnotation(it.beanType, AnonymousCheck::class.java)
        } ?: return true

        val loginRequired = !properties.allowsAnonymousAccess || check.requiresLogin
        if (!loginRequired || !currentUserProvider.get().isAnonymous) return true

        val flash = if (check.displaysFlashMessage) mapOf(RedirectSupport.WARNING to "user.login.alert") else emptyMap()
        val redirect = RedirectSupport.see(request, response, "/users/loginform?redirectUrl=${request.requestURI}", flash)
        response.status = redirect.statusCode.value()
        response.setHeader(HttpHeaders.LOCATION, redirect.headers.getFirst(HttpHeaders.LOCATION))
        return false
    }
}
