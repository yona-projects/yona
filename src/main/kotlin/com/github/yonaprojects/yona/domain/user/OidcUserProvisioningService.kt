package com.github.yonaprojects.yona.domain.user

import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * `LdapUserProvisioningService`와 동일한 취지의 JIT
 * (Just-In-Time) 프로비저닝: 임의의 OIDC IdP(Okta/Azure AD/Keycloak 등)가 인증에 성공해 돌려준
 * `OidcUser`(Spring Security가 ID 토큰/UserInfo 엔드포인트 응답을 이미 파싱해 준 결과)를 받아
 * 이메일 기준으로 기존 로컬 User와 매칭하거나 없으면 신규 생성한다.
 *
 * LDAP과 달리 리다이렉트 기반 로그인이라 로컬 비밀번호를 알 수 없으므로(전달되지도 않음) 신규
 * 생성 계정은 password/passwordSalt를 비워둔다 — 이 계정은 항상 SSO로만 로그인하게 된다(로컬
 * 아이디/비번 로그인은 애초에 시도할 수 없음, YonaAuthenticationProvider.authenticateLocally()가
 * BadCredentialsException을 던질 뿐 별도 차단 로직은 불필요).
 *
 * 보안: 기존 계정에 연결(link)할 때만 `email_verified` 클레임이 true여야 한다 — "임의 IdP"를
 * 지원 대상으로 명시한 계획이라, 이메일 검증을 강제하지 않는(또는 자유 가입이 가능한) IdP에서
 * 피해자의 이메일을 자칭하는 계정으로 로그인해 피해자의 기존 yona 계정을 탈취하는 걸 막는다.
 * 신규 계정 생성은 뺏길 기존 계정이 없어 이 제약을 적용하지 않는다(과도한 제약 방지 — 일부
 * 엔터프라이즈 IdP는 첫 로그인에서 email_verified 자체를 안 보낼 수 있다).
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
            if (oidcUser.emailVerified != true) {
                throw IllegalStateException(
                    "OIDC 이메일($email)이 검증되지 않아(email_verified) 기존 계정에 연결할 수 없습니다."
                )
            }
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
