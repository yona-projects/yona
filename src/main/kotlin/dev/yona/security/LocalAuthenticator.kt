package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.config.YonaProperties
import org.springframework.stereotype.Service

/** 로그인 시도의 판정 결과이다. 화면 쪽은 결과에 따라 v1.6과 같은 안내 문구를 고른다. */
sealed interface LoginResult {
    /**
     * 인증에 성공했다.
     *
     * @property flash 다음 화면에 한 번만 보일 안내. LDAP 인증이 남긴 것이다
     */
    data class Success(val user: User, val flash: Map<String, String> = emptyMap()) : LoginResult

    /**
     * 가입 승인제에서 승인 대기(LOCKED) 계정이다(`user.locked`). 패스워드를 보기 전에 거부하며,
     * 로그인 폼으로 돌려보낸다.
     */
    data object Locked : LoginResult

    /**
     * 패스워드는 맞았지만 계정이 LOCKED이다(`user.locked`). v1.6은 이 경우 로그아웃 처리까지 하고
     * 이전 화면으로 돌려보낸다(`UserApp.java:225-228`).
     */
    data object LockedAfterAuthentication : LoginResult

    /** 계정이 삭제되었다(`user.deleted`). */
    data object Deleted : LoginResult

    /** 계정이 없거나 패스워드가 틀렸다(`user.login.invalid`). */
    data object InvalidCredentials : LoginResult

    /**
     * LDAP 인증이 실패하면서 안내를 남겼다(예: 가입 승인 대기 `user.signup.requested`). 화면은 [InvalidCredentials]와 같게 처리하되
     * 이 안내를 함께 보인다.
     */
    data class InvalidWithNotice(val flash: Map<String, String>) : LoginResult
}

/**
 * 로컬 계정(로그인 ID 또는 이메일 + 패스워드)으로 로그인 가능 여부를 판정한다.
 *
 * v1.6 `UserApp.loginByFormRequest`(`app/controllers/UserApp.java:197-259`)의 판정 순서를 그대로 따른다.
 *
 * 1. 로그인 ID 또는 이메일로 계정을 찾는다. 없으면 [LoginResult.InvalidCredentials]이다.
 * 2. 가입 승인제이고 계정이 LOCKED이면 패스워드를 보기 전에 [LoginResult.Locked]이다.
 * 3. 계정이 DELETED이면 [LoginResult.Deleted]이다.
 * 4. 패스워드가 틀리면 [LoginResult.InvalidCredentials]이다.
 * 5. 패스워드가 맞아도 계정이 LOCKED이면 [LoginResult.LockedAfterAuthentication]이다.
 *
 * LDAP을 쓰는 사이트(`yona.ldap.enabled`)에서는 계정이 없어도 LDAP으로 인증을 시도하고(v1.6에서는 처음 로그인하는 LDAP 사용자가
 * 로컬에 없다), 비밀번호는 로컬이 아니라 LDAP이 판정한다. 소셜 로그인은 이 클래스의 범위가 아니다.
 *
 * @property properties `yona.signup.require-admin-confirm`(v1.6 `signup.require.admin.confirm`)을 읽는다.
 */
@Service
class LocalAuthenticator(
    private val users: UserRepository,
    private val properties: YonaProperties,
    private val ldap: dev.yona.domain.ldap.LdapAuthService
) {

    /**
     * @param loginIdOrEmail 사용자가 입력한 로그인 ID 또는 이메일
     * @param password 사용자가 입력한 평문 패스워드
     * @throws IllegalArgumentException 계정에 솔트나 패스워드가 저장되어 있지 않을 때.
     *   v1.6의 `UserApp.hashedPassword`가 같은 조건에서 같은 예외를 던진다(HTTP 500).
     */
    fun authenticate(loginIdOrEmail: String, password: String): LoginResult {
        val source = users.findByLoginKey(loginIdOrEmail)
        if (source == null && !ldap.enabled) return LoginResult.InvalidCredentials

        if (source != null) {
            if (properties.signup.required() && source.state == UserState.LOCKED) return LoginResult.Locked
            if (source.state == UserState.DELETED) return LoginResult.Deleted
        }

        val user: User?
        var flash: Map<String, String> = emptyMap()
        if (ldap.enabled) {
            val result = ldap.authenticate(loginIdOrEmail, password)
            user = result.user
            flash = result.flash
        } else {
            user = source?.takeIf { passwordMatches(it, password) }
        }
        if (user == null) return if (flash.isEmpty()) LoginResult.InvalidCredentials else LoginResult.InvalidWithNotice(flash)
        if (user.state == UserState.LOCKED) return LoginResult.LockedAfterAuthentication
        return LoginResult.Success(user, flash)
    }

    /**
     * Git HTTP 기본 인증으로 사용자를 찾는다 (v1.6 `BasicAuthAction.authenticate`). LDAP을 쓰면 LDAP으로, 아니면 로그인 ID와 로컬 비밀번호로 확인한다.
     * 틀렸으면 `null`이다.
     */
    fun authenticateBasic(loginId: String, password: String): User? {
        if (ldap.enabled) return ldap.authenticate(loginId, password).user
        // v1.6 authenticateWithPlainPassword는 로그인 ID로만 찾는다(이메일은 받지 않는다)
        val source = users.findByLoginIdIgnoreCase(loginId) ?: return null
        return (authenticate(requireNotNull(source.loginId), password) as? LoginResult.Success)?.user
    }

    private fun passwordMatches(user: User, password: String): Boolean {
        val salt = user.passwordSalt
        val stored = user.password
        require(salt != null && stored != null) { "Bad password or passwordSalt!" }
        return LegacyPasswordHasher.matches(password, salt, stored)
    }
}
