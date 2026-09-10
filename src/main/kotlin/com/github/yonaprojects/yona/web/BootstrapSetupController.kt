package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.PasswordEncodingService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserService
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

// legacy: welcome/secret.scala.html + welcome/restart.scala.html. legacy의 트리거는
// Global.java의 getConfigSecretAction()/hasError() (application.secret이 기본값일 때 최초
// 관리자 계정을 재설정하는 흐름)이지만, yona에는 Play의 application.secret 파일 재기록
// 메커니즘 자체가 없어(별도 인프라 항목, 이 백로그의 템플릿 포팅 범위를 벗어남) 진입 조건만
// "가입자 0명"으로 대체하고, 필드별 검증 로직(hasError)은 legacy 그대로 이식한다.
@Controller
class BootstrapSetupController(
    private val userRepository: UserRepository,
    private val userService: UserService,
    private val passwordEncodingService: PasswordEncodingService,
    @Value("\${yona.site-name:Yona}") private val siteName: String
) {

    // 1. 최초 관리자 생성 화면 진입
    @GetMapping("/bootstrap-setup")
    fun setupForm(model: Model): String {
        // 이미 유저가 존재하면 메인 화면으로 리다이렉트
        if (userRepository.count() > 0L) {
            return "redirect:/"
        }
        model.addAttribute("siteName", siteName)
        model.addAttribute("loginIdErrors", emptyList<String>())
        model.addAttribute("emailErrors", emptyList<String>())
        model.addAttribute("passwordErrors", emptyList<String>())
        model.addAttribute("retypedPasswordErrors", emptyList<String>())
        return "bootstrap-setup"
    }

    // 2. 최초 관리자 생성 처리 (legacy Global.java의 hasError() 검증을 필드별로 그대로 이식)
    @PostMapping("/bootstrap-setup")
    fun setupAdmin(
        @RequestParam("loginId") loginId: String,
        @RequestParam("name") name: String,
        @RequestParam("email") email: String,
        @RequestParam("password") password: String,
        @RequestParam("retypedPassword") retypedPassword: String,
        model: Model
    ): String {
        if (userRepository.count() > 0L) {
            return "redirect:/"
        }

        val loginIdErrors = mutableListOf<String>()
        if (loginId.isBlank()) loginIdErrors.add("user.wrongloginId.alert")
        if (loginId != "admin") loginIdErrors.add("user.wrongloginId.alert")

        val passwordErrors = mutableListOf<String>()
        if (password.isBlank()) passwordErrors.add("user.wrongPassword.alert")

        val retypedPasswordErrors = mutableListOf<String>()
        if (password != retypedPassword) retypedPasswordErrors.add("user.confirmPassword.alert")

        val emailErrors = mutableListOf<String>()
        if (email.isBlank()) emailErrors.add("validation.invalidEmail")
        if (userRepository.findByEmail(email).isPresent) emailErrors.add("user.email.duplicate")

        if (loginIdErrors.isNotEmpty() || passwordErrors.isNotEmpty() ||
            retypedPasswordErrors.isNotEmpty() || emailErrors.isNotEmpty()
        ) {
            model.addAttribute("siteName", siteName)
            model.addAttribute("loginIdErrors", loginIdErrors)
            model.addAttribute("emailErrors", emailErrors)
            model.addAttribute("passwordErrors", passwordErrors)
            model.addAttribute("retypedPasswordErrors", retypedPasswordErrors)
            model.addAttribute("name", name)
            model.addAttribute("email", email)
            return "bootstrap-setup"
        }

        val adminUser = User().apply {
            this.loginId = loginId
            this.name = name
            this.email = email
            this.password = passwordEncodingService.encode(password)
            this.passwordSalt = null
            this.state = UserState.SITE_ADMIN // 최초 가입자는 사이트 총괄 관리자 권한 부여
            this.isGuest = false
        }

        userService.createUser(adminUser)
        model.addAttribute("siteName", siteName)
        return "bootstrap-restart"
    }
}
