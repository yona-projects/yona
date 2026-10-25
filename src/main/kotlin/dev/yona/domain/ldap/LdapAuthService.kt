package dev.yona.domain.ldap

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserLookup
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException

/**
 * LDAP 인증의 결과이다.
 *
 * @property user 인증된 로컬 사용자. 실패하거나 가입 승인 대기이면 `null`
 * @property flash 다음 화면에 한 번만 보일 안내(키: `warning`, `info`, 값: 메시지 키)
 */
data class LdapAuthResult(val user: User? = null, val flash: Map<String, String> = emptyMap())

/**
 * LDAP으로 인증하고 결과를 로컬 사용자와 맞춘다. v1.6 `UserApp.authenticateWithLdap`, `createNewUser(String, LdapUser)`,
 * `createUserDelegate`(`app/controllers/UserApp.java:1156-1200`, `454-475`)와 `LdapService.guessedUser`에 해당한다.
 *
 * 로컬 사용자가 이미 있으면(이메일로 찾는다) 이름, 영문 이름을 LDAP 값으로 고치고 비밀번호가 다르면 입력한 비밀번호로 바꾼다.
 * 없으면 입력한 비밀번호로 새로 만든다. 서버에 연결하지 못하거나 인증에 실패했을 때 `fallbackToLocalLogin`이 켜져 있으면 로컬
 * 비밀번호로 다시 시도한다.
 */
@Service
@Transactional
class LdapAuthService(
    private val directory: LdapDirectory,
    private val users: UserRepository,
    private val lookup: UserLookup,
    private val signup: SignupService,
    private val properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val config get() = properties.ldap

    /** LDAP으로 로그인할 수 있도록 설정되어 있으면 `true`이다 (v1.6 `LdapService.useLdap`). */
    val enabled: Boolean get() = config.enabled

    /**
     * @param loginIdOrEmail 사용자가 입력한 로그인 ID 또는 이메일
     * @param password 입력한 평문 비밀번호
     * @throws IllegalStateException 인증은 됐지만 LDAP에서 사용자를 찾지 못했거나, 이메일로 로그인 ID를 만들어야 하는데 이메일이 없을 때
     *   (v1.6은 같은 조건에서 NPE나 문자열 범위 오류로 HTTP 500이다, R-60)
     */
    fun authenticate(loginIdOrEmail: String, password: String): LdapAuthResult {
        try {
            val ldapUser = directory.authenticate(guessedUser(loginIdOrEmail), password) ?: error("LDAP 서버에서 사용자를 찾지 못했습니다: $loginIdOrEmail")
            return synchronize(ldapUser, password)
        } catch (e: CommunicationException) {
            log.error("Cannot connect to ldap server: {}", e.message)
            return fallback(loginIdOrEmail, password, emptyMap())
        } catch (e: AuthenticationException) {
            log.warn("login failed: {}", e.message)
            // v1.6는 폴백이 성공해도 이 안내를 남긴다.
            return fallback(loginIdOrEmail, password, mapOf("warning" to "user.login.invalid"))
        } catch (e: NamingException) {
            log.error("Cannot connect to ldap server: {}", e.message)
            return LdapAuthResult()
        }
    }

    /** `useEmailBaseLogin`이면 로그인 ID로 찾은 사용자의 이메일로 바꿔 인증한다 (v1.6 `guessedUser`). */
    private fun guessedUser(username: String): String =
        if (config.useEmailBaseLogin) users.findByLoginId(username)?.email ?: username else username

    private fun fallback(loginIdOrEmail: String, password: String, flash: Map<String, String>): LdapAuthResult {
        if (!config.fallbackToLocalLogin) return LdapAuthResult(null, flash)
        log.warn("fallback to local login: {}", loginIdOrEmail)
        // v1.6 `authenticateWithPlainPassword`는 로그인 ID로만 찾고 계정 상태는 보지 않는다.
        val user = users.findByLoginId(loginIdOrEmail)?.takeIf { LegacyPasswordHasher.matches(password, it.passwordSalt.orEmpty(), it.password.orEmpty()) }
        return LdapAuthResult(user, flash)
    }

    private fun synchronize(ldapUser: LdapUser, password: String): LdapAuthResult {
        val local = lookup.findByEmail(ldapUser.email) ?: return create(ldapUser, password)
        if (!LegacyPasswordHasher.matches(password, local.passwordSalt.orEmpty(), local.password.orEmpty())) {
            local.password = LegacyPasswordHasher.hash(password, requireNotNull(local.passwordSalt))
        }
        local.name = ldapUser.displayNameWithDepartment
        if (ldapUser.englishName.isNotBlank()) local.englishName = ldapUser.englishName
        // v1.6 `LdapUser.isGuestUser`는 게스트 접두어 설정 키가 뒤에 공백이 붙은 오타라 항상 거짓이다(R-60).
        local.isGuest = false
        return LdapAuthResult(users.save(local))
    }

    /** 새 로컬 사용자를 만든다. 가입 승인제 등으로 잠기면 안내만 남기고 로그인시키지 않는다. */
    private fun create(ldapUser: LdapUser, password: String): LdapAuthResult {
        val loginId = if (ldapUser.loginId.isBlank() || config.useEmailBaseLogin) {
            check(ldapUser.email.contains('@')) { "이메일이 없어 로그인 ID를 만들 수 없습니다: ${ldapUser.loginId}" }
            signup.generateLoginId(ldapUser.email.substringBefore('@'))
        } else {
            ldapUser.loginId
        }
        val created = signup.create(SignupForm(loginId, ldapUser.displayNameWithDepartment, ldapUser.email, password))
        if (created.state == UserState.LOCKED) return LdapAuthResult(null, mapOf("info" to "user.signup.requested"))
        return LdapAuthResult(created)
    }
}
