package dev.yona.security

import dev.yona.config.YonaProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * 토큰 인증 실패를 주소별로 세어 한도를 넘으면 일정 시간 막는다(추가 개발 항목 X-5, `decisions.md` D-36). 같은 주소에서 [limit]회 연달아 실패하면
 * [block] 동안 막고 성공하면 횟수를 지운다. 막은 동안의 실패는 차단 시간을 늘리지 않는다. 마지막 실패로부터 [block]이 지난 횟수는 잊는다.
 * Yona에는 로그인 실패 제한이 어디에도 없으므로(v1.6, v2) 이것이 첫 제한 장치이다. 단일 서버의 메모리에 두며 추적하는 주소 수는 [maxTracked]로 제한한다.
 *
 * 리버스 프록시 뒤에서는 모든 요청이 프록시 주소로 보이면 한 사람의 실패가 모두를 막으므로, 서버가 전달 주소를 반영하게 설정해야 한다
 * (`server.forward-headers-strategy`).
 */
@Component
class TokenFailureLimiter(
    private val limit: Int,
    private val block: Duration,
    private val clock: Clock,
    private val maxTracked: Int = DEFAULT_MAX_TRACKED
) {

    @Autowired
    constructor(properties: YonaProperties) : this(properties.apiTokenFailureLimit, Duration.ofMinutes(properties.apiTokenBlockMinutes), Clock.systemUTC())

    private class Entry(var failures: Int, var lastFailure: Long, var blockedUntil: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** [address]가 지금 막혀 있으면 `true`이다. */
    fun isBlocked(address: String): Boolean {
        if (limit <= 0) return false
        val entry = entries[address] ?: return false
        return clock.millis() < entry.blockedUntil
    }

    /** 실패를 기록한다. 한도에 닿으면 막는다. */
    fun recordFailure(address: String) {
        if (limit <= 0) return
        val now = clock.millis()
        if (entries.size >= maxTracked && !entries.containsKey(address)) purge(now)
        if (entries.size >= maxTracked && !entries.containsKey(address)) return
        entries.compute(address) { _, old ->
            val entry = old ?: Entry(0, now, 0)
            if (now < entry.blockedUntil) return@compute entry
            if (now - entry.lastFailure >= block.toMillis()) entry.failures = 0
            entry.failures++
            entry.lastFailure = now
            if (entry.failures >= limit) { entry.blockedUntil = now + block.toMillis(); entry.failures = 0 }
            entry
        }
    }

    /** 성공하면 [address]의 횟수를 지운다. */
    fun recordSuccess(address: String) {
        entries.remove(address)
    }

    /** 추적 중인 주소 수이다(시험과 점검용). */
    fun trackedCount(): Int = entries.size

    private fun purge(now: Long) {
        entries.entries.removeIf { (_, e) -> now >= e.blockedUntil && now - e.lastFailure >= block.toMillis() }
        if (entries.size >= maxTracked) entries.keys.take(entries.size - maxTracked + 1).forEach { entries.remove(it) }
    }

    companion object {
        const val DEFAULT_MAX_TRACKED = 10_000
    }
}
