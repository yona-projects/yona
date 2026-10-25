package dev.yona.domain.social

import dev.yona.config.YonaProperties
import org.springframework.stereotype.Component

/**
 * 쓸 수 있는 소셜 로그인 제공자 목록이다. v1.6 `play.plugins`에 등록된 GitHub와 Google 가운데
 * 설정 `application.social.login.support`에 올라 있고 클라이언트 ID와 비밀이 설정된 것만 쓴다(A-95).
 */
@Component
class SocialProviders(private val properties: YonaProperties) {

    private val social get() = properties.social

    private val all: List<SocialProvider>
        get() = listOf(OAuth2SocialProvider.github(withDefaults(social.github, GITHUB)), OAuth2SocialProvider.google(withDefaults(social.google, GOOGLE)))

    /** 비워 둔 권한 범위와 주소를 [defaults]로 채운다. */
    private fun withDefaults(config: YonaProperties.OAuthProvider, defaults: YonaProperties.OAuthProvider) = config.copy(
        scope = config.scope.ifBlank { defaults.scope },
        authorizationUrl = config.authorizationUrl.ifBlank { defaults.authorizationUrl },
        accessTokenUrl = config.accessTokenUrl.ifBlank { defaults.accessTokenUrl },
        userInfoUrl = config.userInfoUrl.ifBlank { defaults.userInfoUrl }
    )

    /** 로그인 화면에 버튼으로 보일 제공자 키이다. v1.6 `forProviders`의 순서를 따른다(GitHub, Google). */
    fun available(): List<String> = all.filter { it.key in social.allowedProviders() && it.configured }.map { it.key }

    /** 쓸 수 있는 제공자를 찾는다. 알 수 없거나 허용되지 않았거나 설정이 없으면 `null`이다. */
    fun find(key: String): SocialProvider? = all.firstOrNull { it.key == key && it.key in social.allowedProviders() && it.configured }

    /**
     * 제공자가 인증을 마치고 돌아올 주소이다 (`play-authenticate.<제공자>.redirectUri`).
     * 기본은 `http`이며 `redirectSecure`를 켜면 `https`이다. 호스트는 `redirectHost`가 있으면 그것을, 없으면 요청의 `Host`를 쓴다.
     */
    fun redirectUri(key: String, requestHost: String, contextPath: String): String {
        val config = if (key == "github") social.github else social.google
        val scheme = if (config.redirectSecure) "https" else "http"
        val host = config.redirectHost.ifBlank { requestHost }
        return "$scheme://$host$contextPath/authenticate/$key"
    }

    private companion object {
        val GITHUB = YonaProperties.OAuthProvider(
            scope = "user:email", authorizationUrl = "https://github.com/login/oauth/authorize",
            accessTokenUrl = "https://github.com/login/oauth/access_token", userInfoUrl = "https://api.github.com/user"
        )
        val GOOGLE = YonaProperties.OAuthProvider(
            scope = "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email",
            authorizationUrl = "https://accounts.google.com/o/oauth2/auth",
            accessTokenUrl = "https://accounts.google.com/o/oauth2/token", userInfoUrl = "https://www.googleapis.com/oauth2/v1/userinfo"
        )
    }
}
