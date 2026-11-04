package dev.yona.security

import dev.yona.config.YonaProperties
import dev.yona.domain.user.User
import jakarta.servlet.http.Cookie
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 로그인 유지 쿠키(`yobi.token`)를 만들고 검증한다.
 *
 * v1.6은 값이 `로그인ID:해시된 비밀번호`라 쿠키가 새면 비밀번호 해시가 그대로 드러났다(R-05). 지금은 `로그인ID:만료 시각:서명`이다. 서명은 사이트 비밀 키로 만든
 * HMAC-SHA256이며 사용자의 비밀번호 해시를 서명 재료로 쓰되 값에는 담지 않는다. 그래서 비밀번호가 바뀌면 이전 쿠키가 무효가 되고, 값을 바꾸면 서명이 맞지 않는다.
 * 만료 시각은 [YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE](30일)이다.
 */
@Component
class RememberMeCookies(private val properties: YonaProperties) {

    /** 쿠키 값이다. [now]부터 30일간 유효하다. */
    fun valueFor(user: User, now: Instant = Instant.now()): String {
        val expires = now.epochSecond + YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE
        return "${user.loginId}:$expires:${signature(user, expires)}"
    }

    /** 로그인 유지 쿠키를 만든다. 스크립트가 읽지 못하게 하고, 같은 사이트 요청에서만 보낸다. [secure]이면 HTTPS에서만 보낸다. */
    fun create(user: User, secure: Boolean): Cookie = Cookie(YonaAuthenticationFilter.REMEMBER_ME_COOKIE, valueFor(user)).apply {
        maxAge = YonaAuthenticationFilter.REMEMBER_ME_MAX_AGE
        path = "/"
        isHttpOnly = true
        this.secure = secure
        setAttribute("SameSite", "Lax")
    }

    /** [value]가 [user]의 유효한 쿠키 값이면 `true`이다. */
    fun verify(value: String, user: User, now: Instant = Instant.now()): Boolean {
        val parts = value.split(YonaAuthenticationFilter.TOKEN_SEPARATOR)
        if (parts.size != 3 || parts[0] != user.loginId) return false
        val expires = parts[1].toLongOrNull() ?: return false
        if (expires < now.epochSecond) return false
        return MessageDigest.isEqual(signature(user, expires).toByteArray(), parts[2].toByteArray())
    }

    private fun signature(user: User, expires: Long): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(properties.secret.toByteArray(), "HmacSHA256")) }
        val bytes = mac.doFinal("${user.loginId}:$expires:${user.password}".toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        /** 쿠키 값에서 로그인 ID를 읽는다. 형식이 맞지 않으면 `null`이다. */
        fun loginIdOf(value: String): String? =
            value.split(YonaAuthenticationFilter.TOKEN_SEPARATOR).takeIf { it.size == 3 }?.get(0)?.takeIf { it.isNotEmpty() }
    }
}
