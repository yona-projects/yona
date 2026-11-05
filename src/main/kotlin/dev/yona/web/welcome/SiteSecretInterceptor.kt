package dev.yona.web.welcome

import dev.yona.config.YonaProperties
import dev.yona.domain.user.LoginIdRules
import dev.yona.domain.user.SignupService
import dev.yona.security.SiteSecret
import dev.yona.web.error.ViewResultException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.ModelAndView

/**
 * 사이트 비밀 키를 정하기 전에는 컨트롤러로 가는 모든 요청을 가로챈다. v1.6 `Global.onRequest`와 `getConfigSecretAction`, `getRestartAction`
 * (`app/Global.java:248-259, 281-370`)에 해당한다. 정적 자원은 컨트롤러가 아니므로 그대로 나간다.
 *
 * - 재시작이 필요한 상태이면 어떤 요청이든 재시작 안내(`welcome/restart`)를 200으로 돌려준다.
 * - POST이면 기본 관리자 계정 입력을 검사한다. 오류가 있으면 400으로 입력 화면을 다시 그리고, 없으면 계정을 바꾸고 새 비밀 키를 쓴 뒤 재시작 안내를 돌려준다.
 * - 그 밖의 요청은 입력 화면(`welcome/secret`)이다.
 */
@Component
class SiteSecretInterceptor(
    private val secret: SiteSecret,
    private val signup: SignupService,
    private val users: dev.yona.domain.user.UserRepository,
    private val properties: YonaProperties
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod) return true
        throw ViewResultException(pendingScreen(request) ?: return true)
    }

    /**
     * 비밀 키를 정하는 중이면 이 요청에 돌려줄 화면이다. 정한 상태이면 `null`이다. 라우트가 없는 GET(v1.6의 포괄 라우트)도 같은 화면을 받도록
     * [dev.yona.web.error.GlobalErrorHandler]가 이 메서드를 부른다.
     */
    fun pendingScreen(request: HttpServletRequest): ModelAndView? {
        if (!secret.invalid) return null
        if (secret.restartRequired) return restartView()
        if (!request.method.equals("POST", ignoreCase = true)) return secretView(emptyMap(), request)

        val errors = validate(request)
        if (errors.isNotEmpty()) return secretView(errors, request).also { it.status = HttpStatus.BAD_REQUEST }
        try {
            secret.configure(request.getParameter("name"), request.getParameter("email"), request.getParameter("password"))
        } catch (e: SiteSecret.SecretWriteException) {
            // 계정은 바뀌지 않았다. 같은 폼을 다시 보이고 다시 시도하라고 안내한다(R-57-5)
            return secretView(mapOf("password" to listOf("app.secret.writeFailed")), request).also { it.status = HttpStatus.INTERNAL_SERVER_ERROR }
        }
        return restartView()
    }

    /** 입력을 검사한다. 항목마다 오류 메시지 키를 모은다 (v1.6 `hasError`). */
    private fun validate(request: HttpServletRequest): Map<String, List<String>> {
        val errors = LinkedHashMap<String, MutableList<String>>()
        // v1.6은 같은 오류를 거듭 쌓아 같은 문구가 여러 번 보였다(R-57-1). 같은 항목에 같은 키가 이미 있으면 더하지 않는다
        fun reject(field: String, key: String) { errors.getOrPut(field) { mutableListOf() }.let { if (key !in it) it += key } }
        // v1.6 `hasError`는 값을 읽어 곧바로 `.equals(...)`를 부르므로 항목이 아예 없으면 NPE로 500이 난다(loginId, password). 값이 비어 있는 것과는 다르다.
        val loginId = request.getParameter("loginId") ?: throw NullPointerException("loginId")
        // 폼 바인딩 단계(`User.loginId`의 @Required, @Pattern, @ValidateWith)의 오류가 먼저 쌓이고, 이어서 `hasError`가 거절한 오류가 쌓인다.
        // Play의 @Required는 빈 문자열만 값이 없다고 보고(공백은 값이다), @Pattern은 빈 문자열을 통과시킨다.
        if (loginId.isEmpty()) reject("loginId", "error.required")
        else {
            if (!LoginIdRules.isValidFormat(loginId)) reject("loginId", "user.wrongloginId.alert")
            if (LoginIdRules.isReserved(loginId)) reject("loginId", "validation.reservedWord")
        }
        if (loginId.isBlank()) reject("loginId", "user.wrongloginId.alert")
        if (loginId != SiteSecret.DEFAULT_ADMIN_LOGIN_ID) reject("loginId", "user.wrongloginId.alert")
        // v1.6은 이름을 읽지도 않고 그대로 저장했다. 이름은 비어 있으면 안 된다(R-57-2)
        if (request.getParameter("name").isNullOrBlank()) reject("name", "error.required")
        val password = request.getParameter("password")
        if (password.isNullOrBlank()) reject("password", "user.wrongPassword.alert")
        if (password == null) throw NullPointerException("password")
        if (password != request.getParameter("retypedPassword")) reject("retypedPassword", "user.confirmPassword.alert")
        val email = request.getParameter("email")
        if (email.isNullOrBlank()) reject("email", "validation.invalidEmail")
        // v1.6는 User.email의 @Constraints.Email 검사를 폼 바인딩이 먼저 하므로 형식이 틀린 이메일은 user.wrongEmail.alert로 거절한다
        if (!email.isNullOrBlank() && !dev.yona.domain.user.SignupService.isValidEmailFormat(email)) reject("email", "user.wrongEmail.alert")
        // 기본 관리자 자신의 이메일은 중복이 아니다. v1.6은 기본값을 그대로 두면 이미 쓰는 이메일이라고 거절했다(R-57-3)
        if (!email.isNullOrBlank() && signup.isEmailExist(email) && !email.equals(users.findByLoginIdIgnoreCase(SiteSecret.DEFAULT_ADMIN_LOGIN_ID)?.email, ignoreCase = true)) reject("email", "user.email.duplicate")
        return errors
    }

    private fun secretView(errors: Map<String, List<String>>, request: HttpServletRequest) = ModelAndView("welcome/secret")
        .addObject("siteName", properties.siteName)
        .addObject("loginId", SiteSecret.DEFAULT_ADMIN_LOGIN_ID)
        .addObject("errors", errors)
        .addObject("name", request.getParameter("name"))
        .addObject("email", request.getParameter("email"))

    private fun restartView() = ModelAndView("welcome/restart")
        .addObject("siteName", properties.siteName)
}
