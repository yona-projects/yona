package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.twofactor.TotpActivationResult
import com.github.yonaprojects.yona.domain.twofactor.TwoFactorService
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

// 계정 보안 설정 화면 — GitHub "Settings > Password and authentication"과 동일한 관례로
// UserViewController의 tokens/ssh-keys 섹션과 같은 URL 패턴(/user/editform/*)을 따르되, 등록
// 폼과 목록을 분리하는 그 파일들의 컨벤션과 별개로 이 기능은 TOTP QR 발급/WebAuthn JSON API/
// 백업코드 1회 노출까지 얽혀 있어 이미 방대한 UserViewController에 더 얹지 않고 별도 파일로 둔다.
// WebAuthn 등록의 navigator.credentials.create() JSON 왕복은 WebauthnRegistrationController
// 참고 — 이 컨트롤러는 화면 렌더링과 TOTP/백업코드/비활성화 같은 일반 폼 흐름만 다룬다.
@Controller
@RequestMapping("/user/editform/security")
class TwoFactorSettingsController(
    private val userRepository: UserRepository,
    private val twoFactorService: TwoFactorService,
    private val passwordEncodingService: PasswordEncodingService
) {

    private fun currentUser(authentication: Authentication?) =
        authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }

    @GetMapping
    fun securityForm(authentication: Authentication?, model: Model): String {
        val user = currentUser(authentication) ?: return "error/403"
        model.addAttribute("user", user)
        model.addAttribute("currentUser", user)
        model.addAttribute("totpCredentials", twoFactorService.listEnabledTotpCredentials(user))
        model.addAttribute("webauthnCredentials", twoFactorService.listWebauthnCredentials(user))
        model.addAttribute("hasBackupCodes", twoFactorService.hasUnusedBackupCodes(user))
        return "user/edit_security"
    }

    @GetMapping("/totp/new")
    fun newTotpForm(authentication: Authentication?, model: Model): String {
        val user = currentUser(authentication) ?: return "error/403"
        val (credential, enrollment) = twoFactorService.beginTotpEnrollment(user, user.loginId)
        model.addAttribute("user", user)
        model.addAttribute("currentUser", user)
        model.addAttribute("credentialId", credential.id)
        model.addAttribute("secret", enrollment.rawSecret)
        model.addAttribute("qrCodeDataUri", enrollment.qrCodeDataUri)
        return "user/edit_security_totp_new"
    }

    @PostMapping("/totp/{id}/verify")
    fun verifyTotp(
        @PathVariable id: Long,
        @RequestParam code: String,
        authentication: Authentication?,
        model: Model,
        redirectAttributes: RedirectAttributes
    ): String {
        val user = currentUser(authentication) ?: return "error/403"

        return when (val result = twoFactorService.verifyAndActivateTotp(user, id, code)) {
            is TotpActivationResult.Success -> {
                if (result.freshBackupCodes != null) {
                    redirectAttributes.addFlashAttribute("freshBackupCodes", result.freshBackupCodes)
                    "redirect:/user/editform/security/backup-codes/show"
                } else {
                    redirectAttributes.addFlashAttribute("totpAdded", true)
                    "redirect:/user/editform/security"
                }
            }
            TotpActivationResult.InvalidCode, TotpActivationResult.NotFound -> {
                val enrollment = twoFactorService.reviewPendingTotpEnrollment(user, id)
                    ?: return "redirect:/user/editform/security/totp/new"
                model.addAttribute("user", user)
                model.addAttribute("currentUser", user)
                model.addAttribute("credentialId", id)
                model.addAttribute("secret", enrollment.rawSecret)
                model.addAttribute("qrCodeDataUri", enrollment.qrCodeDataUri)
                model.addAttribute("totpError", true)
                "user/edit_security_totp_new"
            }
        }
    }

    @PostMapping("/totp/{id}/delete")
    fun deleteTotp(
        @PathVariable id: Long,
        @RequestParam password: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        val user = currentUser(authentication) ?: return "error/403"
        if (!verifyPassword(user, password)) {
            redirectAttributes.addFlashAttribute("deleteError", true)
            return "redirect:/user/editform/security"
        }
        twoFactorService.deleteTotpCredential(user, id)
        return "redirect:/user/editform/security"
    }

    @PostMapping("/webauthn/{id}/delete")
    fun deleteWebauthn(
        @PathVariable id: Long,
        @RequestParam password: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        val user = currentUser(authentication) ?: return "error/403"
        if (!verifyPassword(user, password)) {
            redirectAttributes.addFlashAttribute("deleteError", true)
            return "redirect:/user/editform/security"
        }
        twoFactorService.deleteWebauthnCredential(user, id)
        return "redirect:/user/editform/security"
    }

    @GetMapping("/webauthn/new")
    fun newWebauthnForm(authentication: Authentication?, model: Model): String {
        val user = currentUser(authentication) ?: return "error/403"
        model.addAttribute("user", user)
        model.addAttribute("currentUser", user)
        return "user/edit_security_webauthn_new"
    }

    @PostMapping("/backup-codes/regenerate")
    fun regenerateBackupCodes(authentication: Authentication?, redirectAttributes: RedirectAttributes): String {
        val user = currentUser(authentication) ?: return "error/403"
        val codes = twoFactorService.regenerateBackupCodes(user)
        redirectAttributes.addFlashAttribute("freshBackupCodes", codes)
        return "redirect:/user/editform/security/backup-codes/show"
    }

    // 백업 코드는 발급 직후 딱 한 번만 평문으로 보여준다(GitHub 관례) — flash attribute라
    // 새로고침하면 사라진다.
    @GetMapping("/backup-codes/show")
    fun showBackupCodes(authentication: Authentication?, model: Model): String {
        val user = currentUser(authentication) ?: return "error/403"
        model.addAttribute("user", user)
        model.addAttribute("currentUser", user)
        if (!model.containsAttribute("freshBackupCodes")) {
            return "redirect:/user/editform/security"
        }
        return "user/edit_security_backup_codes"
    }

    @PostMapping("/disable")
    fun disableTwoFactor(
        @RequestParam password: String,
        authentication: Authentication?,
        redirectAttributes: RedirectAttributes
    ): String {
        val user = currentUser(authentication) ?: return "error/403"
        if (!verifyPassword(user, password)) {
            redirectAttributes.addFlashAttribute("disableError", true)
            return "redirect:/user/editform/security"
        }
        twoFactorService.disableAll(user)
        redirectAttributes.addFlashAttribute("twoFactorDisabled", true)
        return "redirect:/user/editform/security"
    }

    // 개별 자격증명 삭제/전체 비활성화 공통 — 등록된 2FA 자격증명을 지우는 조작은 계정 탈취 시
    // 공격자가 방어 수단을 무력화하는 경로이므로, 클라이언트 confirm() 대화상자만으로는 부족하고
    // 서버가 매번 현재 비밀번호를 재확인해야 한다.
    private fun verifyPassword(user: User, password: String): Boolean =
        passwordEncodingService.matches(password, user.password, user.passwordSalt)
}
