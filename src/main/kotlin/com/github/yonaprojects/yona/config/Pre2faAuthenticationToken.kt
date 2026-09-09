package com.github.yonaprojects.yona.config

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.AuthorityUtils

// 1차 비밀번호 인증은 통과했지만 계정에 등록된 2FA(TOTP/WebAuthn) 검증이 아직 남은 "대기" 상태를
// 나타내는 임시 Authentication. 이 토큰만 SecurityContext에 있는 동안은 ROLE_PRE_2FA 권한뿐이라
// 다른 페이지 접근이 전부 막힌다(Pre2faGateFilter 참고) — 실수로 완전한 로그인 상태처럼 취급될
// 여지를 원천 차단한다. 2FA 검증에 성공하면 originalAuthentication(원래 권한을 가진 완전한
// Authentication)으로 SecurityContext를 교체한다(TwoFactorLoginController.finalizeLogin 참고).
class Pre2faAuthenticationToken(
    val originalAuthentication: org.springframework.security.core.Authentication
) : AbstractAuthenticationToken(AuthorityUtils.createAuthorityList("ROLE_PRE_2FA")) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = originalAuthentication.principal ?: originalAuthentication.name
    override fun getName(): String = originalAuthentication.name
}
