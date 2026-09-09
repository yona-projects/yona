package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.sso.SsoSettingsService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

/**
 * 엔터프라이즈 SSO 설정 관리자 UI. `SiteViewController`와
 * 동일한 관리자 전용 패턴(checkAdmin → IllegalArgumentException → error/403)을 그대로 따른다.
 * 필드 구성/문구는 GitHub Enterprise의 조직 설정 > Security > "SAML single sign-on" 화면(Sign on
 * URL/Issuer/Public Certificate)을 차용했다(모호할 때 GitHub 방식을 따르는 공통 방침) — OIDC는
 * GitHub이 조직 SSO로 직접 제공하지 않아 Issuer URL/Client ID/Client Secret이라는 일반적인
 * 엔터프라이즈 SaaS 관행을 따른다.
 */
@Controller
@RequestMapping(value = ["/site/sso", "/sites/sso"])
class SsoAdminController(
    private val ssoSettingsService: SsoSettingsService,
    private val userRepository: UserRepository
) {

    private fun checkAdmin(authentication: Authentication?): User {
        val loginUser = authentication?.let { userRepository.findByLoginId(it.name).orElse(null) }
        if (loginUser == null || !loginUser.isSiteManager) {
            throw IllegalArgumentException("Unauthorized access")
        }
        return loginUser
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleUnauthorized(e: IllegalArgumentException): String {
        return "error/403"
    }

    @GetMapping
    fun view(authentication: Authentication?, model: Model): String {
        val currentUser = checkAdmin(authentication)
        model.addAttribute("currentUser", currentUser)
        model.addAttribute("oidcSettings", ssoSettingsService.getOidcSettings())
        model.addAttribute("saml2Settings", ssoSettingsService.getSaml2Settings())
        model.addAttribute("message", "title.siteSetting")
        return "site/sso"
    }

    @PostMapping("/oidc")
    fun saveOidc(
        authentication: Authentication?,
        @RequestParam(value = "enabled", defaultValue = "false") enabled: Boolean,
        @RequestParam(value = "registrationId", defaultValue = "oidc") registrationId: String,
        @RequestParam(value = "issuerUri", required = false) issuerUri: String?,
        @RequestParam(value = "clientId", required = false) clientId: String?,
        @RequestParam(value = "clientSecret", required = false) clientSecret: String?
    ): String {
        checkAdmin(authentication)
        ssoSettingsService.saveOidcSettings(enabled, registrationId, issuerUri, clientId, clientSecret)
        return "redirect:/site/sso"
    }

    @PostMapping("/saml2")
    fun saveSaml2(
        authentication: Authentication?,
        @RequestParam(value = "enabled", defaultValue = "false") enabled: Boolean,
        @RequestParam(value = "registrationId", defaultValue = "saml2") registrationId: String,
        @RequestParam(value = "idpSsoUrl", required = false) idpSsoUrl: String?,
        @RequestParam(value = "idpEntityId", required = false) idpEntityId: String?,
        @RequestParam(value = "idpCertificate", required = false) idpCertificate: String?
    ): String {
        checkAdmin(authentication)
        ssoSettingsService.saveSaml2Settings(enabled, registrationId, idpSsoUrl, idpEntityId, idpCertificate)
        return "redirect:/site/sso"
    }
}
