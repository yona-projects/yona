package dev.yona.web.user

import dev.yona.security.LoginCompletion
import dev.yona.security.LoginDeniedException
import dev.yona.security.LoginResult
import dev.yona.security.LoginSessionService
import dev.yona.security.PasswordLoginDetails
import dev.yona.security.TwoFactorLogin
import dev.yona.web.RedirectSupport
import dev.yona.util.UrlPaths
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder

/** 두 처리기가 함께 쓰는 응답 도구이다. 필터는 디스패처 서블릿 앞에서 돌아 `ResponseEntity`와 `RedirectSupport.see`를 쓸 수 없어 응답을 직접 쓴다. */
internal object PasswordLoginResponses {

    /** 로그인 유지를 골랐는지이다. Play 폼 바인딩(Spring DataBinder)은 체크박스가 보내는 on을 참으로 읽는다: true, on, yes, 1 */
    fun wantsRememberMe(request: HttpServletRequest): Boolean = request.getParameter("rememberMe")?.lowercase() in setOf("true", "on", "yes", "1")

    /** 303으로 [location]에 보낸다. [flash]는 다음 요청에서 한 번만 보인다. */
    fun redirect(request: HttpServletRequest, response: HttpServletResponse, location: String, flash: Map<String, Any> = emptyMap()) {
        if (flash.isNotEmpty()) RedirectSupport.saveFlashBeforeDispatch(request, response, flash)
        response.status = HttpStatus.SEE_OTHER.value()
        response.setHeader(HttpHeaders.LOCATION, UrlPaths.encodeLocation(location))
    }

    fun json(response: HttpServletResponse, status: HttpStatus, body: String) {
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(body)
    }

    /** 메시지 키 하나를 담은 JSON이다. 키는 상수라 따옴표나 역슬래시가 없다. */
    fun message(response: HttpServletResponse, status: HttpStatus, key: String) = json(response, status, "{\"message\":\"$key\"}")

    /** 로그인 폼 주소이다. 원래 가려던 `redirectUrl`을 그대로 이어 붙인다 (v1.6 `getLoginFormURLWithRedirectURL`). */
    fun loginFormUrl(request: HttpServletRequest): String = "/users/loginform?redirectUrl=" + (request.getParameter("redirectUrl") ?: "")

    /**
     * 경로의 각 구간을 URL 인코딩한다 (v1.6 `encodedPath`).
     *
     * `//example.com`처럼 `/`로 시작하는 값은 그대로 외부 주소로 이동한다. v1.6의 결함이며 그대로 따른다([refactoring-backlog.md] R-06).
     */
    fun encodedPath(path: String): String {
        // Java split처럼 끝의 빈 구간을 버린다. 남는 구간이 없으면(/, //) "/"이다
        val segments = path.split("/").dropLastWhile { it.isEmpty() }
        if (segments.isEmpty()) return "/"
        return segments.joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }
    }
}

/**
 * 비밀번호 로그인에 성공한 뒤의 처리이다(로드맵 X-43 3단계, `decisions.md` D-129). 인증 객체의 `details`([PasswordLoginDetails])에서 사용자와 LDAP이 남긴 안내를 꺼내
 * v1.6 `UserApp.loginByFormRequest`, `loginByAjaxRequest`의 성공 처리를 그대로 한다.
 *
 * - **2단계 인증을 켠 사용자는 비밀번호만으로 로그인되지 않는다**(X-2, D-69). 2단계 화면으로 보내고 로그인은 코드를 확인한 뒤에 끝난다. 이때 스프링이 보안 컨텍스트에
 *   둔 인증은 지운다. 로그인 성공 감사도 2단계를 마친 뒤에 남는다.
 * - 켜지 않은 사용자는 [LoginCompletion]이 로그인 유지 쿠키, 언어 갱신, 세션 시작(세션 식별자 회전)을 하고 `password` 방법의 로그인 성공을 감사에 남긴다.
 * - 폼은 `redirectUrl`(이 사이트 안의 경로만, 아니면 첫 화면)로 303 이동하며 안내를 남기고, AJAX는 `text/plain`의 `{}`(v1.6 `ok("{}")`)이다.
 */
