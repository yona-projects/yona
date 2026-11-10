package dev.yona.security

import org.springframework.security.crypto.password.PasswordEncoder

/**
 * v1.6 해시를 스프링 시큐리티의 [PasswordEncoder] 모양으로 다룬다(로드맵 X-43 1단계, `decisions.md` D-129).
 *
 * v1.6은 솔트를 `password_salt` 열에 따로 두므로, 인코딩 문자열 하나만 받는 [PasswordEncoder]로는 그대로 확인할 수 없다. 그래서 두 열을 합친
 * **메모리 안의 합성 표현** `{yona-sha256}솔트$해시`를 인코딩 문자열로 쓴다. 이 표현은 저장하지 않는다. 저장은 계속 두 열이다.
 * 솔트에 `$`가 있어도 되도록 해시(Base64라 `$`가 없다) 앞의 **마지막** `$`로 나눈다.
 *
 * 나중에 argon2id를 쓰면 솔트가 문자열 안에 들어 있어 같은 구조에 `DelegatingPasswordEncoder`로 붙일 수 있다.
 */
class LegacyPasswordEncoder : PasswordEncoder {

    /** [rawPassword]로 새 솔트를 만들어 합성 표현을 돌려준다. */
    override fun encode(rawPassword: CharSequence?): String {
        requireNotNull(rawPassword) { "비밀번호가 없습니다" }
        val salt = LegacyPasswordHasher.generateSalt()
        return compose(salt, LegacyPasswordHasher.hash(rawPassword.toString(), salt))
    }

    /** [encodedPassword]가 합성 표현이고 [rawPassword]와 같으면 `true`이다. 모양이 맞지 않거나 비밀번호가 없으면 `false`이다. */
    override fun matches(rawPassword: CharSequence?, encodedPassword: String?): Boolean {
        if (rawPassword == null) return false
        val body = encodedPassword?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return false
        val separator = body.lastIndexOf(SEPARATOR)
        if (separator < 0) return false
        return LegacyPasswordHasher.matches(rawPassword.toString(), body.substring(0, separator), body.substring(separator + 1))
    }

    /** 지금은 방식이 하나뿐이라 다시 인코딩할 필요가 없다. */
    override fun upgradeEncoding(encodedPassword: String?): Boolean = false

    companion object {
        const val PREFIX = "{yona-sha256}"
        private const val SEPARATOR = '$'

        /** `password_salt`와 `password` 두 열의 값으로 합성 표현을 만든다. */
        fun compose(salt: String, hash: String): String = "$PREFIX$salt$SEPARATOR$hash"
    }
}
