package com.github.yonaprojects.yona.domain.oauth2server

import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference

// yona-wiki P3-07(MCP 서버) Step2 — OAuth2AuthorizationService의 JPA 기반 구현. 컬럼 구성/직렬화
// 방식은 JdbcOAuth2AuthorizationService 공식 소스를 그대로 참고했다(OAuthAuthorization.kt 상단 주석
// 참고). user_code/device_code 두 토큰 타입은 이 프로젝트가 쓰지 않아(디바이스 플로우 없음) 여전히
// 제외한다 — oidc_id_token은 P3-14 2라운드부터 지원한다(아래 toEntity/toAuthorization의
// OidcIdToken 처리 참고).
@Component
class JpaOAuth2AuthorizationService(
    private val repository: OAuthAuthorizationRepository,
    private val registeredClientRepository: RegisteredClientRepository
) : OAuth2AuthorizationService {

    @Transactional
    override fun save(authorization: OAuth2Authorization) {
        repository.save(toEntity(authorization))
    }

    @Transactional
    override fun remove(authorization: OAuth2Authorization) {
        repository.deleteById(authorization.id)
    }

    override fun findById(id: String): OAuth2Authorization? =
        repository.findById(id).map { toAuthorization(it) }.orElse(null)

    override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
        val entity = when {
            tokenType == null ->
                repository.findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(
                    token, token, token, token
                )
            OAuth2ParameterNames.STATE == tokenType.value -> repository.findByState(token)
            OAuth2ParameterNames.CODE == tokenType.value -> repository.findByAuthorizationCodeValue(token)
            OAuth2TokenType.ACCESS_TOKEN == tokenType -> repository.findByAccessTokenValue(token)
            OAuth2TokenType.REFRESH_TOKEN == tokenType -> repository.findByRefreshTokenValue(token)
            else -> return null
        }
        return entity.map { toAuthorization(it) }.orElse(null)
    }

    companion object {
        private val mapper = OAuthObjectMapper.instance
        private val mapTypeRef = object : TypeReference<Map<String, Any>>() {}

        private fun writeMap(data: Map<String, Any>): String? =
            if (data.isEmpty()) null else mapper.writeValueAsString(data)

        private fun readMap(data: String?): Map<String, Any> =
            if (data.isNullOrBlank()) emptyMap() else mapper.readValue(data, mapTypeRef)

        fun toEntity(authorization: OAuth2Authorization): OAuthAuthorization {
            val entity = OAuthAuthorization(
                id = authorization.id,
                registeredClientId = authorization.registeredClientId,
                principalName = authorization.principalName,
                authorizationGrantType = authorization.authorizationGrantType.value,
                authorizedScopes = authorization.authorizedScopes.takeIf { it.isNotEmpty() }?.joinToString(","),
                attributes = writeMap(authorization.attributes),
                state = authorization.getAttribute<String>(OAuth2ParameterNames.STATE)
            )

            authorization.getToken(OAuth2AuthorizationCode::class.java)?.let { code ->
                entity.authorizationCodeValue = code.token.tokenValue
                entity.authorizationCodeIssuedAt = code.token.issuedAt
                entity.authorizationCodeExpiresAt = code.token.expiresAt
                entity.authorizationCodeMetadata = writeMap(code.metadata)
            }

            authorization.getToken(OAuth2AccessToken::class.java)?.let { accessToken ->
                entity.accessTokenValue = accessToken.token.tokenValue
                entity.accessTokenIssuedAt = accessToken.token.issuedAt
                entity.accessTokenExpiresAt = accessToken.token.expiresAt
                entity.accessTokenMetadata = writeMap(accessToken.metadata)
                entity.accessTokenType = accessToken.token.tokenType.value
                entity.accessTokenScopes = accessToken.token.scopes.takeIf { it.isNotEmpty() }?.joinToString(",")
            }

            authorization.refreshToken?.let { refreshToken ->
                entity.refreshTokenValue = refreshToken.token.tokenValue
                entity.refreshTokenIssuedAt = refreshToken.token.issuedAt
                entity.refreshTokenExpiresAt = refreshToken.token.expiresAt
                entity.refreshTokenMetadata = writeMap(refreshToken.metadata)
            }

            // yona-wiki P3-14 2라운드(OIDC) — OAuthAuthorization.kt의 oidcIdToken* 필드 주석 참고.
            // idToken.metadata에는 프레임워크가 발급 시점에 이미 CLAIMS_METADATA_NAME 키로 클레임
            // 전체를 담아뒀으므로(OAuth2AuthorizationCodeAuthenticationProvider 공식 소스 확인),
            // 다른 토큰들과 동일하게 metadata만 통째로 직렬화하면 클레임까지 함께 보존된다 — 별도
            // claims 컬럼/역직렬화 로직이 필요 없다.
            authorization.getToken(OidcIdToken::class.java)?.let { idToken ->
                entity.oidcIdTokenValue = idToken.token.tokenValue
                entity.oidcIdTokenIssuedAt = idToken.token.issuedAt
                entity.oidcIdTokenExpiresAt = idToken.token.expiresAt
                entity.oidcIdTokenMetadata = writeMap(idToken.metadata)
            }

            return entity
        }

        fun toAuthorization(entity: OAuthAuthorization, registeredClientRepository: RegisteredClientRepository): OAuth2Authorization {
            val registeredClient = registeredClientRepository.findById(entity.registeredClientId)
                ?: throw IllegalArgumentException(
                    "RegisteredClient with id '${entity.registeredClientId}' was not found"
                )

            val builder = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(entity.id)
                .principalName(entity.principalName)
                .authorizationGrantType(AuthorizationGrantType(entity.authorizationGrantType))
                .authorizedScopes(
                    entity.authorizedScopes?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                )
                .attributes { it.putAll(readMap(entity.attributes)) }

            val stateValue = entity.state
            if (!stateValue.isNullOrBlank()) {
                builder.attribute(OAuth2ParameterNames.STATE, stateValue)
            }

            entity.authorizationCodeValue?.let { value ->
                val code = OAuth2AuthorizationCode(
                    value,
                    requireNotNull(entity.authorizationCodeIssuedAt),
                    requireNotNull(entity.authorizationCodeExpiresAt)
                )
                builder.token(code) { it.putAll(readMap(entity.authorizationCodeMetadata)) }
            }

            entity.accessTokenValue?.let { value ->
                val tokenType = when (entity.accessTokenType) {
                    OAuth2AccessToken.TokenType.DPOP.value -> OAuth2AccessToken.TokenType.DPOP
                    else -> OAuth2AccessToken.TokenType.BEARER
                }
                val scopes = entity.accessTokenScopes?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
                val accessToken = OAuth2AccessToken(
                    tokenType,
                    value,
                    entity.accessTokenIssuedAt,
                    entity.accessTokenExpiresAt,
                    scopes
                )
                builder.token(accessToken) { it.putAll(readMap(entity.accessTokenMetadata)) }
            }

            entity.refreshTokenValue?.let { value ->
                val refreshToken = OAuth2RefreshToken(value, entity.refreshTokenIssuedAt, entity.refreshTokenExpiresAt)
                builder.token(refreshToken) { it.putAll(readMap(entity.refreshTokenMetadata)) }
            }

            entity.oidcIdTokenValue?.let { value ->
                val metadata = readMap(entity.oidcIdTokenMetadata)
                @Suppress("UNCHECKED_CAST")
                val claims = metadata[OAuth2Authorization.Token.CLAIMS_METADATA_NAME] as? Map<String, Any>
                // OidcIdToken 생성자는 claims가 비어있으면 IllegalArgumentException을 던진다
                // (claims는 sub 등 필수 클레임을 담는 곳이라 프레임워크가 이렇게 강제한다) — 정상
                // 발급 경로라면 항상 채워져 있지만(위 toEntity 주석 참고), 방어적으로 비어있는
                // 경우엔 이 토큰을 아예 복원하지 않는다(예외로 authorization 전체 조회가 깨지는 것보다
                // 낫다).
                if (!claims.isNullOrEmpty()) {
                    val idToken = OidcIdToken(value, entity.oidcIdTokenIssuedAt, entity.oidcIdTokenExpiresAt, claims)
                    builder.token(idToken) { it.putAll(metadata) }
                }
            }

            return builder.build()
        }
    }

    private fun toAuthorization(entity: OAuthAuthorization): OAuth2Authorization =
        toAuthorization(entity, registeredClientRepository)
}
