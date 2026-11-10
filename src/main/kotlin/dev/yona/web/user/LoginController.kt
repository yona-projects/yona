package dev.yona.web.user

import dev.yona.domain.user.UserRepository
import dev.yona.security.YonaAuthentication
import dev.yona.web.HttpPreference
import dev.yona.web.RedirectSupport
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 로그인 입력이 빠진 요청의 응답과 로그아웃이다. v1.6 `UserApp.login`, `logout`에 해당한다(`app/controllers/UserApp.java:154-345`).
 *
 * 비밀번호 로그인의 판정과 성공, 실패 처리는 스프링의 인증 필터 [PasswordLoginFilter]와 그 처리기([PasswordLoginSuccessHandler], [PasswordLoginFailureHandler])가 맡는다
 * (로드맵 X-43 3단계, `decisions.md` D-129). 필터는 로그인 ID와 비밀번호가 모두 있는 요청만 맡으므로([PasswordLoginRequestMatcher]), 이 컨트롤러의 [login]에는
 * **입력이 빠진 요청만** 온다. 폼은 v1.6처럼 로그인 화면을 400으로 다시 그리고, AJAX는 400 JSON이다.
 */
@RestController
class LoginController(
    private val users: UserRepository,
    private val loginSession: dev.yona.security.LoginSessionService,
    private val audit: dev.yona.domain.audit.AuditBoundary
) {

    @PostMapping("/users/login")
    fun login(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        if (HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to "user.login.required"))
        }
        // v1.6: 안내 문구(flash)와 함께 로그인 화면을 400으로 다시 그린다. 이 안내는 같은 요청의 화면에서 바로 보인다.
        response.status = HttpStatus.BAD_REQUEST.value()
        // v1.6 `login.render("title.login", authInfoForm, null)`은 이동 주소를 비운다
        return LoginFormController.loginPage(null)
            .also { RedirectSupport.flashNow(mapOf(RedirectSupport.WARNING to "user.login.required")) }
    }

    @GetMapping("/users/logout")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        processLogout(request, response)
        // v1.6은 `redirect(request().getHeader("Referer"))`라 Referer가 없으면 `Location: null`로 이동한다(결함 재현, [refactoring-backlog.md] R-03).
        val referer = request.getHeader(HttpHeaders.REFERER)?.takeIf { it.isNotEmpty() } ?: "null"
        return RedirectSupport.see(request, response, referer, mapOf(RedirectSupport.SUCCESS to "user.logout.success"))
    }

    /** 세션의 인증 정보와 remember-me 쿠키를 지운다 (v1.6 `processLogout`). */
    private fun processLogout(request: HttpServletRequest, response: HttpServletResponse) {
        // 로그인한 사용자의 로그아웃만 남긴다(잠긴 계정을 되돌리는 경로는 아직 로그인 전이다)
        val authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().authentication as? YonaAuthentication
        authentication?.let { users.findById(it.principal.userId).orElse(null) }?.let { audit.logout(it) }
        loginSession.end(request, response)
    }

    /** 캐시를 막는 응답 헤더를 붙인다 (v1.6 `UserApp.noCache`). */
    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }
}
