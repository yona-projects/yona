package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** 로그인 시도의 판정 결과이다. 화면 쪽은 결과에 따라 v1.6과 같은 안내 문구를 고른다. */
sealed interface LoginResult {
    /** 인증에 성공했다. */
    data class Success(val user: User) : LoginResult

    /** 계정이 잠겼다(`user.locked`). 가입 승인 대기 계정도 여기에 해당한다. */
    data object Locked : LoginResult

    /** 계정이 삭제되었다(`user.deleted`). */
    data object Deleted : LoginResult

    /** 계정이 없거나 패스워드가 틀렸다(`user.login.invalid`). */
    data object InvalidCredentials : LoginResult
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
 * 5. 패스워드가 맞아도 계정이 LOCKED이면 [LoginResult.Locked]이다.
 *
 * LDAP과 소셜 로그인은 이 클래스의 범위가 아니다.
 *
 * @property signUpConfirmRequired v1.6의 `signup.require.admin.confirm` 설정이다.
 */
@Service
class LocalAuthenticator(
    private val users: UserRepository,
    @Value("\${yona.signup.require-admin-confirm:false}") private val signUpConfirmRequired: Boolean
) {

    /**
     * @param loginIdOrEmail 사용자가 입력한 로그인 ID 또는 이메일
     * @param password 사용자가 입력한 평문 패스워드
     * @throws IllegalArgumentException 계정에 솔트나 패스워드가 저장되어 있지 않을 때.
     *   v1.6의 `UserApp.hashedPassword`가 같은 조건에서 같은 예외를 던진다(HTTP 500).
     */
    fun authenticate(loginIdOrEmail: String, password: String): LoginResult {
        val user = users.findByLoginKey(loginIdOrEmail) ?: return LoginResult.InvalidCredentials

        if (signUpConfirmRequired && user.state == UserState.LOCKED) return LoginResult.Locked
        if (user.state == UserState.DELETED) return LoginResult.Deleted
        if (!passwordMatches(user, password)) return LoginResult.InvalidCredentials
        if (user.state == UserState.LOCKED) return LoginResult.Locked
        return LoginResult.Success(user)
    }

    private fun passwordMatches(user: User, password: String): Boolean {
        val salt = user.passwordSalt
        val stored = user.password
        require(salt != null && stored != null) { "Bad password or passwordSalt!" }
        return LegacyPasswordHasher.matches(password, salt, stored)
    }
}
