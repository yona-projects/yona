package dev.yona.oauth2server

import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.CurrentUserProvider
import dev.yona.web.access.AnonymousCheck
import dev.yona.web.error.ErrorViews
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.ModelAndView
import java.net.URI

/**
 * 승인(동의) 화면이다(추가 개발 항목 X-1, `decisions.md` D-71). 라이브러리가 사용자의 승인이 필요한 승인 요청을 이 화면으로 보낸다(`/oauth2/consent?client_id=&scope=&state=`, `state`는 라이브러리가
 * 만든 값이다). 앱 이름, 만든 사용자, 돌아갈 주소(도메인), 요청한 범위의 설명을 보이고 허용과 거부 단추를 둔다. 허용은 요청한 범위 **전체**를 보낸다(범위를 줄일 수 없다, D-71 10번).
 * 요청이 현재 사용자의 것이 아니거나 모르는 요청이면 400이다. 다른 곳에 끼워 넣어 클릭을 가로채지 못하도록 `frame-ancestors 'none'`과 `X-Frame-Options: DENY`를 건다.
 */
@Controller
class OAuthConsentController(
    private val currentUserProvider: CurrentUserProvider,
    private val authorizations: YonaOAuth2AuthorizationService,
    private val clients: OAuthClientRepository,
    private val users: UserRepository,
    private val organizations: dev.yona.domain.project.OrganizationRepository
) {

    @GetMapping(AuthorizationServerConfig.CONSENT_PAGE)
    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    fun consent(@RequestParam("client_id", required = false) clientId: String?, @RequestParam(required = false) scope: String?, @RequestParam(required = false) state: String?, response: HttpServletResponse): Any {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
        response.setHeader(HttpHeaders.PRAGMA, "no-cache")
        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'")
        response.setHeader("X-Frame-Options", "DENY")
        val user = currentUserProvider.get().user ?: return ErrorViews.badRequest()
        if (state.isNullOrBlank()) return ErrorViews.badRequest()
        val authorization = authorizations.findByToken(state, OAuth2TokenType(OAuth2ParameterNames.STATE)) ?: return ErrorViews.badRequest()
        if (authorization.principalName != user.id.toString()) return ErrorViews.badRequest()
        val client = authorization.registeredClientId.toLongOrNull()?.let { clients.findById(it).orElse(null) } ?: return ErrorViews.badRequest()
        if (clientId != null && clientId != client.clientId) return ErrorViews.badRequest()
        val requested = authorization.getAttribute<OAuth2AuthorizationRequest>(OAuth2AuthorizationRequest::class.java.name)?.redirectUri
        val host = requested?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
        val owner = client.ownerId?.takeIf { client.ownerType == dev.yona.domain.oauth2server.OAuthClient.USER }?.let { users.findById(it).orElse(null) }
        val ownerOrganization = client.ownerId?.takeIf { client.ownerType == dev.yona.domain.oauth2server.OAuthClient.ORGANIZATION }?.let { organizations.findById(it).orElse(null)?.name }
        return ModelAndView("oauth2/consent")
            .addObject("title", "oauth.consent.heading")
            .addObject("clientName", client.name).addObject("clientId", client.clientId).addObject("homeUrl", client.homeUrl.orEmpty())
            .addObject("ownerLoginId", owner?.loginId).addObject("ownerOrganization", ownerOrganization).addObject("redirectHost", host)
            .addObject("loopback", host in setOf("localhost", "127.0.0.1", "[::1]", "::1"))
            .addObject("state", state)
            .addObject("cimdHost", if (client.registration == dev.yona.domain.oauth2server.OAuthClient.CIMD) runCatching { URI(client.clientId.orEmpty()).host }.getOrNull() else null)
            .addObject("scopes", scope.orEmpty().split(' ').filter { it.isNotBlank() })
    }
}
