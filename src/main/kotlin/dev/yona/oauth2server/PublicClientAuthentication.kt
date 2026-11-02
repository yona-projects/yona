package dev.yona.oauth2server

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2ErrorCodes
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.web.authentication.AuthenticationConverter

/**
 * 공개 앱(비밀 값이 없는 앱)의 갱신 토큰 교환과 취소를 위한 클라이언트 인증이다(추가 개발 항목 X-1, `decisions.md` D-71). 라이브러리의 공개 클라이언트 인증은 인가 코드 교환의 PKCE 검증값에 묶여 있어
 * 갱신 토큰 교환(`grant_type=refresh_token`)과 취소(`/oauth2/revoke`)에는 쓸 수 없다. OAuth 2.1은 공개 클라이언트의 갱신 토큰 교환을 허용하되 갱신 토큰을 쓸 때마다 돌리도록(회전) 하고,
 * 갱신 토큰은 발급받은 앱에만 통한다(라이브러리가 확인한다). 비밀 값이나 `Authorization` 헤더가 있으면 이 방식을 쓰지 않는다.
 */
class PublicClientTokenAuthentication(clientId: String, additionalParameters: Map<String, Any>) :
    OAuth2ClientAuthenticationToken(clientId, ClientAuthenticationMethod.NONE, null, additionalParameters)

/** 갱신 토큰 교환과 취소 요청에서 `client_id`만 있는 공개 앱 인증 요청을 읽는다. */
class PublicClientTokenConverter : AuthenticationConverter {

    override fun convert(request: HttpServletRequest): Authentication? {
        val path = request.requestURI
        val refresh = path.endsWith("/oauth2/token") && request.getParameter("grant_type") == "refresh_token"
        val revoke = path.endsWith("/oauth2/revoke")
        if (!refresh && !revoke) return null
        if (request.getHeader("Authorization") != null || request.getParameter("client_secret") != null) return null
        val clientId = request.getParameter("client_id")?.takeIf { it.isNotBlank() } ?: return null
        val parameters = request.parameterMap.filterKeys { it != "client_id" }.mapValues { it.value.firstOrNull().orEmpty() }
        return PublicClientTokenAuthentication(clientId, parameters)
    }
}

/** [PublicClientTokenAuthentication]을 확인한다. 앱이 있고 공개 앱(인증 방식 `none`)일 때만 통과한다. */
class PublicClientTokenProvider(private val clients: RegisteredClientRepository) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val token = authentication as PublicClientTokenAuthentication
        val client = clients.findByClientId(token.principal as String)
        if (client == null || ClientAuthenticationMethod.NONE !in client.clientAuthenticationMethods) {
            throw OAuth2AuthenticationException(OAuth2Error(OAuth2ErrorCodes.INVALID_CLIENT))
        }
        return OAuth2ClientAuthenticationToken(client, ClientAuthenticationMethod.NONE, null)
    }

    override fun supports(authentication: Class<*>): Boolean = PublicClientTokenAuthentication::class.java.isAssignableFrom(authentication)
}
