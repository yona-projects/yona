package dev.yona.web.user

import dev.yona.domain.user.UserRepository
import dev.yona.security.LocalAuthenticator
import dev.yona.security.LoginResult
import dev.yona.security.YonaAuthentication
import dev.yona.security.YonaAuthenticationFilter
import dev.yona.web.HttpPreference
import dev.yona.web.RedirectSupport
import dev.yona.web.layout.FlashMessageTranslator
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URLEncoder

/**
 * 로그인과 로그아웃이다. v1.6 `UserApp.login`, `loginByFormRequest`, `loginByAjaxRequest`, `logout`에 해당한다
 * (`app/controllers/UserApp.java:154-345`).
 *
 * 폼 요청과 AJAX 요청은 `Accept` 헤더로 구분하며([HttpPreference]), 판정 규칙은 [LocalAuthenticator]를 공유한다.
 * 안내 문구는 v1.6처럼 메시지 **키**(`user.login.invalid` 등)로 전달하고 화면에서 번역한다.
 *
 */
@RestController
class LoginController(
    private val authenticator: LocalAuthenticator,
    private val users: UserRepository,
    private val flashTranslator: FlashMessageTranslator,
    private val loginSession: dev.yona.security.LoginSessionService,
    private val properties: dev.yona.config.YonaProperties,
    private val home: dev.yona.web.home.HomeController
) {


    @PostMapping("/users/login")
    @Transactional
    fun login(
        request: HttpServletRequest,
        response: HttpServletResponse
    ): Any {
        noCache(response)
        if (properties.socialLoginOnly) return socialLoginOnly(request, response)
        return if (HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))) {
            loginByAjax(request, response)
        } else {
            loginByForm(request, response)
        }
    }

    /**
     * 소셜 로그인만 허용하는 사이트에서 로그인 요청이 오면 안내와 함께 홈 화면을 그린다 (v1.6 `UserApp.login`의
     * `useSocialLoginOnly` 분기: `flash(error)` 뒤 `Application.index()`). 안내는 이 화면에 바로 보이고 다음 요청에도 한 번 보인다.
     */
    private fun socialLoginOnly(request: HttpServletRequest, response: HttpServletResponse): Any {
        val notice = mapOf<String, Any>(RedirectSupport.ERROR to "app.warn.support.social.login.only")
        RedirectSupport.saveFlash(request, response, notice)
        val page = home.index(request, response)
        return if (page is org.springframework.web.servlet.ModelAndView) page.addObject("flashMessages", flashTranslator.translate(notice)) else page
    }

    @GetMapping("/users/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        processLogout(request, response)
        // v1.6은 Referer가 없으면 null 리다이렉트로 오류가 난다. 홈으로 보낸다([refactoring-backlog.md] R-03).
        val referer = request.getHeader(HttpHeaders.REFERER)?.takeIf { it.isNotEmpty() } ?: "/"
        return RedirectSupport.see(request, response, referer, mapOf(RedirectSupport.SUCCESS to "user.logout.success"))
    }

    // --- 폼 로그인 ---

    private fun loginByForm(request: HttpServletRequest, response: HttpServletResponse): Any {
        val loginKey = request.getParameter("loginIdOrEmail")
        val password = request.getParameter("password")
        if (loginKey.isNullOrEmpty() || password.isNullOrEmpty()) {
            // v1.6: 안내 문구(flash)와 함께 로그인 화면을 400으로 다시 그린다. 이 안내는 같은 요청의 화면에서 바로 보인다.
            response.status = HttpStatus.BAD_REQUEST.value()
            return LoginFormController.loginPage(request.getParameter("redirectUrl"))
                .addObject("flashMessages", flashTranslator.translate(mapOf(RedirectSupport.WARNING to "user.login.required")))
        }

        return when (val result = authenticator.authenticate(loginKey, password)) {
            is LoginResult.Success -> {
                completeLogin(request, response, result)
                val redirectUrl = request.getParameter("redirectUrl")
                RedirectSupport.see(
                    request, response, if (redirectUrl.isNullOrEmpty()) "/" else encodedPath(redirectUrl), result.flash
                )
            }
            LoginResult.Locked ->
                RedirectSupport.see(request, response, loginFormUrl(request), mapOf(RedirectSupport.WARNING to "user.locked"))
            LoginResult.Deleted ->
                RedirectSupport.see(request, response, loginFormUrl(request), mapOf(RedirectSupport.WARNING to "user.deleted"))
            LoginResult.LockedAfterAuthentication -> {
                // v1.6: 안내 문구를 남기고 logout()을 호출한다. logout()이 성공 문구를 덧붙이고 Referer로 돌려보낸다.
                processLogout(request, response)
                val referer = request.getHeader(HttpHeaders.REFERER)?.takeIf { it.isNotEmpty() } ?: "/"
                RedirectSupport.see(
                    request, response, referer,
                    mapOf(RedirectSupport.WARNING to "user.locked", RedirectSupport.SUCCESS to "user.logout.success")
                )
            }
            LoginResult.InvalidCredentials ->
                RedirectSupport.see(request, response, loginFormUrl(request), mapOf(RedirectSupport.WARNING to "user.login.invalid"))
            // LDAP이 남긴 안내(가입 승인 대기 등)에 인증 실패 안내가 더해진다.
            is LoginResult.InvalidWithNotice ->
                RedirectSupport.see(request, response, loginFormUrl(request), result.flash + (RedirectSupport.WARNING to "user.login.invalid"))
        }
    }

    // --- AJAX 로그인 ---

    private fun loginByAjax(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> {
        val loginKey = request.getParameter("loginIdOrEmail")
        val password = request.getParameter("password")
        if (loginKey.isNullOrEmpty() || password.isNullOrEmpty()) return message(HttpStatus.BAD_REQUEST, "user.login.required")

        return when (val result = authenticator.authenticate(loginKey, password)) {
            is LoginResult.Success -> {
                completeLogin(request, response, result)
                // v1.6의 `ok("{}")`는 문자열이라 Content-Type이 text/plain이다.
                ResponseEntity.ok().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("{}")
            }
            LoginResult.Locked, LoginResult.LockedAfterAuthentication -> message(HttpStatus.FORBIDDEN, "user.locked")
            LoginResult.Deleted -> message(HttpStatus.NOT_FOUND, "user.deleted")
            LoginResult.InvalidCredentials, is LoginResult.InvalidWithNotice -> message(HttpStatus.FORBIDDEN, "user.login.invalid")
        }
    }

    private fun message(status: HttpStatus, key: String): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to key))

    // --- 공통 ---

    /** 로그인 성공 처리: 세션에 기록하고, 로그인 유지를 켰으면 쿠키를 만들고, 사용자의 언어를 갱신한다. */
    private fun completeLogin(request: HttpServletRequest, response: HttpServletResponse, result: LoginResult.Success) {
        val user = result.user
        if (request.getParameter("rememberMe").toBoolean()) {
            response.addCookie(Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, "${user.loginId}:${user.password}").apply {
                maxAge = YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE
                path = "/"
            })
        }
        user.lang = LocaleContextHolder.getLocale().toLanguageTag()
        users.save(user)

        loginSession.start(request, response, user)
    }

    /** 세션의 인증 정보와 remember-me 쿠키를 지운다 (v1.6 `processLogout`). */
    private fun processLogout(request: HttpServletRequest, response: HttpServletResponse) = loginSession.end(request, response)

    /** 로그인 폼 주소이다. 원래 가려던 `redirectUrl`을 그대로 이어 붙인다 (v1.6 `getLoginFormURLWithRedirectURL`). */
    private fun loginFormUrl(request: HttpServletRequest): String =
        "/users/loginform?redirectUrl=" + (request.getParameter("redirectUrl") ?: "")

    /**
     * 경로의 각 구간을 URL 인코딩한다 (v1.6 `encodedPath`).
     *
     * `//example.com`처럼 `/`로 시작하는 값은 그대로 외부 주소로 이동한다. v1.6의 결함이며 그대로 따른다
     * ([refactoring-backlog.md] R-06).
     */
    private fun encodedPath(path: String): String {
        val segments = path.split("/")
        if (segments.all { it.isEmpty() }) return "/"
        return segments.joinToString("/") { URLEncoder.encode(it, Charsets.UTF_8).replace("+", "%20") }
    }

    /** 캐시를 막는 응답 헤더를 붙인다 (v1.6 `UserApp.noCache`). */
    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }
}
