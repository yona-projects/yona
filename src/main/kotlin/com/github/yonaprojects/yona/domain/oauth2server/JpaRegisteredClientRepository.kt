package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.ClientAuthenticationMethod
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

// RegisteredClientRepository의 JPA 기반 구현. 설계 근거는 OAuthRegisteredClient.kt 상단 주석 참고.
@Component
class JpaRegisteredClientRepository(
    private val repository: OAuthRegisteredClientRepository
) : RegisteredClientRepository {

    @Transactional
    override fun save(registeredClient: RegisteredClient) {
        repository.save(toEntity(registeredClient))
    }

    override fun findById(id: String): RegisteredClient? =
        repository.findById(id).map { toRegisteredClient(it) }.orElse(null)

    override fun findByClientId(clientId: String): RegisteredClient? =
        repository.findByClientId(clientId).map { toRegisteredClient(it) }.orElse(null)

    companion object {
        private const val DELIMITER = ","

        fun toEntity(client: RegisteredClient): OAuthRegisteredClient {
            val tokenSettings = client.tokenSettings
            return OAuthRegisteredClient(
                id = client.id,
                clientId = client.clientId,
                clientIdIssuedAt = client.clientIdIssuedAt ?: Instant.now(),
                clientSecret = client.clientSecret,
                clientSecretExpiresAt = client.clientSecretExpiresAt,
                clientName = client.clientName,
                clientAuthenticationMethods = client.clientAuthenticationMethods.joinToString(DELIMITER) { it.value },
                authorizationGrantTypes = client.authorizationGrantTypes.joinToString(DELIMITER) { it.value },
                redirectUris = client.redirectUris.takeIf { it.isNotEmpty() }?.joinToString(DELIMITER),
                // RFC7591 DCR은 클라이언트가 scope를 직접 지정하는 것을 기본 정책상 거부한다
                // (McpOAuthScopes.kt 참고) — 클라이언트가 요청한 scope를 신뢰하지 않고 이 앱이
                // 지원하는 전체 스코프 목록을 무조건 부여한다. 실제 발급 스코프는 /oauth2/authorize
                // 요청 + 동의 화면에서 좁혀진다.
                scopes = McpOAuthScopes.ALL.joinToString(DELIMITER),
                // 이 리포지토리가 클라이언트 영속화의 유일한 지점이라는 점을 이용해
                // requireProofKey/requireAuthorizationConsent 둘 다 무조건 true로 강제한다. Spring의
                // DCR 기본 클라이언트 설정은 requireAuthorizationConsent=false라 그대로 두면 이 앱의
                // 동의 화면(OAuthConsentController)이 DCR로 등록된 MCP 클라이언트에는 뜨지 않는다.
                requireProofKey = true,
                requireAuthorizationConsent = true,
                // DCR로 등록되는 RegisteredClient는 클라이언트가 TokenSettings를 지정할 방법이 없어
                // (RFC7591 client metadata에 토큰 수명 필드가 없음) 항상 Spring Authorization
                // Server의 내장 기본값(액세스 5분)을 그대로 받는다 — 5분마다 MCP 클라이언트가 전체
                // 인가 화면을 다시 띄워야 하는 건 "접속만으로 자동 인가" 목표와 맞지 않아, 전달받은
                // tokenSettings를 신뢰하지 않고 이 앱의 정책값(1시간)을 항상 적용한다.
                accessTokenTtlSeconds = OAuthRegisteredClient.DEFAULT_ACCESS_TOKEN_TTL_SECONDS,
                refreshTokenTtlSeconds = OAuthRegisteredClient.DEFAULT_REFRESH_TOKEN_TTL_SECONDS,
                reuseRefreshTokens = tokenSettings.isReuseRefreshTokens
            )
        }

        fun toRegisteredClient(entity: OAuthRegisteredClient): RegisteredClient {
            val builder = RegisteredClient.withId(entity.id)
                .clientId(entity.clientId)
                .clientIdIssuedAt(entity.clientIdIssuedAt)
                .clientName(entity.clientName)
            entity.clientSecret?.let { builder.clientSecret(it) }
            entity.clientSecretExpiresAt?.let { builder.clientSecretExpiresAt(it) }

            entity.clientAuthenticationMethods.split(DELIMITER).filter { it.isNotBlank() }.forEach {
                builder.clientAuthenticationMethod(ClientAuthenticationMethod(it))
            }
            entity.authorizationGrantTypes.split(DELIMITER).filter { it.isNotBlank() }.forEach {
                builder.authorizationGrantType(AuthorizationGrantType(it))
            }
            entity.redirectUris?.split(DELIMITER)?.filter { it.isNotBlank() }?.forEach {
                builder.redirectUri(it)
            }
            entity.scopes.split(DELIMITER).filter { it.isNotBlank() }.forEach {
                builder.scope(it)
            }

            builder.clientSettings(
                ClientSettings.builder()
                    .requireProofKey(entity.requireProofKey)
                    .requireAuthorizationConsent(entity.requireAuthorizationConsent)
                    .build()
            )
            builder.tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofSeconds(entity.accessTokenTtlSeconds))
                    .refreshTokenTimeToLive(Duration.ofSeconds(entity.refreshTokenTtlSeconds))
                    .reuseRefreshTokens(entity.reuseRefreshTokens)
                    .build()
            )

            return builder.build()
        }
    }
}
