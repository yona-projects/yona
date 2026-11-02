package dev.yona.sso

import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 이미 받은 SAML 어설션을 기억해 같은 어설션의 재사용을 막는다(X-16, `decisions.md` D-80 14, 23번). 요청 없이 도착하는 응답(IdP 시작 로그인)은 요청 ID로 짝을 지을 수 없으므로
 * 가로챈 응답을 다시 보내는 공격을 이것으로 막는다. 기억은 어설션이 유효한 동안만 두며 메모리에 둔다(이 서버가 다시 시작되면 비워지지만 그 사이 어설션은 대부분 만료된다).
 */
@Component
class SsoReplayCache {

    private val seen = ConcurrentHashMap<String, Instant>()

    /**
     * [key](발급자와 어설션 ID)를 처음 쓰면 기록하고 `true`를, 이미 썼으면 `false`를 돌려준다. [expiresAt]이 지나면 기록을 잊는다.
     */
    fun firstUse(key: String, expiresAt: Instant, now: Instant = Instant.now()): Boolean {
        if (seen.size > PURGE_THRESHOLD) seen.entries.removeIf { it.value.isBefore(now) }
        val previous = seen.putIfAbsent(key, expiresAt)
        if (previous == null) return true
        if (previous.isBefore(now)) { seen[key] = expiresAt; return true }
        return false
    }

    private companion object { const val PURGE_THRESHOLD = 1000 }
}
