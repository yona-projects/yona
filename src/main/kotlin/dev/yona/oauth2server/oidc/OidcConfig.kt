package dev.yona.oauth2server.oidc

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import dev.yona.domain.user.UserRepository
import dev.yona.oauth2server.ProtectedResources
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.Duration
import java.time.Instant

/**
 * OIDC 제공자의 서명 키와 ID 토큰 생성기이다(추가 개발 항목 X-40, `decisions.md` D-77). 서명 키 파일을 정했을 때만 만들어진다. 공개키 목록(`/oauth2/jwks`)은 이 [JWKSource]가 있으면
 * 라이브러리가 자동으로 연다(개인키 성분은 걸러서 낸다).
 */
@Configuration
@ConditionalOnExpression("!'\${yona.oauth2.oidc.signing-key-file:}'.isBlank()")
class OidcConfig {

    @Bean
    fun oidcJwkSource(settings: OidcSettings): JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(requireNotNull(settings.signingKey)))

    /** ID 토큰만 만드는 생성기이다. 액세스 토큰과 갱신 토큰은 불투명 토큰이라 [dev.yona.oauth2server.YonaTokenGenerator]가 먼저 맡고, 이 생성기는 ID 토큰 차례에만 쓰인다. */
    @Bean
    fun oidcIdTokenGenerator(jwkSource: JWKSource<SecurityContext>, users: UserRepository, claims: OidcClaims): JwtGenerator =
        JwtGenerator(NimbusJwtEncoder(jwkSource)).also { it.setJwtCustomizer(IdTokenCustomizer(users, claims)) }

    /**
     * ID 토큰의 클레임을 정한다. 발급자는 발견 문서와 같은 값(요청의 출처와 컨텍스트 경로)으로 맞추고, 유효 시간은 10분이며, 범위가 정한 사용자 클레임을 더한다.
     * `sub`(사용자 번호), `aud`(앱), `nonce`는 라이브러리가 채운다.
     */
    class IdTokenCustomizer(private val users: UserRepository, private val claims: OidcClaims) : OAuth2TokenCustomizer<JwtEncodingContext> {

        override fun customize(context: JwtEncodingContext) {
            if (context.tokenType.value != OidcParameterNames.ID_TOKEN) return
            val request = (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request ?: return
            val base = ProtectedResources.base(request)
            val now = Instant.now()
            context.claims.issuer(base).issuedAt(now).expiresAt(now.plus(ID_TOKEN_TTL))
            val user = context.getPrincipal<org.springframework.security.core.Authentication>()?.name?.toLongOrNull()?.let { users.findById(it).orElse(null) } ?: return
            claims.of(user, context.authorizedScopes, base).filterKeys { it != "sub" }.forEach { (name, value) -> context.claims.claim(name, value) }
        }
    }

    companion object {
        /** ID 토큰의 유효 시간이다. */
        val ID_TOKEN_TTL: Duration = Duration.ofMinutes(10)
    }
}
