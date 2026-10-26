package dev.yona.web.access

import dev.yona.access.AccessControl
import dev.yona.security.CurrentUserProvider
import dev.yona.web.error.ErrorViews
import dev.yona.web.error.ViewResultException
import org.springframework.stereotype.Component

/**
 * 사이트 관리자만 쓸 수 있는 컨트롤러(또는 메서드)에 붙인다. v1.6 `@With(SiteManagerAuthAction.class)`이다.
 *
 * 사이트 관리자가 아니면 `error.auth.unauthorized.waringMessage`를 담은 403 화면을 돌려준다([SiteManagerOnlyStage]).
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class SiteManagerOnly

/**
 * [SiteManagerOnly] 한 개를 실행해 사이트 관리자가 아니면 403으로 막는다.
 * v1.6 `utils.SiteManagerAuthAction`(`app/utils/SiteManagerAuthAction.java:33-39`)과 같다.
 *
 * v1.6에서는 이 액션이 `@AnonymousCheck`보다 바깥이라 먼저 실행된다([AccessChainInterceptor]). 그래서 사이트가 익명 접근을 막아도 익명은
 * 로그인 폼이 아니라 403을 받는다.
 */
@Component
class SiteManagerOnlyStage(
    private val currentUserProvider: CurrentUserProvider,
    private val accessControl: AccessControl
) {

    fun run() {
        if (accessControl.isSiteManager(currentUserProvider.get().user)) return
        throw ViewResultException(ErrorViews.forbidden("error.auth.unauthorized.waringMessage"))
    }
}
