package com.github.yonaprojects.yona.config.sso

import com.github.yonaprojects.yona.domain.user.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import org.springframework.security.oauth2.core.oidc.user.OidcUser

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) — `config/oauth2/YonaOAuth2User.kt`와 동일한 취지. `getName()`이
 * IdP의 원본 sub 클레임이 아니라 로컬 User.loginId를 반환해야, Git/SVN 인증 필터나
 * `userRepository.findByLoginId(authentication.name)` 같은 기존 관례가 그대로 동작한다.
 */
class YonaOidcUser(
    val user: User,
    private val idToken: OidcIdToken,
    private val userInfo: OidcUserInfo?,
    private val authorities: Collection<GrantedAuthority>
) : OidcUser {
    override fun getName(): String = user.loginId
    override fun getAttributes(): Map<String, Any> = idToken.claims
    override fun getAuthorities(): Collection<GrantedAuthority> = authorities
    override fun getClaims(): Map<String, Any> = idToken.claims
    override fun getUserInfo(): OidcUserInfo? = userInfo
    override fun getIdToken(): OidcIdToken = idToken
}
