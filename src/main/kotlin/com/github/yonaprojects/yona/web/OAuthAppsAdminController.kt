package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClient
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.transaction.annotation.Transactional
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

// yona-wiki P3-14(yona를 OAuth2 서버로 제공) 1라운드 — GitHub OAuth Apps처럼 사이트 관리자가 제3자
// 앱을 직접 등록해 client_id/(confidential이면) client_secret을 발급하는 화면. [[p3-07]]의 DCR
// (RFC7591, 오픈 등록)은 MCP 같은 자동등록 클라이언트 전용으로 그대로 두고 손대지 않는다 — 이 화면은
// JpaRegisteredClientRepository.toEntity()(DCR 저장 경로, MCP 전용 정책을 항상 강제)를 거치지 않고
// OAuthRegisteredClientRepository(JPA 리포지토리)에 직접 저장해 완전히 별개의 경로로 둔다.
// `SsoAdminController`와 동일한 사이트 관리자 전용 패턴(checkAdmin → IllegalArgumentException →
// error/403)을 따른다.
@Controller
@RequestMapping(value = ["/site/oauth-apps", "/sites/oauth-apps"])
class OAuthAppsAdminController(
    private val clientRepository: OAuthRegisteredClientRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository,
    private val authorizationRepository: OAuthAuthorizationRepository,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
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
        model.addAttribute("apps", clientRepository.findAll().sortedBy { it.clientName.lowercase() })
        model.addAttribute("availableScopes", availableScopes())
        model.addAttribute("message", "title.siteSetting")
        return "site/oauth_apps"
    }

    // 스코프 선택지 — DCR(McpOAuthScopes.ALL)과 동일한 축(ApiTokenScopeGroup x READ/WRITE)을
    // 재사용한다(신규 축 설계 없음, [[p3-02]]/[[p3-07]]과 일관성 유지).
    private fun availableScopes(): List<String> =
        ApiTokenScopeGroup.entries.flatMap { group ->
            listOf(ApiTokenPermission.READ, ApiTokenPermission.WRITE).map {
                "${group.name.lowercase()}:${it.name.lowercase()}"
            }
        }

    @PostMapping("/register")
    fun register(
        authentication: Authentication?,
        @RequestParam clientName: String,
        @RequestParam redirectUri: String,
        @RequestParam(defaultValue = "false") confidential: Boolean,
        @RequestParam(required = false) scopes: List<String>?,
        model: Model
    ): String {
        checkAdmin(authentication)

        val id = UUID.randomUUID().toString()
        val clientId = UUID.randomUUID().toString()
        val plainSecret = if (confidential) generateSecret() else null

        val client = OAuthRegisteredClient(
            id = id,
            clientId = clientId,
            clientIdIssuedAt = Instant.now(),
            clientSecret = plainSecret?.let { passwordEncoder.encode(it) },
            clientName = clientName,
            // client_secret_basic은 confidential 클라이언트가 Authorization 헤더로 자신을 증명하는
            // 표준 방식(Spring 기본 지원) — none은 PKCE 전용 공개 클라이언트(DCR 클라이언트와 동일한
            // 방식).
            clientAuthenticationMethods = if (confidential) "client_secret_basic" else "none",
            authorizationGrantTypes = "authorization_code,refresh_token",
            redirectUris = redirectUri,
            scopes = (scopes.orEmpty().filter { it in availableScopes() }).joinToString(",").ifEmpty { "issues:read" },
            dynamicallyRegistered = false,
            // confidential 클라이언트는 client_secret로 이미 자신을 증명하므로 PKCE까지 강제하지
            // 않는다(공개 클라이언트만 PKCE 필수 — DCR과 동일한 원칙).
            requireProofKey = !confidential,
            requireAuthorizationConsent = true
        )
        clientRepository.save(client)

        model.addAttribute("registeredClientId", clientId)
        model.addAttribute("registeredPlainSecret", plainSecret)
        return view(authentication, model)
    }

    @PostMapping("/{id}/delete")
    @Transactional
    fun delete(authentication: Authentication?, @PathVariable id: String): String {
        checkAdmin(authentication)
        val client = clientRepository.findById(id).orElse(null) ?: return "redirect:/site/oauth-apps"

        // 이 클라이언트를 참조하는 동의/발급 토큰 레코드도 함께 정리한다 — 삭제 후에도 남아있으면
        // OAuthAuthorizedAppsService가 존재하지 않는 클라이언트를 가리키는 고아 레코드를 만나게 된다.
        consentRepository.deleteByRegisteredClientId(client.id)
        authorizationRepository.deleteByRegisteredClientId(client.id)
        clientRepository.deleteById(id)
        return "redirect:/site/oauth-apps"
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
