package dev.yona.security

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * 비밀번호 재설정 주소에 쓰는 일회용 값이다. v1.6 `utils.PasswordReset`에 해당한다.
 *
 * v1.6처럼 메모리에 두므로 서버를 다시 시작하면 사라지고 여러 서버 사이에 공유되지 않는다. 사용자마다 하나만 유효하며
 * 새로 요청하면 이전 값은 쓸 수 없다. 만든 지 한 시간이 지나면 만료된다.
 */
@Component
class PasswordResetTokens {

    private val tokenByLoginId = ConcurrentHashMap<String, String>()
    private val issuedAt = ConcurrentHashMap<String, Long>()
    private val random = SecureRandom()

    /** [loginId]로 무작위 40자 16진수 값을 만든다. 기억하지는 않는다 (v1.6 `generateResetHash`는 SHA-1). */
    fun generate(loginId: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val digest = MessageDigest.getInstance("SHA-1").digest(salt + loginId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** [loginId]의 새 값을 만들어 기억한다. 이전 값은 쓸 수 없게 된다. */
    fun issue(loginId: String, now: Long = System.currentTimeMillis()): String {
        val token = generate(loginId)
        tokenByLoginId[loginId] = token
        issuedAt[token] = now
        return token
    }

    /** 기억하고 있는 값이고 만료되지 않았으면 `true`이다. */
    fun isValid(token: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (token == null || !tokenByLoginId.containsValue(token)) return false
        val issued = issuedAt[token] ?: return false
        return issued + EXPIRE_MILLIS >= now
    }

    /** 값으로 사용자의 로그인 ID를 찾는다. 모르는 값이면 `null`이다. */
    fun loginIdOf(token: String): String? = tokenByLoginId.entries.firstOrNull { it.value == token }?.key

    /** 값을 지운다. */
    fun remove(token: String) {
        loginIdOf(token)?.let { tokenByLoginId.remove(it) }
        issuedAt.remove(token)
    }

    private companion object {
        const val SALT_BYTES = 16
        const val EXPIRE_MILLIS = 3_600_000L
    }
}