@Component
class PasswordLoginSuccessHandler(
    private val completion: LoginCompletion,
    private val attempts: dev.yona.security.TwoFactorAttempts,
    private val twoFactorLogin: TwoFactorLogin,
    private val webAuthn: dev.yona.domain.twofactor.WebAuthnService,
    private val audit: dev.yona.domain.audit.AuditBoundary
) : AuthenticationSuccessHandler {

    override fun onAuthenticationSuccess(request: HttpServletRequest, response: HttpServletResponse, authentication: Authentication) {
        val details = authentication.details as PasswordLoginDetails
        val user = details.user
        attempts.clear(PasswordLoginFailureHandler.limitKey(request))
        val twoFactor = twoFactorLogin.required(user, request)
        // 2단계 인증을 켠 사용자의 로그인 성공은 2단계를 마친 뒤에 남긴다(X-2, D-69)
        if (!twoFactor) audit.loginSuccess(user, "password")
        val rememberMe = PasswordLoginResponses.wantsRememberMe(request)
        if (PasswordLoginFilter.wantsJson(request)) {
            if (twoFactor) {
                // 팝업이 코드 입력 단계로 바뀌도록 알린다(X-2, D-69). 로그인은 코드를 확인한 뒤에 끝난다
                twoFactorLogin.begin(request, user, "password", rememberMe, null)
                SecurityContextHolder.clearContext()
                PasswordLoginResponses.json(response, HttpStatus.OK, "{\"twoFactor\":true,\"securityKey\":${webAuthn.hasSecurityKey(user)}}")
                return
            }
            completion.complete(request, response, user, rememberMe, twoFactorVerified = false)
            // v1.6의 `ok("{}")`는 문자열이라 Content-Type이 text/plain이다.
            response.status = HttpStatus.OK.value()
            response.contentType = "text/plain;charset=UTF-8"
            response.writer.write("{}")
            return
        }
        // 이 사이트 안의 경로가 아니면 첫 화면으로 보낸다. v1.6은 `//example.com`으로도 이동했다(R-06)
        val target = RedirectSupport.localTarget(request.getParameter("redirectUrl"), request)?.let { PasswordLoginResponses.encodedPath(it) } ?: "/"
        if (twoFactor) {
            // 2단계 인증을 켠 사용자는 비밀번호만으로 로그인되지 않는다(X-2, D-69). 2단계 화면에서 코드를 확인한 뒤에 로그인을 끝낸다
            twoFactorLogin.begin(request, user, "password", rememberMe, target, details.flash.mapValues { it.value.toString() })
            SecurityContextHolder.clearContext()
            PasswordLoginResponses.redirect(request, response, TwoFactorLogin.CHALLENGE_PATH)
        } else {
            completion.complete(request, response, user, rememberMe, twoFactorVerified = false)
            PasswordLoginResponses.redirect(request, response, target, details.flash)
        }
    }
}

/**
 * 비밀번호 로그인이 거절된 뒤의 처리이다(로드맵 X-43 3단계, `decisions.md` D-129). [LoginDeniedException]의 [LoginResult]에 따라 v1.6의 안내와 응답을 그대로 만들고
 * 실패를 감사에 남긴다. 이유는 `invalid_credentials`(없는 사용자와 틀린 비밀번호를 같게 둔다), `locked`, `deleted`, `locked_after_authentication`, `sso_only`이다.
 * 비밀번호는 어디에도 남기지 않는다(X-9, D-62).
 *
 * | 결과 | 폼 | AJAX |
 * |---|---|---|
 * | SSO 전용 | 로그인 폼, `sso.error.ssoOnly` | 403 |
 * | 승인 대기 잠금 | 로그인 폼, `user.locked` | 403 `user.locked` |
 * | 삭제 | 잘못된 자격과 같다(계정의 존재를 알리지 않는다, usersec-F11) | 403 `user.login.invalid` |
 * | 잠김(5번 틀림) | 로그인 폼, `user.login.tooMany` | 429 `user.login.tooMany` |
 * | 맞은 뒤 잠김 | 로그아웃하고 Referer로(없으면 `null` 문자열, R-03), `user.locked`와 로그아웃 안내 | 403 `user.locked` |
 * | 잘못된 자격 | 로그인 폼, `user.login.invalid`(LDAP 안내가 있으면 함께) | 403 `user.login.invalid` |
 */
