package dev.yona.domain.social

import dev.yona.config.YonaProperties
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLEncoder

/** 소셜 로그인 제공자와의 통신이 실패했다. 토큰을 받지 못했거나 사용자 정보를 읽지 못한 경우이다 (v1.6 play-authenticate `AuthException`). */
class SocialLoginException(message: String) : RuntimeException(message)

/**
 * 소셜 로그인 제공자 한 곳(GitHub 또는 Google)과 OAuth 2.0 인증 코드 흐름으로 통신한다. v1.6 play-authenticate 0.6.9의
 * `GithubAuthProvider`, `GoogleAuthProvider`에 해당한다. 테스트에서는 이 인터페이스를 바꿔 끼운다.
 */
interface SocialProvider {

    /** 제공자 키 (`github`, `google`). */
    val key: String

    /** 설정에 클라이언트 ID와 비밀이 모두 있으면 `true`이다. */
    val configured: Boolean

    /**
     * 사용자를 보낼 인증 화면 주소이다.
     *
     * @param redirectUri 인증을 마치고 제공자가 돌아올 주소
     * @param state 요청 위조를 막으려고 세션에 저장해 두었다가 돌아올 때 대조하는 값
     */
    fun authorizationUri(redirectUri: String, state: String): String

    /**
     * 인증 코드를 접근 토큰으로 바꾸고 사용자 정보를 읽어 신원으로 돌려준다.
     *
     * @throws SocialLoginException 토큰이나 사용자 정보를 받지 못했을 때
     */
    fun identityOf(code: String, redirectUri: String): SocialIdentity
}

/**
 * [SocialProvider]의 표준 OAuth 2.0 구현이다. 제공자마다 다른 것은 사용자 정보를 신원으로 바꾸는 방법뿐이다.
 *
 * v1.6과 달리 `state`를 보내고 접근 토큰은 `Authorization` 헤더로 보낸다(GitHub가 주소의 `access_token` 인자를 더 이상 받지 않는다, A-95).
 */
class OAuth2SocialProvider(
    override val key: String,
    private val config: YonaProperties.OAuthProvider,
    private val toIdentity: (JsonNode) -> SocialIdentity?
) : SocialProvider {

    /** 접근 토큰과 만료 시각(UNIX 시간, 밀리초. 만료가 없으면 -1)이다. */
    private data class Token(val value: String, val expires: Long)

    private val mapper = JsonMapper.builder().build()

    override val configured: Boolean get() = config.clientId.isNotBlank() && config.clientSecret.isNotBlank()

    override fun authorizationUri(redirectUri: String, state: String): String {
        val separator = if (config.authorizationUrl.contains('?')) '&' else '?'
        fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8)
        return "${config.authorizationUrl}${separator}client_id=${enc(config.clientId)}&redirect_uri=${enc(redirectUri)}" +
            "&scope=${enc(config.scope)}&response_type=code&state=${enc(state)}"
    }

    override fun identityOf(code: String, redirectUri: String): SocialIdentity {
        val token = exchange(code, redirectUri)
        val body = RestClient.create().get().uri(config.userInfoUrl)
            .header("Authorization", "Bearer ${token.value}").header("Accept", "application/json")
            .retrieve().onStatus({ true }, { _, _ -> }).toEntity(String::class.java)
        val user = runCatching { mapper.readTree(body.body.orEmpty()) }.getOrNull()
        // v1.6: 응답에 `error`가 있으면 그 문구로 실패한다.
        user?.get("error")?.let { throw SocialLoginException(it.asString()) }
        // v1.6은 상태 코드를 보지 않고 JSON의 필드를 읽는다. 깨진 본문이나 id가 없는 응답은 NPE로 오류 화면 500이다(문구 본문이 아니다)
        return user?.let(toIdentity)?.copy(expires = token.expires) ?: error("$key 사용자 정보를 읽지 못했습니다: ${body.statusCode.value()}")
    }

    private fun exchange(code: String, redirectUri: String): Token {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", config.clientId); add("client_secret", config.clientSecret); add("code", code)
            add("redirect_uri", redirectUri); add("grant_type", "authorization_code")
        }
        val response = RestClient.create().post().uri(config.accessTokenUrl)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            // v1.6 play-authenticate: GitHub만 `Accept: application/json`을 보내고 Google은 기본값(`*/*`)이다
            .accept(if (key == "github") MediaType.APPLICATION_JSON else MediaType.ALL).body(form)
            .retrieve().onStatus({ true }, { _, _ -> }).toEntity(String::class.java)
        val json = if (response.statusCode.is2xxSuccessful) runCatching { mapper.readTree(response.body.orEmpty()) }.getOrNull() else null
        json?.get("error")?.let { throw SocialLoginException(it.asString()) }
        val token = json?.path("access_token")?.asString("").orEmpty()
        if (token.isEmpty()) throw SocialLoginException("$key 접근 토큰을 받지 못했습니다: ${response.statusCode.value()}")
        val expiresIn = json?.path("expires_in")?.asLong(0) ?: 0
        return Token(token, if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else -1)
    }

    companion object {
        /**
         * 응답의 [field]를 문자열로 읽는다. 필드가 없거나 JSON `null`이면 `null`이다.
         * v1.6 play-authenticate는 `n.has(field) ? n.get(field).asText()`라 JSON `null`을 문자열 `"null"`로 읽어, 이름이 없는 GitHub 사용자의 이름이 `"null"`이 되었다(R-59-11).
         */
        private fun text(node: JsonNode, field: String): String? = node.get(field)?.takeUnless { it.isNull }?.asString()

        /** GitHub 제공자이다. */
        fun github(config: YonaProperties.OAuthProvider): OAuth2SocialProvider = OAuth2SocialProvider("github", config) { user ->
            text(user, "id")?.let { SocialIdentity("github", it, text(user, "email"), text(user, "name")) }
        }

        /** Google 제공자이다. */
        fun google(config: YonaProperties.OAuthProvider): OAuth2SocialProvider = OAuth2SocialProvider("google", config) { user ->
            // 제공자가 확인하지 않은 이메일은 쓰지 않는다. v1.6은 확인 여부를 보지 않고 확인된 것으로 보아 같은 이메일의 기존 계정으로 로그인시켰다(R-59-3).
            // GitHub의 공개 이메일은 GitHub가 확인한 주소만 고를 수 있어 그대로 쓴다.
            text(user, "id")?.let { SocialIdentity("google", it, text(user, "email")?.takeIf { user.path("verified_email").asBoolean(false) }, text(user, "name")) }
        }
    }
}
