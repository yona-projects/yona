package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.EmailDomainValidator
import com.github.yonaprojects.yona.domain.user.LoginIdFormatValidator
import com.github.yonaprojects.yona.domain.user.ReservedWordsValidator
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@Controller
class AuthController(
    private val userService: UserService,
    @Value("\${yona.signup.allowed-email-domains:}")
    private val allowedEmailDomains: String,
    // yona UserApp.java:1218-1224 isUsingSignUpConfirm()(signup.require.admin.confirm) 대응 (P1-77).
    @Value("\${yona.signup.require-admin-confirm:false}")
    private val requireAdminConfirm: Boolean
) {

    @GetMapping("/login")
    fun redirectToLoginForm(
        @RequestParam(value = "error", required = false) error: String?,
        @RequestParam(value = "logout", required = false) logout: String?,
        redirectAttributes: RedirectAttributes
    ): String {
        if (error != null) {
            redirectAttributes.addAttribute("error", error)
        }
        if (logout != null) {
            redirectAttributes.addAttribute("logout", logout)
        }
        return "redirect:/users/loginform"
    }

    @GetMapping("/users/loginform")
    fun loginForm(
        @RequestParam(value = "error", required = false) error: String?,
        @RequestParam(value = "logout", required = false) logout: String?,
        model: Model
    ): String {
        if (error != null) {
            model.addAttribute("loginError", "아이디 또는 비밀번호가 올바르지 않습니다.")
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "성공적으로 로그아웃되었습니다.")
        }
        return "login"
    }

    @GetMapping("/users/logout")
    fun logout(): String {
        return "redirect:/logout"
    }

    @GetMapping(value = ["/signup", "/users/signupform"])
    fun signupForm(model: Model): String {
        model.addAttribute("user", User())
        model.addAttribute("requireAdminConfirm", requireAdminConfirm)
        return "signup"
    }

    @PostMapping(value = ["/signup", "/users/signup"])
    fun signup(
        @ModelAttribute("user") user: User,
        @RequestParam("retypedPassword") retypedPassword: String,
        bindingResult: BindingResult,
        model: Model
    ): String {
        model.addAttribute("requireAdminConfirm", requireAdminConfirm)
        // yona models/User.java:65-66,80 LOGIN_ID_PATTERN(@Pattern) 대응 (P1-104).
        if (!LoginIdFormatValidator.isValid(user.loginId)) {
            bindingResult.rejectValue("loginId", "pattern", "아이디 형식이 올바르지 않습니다.")
        }
        if (userService.isLoginIdExist(user.loginId)) {
            bindingResult.rejectValue("loginId", "duplicate", "이미 존재하는 아이디입니다.")
        }
        if (ReservedWordsValidator.isReserved(user.loginId)) {
            bindingResult.rejectValue("loginId", "reservedWord", "사용할 수 없는 아이디입니다.")
        }
        if (user.password != retypedPassword) {
            model.addAttribute("passwordError", "비밀번호가 일치하지 않습니다.")
            return "signup"
        }
        if (!EmailDomainValidator.isAllowed(user.email, allowedEmailDomains)) {
            model.addAttribute("emailDomainError", "허용되지 않은 이메일 도메인입니다.")
            return "signup"
        }
        if (bindingResult.hasErrors()) {
            return "signup"
        }

        val salt = UUID.randomUUID().toString().substring(0, 8)
        val hashed = hashPassword(user.password ?: "", salt)
        user.password = hashed
        user.passwordSalt = salt

        // yona UserApp.java:1260-1275 createNewUser()의 "관리자 승인 대기면 State.LOCKED로 생성"
        // 대응 (P1-77). 로그인 시 LOCKED 계정 차단 자체는 이미 YonaAuthenticationProvider(P0-13)가
        // 이 설정과 무관하게 항상 수행하므로, 여기서는 가입 시점의 초기 상태 결정만 담당한다.
        if (requireAdminConfirm) {
            user.state = UserState.LOCKED
        }

        userService.createUser(user)
        return if (requireAdminConfirm) {
            "redirect:/users/loginform?signupRequested"
        } else {
            "redirect:/users/loginform?signupSuccess"
        }
    }

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(Charsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(Charsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }
}
