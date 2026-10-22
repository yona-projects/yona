package dev.yona.web.access

/**
 * 익명 사용자 접근을 검사할 컨트롤러 메서드(또는 클래스)에 붙인다. v1.6 `controllers.annotation.AnonymousCheck`이다.
 *
 * 사이트가 익명 접근을 막고 있거나(`yona.allows-anonymous-access=false`) [requiresLogin]이 `true`이면,
 * 익명 사용자를 로그인 폼(`/users/loginform?redirectUrl=<요청 경로>`)으로 보낸다
 * ([AnonymousCheckInterceptor]).
 *
 * @property requiresLogin 사이트 설정과 무관하게 로그인을 요구한다
 * @property displaysFlashMessage 로그인 폼에서 `user.login.alert` 안내를 보여 준다
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class AnonymousCheck(
    val requiresLogin: Boolean = false,
    val displaysFlashMessage: Boolean = false
)
