package dev.yona.domain.lfs

import dev.yona.config.YonaProperties
import dev.yona.domain.project.Project
import dev.yona.domain.user.User
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SSH의 `git-lfs-authenticate`가 주는 짧은 수명의 LFS 토큰이다(추가 개발 항목 X-8 단계 4, `decisions.md` D-79 8번). 사용자, 프로젝트, 동작(내려받기 또는 올리기),
 * 만료 시각에 묶인 서명 값이고 **LFS 경로에서만** 통한다(`Authorization: Bearer yona_lfs_…`). 서버는 아무것도 저장하지 않고(서명으로 검증), 사이트 비밀 키
 * (`application.secret`)로 HMAC-SHA256 서명한다. 수명은 [TTL_SECONDS]이다.
 */
@Component
class LfsTokens(private val properties: YonaProperties) {

    /** 토큰에 담긴 값이다. [write]가 참이면 올리기까지 되고 거짓이면 내려받기만 된다. */
    data class Claims(val userId: Long, val projectId: Long, val write: Boolean, val expires: Instant)

    /** [user]가 [project]에 [write] 동작을 하도록 토큰을 만든다. */
    fun issue(user: User, project: Project, write: Boolean, now: Instant = Instant.now()): String {
        val payload = "${requireNotNull(user.id)}:${requireNotNull(project.id)}:${if (write) "w" else "r"}:${now.epochSecond + TTL_SECONDS}"
        val encoded = ENCODER.encodeToString(payload.toByteArray())
        return "$PREFIX$encoded.${sign(encoded)}"
    }

    /** 토큰이 유효하면 담긴 값을, 형식이 틀리거나 서명이 맞지 않거나 만료되었으면 `null`을 돌려준다. */
    fun verify(token: String, now: Instant = Instant.now()): Claims? {
        if (!token.startsWith(PREFIX)) return null
        val body = token.removePrefix(PREFIX)
        val dot = body.lastIndexOf('.')
        if (dot <= 0) return null
        val encoded = body.substring(0, dot); val signature = body.substring(dot + 1)
        if (!MessageDigest.isEqual(sign(encoded).toByteArray(), signature.toByteArray())) return null
        val parts = runCatching { String(DECODER.decode(encoded)).split(':') }.getOrNull()?.takeIf { it.size == 4 } ?: return null
        val userId = parts[0].toLongOrNull() ?: return null; val projectId = parts[1].toLongOrNull() ?: return null; val expires = parts[3].toLongOrNull() ?: return null
        if (parts[2] != "r" && parts[2] != "w") return null
        if (now.epochSecond > expires) return null
        return Claims(userId, projectId, parts[2] == "w", Instant.ofEpochSecond(expires))
    }

    private fun sign(value: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(("lfs-token:" + properties.secret).toByteArray(), "HmacSHA256"))
        return ENCODER.encodeToString(mac.doFinal(value.toByteArray()))
    }

    companion object {
        const val PREFIX = "yona_lfs_"

        /** 토큰의 수명(초)이다. 클라이언트는 한 번의 작업 동안만 쓴다. */
        const val TTL_SECONDS = 600L
        private val ENCODER = Base64.getUrlEncoder().withoutPadding()
        private val DECODER = Base64.getUrlDecoder()
    }
}
