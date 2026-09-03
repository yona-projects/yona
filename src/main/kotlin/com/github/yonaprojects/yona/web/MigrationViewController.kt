package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.service.MigrationService
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/migration")
class MigrationViewController(
    private val userRepository: UserRepository,
    private val migrationService: MigrationService
) {

    private fun getLoginUser(authentication: Authentication?): User? {
        if (authentication == null) return null
        return userRepository.findByLoginId(authentication.name).orElse(null)
    }

    @GetMapping
    fun migrationHome(
        @RequestParam(value = "code", required = false) code: String?,
        authentication: Authentication?,
        model: Model
    ): String {
        if (!migrationService.isAllowMigration()) {
            return "error/403"
        }

        val currentUser = getLoginUser(authentication) ?: return "redirect:/users/loginform"
        model.addAttribute("currentUser", currentUser)

        if (!code.isNullOrBlank()) {
            val token = migrationService.getOAuthToken(code)
            model.addAttribute("token", token)
            model.addAttribute("code", code)
        } else {
            model.addAttribute("token", "")
            model.addAttribute("code", "")
        }

        return "migration/home"
    }
}
