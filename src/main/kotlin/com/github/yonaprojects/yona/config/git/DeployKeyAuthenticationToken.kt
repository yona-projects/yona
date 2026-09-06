package com.github.yonaprojects.yona.config.git

import com.github.yonaprojects.yona.domain.deploykey.DeployKey
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

// yona-wiki P3-03 Step2 — DeployKeyAuthenticationProvider가 HTTPS Basic 인증에 성공했을 때
// SecurityContext에 심는 인증 객체. GitAuthorizationFilter가 이 타입을 인식해 project 스코프/
// read_only 검사를 수행한다(일반 로그인 사용자의 loginId 기반 멤버십 검사와는 분기).
class DeployKeyAuthenticationToken private constructor(
    val deployKey: DeployKey
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_DEPLOY_KEY"))) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): Any = deployKey
    override fun getName(): String = "deploy-key:${deployKey.id}"

    companion object {
        fun authenticated(deployKey: DeployKey): DeployKeyAuthenticationToken = DeployKeyAuthenticationToken(deployKey)
    }
}
