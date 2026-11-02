package dev.yona.web.user

import dev.yona.domain.twofactor.WebAuthnRegistration
import dev.yona.domain.twofactor.WebAuthnService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.RedirectSupport
import dev.yona.web.access.AnonymousCheck
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.ModelAndView

/**
 * 설정에서 보안 키(WebAuthn)를 등록하는 길이다(추가 개발 항목 X-2, `decisions.md` D-69). 브라우저 스크립트가 두 번 부른다: `options`로 등록 요청을 받아 `navigator.credentials.create`에 넘기고,
 * 결과를 `register`로 보낸다. 백업 코드를 처음 만들었으면 세션에 잠깐 두었다가 `done` 화면에서 한 번 보이고 지운다.
 */
@Controller
class WebAuthnSettingsController(
    private val currentUserProvider: CurrentUserProvider,
    private val users: UserRepository,
    private val webAuthn: WebAuthnService
) {

    @PostMapping("/user/two-factor/webauthn/options", produces = [MediaType.APPLICATION_JSON_VALUE])
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @ResponseBody
    fun options(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<String> {
        noCache(response)
        val options = webAuthn.startRegistration(currentUser(), WebAuthnContexts.of(request))
        request.getSession(true).setAttribute(REQUEST, options.server)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(options.browser)
    }

    /** 등록 응답을 검증해 저장한다. 성공은 `{"ok": true}`이고 실패는 400의 `{"message": 메시지 키}`이다. */
    @PostMapping("/user/two-factor/webauthn/register", produces = [MediaType.APPLICATION_JSON_VALUE])
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @Transactional
    @ResponseBody
    fun register(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Map<String, Any>> {
        noCache(response)
        val session = request.getSession(false)
        val pending = session?.getAttribute(REQUEST) as? String
        session?.removeAttribute(REQUEST)
        val rejected = ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(mapOf<String, Any>("message" to "twoFactor.securityKey.rejected"))
        if (pending == null) return rejected
        val result = webAuthn.finishRegistration(currentUser(), request.getParameter("name").orEmpty(), pending, request.getParameter("credential").orEmpty(), WebAuthnContexts.of(request))
        if (result !is WebAuthnRegistration.Registered) return rejected
        result.backupCodes?.let { session?.setAttribute(CODES, it) }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mapOf("ok" to true))
    }

    /** 방금 만든 백업 코드를 한 번 보인다. 보일 코드가 없으면 목록으로 돌려보낸다. */
    @GetMapping("/user/two-factor/webauthn/done")
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun done(request: HttpServletRequest, response: HttpServletResponse): Any {
        noCache(response)
        val session = request.getSession(false)
        @Suppress("UNCHECKED_CAST")
        val codes = session?.getAttribute(CODES) as? List<String>
        session?.removeAttribute(CODES)
        if (codes == null) return RedirectSupport.see(request, response, "/user/editform/two-factor")
        return ModelAndView("user/edit-two-factor-codes").addObject("title", "userinfo.twoFactor").addObject("tabId", "two-factor").addObject("codes", codes).addObject("noticeKey", "twoFactor.backup.created")
    }

    private fun currentUser(): User = users.findById(requireNotNull(currentUserProvider.get().user?.id)).get()

    private fun noCache(response: HttpServletResponse) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader(HttpHeaders.EXPIRES, "0")
    }

    private companion object {
        const val REQUEST = "dev.yona.web.WebAuthnRegistrationRequest"
        const val CODES = "dev.yona.web.WebAuthnBackupCodes"
    }
}
