package dev.yona.security

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.oauth2server.OAuthAccessTokenService
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.core.Authentication

/**
 * 외부 API(`/-_-api`) 요청의 토큰 값을 인증해 달라는 요청이다(로드맵 X-43 2단계 작업 2-2, `decisions.md` D-129). 개인용 토큰과 OAuth 액세스 토큰을 가리지 않고 값만 싣는다.
 * 어느 제공자가 맡을지는 값의 접두사(`yona_oat_`)로 정한다.
 *
 * @property value 헤더에서 꺼낸 토큰 값
 * @property resource 이 요청이 가리키는 보호 자원의 주소(RFC 8707). OAuth 토큰이 이 자원에서 쓸 수 있는지 확인하는 데 쓴다
 */
class ApiTokenAuthenticationToken(val value: String, val resource: String) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = false
    }

    override fun getCredentials(): Any = value

    override fun getPrincipal(): Any = value
}

/**
 * 개인용 API 토큰(Classic)을 인증하는 [AuthenticationProvider]이다. 해시가 맞고 만료되지 않았고 주인이 잠기거나 삭제되지 않았을 때만 통과시킨다
 * ([ApiTokenService.authenticate]). 범위는 [ApiTokenService.scopesOf]가 정한다(범위 행이 없는 옛 토큰은 범위 제한 없음, `decisions.md` D-126).
 * OAuth 액세스 토큰(접두사 `yona_oat_`)은 [OAuthAccessTokenAuthenticationProvider]의 몫이라 `null`을 돌려준다.
 *
 * 스프링 빈이 아니다. 필터가 자기만의 `ProviderManager`에 넣어 쓴다([YonaPasswordAuthenticationProvider]와 같은 이유로 전역 인증 관리자에 등록하지 않는다).
 *
 * @throws BadCredentialsException 토큰이 틀렸을 때. 호출하는 쪽이 실패 횟수와 감사에 센다
 */
class ApiTokenAuthenticationProvider(private val apiTokens: ApiTokenService) : AuthenticationProvider {

    override fun supports(authentication: Class<*>): Boolean = ApiTokenAuthenticationToken::class.java.isAssignableFrom(authentication)

    override fun authenticate(authentication: Authentication): Authentication? {
        val request = authentication as ApiTokenAuthenticationToken
        if (request.value.startsWith(OAuthAccessTokenService.PREFIX)) return null
        val token = apiTokens.authenticate(request.value)
        val owner = token?.user ?: throw BadCredentialsException("api token")
        return YonaAuthentication.of(owner, viaToken = true, scopes = apiTokens.scopesOf(token), tokenId = token.id)
    }
}

/**
 * OAuth 액세스 토큰(접두사 `yona_oat_`)을 인증하는 [AuthenticationProvider]이다([OAuthAccessTokenService.authenticate]). 개인용 토큰과 같은 실패 제한과 범위 검사를 받는다.
 * 개인용 토큰은 [ApiTokenAuthenticationProvider]의 몫이라 `null`을 돌려준다.
 *
 * 토큰의 대상(RFC 8707)이 이 요청의 자원과 맞지 않으면(`resource` 없이 발급되었거나 외부 API를 가리켜 발급된 토큰만 받는다. 다른 자원(MCP)용 토큰은 여기서 쓸 수 없다)
 * [InsufficientAuthenticationException]을 던진다. 토큰 자체는 맞으므로 **실패로 세지 않는다**.
 *
 * @throws BadCredentialsException 토큰이 틀렸을 때
 */
class OAuthAccessTokenAuthenticationProvider(private val oauthTokens: OAuthAccessTokenService) : AuthenticationProvider {

    override fun supports(authentication: Class<*>): Boolean = ApiTokenAuthenticationToken::class.java.isAssignableFrom(authentication)

    override fun authenticate(authentication: Authentication): Authentication? {
        val request = authentication as ApiTokenAuthenticationToken
        if (!request.value.startsWith(OAuthAccessTokenService.PREFIX)) return null
        val grant = oauthTokens.authenticate(request.value) ?: throw BadCredentialsException("oauth token")
        if (!grant.allows(request.resource, default = true)) throw InsufficientAuthenticationException("oauth token for another resource")
        return YonaAuthentication.of(grant.user, viaToken = true, scopes = grant.scopes, oauthClientRowId = grant.clientRowId)
    }
}
