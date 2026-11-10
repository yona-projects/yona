package dev.yona.security

import dev.yona.domain.user.User
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

/**
 * 새로 만든 비밀번호 정보이다.
 *
 * @property salt `n4user.password_salt`에 저장할 값(Base64 24글자). argon2id는 솔트가 [hash] 문자열 안에 들어 있어 이 값을 쓰지 않는다. 열의 형식과 "비어 있지 않다"는 조건을 v1.6과 같게 두려고 채운다
 * @property hash `n4user.password`에 저장할 해시. 새로 만드는 값은 argon2id 인코딩(`$argon2id$v=19$m=16384,t=2,p=1$솔트$해시`)이다
 */
data class PasswordCredentials(val salt: String, val hash: String)

/**
 * 사용자의 비밀번호를 만들고 확인하는 **유일한 입구**이다(로드맵 X-43 1단계, `decisions.md` D-129). 운영 코드는 이 클래스를 거쳐서만 해시를 다룬다(구조 시험이 지킨다).
 *
 * **새 해시는 argon2id**이다(`decisions.md` D-131). 가입, 비밀번호 변경과 재설정, 사이트 관리자의 임시 비밀번호가 모두 argon2id로 저장한다.
 * **v1.6이 만든 해시(Shiro SHA-256 1024회와 별도 솔트 열)도 계속 확인한다.** 어느 방식인지는 저장된 해시가 `$argon2id$`로 시작하는지로 가린다. v1.6 해시를 가진 사용자가
 * 비밀번호로 로그인에 성공하면 그 자리에서 argon2id로 바꿔 저장한다([needsUpgrade], 호출은 [YonaPasswordAuthenticationProvider]). 자주 쓰지 않는 계정은 그대로 남는다.
 *
 * 열은 v1.6과 같다. 솔트 열(`password_salt`)은 값을 유지하고 argon2id 사용자에게는 쓰이지 않는 채움 값이다. argon2id로 바꾼 계정은 v1.6로 되돌릴 수 없다.
 *
 * 이 클래스는 **정책을 담지 않는다**. SSO 사용자의 비밀번호 생성 금지, 입력 규칙 같은 것은 호출하는 쪽이 정한다.
 */
@Component
class UserPasswordService {

    /** argon2id 인코더이다. 스프링 시큐리티 5.8의 권장 값(메모리 16MiB, 반복 2회, 병렬 1)이다. 인코딩 문자열에 매개변수가 들어가므로 값을 바꿔도 이미 저장한 해시는 그대로 확인된다. */
    private val argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    /** [raw]로 새 해시를 만든다. 가입처럼 솔트가 없는 계정을 만들 때 쓴다. 솔트 열에 넣을 채움 값도 함께 만든다. */
    fun newCredentials(raw: String): PasswordCredentials = PasswordCredentials(LegacyPasswordHasher.generateSalt(), requireNotNull(argon2.encode(raw)))

    /**
     * [user]의 저장된 비밀번호가 [raw]와 같은지 확인한다. 비교는 상수 시간이다. argon2id 해시와 v1.6 해시를 모두 확인한다.
     *
     * @throws IllegalArgumentException 솔트나 해시가 저장되어 있지 않을 때. v1.6의 `UserApp.hashedPassword`가 같은 조건에서 같은 예외를 던진다(HTTP 500).
     */
    fun matches(user: User, raw: String): Boolean {
        val salt = user.passwordSalt
        val stored = user.password
        require(salt != null && stored != null) { MISSING }
        return if (isArgon2(stored)) argon2.matches(raw, stored) else LegacyPasswordHasher.matches(raw, salt, stored)
    }

    /**
     * [matches]와 같지만 솔트나 해시가 없어도 예외를 던지지 않고 없는 값을 빈 문자열로 본다(`false`가 된다). 로컬 계정을 LDAP의 폴백으로 확인하는 곳이 쓴다
     * (v1.6 `UserApp.authenticateWithPlainPassword`가 비어 있는 값을 그냥 비교한다).
     */
    fun matchesLenient(user: User, raw: String): Boolean {
        val stored = user.password.orEmpty()
        return if (isArgon2(stored)) argon2.matches(raw, stored) else LegacyPasswordHasher.matches(raw, user.passwordSalt.orEmpty(), stored)
    }

    /**
     * [user]의 비밀번호를 [raw]로 바꾼다. **솔트 열은 그대로 두고 해시만 argon2id로 바꾼다**(v1.6 `User.resetPassword`는 솔트를 유지한다). 저장은 호출하는 쪽이 한다.
     *
     * @throws IllegalArgumentException 솔트가 없을 때
     */
    fun set(user: User, raw: String) {
        val salt = user.passwordSalt
        require(salt != null) { MISSING }
        user.password = requireNotNull(argon2.encode(raw))
    }

    /**
     * 저장된 해시를 argon2id로 다시 만들어야 하면 `true`이다(해시가 있고 아직 v1.6 방식일 때). 로그인에 성공했을 때 호출하는 쪽이 [set]으로 바꾼다.
     * 해시가 없으면 바꿀 것이 없다. 로그인 유지 쿠키의 서명이 저장된 해시를 쓰므로([RememberMeCookies]) 바꾸면 그 사용자의 다른 기기 쿠키는 한 번 무효가 된다
     * (비밀번호를 바꿀 때와 같다).
     */
    fun needsUpgrade(user: User): Boolean = user.password?.let { it.isNotEmpty() && !isArgon2(it) } == true

    private fun isArgon2(stored: String): Boolean = stored.startsWith(ARGON2_PREFIX)

    private companion object {
        const val ARGON2_PREFIX = "\$argon2id\$"

        const val MISSING = "Bad password or passwordSalt!"
    }
}
