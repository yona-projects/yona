package dev.yona.oauth2server

import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.core.OAuth2RefreshToken
import org.springframework.security.oauth2.core.OAuth2Token
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * OAuth 토큰 생성기이다(추가 개발 항목 X-1, `decisions.md` D-71). 액세스 토큰은 `yona_oat_`, 갱신 토큰은 `yona_ort_`로 시작하는 무작위 불투명 값이다(접두어가 있어 비밀 값 검색 도구와 토큰 종류 판별에 쓸 수 있다).
 * 수명은 앱의 토큰 설정을 따른다. 인가 코드는 라이브러리의 기본 생성기가 만든다.
 */
class YonaTokenGenerator : OAuth2TokenGenerator<OAuth2Token> {

    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    override fun generate(context: OAuth2TokenContext): OAuth2Token? {
        val settings = context.registeredClient.tokenSettings
        val issuedAt = Instant.now()
        return when (context.tokenType.value) {
            OAuth2ParameterNames.ACCESS_TOKEN -> OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, ACCESS_PREFIX + randomValue(), issuedAt, issuedAt.plus(settings.accessTokenTimeToLive), context.authorizedScopes)
            OAuth2ParameterNames.REFRESH_TOKEN -> OAuth2RefreshToken(REFRESH_PREFIX + randomValue(), issuedAt, issuedAt.plus(settings.refreshTokenTimeToLive))
            else -> null
        }
    }

    private fun randomValue(): String = ByteArray(RANDOM_BYTES).also { random.nextBytes(it) }.let { encoder.encodeToString(it) }

    companion object {
        const val ACCESS_PREFIX = "yona_oat_"
        const val REFRESH_PREFIX = "yona_ort_"
        private const val RANDOM_BYTES = 36
    }
}
