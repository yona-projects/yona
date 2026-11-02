package dev.yona.web.user

import dev.yona.security.TwoFactorLogin
import dev.yona.security.TwoFactorSubmission
import dev.yona.web.RedirectSupport
import dev.yona.web.HttpPreference
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/**
 * 로그인의 2단계 화면이다(추가 개발 항목 X-2, `decisions.md` D-69). 2단계 인증을 켠 사용자가 비밀번호(또는 소셜 로그인, 메일 링크)를 거친 뒤 인증 앱 코드나 백업 코드를 입력한다.
 * 웹 폼은 화면과 이동으로, AJAX 로그인 팝업은 JSON으로 같은 판정을 따른다(`Accept` 헤더로 구분한다). 진행 중인 로그인이 없으면 로그인 화면으로 보낸다.
 */
@Controller
class TwoFactorLoginController(private val twoFactorLogin: TwoFactorLogin, private val users: dev.yona.domain.user.UserRepository, private val webAuthn: dev.yona.domain.twofactor.WebAuthnService) {

    @GetMapping(TwoFactorLogin.CHALLENGE_PATH)
    fun challenge(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        val pending = twoFactorLogin.pending(request) ?: return RedirectSupport.see(request, response, LOGIN_FORM)
        val securityKey = users.findById(pending.userId).orElse(null)?.let { webAuthn.hasSecurityKey(it) } ?: false
        return ModelAndView("user/login-two-factor").addObject("title", "title.twoFactor").addObject("securityKey", securityKey)
    }

    /** 보안 키 인증 요청(`navigator.credentials.get`의 입력 JSON)을 만든다. 진행 중인 로그인이 없거나 보안 키가 없으면 403이다. */
    @PostMapping("/users/login/2fa/webauthn/options", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun securityKeyOptions(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> {
        noCache(response)
        val options = twoFactorLogin.startSecurityKey(request, WebAuthnContexts.of(request)) ?: return message("user.twoFactor.expired")
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(options)
    }

    /** 보안 키의 인증 응답을 확인한다. 결과는 JSON이다: 성공은 `{"redirect": 이동할 주소}`이다. */
    @PostMapping("/users/login/2fa/webauthn", produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseBody
    fun securityKeySubmit(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<*> {
        noCache(response)
        val rememberDevice = request.getParameter("rememberDevice")?.lowercase() in setOf("true", "on", "yes", "1")
        return when (val result = twoFactorLogin.submitSecurityKey(request, response, request.getParameter("credential").orEmpty(), WebAuthnContexts.of(request), rememberDevice)) {
            is TwoFactorSubmission.Success -> ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("redirect" to (result.redirectUrl ?: "/")))
            TwoFactorSubmission.Invalid -> message("user.twoFactor.invalid")
            TwoFactorSubmission.Locked -> message("user.twoFactor.locked")
            TwoFactorSubmission.Expired -> message("user.twoFactor.expired")
        }
    }

    @PostMapping(TwoFactorLogin.CHALLENGE_PATH)
    @ResponseBody
    fun submit(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        val json = HttpPreference.isJsonPreferred(request.getHeader(HttpHeaders.ACCEPT))
        val result = twoFactorLogin.submit(request, response, request.getParameter("code").orEmpty(), request.getParameter("rememberDevice")?.lowercase() in setOf("true", "on", "yes", "1"))
        return if (json) jsonResult(result) else formResult(request, response, result)
    }

    private fun jsonResult(result: TwoFactorSubmission): ResponseEntity<*> = when (result) {
        is TwoFactorSubmission.Success -> ResponseEntity.ok().contentType(MediaType("text", "plain", Charsets.UTF_8)).body("{}")
        TwoFactorSubmission.Invalid -> message("user.twoFactor.invalid")
        TwoFactorSubmission.Locked -> message("user.twoFactor.locked")
        TwoFactorSubmission.Expired -> message("user.twoFactor.expired")
    }

    private fun formResult(request: HttpServletRequest, response: HttpServletResponse, result: TwoFactorSubmission): Any = when (result) {
        is TwoFactorSubmission.Success -> RedirectSupport.see(request, response, result.redirectUrl ?: "/", result.flash)
        TwoFactorSubmission.Invalid -> RedirectSupport.see(request, response, TwoFactorLogin.CHALLENGE_PATH, mapOf(RedirectSupport.WARNING to "user.twoFactor.invalid"))
        TwoFactorSubmission.Locked -> RedirectSupport.see(request, response, LOGIN_FORM, mapOf(RedirectSupport.WARNING to "user.twoFactor.locked"))
        TwoFactorSubmission.Expired -> RedirectSupport.see(request, response, LOGIN_FORM, mapOf(RedirectSupport.WARNING to "user.twoFactor.expired"))
    }

    private fun message(key: String): ResponseEntity<Map<String, String>> = ResponseEntity.status(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(mapOf("message" to key))

    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    private companion object {
        const val LOGIN_FORM = "/users/loginform?redirectUrl="
    }
}
