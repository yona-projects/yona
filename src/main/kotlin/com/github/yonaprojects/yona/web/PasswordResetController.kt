package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.mail.MailService
import com.github.yonaprojects.yona.domain.user.PasswordResetService
import com.github.yonaprojects.yona.domain.user.UserRepository
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class PasswordResetController(
    private val passwordResetService: PasswordResetService,
    private val userRepository: UserRepository,
    private val mailService: MailService,
    // yona utils.Config.getSiteName() 대응.
    @Value("\${yona.site-name:Yona}") private val siteName: String
) {

    private fun getServerUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort
        return if (serverPort == 80 || serverPort == 443) {
            "$scheme://$serverName"
        } else {
            "$scheme://$serverName:$serverPort"
        }
    }

    // 1. 비밀번호 찾기 메일 신청 화면 (GET /lostPassword)
    @GetMapping("/lostPassword")
    fun lostPasswordForm(model: Model): String {
        model.addAttribute("siteName", siteName)
        return "user/lostPassword"
    }

    // 2. 비밀번호 찾기 메일 발송 처리 (POST /lostPassword)
    // yona PasswordResetApp.requestResetPasswordEmail() 대응 — 아이디/이메일이 일치하지 않으면 i18n 메시지 키를
    // errorMessage로 담아 그대로 재렌더링하고(항상 200), 메일 발송 자체가 예외로 실패한 경우는 legacy와 동일하게
    // 화면에 별도 에러를 노출하지 않는다(legacy sendPasswordResetMail()도 실패를 그저 로그만 남기고 삼킨다).
    @PostMapping("/lostPassword")
    fun requestResetPasswordEmail(
        @RequestParam("loginId") loginId: String,
        @RequestParam("emailAddress") emailAddress: String,
        request: HttpServletRequest,
        model: Model
    ): String {
        model.addAttribute("siteName", siteName)
        val user = userRepository.findByLoginId(loginId).orElse(null)

        if (user == null || user.email != emailAddress) {
            model.addAttribute("errorMessage", "site.resetPasswordEmail.invalidRequest")
            return "user/lostPassword"
        }

        val hashString = passwordResetService.generateResetHash(loginId)
        passwordResetService.addHashToResetTable(loginId, hashString)

        val serverUrl = getServerUrl(request)
        val resetPasswordUrl = "$serverUrl/user/reset-password?hash=$hashString"

        try {
            mailService.sendHtmlMail(
                user.email,
                user.name,
                "[$siteName] 비밀번호 재설정",
                "비밀번호 재설정 안내\n\n$resetPasswordUrl"
            )
            model.addAttribute("isSent", true)
        } catch (e: Exception) {
            // legacy sendPasswordResetMail()도 EmailException을 잡아 로그만 남기고 화면에는 아무것도 노출하지 않는다.
        }
        return "user/lostPassword"
    }

    // 3. 비밀번호 재설정 링크 접속 화면 (GET /user/reset-password)
    @GetMapping("/user/reset-password")
    fun resetPasswordForm(
        @RequestParam("hash") hash: String,
        model: Model
    ): String {
        val isValid = passwordResetService.isValidResetHash(hash)
        return if (isValid) {
            model.addAttribute("hash", hash)
            "user/resetPassword"
        } else {
            model.addAttribute("errorMessage", "잘못되거나 이미 만료된 재설정 링크입니다.")
            "user/resetPassword"
        }
    }

    // 4. 비밀번호 재설정 처리 (POST /user/reset-password)
    @PostMapping("/user/reset-password")
    fun resetPassword(
        @RequestParam("hashString") hashString: String,
        @RequestParam("password") newPassword: String,
        @RequestParam("retypedPassword") retypedPassword: String,
        redirectAttributes: RedirectAttributes,
        model: Model
    ): String {
        if (newPassword != retypedPassword) {
            model.addAttribute("hash", hashString)
            model.addAttribute("errorMessage", "입력한 두 비밀번호가 일치하지 않습니다.")
            return "user/resetPassword"
        }

        val success = passwordResetService.resetPassword(hashString, newPassword)
        return if (success) {
            redirectAttributes.addFlashAttribute("logoutMessage", "비밀번호가 성공적으로 변경되었습니다. 새로운 비밀번호로 로그인해 주세요.")
            "redirect:/users/loginform"
        } else {
            model.addAttribute("hash", hashString)
            model.addAttribute("errorMessage", "비밀번호 재설정에 실패했습니다. 링크 만료 여부를 확인하십시오.")
            "user/resetPassword"
        }
    }
}
