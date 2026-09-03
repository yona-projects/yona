package com.github.yonaprojects.yona.config.oauth2

class OAuth2UserInfoFactory {
    companion object {
        fun getOAuth2UserInfo(registrationId: String, attributes: Map<String, Any>): OAuth2UserInfo {
            return when (registrationId.lowercase()) {
                "google" -> GoogleOAuth2UserInfo(attributes)
                "github" -> GithubOAuth2UserInfo(attributes)
                else -> throw IllegalArgumentException("지원하지 않는 소셜 로그인 제공자입니다: $registrationId")
            }
        }
    }
}
