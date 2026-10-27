package dev.yona.web.welcome

import dev.yona.config.YonaProperties
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
    private val properties: YonaProperties
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod || !secret.invalid) return true
        if (secret.restartRequired) throw ViewResultException(restartView())
        if (!request.method.equals("POST", ignoreCase = true)) throw ViewResultException(secretView(emptyMap(), request))

        val errors = validate(request)
        if (errors.isNotEmpty()) throw ViewResultException(secretView(errors, request).also { it.status = HttpStatus.BAD_REQUEST })
        secret.configure(request.getParameter("name"), request.getParameter("email"), request.getParameter("password"))
        throw ViewResultException(restartView())
    }

    /** 입력을 검사한다. 항목마다 오류 메시지 키를 모은다 (v1.6 `hasError`). */
    private fun validate(request: HttpServletRequest): Map<String, List<String>> {
        val errors = LinkedHashMap<String, MutableList<String>>()
        fun reject(field: String, key: String) { errors.getOrPut(field) { mutableListOf() } += key }
        val loginId = request.getParameter("loginId")
        if (loginId.isNullOrBlank() || loginId != SiteSecret.DEFAULT_ADMIN_LOGIN_ID) reject("loginId", "user.wrongloginId.alert")
        // v1.6은 비어 있는 ID를 "admin이 아니다" 검사에서 한 번 더 거절해 같은 오류가 두 번 쌓인다. 화면에는 같은 문구가 한 번만 보이도록 하나로 묶는다.
        val password = request.getParameter("password")
        if (password.isNullOrBlank()) reject("password", "user.wrongPassword.alert")
        if (password != request.getParameter("retypedPassword")) reject("retypedPassword", "user.confirmPassword.alert")
        val email = request.getParameter("email")
        if (email.isNullOrBlank()) reject("email", "validation.invalidEmail")
        // v1.6는 User.email의 @Constraints.Email 검사를 폼 바인딩이 먼저 하므로 형식이 틀린 이메일은 user.wrongEmail.alert로 거절한다
        if (!email.isNullOrBlank() && !dev.yona.domain.user.SignupService.isValidEmailFormat(email)) reject("email", "user.wrongEmail.alert")
        if (!email.isNullOrBlank() && signup.isEmailExist(email)) reject("email", "user.email.duplicate")
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
        .addObject("failedToUpdate", secret.failedToUpdate)
}
