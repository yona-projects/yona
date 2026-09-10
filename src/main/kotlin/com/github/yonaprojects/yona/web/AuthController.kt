package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import com.github.yonaprojects.yona.domain.user.EmailDomainValidator
import com.github.yonaprojects.yona.domain.user.LoginIdFormatValidator
import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
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

@Controller
class AuthController(
    private val userService: UserService,
    @Value("\${yona.signup.allowed-email-domains:}")
    private val allowedEmailDomains: String,
    // yona UserApp.isUsingSignUpConfirm()(signup.require.admin.confirm) 대응.
    @Value("\${yona.signup.require-admin-confirm:false}")
    private val requireAdminConfirm: Boolean,
    // 로그인 화면에 OIDC/SAML2 로그인 버튼을 조건부로 노출한다.
    private val ssoSettingsService: SsoSettingsService,
    private val passwordEncodingService: PasswordEncodingService
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

        val oidcSettings = ssoSettingsService.getOidcSettings()
        model.addAttribute("ssoOidcEnabled", oidcSettings.enabled)
        model.addAttribute("ssoOidcRegistrationId", oidcSettings.registrationId)

        val saml2Settings = ssoSettingsService.getSaml2Settings()
        model.addAttribute("ssoSaml2Enabled", saml2Settings.enabled)
        model.addAttribute("ssoSaml2RegistrationId", saml2Settings.registrationId)

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
        // yona User.LOGIN_ID_PATTERN(@Pattern) 대응.
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

        user.password = passwordEncodingService.encode(user.password ?: "")
        user.passwordSalt = null

        // yona UserApp.createNewUser()의 "관리자 승인 대기면 State.LOCKED로 생성"
        // 대응. 로그인 시 LOCKED 계정 차단 자체는 이미 YonaAuthenticationProvider가
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

}
