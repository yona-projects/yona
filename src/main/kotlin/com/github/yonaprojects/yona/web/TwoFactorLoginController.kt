package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.Pre2faAuthenticationToken
import com.github.yonaprojects.yona.domain.device.DeviceRecognitionService
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.savedrequest.HttpSessionRequestCache
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse
import org.springframework.security.web.webauthn.api.PublicKeyCredential
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository
import org.springframework.security.web.webauthn.management.PublicKeyCredentialRequestOptionsRequest
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.servlet.mvc.support.RedirectAttributes

// 1차 비밀번호 인증 통과 후 SecurityContext에 Pre2faAuthenticationToken이 심긴 상태에서만
// 의미가 있는 2단계 인증 화면/검증 API. WebAuthn이 등록돼 있으면 먼저 시도하고, 실패/거부 시
// "다시 시도"/"다른 방법 사용"(TOTP)을 보여주는 구글 로그인 방식 UX를 위해 이 컨트롤러는 검증
// 방식과 무관하게 같은 대기 상태(Pre2faAuthenticationToken)를 유지한 채 method 파라미터로 화면만
// 바꾼다 — 검증 성공 시에만 SecurityContext를 원래의 완전한 Authentication으로 교체한다.
@Controller
@RequestMapping("/users/login/2fa")
class TwoFactorLoginController(
    private val userRepository: UserRepository,
    private val twoFactorService: TwoFactorService,
    private val webAuthnRelyingPartyOperations: WebAuthnRelyingPartyOperations,
    private val requestOptionsRepository: PublicKeyCredentialRequestOptionsRepository,
    private val deviceRecognitionService: DeviceRecognitionService
) {
    private val requestCache = HttpSessionRequestCache()
    private val securityContextRepository = HttpSessionSecurityContextRepository()

    private fun pendingToken(): Pre2faAuthenticationToken? =
        SecurityContextHolder.getContext().authentication as? Pre2faAuthenticationToken

    @GetMapping
    fun show(@RequestParam(required = false) method: String?, model: Model): String {
        val token = pendingToken() ?: return "redirect:/users/loginform"
        val user = userRepository.findByLoginId(token.name).orElse(null) ?: return "redirect:/users/loginform"

        val hasWebauthn = twoFactorService.listWebauthnCredentials(user).isNotEmpty()
        val hasTotp = twoFactorService.listEnabledTotpCredentials(user).isNotEmpty()
        val hasBackup = twoFactorService.hasUnusedBackupCodes(user)

        val step = when {
            method == "backup" -> "backup"
            method == "totp" && hasTotp -> "totp"
            method == "webauthn" && hasWebauthn -> "webauthn"
            hasWebauthn -> "webauthn"
            hasTotp -> "totp"
            else -> "backup"
        }

        model.addAttribute("step", step)
        model.addAttribute("hasWebauthn", hasWebauthn)
        model.addAttribute("hasTotp", hasTotp)
        model.addAttribute("hasBackup", hasBackup)
        return "login_2fa"
    }

    @PostMapping("/webauthn/options")
    @ResponseBody
    fun webauthnOptions(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Any> {
        val token = pendingToken() ?: return ResponseEntity.status(401).build()
        val options = webAuthnRelyingPartyOperations.createCredentialRequestOptions(
            PublicKeyCredentialRequestOptionsRequest { token }
        )
        requestOptionsRepository.save(request, response, options)
        return ResponseEntity.ok(options)
    }

    @PostMapping("/webauthn")
    @ResponseBody
    fun webauthnVerify(
        @RequestBody credential: PublicKeyCredential<AuthenticatorAssertionResponse>,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, Any?>> {
        val token = pendingToken() ?: return ResponseEntity.status(401).build()
        val options = requestOptionsRepository.load(request)
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "인증 세션이 만료되었습니다. 다시 시도해주세요."))

        return try {
            val resolvedUser = webAuthnRelyingPartyOperations.authenticate(RelyingPartyAuthenticationRequest(options, credential))
            if (resolvedUser.name != token.name) {
                return ResponseEntity.status(403).body(mapOf("error" to "인증에 실패했습니다."))
            }
            val user = userRepository.findByLoginId(token.name).orElse(null)
                ?: return ResponseEntity.status(401).body(mapOf("error" to "인증에 실패했습니다."))
            finalizeLogin(token, user, request, response)
            ResponseEntity.ok(mapOf("status" to "success", "redirectUrl" to targetUrl(request, response)))
        } catch (e: Exception) {
            ResponseEntity.status(401).body(mapOf("error" to "인증에 실패했습니다."))
        }
    }

    @PostMapping("/totp")
    fun totpVerify(
        @RequestParam code: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        redirectAttributes: RedirectAttributes
    ): String {
        val token = pendingToken() ?: return "redirect:/users/loginform"
        val user = userRepository.findByLoginId(token.name).orElse(null) ?: return "redirect:/users/loginform"

        if (!twoFactorService.verifyTotpForLogin(user, code)) {
            redirectAttributes.addFlashAttribute("twoFactorError", true)
            return "redirect:/users/login/2fa?method=totp"
        }
        finalizeLogin(token, user, request, response)
        return "redirect:${targetUrl(request, response)}"
    }

    @PostMapping("/backup")
    fun backupVerify(
        @RequestParam code: String,
        request: HttpServletRequest,
        response: HttpServletResponse,
        redirectAttributes: RedirectAttributes
    ): String {
        val token = pendingToken() ?: return "redirect:/users/loginform"
        val user = userRepository.findByLoginId(token.name).orElse(null) ?: return "redirect:/users/loginform"

        if (!twoFactorService.consumeBackupCode(user, code)) {
            redirectAttributes.addFlashAttribute("twoFactorError", true)
            return "redirect:/users/login/2fa?method=backup"
        }
        finalizeLogin(token, user, request, response)
        return "redirect:${targetUrl(request, response)}"
    }

    private fun targetUrl(request: HttpServletRequest, response: HttpServletResponse): String =
        requestCache.getRequest(request, response)?.redirectUrl ?: "/"

    private fun finalizeLogin(token: Pre2faAuthenticationToken, user: User, request: HttpServletRequest, response: HttpServletResponse) {
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = token.originalAuthentication
        SecurityContextHolder.setContext(context)
        securityContextRepository.saveContext(context, request, response)
        deviceRecognitionService.recognizeLogin(user, request, response)
    }
}
