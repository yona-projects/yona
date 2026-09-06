package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.oauth2server.JpaOAuth2AuthorizationConsentService
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.StringUtils
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.security.Principal

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2 동의(consent) 화면. 사용자 방침("모호하면 GitHub 방식을
// 기본값으로")에 따라 GitHub의 OAuth 동의 화면 문구/레이아웃(앱 이름 + 요청 권한 목록 + Authorize/Cancel
// 버튼)을 그대로 차용했다. Spring Authorization Server는 이 화면을 자체 제공하지 않고
// 애플리케이션이 직접 구현하도록 설계돼 있다(공식 샘플 저장소의 AuthorizationConsentController와
// 동일한 패턴 — 요청된 스코프 중 이미 동의한 것과 새로 동의를 구해야 하는 것을 구분해 보여준다).
@Controller
class OAuthConsentController(
    private val registeredClientRepository: RegisteredClientRepository,
    private val authorizationConsentService: JpaOAuth2AuthorizationConsentService
) {

    @GetMapping("/oauth2/consent")
    fun consent(
        principal: Principal,
        model: Model,
        @RequestParam(OAuth2ParameterNames.CLIENT_ID) clientId: String,
        @RequestParam(OAuth2ParameterNames.SCOPE) scope: String,
        @RequestParam(OAuth2ParameterNames.STATE) state: String
    ): String {
        val registeredClient = registeredClientRepository.findByClientId(clientId)
            ?: return "error/404"

        val currentConsent = authorizationConsentService.findById(registeredClient.id, principal.name)
        val alreadyApprovedScopes = currentConsent?.scopes ?: emptySet()

        val scopesToApprove = mutableListOf<String>()
        val previouslyApprovedScopes = mutableListOf<String>()
        StringUtils.delimitedListToStringArray(scope, " ").forEach { requestedScope ->
            if (requestedScope in alreadyApprovedScopes) {
                previouslyApprovedScopes.add(requestedScope)
            } else {
                scopesToApprove.add(requestedScope)
            }
        }

        model.addAttribute("clientId", clientId)
        model.addAttribute("clientName", registeredClient.clientName)
        model.addAttribute("state", state)
        model.addAttribute("scopes", scopesToApprove)
        model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes)
        model.addAttribute("principalName", principal.name)
        return "oauth2/consent"
    }
}
