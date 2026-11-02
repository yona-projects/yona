package dev.yona.oauth2server

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientRepository
import dev.yona.domain.oauth2server.RedirectUris
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Spring Authorization Server가 앱(클라이언트)을 찾는 길이다(추가 개발 항목 X-1, `decisions.md` D-71). 저장된 [OAuthClient]를 라이브러리의 [RegisteredClient]로 바꾼다.
 * 동적 클라이언트 등록은 하지 않으므로 [save]는 지원하지 않는다. 모든 앱에 PKCE와 승인 화면을 요구하고, 액세스 토큰은 불투명(reference) 토큰이다.
 */
@Component
class YonaRegisteredClientRepository(private val clients: OAuthClientRepository) : RegisteredClientRepository {

    override fun save(registeredClient: RegisteredClient) = throw UnsupportedOperationException("OAuth 앱은 설정 화면에서만 등록한다")

    @Transactional(readOnly = true)
    override fun findById(id: String): RegisteredClient? = id.toLongOrNull()?.let { clients.findById(it).orElse(null) }?.let(::toRegistered)

    @Transactional(readOnly = true)
    override fun findByClientId(clientId: String): RegisteredClient? = clients.findByClientId(clientId)?.let(::toRegistered)

    private fun toRegistered(client: OAuthClient): RegisteredClient {
        val builder = RegisteredClient.withId(requireNotNull(client.id).toString())
            .clientId(requireNotNull(client.clientId))
            .clientName(client.name.orEmpty())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .redirectUris { uris -> uris.addAll(RedirectUris.parse(client.redirectUris)) }
            .scopes { scopes -> scopes.addAll(ApiScopes.parseOrEmpty(client.allowedScopes)) }
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
            .tokenSettings(
                TokenSettings.builder().accessTokenFormat(OAuth2TokenFormat.REFERENCE).accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TTL).authorizationCodeTimeToLive(CODE_TTL).reuseRefreshTokens(false).build()
            )
        if (client.confidential == true) {
            builder.clientSecret(requireNotNull(client.secretHash)).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
        }
        return builder.build()
    }

    companion object {
        /** 액세스 토큰의 수명이다(D-71 7번). */
        val ACCESS_TOKEN_TTL: Duration = Duration.ofHours(1)

        /** 갱신 토큰의 수명이다. 쓸 때마다 새로 돌린다. */
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)

        /** 인가 코드의 수명이다. */
        val CODE_TTL: Duration = Duration.ofMinutes(5)
    }
}
