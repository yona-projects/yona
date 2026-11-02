package dev.yona.oauth2server

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import org.springframework.dao.DataRetrievalFailureException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.jackson.SecurityJacksonModules
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationCode
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository
import org.springframework.security.oauth2.server.authorization.jackson.OAuth2AuthorizationServerJacksonModule
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.security.Principal
import java.time.Instant
import java.util.Date

/**
 * OAuth 인가의 저장소이다(추가 개발 항목 X-1, `decisions.md` D-71). Spring Authorization Server의 [OAuth2AuthorizationService]를 JPA로 구현한다. **토큰 값은 저장하지 않고 SHA-256 해시만 둔다**
 * (개인용 토큰과 같다). 저장한 인가를 다시 읽을 때 토큰 값 자리에는 `h:<해시>`를 넣는다(진짜 값은 서버도 모른다). 이미 읽은 인가를 다시 저장할 때는 `h:` 값을 해시로 되돌려 다시 해시하지 않는다.
 * 인가 요청의 주인(`Principal`)은 사용자 번호만 담은 단순한 인증 객체로 바꿔 저장한다(세션 인증 객체를 직렬화하지 않는다).
 */
@Component
class YonaOAuth2AuthorizationService(
    private val authorizations: OAuthAuthorizationRepository,
    private val registeredClients: RegisteredClientRepository
) : OAuth2AuthorizationService {

    private val mapper: JsonMapper = JsonMapper.builder().addModules(SecurityJacksonModules.getModules(YonaOAuth2AuthorizationService::class.java.classLoader)).addModule(OAuth2AuthorizationServerJacksonModule()).build()
    private val mapType = object : TypeReference<Map<String, Any?>>() {}

    @Transactional
    override fun save(authorization: OAuth2Authorization) {
        authorizations.save(toEntity(authorization))
    }

    @Transactional
    override fun remove(authorization: OAuth2Authorization) {
        authorizations.deleteById(authorization.id)
    }

    @Transactional(readOnly = true)
    override fun findById(id: String): OAuth2Authorization? = authorizations.findById(id).orElse(null)?.let(::toObject)

    @Transactional(readOnly = true)
    override fun findByToken(token: String, tokenType: OAuth2TokenType?): OAuth2Authorization? {
        val hash = TokenHasher.sha256Hex(token)
        val found = when (tokenType?.value) {
            OAuth2ParameterNames.STATE -> authorizations.findByState(token)
            OAuth2ParameterNames.CODE -> authorizations.findByCodeHash(hash)
            OAuth2ParameterNames.ACCESS_TOKEN -> authorizations.findByAccessTokenHash(hash)
            OAuth2ParameterNames.REFRESH_TOKEN -> authorizations.findByRefreshTokenHash(hash)
            null -> authorizations.findByState(token) ?: authorizations.findByCodeHash(hash) ?: authorizations.findByAccessTokenHash(hash) ?: authorizations.findByRefreshTokenHash(hash)
            else -> null
        }
        return found?.let { toObject(it, token) }
    }

    private fun toEntity(source: OAuth2Authorization): OAuthAuthorization {
        val entity = authorizations.findById(source.id).orElse(null) ?: OAuthAuthorization(id = source.id, createdDate = Date())
        entity.clientRowId = source.registeredClientId.toLong()
        entity.userId = source.principalName.toLong()
        entity.grantType = source.authorizationGrantType.value
        entity.authorizedScopes = source.authorizedScopes.joinToString(",")
        entity.state = source.getAttribute<String>(OAuth2ParameterNames.STATE)
        val attributes = LinkedHashMap(source.attributes)
        attributes[Principal::class.java.name] = UsernamePasswordAuthenticationToken.authenticated(source.principalName, null, emptyList())
        entity.attributes = mapper.writeValueAsString(attributes)

        source.getToken(OAuth2AuthorizationCode::class.java).let { t ->
            entity.codeHash = t?.token?.tokenValue?.let(::hashOf); entity.codeIssuedDate = t?.token?.issuedAt?.let(Date::from); entity.codeExpiresDate = t?.token?.expiresAt?.let(Date::from); entity.codeMetadata = t?.let { mapper.writeValueAsString(it.metadata) }
        }
        source.getToken(OAuth2AccessToken::class.java).let { t ->
            entity.accessTokenHash = t?.token?.tokenValue?.let(::hashOf); entity.accessTokenIssuedDate = t?.token?.issuedAt?.let(Date::from); entity.accessTokenExpiresDate = t?.token?.expiresAt?.let(Date::from)
            entity.accessTokenMetadata = t?.let { mapper.writeValueAsString(it.metadata) }; entity.accessTokenScopes = t?.token?.scopes?.joinToString(",")
        }
        source.getToken(OAuth2RefreshToken::class.java).let { t ->
            entity.refreshTokenHash = t?.token?.tokenValue?.let(::hashOf); entity.refreshTokenIssuedDate = t?.token?.issuedAt?.let(Date::from); entity.refreshTokenExpiresDate = t?.token?.expiresAt?.let(Date::from)
            entity.refreshTokenMetadata = t?.let { mapper.writeValueAsString(it.metadata) }
        }
        return entity
    }

    /**
     * 저장한 행을 인가 객체로 바꾼다. 토큰 값 자리에는 `h:<해시>`를 넣되, 조회에 쓴 진짜 값([presented])의 해시와 같은 토큰은 그 진짜 값을 넣는다
     * (라이브러리가 취소할 때 값으로 토큰을 찾기 때문이다).
     */
    private fun toObject(entity: OAuthAuthorization, presented: String? = null): OAuth2Authorization {
        val presentedHash = presented?.let { TokenHasher.sha256Hex(it) }
        fun valueOf(hash: String) = if (presented != null && hash == presentedHash) presented else "$MARK$hash"
        val client = registeredClients.findById(entity.clientRowId.toString()) ?: throw DataRetrievalFailureException("앱이 없다: ${entity.clientRowId}")
        val builder = OAuth2Authorization.withRegisteredClient(client).id(requireNotNull(entity.id)).principalName(entity.userId.toString())
            .authorizationGrantType(AuthorizationGrantType(requireNotNull(entity.grantType))).authorizedScopes(entity.authorizedScopes.orEmpty().split(',').filter { it.isNotEmpty() }.toSet())
            .attributes { it.putAll(read(entity.attributes)) }
        entity.codeHash?.let { builder.token(OAuth2AuthorizationCode(valueOf(it), requireNotNull(entity.codeIssuedDate).toInstant(), requireNotNull(entity.codeExpiresDate).toInstant())) { m -> m.putAll(read(entity.codeMetadata)) } }
        entity.accessTokenHash?.let {
            builder.token(OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, valueOf(it), requireNotNull(entity.accessTokenIssuedDate).toInstant(), requireNotNull(entity.accessTokenExpiresDate).toInstant(), entity.accessTokenScopes.orEmpty().split(',').filter { s -> s.isNotEmpty() }.toSet())) { m -> m.putAll(read(entity.accessTokenMetadata)) }
        }
        entity.refreshTokenHash?.let { builder.token(OAuth2RefreshToken(valueOf(it), requireNotNull(entity.refreshTokenIssuedDate).toInstant(), requireNotNull(entity.refreshTokenExpiresDate).toInstant())) { m -> m.putAll(read(entity.refreshTokenMetadata)) } }
        return builder.build()
    }

    private fun read(json: String?): Map<String, Any> = if (json.isNullOrBlank()) emptyMap() else mapper.readValue(json, mapType).filterValues { it != null }.mapValues { requireNotNull(it.value) }

    private fun hashOf(value: String): String = if (value.startsWith(MARK)) value.substring(MARK.length) else TokenHasher.sha256Hex(value)

    private companion object {
        const val MARK = "h:"
    }
}
