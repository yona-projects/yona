package dev.yona.web.access

import dev.yona.access.AccessControl
import dev.yona.security.CurrentUserProvider
import dev.yona.web.error.ErrorViews
import dev.yona.web.error.ViewResultException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 사이트 관리자만 쓸 수 있는 컨트롤러(또는 메서드)에 붙인다. v1.6 `@With(SiteManagerAuthAction.class)`이다.
 *
 * 사이트 관리자가 아니면 `error.auth.unauthorized.waringMessage`를 담은 403 화면을 돌려준다([SiteManagerOnlyInterceptor]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class SiteManagerOnly

/**
 * [SiteManagerOnly]가 붙은 요청을 가로채 사이트 관리자가 아니면 403으로 막는다.
 * v1.6 `utils.SiteManagerAuthAction`(`app/utils/SiteManagerAuthAction.java:33-39`)과 같다.
 *
 * 로그인 검사([AnonymousCheckInterceptor])가 먼저 실행되므로, 사이트가 익명 접근을 막으면 익명은 로그인 폼으로 간다.
 */
@Component
class SiteManagerOnlyInterceptor(
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val handlerMethod = handler as? HandlerMethod ?: return true
        val guarded = AnnotatedElementUtils.hasAnnotation(handlerMethod.method, SiteManagerOnly::class.java) ||
            AnnotatedElementUtils.hasAnnotation(handlerMethod.beanType, SiteManagerOnly::class.java)
        if (!guarded || accessControl.isSiteManager(currentUserProvider.get().user)) return true
        throw ViewResultException(ErrorViews.forbidden("error.auth.unauthorized.waringMessage"))
    }
}
