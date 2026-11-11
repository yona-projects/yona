package dev.yona.security

import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 연속 실패의 잠금을 관리한다. 처음에는 2단계 코드 입력(추가 개발 항목 X-2, `decisions.md` D-69)에만 썼고, 기기 코드 입력과
 * 로그인 비밀번호 입력에도 같은 규칙을 쓴다. [key]마다 연달아 [LIMIT]번 틀리면 [LOCK]동안 잠그고(잠긴 동안은 맞는 값도 받지 않는다),
 * 성공하면 횟수를 지운다. 횟수는 DB([AttemptRecord])에 둔다. 메모리에만 두면 서버를 다시 시작해 잠금을 풀 수 있기 때문이다(usersec-F12).
 * 비밀번호를 아는 사람이 일부러 틀려 사용자를 잠글 수 있지만 잠금은 짧고, 값을 맞히려는 시도를 막는 것이 더 중요하다.
 */
@Component
class TwoFactorAttempts(private val store: AttemptStore) {

    private var skewMillis = 0L

    /** 현재 시각(에폭 밀리초)이다. 시험이 시간을 앞당길 수 있다. */
    fun now(): Long = System.currentTimeMillis() + skewMillis

    internal fun advanceClock(by: Duration) { skewMillis += by.toMillis() }

    internal fun resetClock() { skewMillis = 0 }

    /** [key]가 지금 잠겨 있으면 `true`이다. */
    fun isLocked(key: String): Boolean = store.lockedUntil(key) > now()

    /** 실패를 기록한다. 한도에 닿으면 잠그고 `true`를 돌려준다(방금 잠겼다). */
    fun recordFailure(key: String): Boolean = store.fail(key, now(), LIMIT, LOCK.toMillis())

    /** 성공했거나 관리자가 풀었을 때 기록을 지운다. */
    fun clear(key: String) = store.clear(key)

    /** 2단계 코드 입력용 키이다. */
    private fun twoFactorKey(userId: Long) = "2fa:$userId"

    fun isLocked(userId: Long): Boolean = isLocked(twoFactorKey(userId))
    fun recordFailure(userId: Long): Boolean = recordFailure(twoFactorKey(userId))
    fun clear(userId: Long) = clear(twoFactorKey(userId))

    companion object {
        const val LIMIT = 5
        val LOCK: Duration = Duration.ofMinutes(15)

        /** 비밀번호를 확인한 뒤 2단계를 마칠 수 있는 시간이다. */
        val PENDING_VALID: Duration = Duration.ofMinutes(10)
    }
}
