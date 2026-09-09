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

// OAuth2 동의(consent) 화면. 모호한 설계 결정은 GitHub 방식을 기본값으로 삼는 방침에 따라
// GitHub의 OAuth 동의 화면 문구/레이아웃(앱 이름 + 요청 권한 목록 + Authorize/Cancel
// 버튼)을 그대로 차용했다. Spring Authorization Server는 이 화면을 자체 제공하지 않고
// 애플리케이션이 직접 구현하도록 설계돼 있다(공식 샘플 저장소의 AuthorizationConsentController와
// 동일한 패턴 — 요청된 스코프 중 이미 동의한 것과 새로 동의를 구해야 하는 것을 구분해 보여준다).
@Controller
class OAuthConsentController(
    private val registeredClientRepository: RegisteredClientRepository,
    private val authorizationConsentService: JpaOAuth2AuthorizationConsentService
) {

    companion object {
        // `issues:read`류 API 스코프는 원래부터 원문 스코프 문자열을
        // 그대로 보여주는 것 말고는 아무 설명 메커니즘이 없었다 —
        // 그 관례를 깨지 않으면서, GitHub이 OIDC identity 스코프에 한해서는 plain-language 설명을
        // 보여주는 것과 동등하게 openid/profile/email 세 개만 메시지 키로 매핑해 사람이 읽을 수 있는
        // 문구로 대체한다(GitHub 컨벤션 기본값 방침). 매핑에 없는 스코프(API 스코프 포함)는 계속
        // 원문 그대로 보여준다 — 기존 동작 무변경.
        val IDENTITY_SCOPE_DESCRIPTION_KEYS: Map<String, String> = mapOf(
            "openid" to "oauth2.consent.scope.openid",
            "profile" to "oauth2.consent.scope.profile",
            "email" to "oauth2.consent.scope.email"
        )
    }

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
        model.addAttribute("scopeDescriptionKeys", IDENTITY_SCOPE_DESCRIPTION_KEYS)
        return "oauth2/consent"
    }
}
