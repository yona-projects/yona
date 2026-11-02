package dev.yona.domain.sso

import dev.yona.domain.audit.AuditRecorder
import dev.yona.domain.audit.auditUser
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.LinkedAccount
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.LoginIdRules
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredential
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.util.HtmlSanitizer
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

/**
 * SSO 신원을 로컬 계정에 맞춘다(추가 개발 항목 X-16, `decisions.md` D-80 6~10번). 소셜 로그인의 [dev.yona.domain.social.SocialAccountService]와 같은 자리이고 같은 테이블
 * (`user_credential`, `linked_account`)을 쓴다. 규칙은 이전 구현의 시제품과 다르게 **이메일만 보고 계정에 붙지 않는다**:
 * 1. 안정 식별자(발급자와 subject)로 먼저 찾는다. 이메일이 바뀌어도 같은 사람이다.
 * 2. 처음이면 같은 이메일의 로컬 사용자에게 **IdP가 이메일을 확인했다고 알린 경우에만**(설정으로 완화) 연결한다.
 * 3. 로컬 사용자가 없으면 설정에 따라 새로 만든다(활성, 쓸 수 없는 비밀번호, 충돌을 피한 로그인 ID).
 * 잠긴 사용자는 로그인시키지 않는다. 세션과 2단계 인증은 다루지 않고 결과 사용자를 받은 호출한 쪽이 로그인시킨다.
 */
@Service
@Transactional
class SsoAccountService(
    private val credentials: UserCredentialRepository,
    private val linkedAccounts: LinkedAccountRepository,
    private val users: UserRepository,
    private val lookup: UserLookup,
    private val signup: SignupService,
    private val audit: AuditRecorder
) {

    private val random = SecureRandom()

    /** [identity]로 로그인시킬 사용자를 정한다. 거절하면 이유를 돌려주고 아무것도 만들지 않는다. */
    fun signIn(identity: SsoIdentity, policy: SsoPolicy): SsoSignInResult {
        credentials.findActiveByProvider(identity.protocol.providerKey, identity.providerUserId).firstOrNull()?.let { credential ->
            val user = credential.user ?: credential.loginId?.let { users.findByLoginId(it) } ?: return SsoSignInResult.Rejected(SsoRejection.USER_NOT_FOUND)
            if (user.state != UserState.ACTIVE) return SsoSignInResult.Rejected(SsoRejection.LOCKED)
            if (policy.nameSync && !identity.name.isNullOrBlank()) { user.name = HtmlSanitizer.defaultSanitize(identity.name); users.save(user) }
            return SsoSignInResult.Success(user, created = false, linked = false)
        }
        val email = identity.email?.trim()?.takeIf { it.isNotEmpty() } ?: return SsoSignInResult.Rejected(SsoRejection.EMAIL_MISSING)
        if (!identity.emailVerified && !policy.allowUnverifiedEmail) return SsoSignInResult.Rejected(SsoRejection.EMAIL_UNVERIFIED)
        val existing = lookup.findByEmail(email)
        if (existing != null) {
            if (!policy.linkByEmail) return SsoSignInResult.Rejected(SsoRejection.EMAIL_IN_USE)
            if (existing.state != UserState.ACTIVE) return SsoSignInResult.Rejected(SsoRejection.LOCKED)
            link(existing, identity, email)
            return SsoSignInResult.Success(existing, created = false, linked = true)
        }
        if (!policy.autoCreate) return SsoSignInResult.Rejected(SsoRejection.USER_NOT_FOUND)
        if (!signup.isAllowedEmailDomain(email)) return SsoSignInResult.Rejected(SsoRejection.EMAIL_DOMAIN)
        val created = create(identity, email)
        link(created, identity, email)
        return SsoSignInResult.Success(created, created = true, linked = true)
    }

    /** 신원을 [user]에 연결한다. */
    private fun link(user: User, identity: SsoIdentity, email: String) {
        val credential = credentials.save(UserCredential(user = user, loginId = user.loginId, email = email, name = identity.name, active = true, emailValidated = identity.emailVerified))
        linkedAccounts.save(LinkedAccount(userCredential = credential, providerKey = identity.protocol.providerKey, providerUserId = identity.providerUserId))
    }

    /**
     * 새 사용자를 만든다. IdP가 보증하므로 활성이고 이메일 인증과 가입 승인은 거치지 않는다(소셜 로그인과 같다). 비밀번호는 무작위 값이라 로컬 비밀번호 로그인은 할 수 없다
     * (저장소는 개인용 토큰으로 쓴다, D-70). 인증 메일을 보내지 않으려고 [SignupService.create]를 거치지 않고 직접 만든다.
     */
    private fun create(identity: SsoIdentity, email: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(
            User(
                loginId = newLoginId(identity, email), name = HtmlSanitizer.defaultSanitize(identity.name?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')), email = email,
                passwordSalt = salt, password = LegacyPasswordHasher.hash(randomPassword(), salt), state = UserState.ACTIVE, createdDate = Date()
            )
        )
        audit.record(auditUser("account.signup", user, mapOf("state" to user.state?.name, "source" to identity.protocol.providerKey)))
        return user
    }

    /** 후보(설정한 클레임, 없으면 이메일 앞부분)를 로그인 ID 형식에 맞게 정리하고 충돌과 예약어를 피한다. */
    private fun newLoginId(identity: SsoIdentity, email: String): String {
        val candidate = sanitize(identity.preferredLoginId).ifEmpty { sanitize(email.substringBefore('@')) }.ifEmpty { "user" }
        val unique = signup.generateLoginId(candidate)
        return if (LoginIdRules.isReserved(unique)) signup.generateLoginId("$candidate-sso") else unique
    }

    /** 허용하지 않는 글자는 `-`로 바꾸고 앞뒤의 `-`와 연속된 `-`를 줄인다. 결과가 형식을 지키지 않으면 빈 값이다. */
    private fun sanitize(value: String?): String {
        val cleaned = value.orEmpty().trim().replace(Regex("[^a-zA-Z0-9가-힣_.-]"), "-").replace(Regex("-{2,}"), "-").trim('-', '.', '_')
        return cleaned.takeIf { LoginIdRules.isValidFormat(it) && !LoginIdRules.isReserved(it) } ?: ""
    }

    private fun randomPassword(): String = Base64.getEncoder().encodeToString(ByteArray(24).also { random.nextBytes(it) })
}
