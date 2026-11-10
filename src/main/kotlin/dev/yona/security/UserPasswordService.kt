package dev.yona.security

import dev.yona.domain.user.User
import org.springframework.stereotype.Component

/**
 * 새로 만든 비밀번호 정보이다.
 *
 * @property salt `n4user.password_salt`에 저장할 솔트(Base64 24글자)
 * @property hash `n4user.password`에 저장할 해시(Shiro SHA-256 1024회, Base64)
 */
data class PasswordCredentials(val salt: String, val hash: String)

/**
 * 사용자의 비밀번호를 만들고 확인하는 **유일한 입구**이다(로드맵 X-43 1단계, `decisions.md` D-129). 해시 알고리즘은 [LegacyPasswordHasher]에만 있고,
 * 운영 코드는 이 클래스를 거쳐서만 해시를 다룬다(구조 시험이 지킨다). 나중에 해시를 강화(argon2id)해도 이 클래스 안에서 끝나도록 하려는 것이다.
 *
 * v1.6은 솔트를 비밀번호와 별도 열에 둔다. 그 형식을 그대로 지킨다.
 *
 * 이 클래스는 **정책을 담지 않는다**. SSO 사용자의 비밀번호 생성 금지, 입력 규칙 같은 것은 호출하는 쪽이 정한다.
 */
@Component
class UserPasswordService {

    /** [raw]로 새 솔트와 해시를 만든다. 가입처럼 솔트가 없는 계정을 만들 때 쓴다. */
    fun newCredentials(raw: String): PasswordCredentials {
        val salt = LegacyPasswordHasher.generateSalt()
        return PasswordCredentials(salt, LegacyPasswordHasher.hash(raw, salt))
    }

    /**
     * [user]의 저장된 비밀번호가 [raw]와 같은지 확인한다. 비교는 상수 시간이다.
     *
     * @throws IllegalArgumentException 솔트나 해시가 저장되어 있지 않을 때. v1.6의 `UserApp.hashedPassword`가 같은 조건에서 같은 예외를 던진다(HTTP 500).
     */
    fun matches(user: User, raw: String): Boolean {
        val salt = user.passwordSalt
        val stored = user.password
        require(salt != null && stored != null) { MISSING }
        return LegacyPasswordHasher.matches(raw, salt, stored)
    }

    /**
     * [matches]와 같지만 솔트나 해시가 없어도 예외를 던지지 않고 없는 값을 빈 문자열로 본다(`false`가 된다). 로컬 계정을 LDAP의 폴백으로 확인하는 곳이 쓴다
     * (v1.6 `UserApp.authenticateWithPlainPassword`가 비어 있는 값을 그냥 비교한다).
     */
    fun matchesLenient(user: User, raw: String): Boolean = LegacyPasswordHasher.matches(raw, user.passwordSalt.orEmpty(), user.password.orEmpty())

    /**
     * [user]의 비밀번호를 [raw]로 바꾼다. **솔트는 그대로 두고 해시만 바꾼다**(v1.6 `User.resetPassword`). 저장은 호출하는 쪽이 한다.
     *
     * @throws IllegalArgumentException 솔트가 없을 때
     */
    fun set(user: User, raw: String) {
        val salt = user.passwordSalt
        require(salt != null) { MISSING }
        user.password = LegacyPasswordHasher.hash(raw, salt)
    }

    /**
     * 저장된 해시를 더 강한 방식으로 다시 만들어야 하면 `true`이다. 지금은 v1.6 방식 하나뿐이라 항상 `false`이다.
     * 해시 강화(argon2id)를 하게 되면 여기서 판단하고, 로그인 성공 때 [set]으로 새 방식으로 바꾼다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun needsUpgrade(user: User): Boolean = false

    private companion object {
        const val MISSING = "Bad password or passwordSalt!"
    }
}
