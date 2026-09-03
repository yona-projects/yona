package com.github.yonaprojects.yona.config.oauth2

abstract class OAuth2UserInfo(protected val attributes: Map<String, Any>) {
    abstract val id: String
    abstract val name: String
    abstract val email: String
    abstract val loginId: String
}

class GoogleOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val id: String
        get() = attributes["sub"] as String
    override val name: String
        get() = attributes["name"] as String
    override val email: String
        get() = attributes["email"] as String
    override val loginId: String
        get() = email.substringBefore("@")
}

class GithubOAuth2UserInfo(attributes: Map<String, Any>) : OAuth2UserInfo(attributes) {
    override val id: String
        get() = (attributes["id"] as Number).toString()
    override val name: String
        get() = (attributes["name"] ?: attributes["login"]) as String
    override val email: String
        get() = (attributes["email"] ?: "") as String
    override val loginId: String
        get() = attributes["login"] as String
}
