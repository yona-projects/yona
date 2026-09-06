package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

/**
 * yona-wiki P3-03 Step2 — HTTPS 경로의 Deploy Key 인증. GitHub App의 설치 토큰 관례
 * ("x-access-token" 사용자명 + 토큰을 비밀번호로 쓰는 Basic 인증)을 그대로 따른다
 * ("모호하면 GitHub 방식을 기본값으로" 원칙).
 *
 * `HttpSecurity.authenticationProvider(this)`로 등록되면(SecurityConfig 참고) 기존
 * DaoAuthenticationProvider(폼 로그인/일반 Basic 인증)를 대체하지 않고 공유 AuthenticationManager의
 * 후보 목록에 추가만 된다 — 이 provider가 null을 반환하면(사용자명이 다르거나 알 수 없는 토큰)
 * ProviderManager가 다음 provider(DaoAuthenticationProvider)로 계속 진행해 기존 사용자명/비밀번호
 * 로그인 동작에 영향을 주지 않는다.
 */
@Component
class DeployKeyAuthenticationProvider(
    private val deployKeyService: DeployKeyService
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication? {
        if (authentication.name != DEPLOY_KEY_USERNAME) return null

        val rawToken = authentication.credentials?.toString()
        if (rawToken.isNullOrBlank()) return null

        val deployKey = deployKeyService.findByHttpsToken(rawToken) ?: return null

        deployKeyService.markUsed(deployKey)
        return DeployKeyAuthenticationToken.authenticated(deployKey)
    }

    override fun supports(authentication: Class<*>): Boolean =
        UsernamePasswordAuthenticationToken::class.java.isAssignableFrom(authentication)

    companion object {
        const val DEPLOY_KEY_USERNAME = "x-access-deploykey"
    }
}
