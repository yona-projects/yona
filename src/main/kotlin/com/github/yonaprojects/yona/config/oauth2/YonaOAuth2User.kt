package com.github.yonaprojects.yona.config.oauth2

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User

class YonaOAuth2User(
    val user: User,
    private val attributes: Map<String, Any>,
    private val authorities: Collection<GrantedAuthority>
) : OAuth2User {
    override fun getName(): String = user.loginId
    override fun getAttributes(): Map<String, Any> = attributes
    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
}
