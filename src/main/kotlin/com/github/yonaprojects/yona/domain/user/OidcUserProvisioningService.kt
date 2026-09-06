package com.github.yonaprojects.yona.domain.user

import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * yona-wiki P3-06(엔터프라이즈 SSO) Step2 — `LdapUserProvisioningService`와 동일한 취지의 JIT
 * (Just-In-Time) 프로비저닝: 임의의 OIDC IdP(Okta/Azure AD/Keycloak 등)가 인증에 성공해 돌려준
 * `OidcUser`(Spring Security가 ID 토큰/UserInfo 엔드포인트 응답을 이미 파싱해 준 결과)를 받아
 * 이메일 기준으로 기존 로컬 User와 매칭하거나 없으면 신규 생성한다.
 *
 * LDAP과 달리 리다이렉트 기반 로그인이라 로컬 비밀번호를 알 수 없으므로(전달되지도 않음) 신규
 * 생성 계정은 password/passwordSalt를 비워둔다 — 이 계정은 항상 SSO로만 로그인하게 된다(로컬
 * 아이디/비번 로그인은 애초에 시도할 수 없음, YonaAuthenticationProvider.authenticateLocally()가
 * BadCredentialsException을 던질 뿐 별도 차단 로직은 불필요).
 */
@Service
class OidcUserProvisioningService(
    private val userRepository: UserRepository
) {
    @Transactional
    fun reconcile(oidcUser: OidcUser): User {
        val email = oidcUser.email
            ?: throw IllegalStateException("OIDC 사용자 정보에 이메일(email) 클레임이 없습니다.")

        val existing = userRepository.findByEmail(email).orElse(null)
        return if (existing == null) {
            createNewUser(email, oidcUser)
        } else {
            syncExistingUser(existing, oidcUser)
        }
    }

    private fun resolveName(email: String, oidcUser: OidcUser): String {
        return oidcUser.fullName
            ?: oidcUser.preferredUsername
            ?: email.substringBefore("@")
    }

    private fun createNewUser(email: String, oidcUser: OidcUser): User {
        val loginId = oidcUser.preferredUsername ?: email.substringBefore("@")
        val user = User(
            loginId = loginId,
            name = resolveName(email, oidcUser),
            email = email,
            state = UserState.ACTIVE,
            createdDate = Instant.now()
        )
        return userRepository.save(user)
    }

    private fun syncExistingUser(user: User, oidcUser: OidcUser): User {
        user.name = resolveName(user.email, oidcUser)
        return userRepository.save(user)
    }
}