@Component
class PasswordLoginFailureHandler(
    private val loginSession: LoginSessionService,
    private val audit: dev.yona.domain.audit.AuditBoundary,
    private val attempts: dev.yona.security.TwoFactorAttempts
) : AuthenticationFailureHandler {

    /** 이 요청의 아이디가 연달아 틀려 잠겨 있는지이다. */
    fun isLocked(request: HttpServletRequest): Boolean = attempts.isLocked(limitKey(request))

    companion object {
        /** 횟수를 세는 키이다. 아이디와 이메일은 대소문자를 가리지 않으므로 소문자로 맞춘다. */
        fun limitKey(request: HttpServletRequest): String = "login:" + request.getParameter("loginIdOrEmail").orEmpty().trim().lowercase()
    }

    override fun onAuthenticationFailure(request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) {
        val loginKey = request.getParameter("loginIdOrEmail")
        val result = (exception as? LoginDeniedException)?.result ?: LoginResult.InvalidCredentials
        when (result) {
            LoginResult.SsoOnly -> audit.loginFailure(loginKey, "sso_only", denied = true)
            LoginResult.Locked -> audit.loginFailure(loginKey, "locked", denied = true)
            LoginResult.Deleted -> audit.loginFailure(loginKey, "deleted", denied = true)
            LoginResult.TooManyAttempts -> audit.loginFailure(loginKey, "too_many_attempts", denied = true)
            LoginResult.LockedAfterAuthentication -> audit.loginFailure(loginKey, "locked_after_authentication", denied = true)
            LoginResult.InvalidCredentials, is LoginResult.InvalidWithNotice -> audit.loginFailure(loginKey, "invalid_credentials", denied = false)
            is LoginResult.Success -> Unit
        }
        // 비밀번호가 틀린 경우(없는 계정, 삭제된 계정 포함)만 횟수에 센다. 잠겨서 거절한 요청은 세지 않는다
        if (result == LoginResult.InvalidCredentials || result is LoginResult.InvalidWithNotice || result == LoginResult.Deleted) attempts.recordFailure(limitKey(request))
        if (PasswordLoginFilter.wantsJson(request)) {
            when (result) {
                LoginResult.TooManyAttempts -> PasswordLoginResponses.message(response, HttpStatus.TOO_MANY_REQUESTS, "user.login.tooMany")
                LoginResult.SsoOnly -> PasswordLoginResponses.message(response, HttpStatus.FORBIDDEN, "sso.error.ssoOnly")
                LoginResult.Locked, LoginResult.LockedAfterAuthentication -> PasswordLoginResponses.message(response, HttpStatus.FORBIDDEN, "user.locked")
                else -> PasswordLoginResponses.message(response, HttpStatus.FORBIDDEN, "user.login.invalid")
            }
            return
        }
        val formUrl = PasswordLoginResponses.loginFormUrl(request)
        when (result) {
            LoginResult.SsoOnly -> PasswordLoginResponses.redirect(request, response, formUrl, mapOf(RedirectSupport.WARNING to "sso.error.ssoOnly"))
            LoginResult.Locked -> PasswordLoginResponses.redirect(request, response, formUrl, mapOf(RedirectSupport.WARNING to "user.locked"))
            LoginResult.TooManyAttempts -> PasswordLoginResponses.redirect(request, response, formUrl, mapOf(RedirectSupport.WARNING to "user.login.tooMany"))
            LoginResult.LockedAfterAuthentication -> {
                // v1.6: 안내 문구를 남기고 logout()을 호출한다. logout()이 성공 문구를 덧붙이고 Referer로 돌려보낸다.
                loginSession.end(request, response)
                // logout()은 `redirect(Referer 머리글)`이라 v1.6은 Referer가 없으면 `Location: null`이었다(R-03, usersec-F13). 고쳤다: 홈으로 보낸다.
                val referer = request.getHeader(HttpHeaders.REFERER)?.takeIf { it.isNotEmpty() } ?: "/"
                PasswordLoginResponses.redirect(request, response, referer, mapOf(RedirectSupport.WARNING to "user.locked", RedirectSupport.SUCCESS to "user.logout.success"))
            }
            // LDAP이 남긴 안내(가입 승인 대기 등)에 인증 실패 안내가 더해진다.
            is LoginResult.InvalidWithNotice ->
                PasswordLoginResponses.redirect(request, response, formUrl, result.flash + (RedirectSupport.WARNING to "user.login.invalid"))
            else -> PasswordLoginResponses.redirect(request, response, formUrl, mapOf(RedirectSupport.WARNING to "user.login.invalid"))
        }
    }
}
