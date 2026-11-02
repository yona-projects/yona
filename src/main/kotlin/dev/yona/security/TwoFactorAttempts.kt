package dev.yona.security

import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 2단계 코드 입력 실패의 잠금과 진행 중인 로그인의 유효 시간을 관리한다(추가 개발 항목 X-2, `decisions.md` D-69). 사용자마다 연달아 [LIMIT]번 틀리면 [LOCK]동안 잠근다
 * (잠긴 동안은 맞는 코드도 받지 않는다). 성공하면 횟수를 지운다. 기억은 이 서버의 메모리에만 있다(다시 시작하면 사라진다).
 * 비밀번호를 아는 사람이 일부러 틀려 사용자를 잠글 수 있지만 잠금은 짧고, 코드를 맞히려는 시도를 막는 것이 더 중요하다.
 */
@Component
class TwoFactorAttempts {

    private class Entry(var failures: Int = 0, var lockedUntil: Long = 0)

    private val entries = ConcurrentHashMap<Long, Entry>()
    private var skewMillis = 0L

    /** 현재 시각(에폭 밀리초)이다. 시험이 시간을 앞당길 수 있다. */
    fun now(): Long = System.currentTimeMillis() + skewMillis

    internal fun advanceClock(by: Duration) { skewMillis += by.toMillis() }

    internal fun resetClock() { skewMillis = 0 }

    /** [userId]가 지금 잠겨 있으면 `true`이다. */
    fun isLocked(userId: Long): Boolean = (entries[userId]?.lockedUntil ?: 0) > now()

    /** 실패를 기록한다. 한도에 닿으면 잠그고 `true`를 돌려준다(방금 잠겼다). */
    fun recordFailure(userId: Long): Boolean {
        var locked = false
        entries.compute(userId) { _, old ->
            val entry = old ?: Entry()
            if (entry.lockedUntil in 1..now()) { entry.failures = 0; entry.lockedUntil = 0 }
            entry.failures++
            if (entry.failures >= LIMIT) { entry.lockedUntil = now() + LOCK.toMillis(); entry.failures = 0; locked = true }
            entry
        }
        return locked
    }

    /** 성공했거나 관리자가 풀었을 때 기록을 지운다. */
    fun clear(userId: Long) { entries.remove(userId) }

    companion object {
        const val LIMIT = 5
        val LOCK: Duration = Duration.ofMinutes(15)

        /** 비밀번호를 확인한 뒤 2단계를 마칠 수 있는 시간이다. */
        val PENDING_VALID: Duration = Duration.ofMinutes(10)
    }
}
