package dev.yona.security

import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 토큰 인증 실패 제한을 검증한다(X-5, D-36). 같은 주소에서 5회 실패하면 15분 동안 막고 성공하면 횟수를 지운다. */
class TokenFailureLimiterTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-10-29T00:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private val clock = MutableClock()
    private fun limiter(limit: Int = 5, minutes: Long = 15) = TokenFailureLimiter(limit, Duration.ofMinutes(minutes), clock)

    @Test
    fun `5회 실패하면 막고 다른 주소는 영향이 없다`() {
        val l = limiter()
        repeat(4) { l.recordFailure("1.1.1.1"); assertFalse(l.isBlocked("1.1.1.1")) }
        l.recordFailure("1.1.1.1")
        assertTrue(l.isBlocked("1.1.1.1"))
        assertFalse(l.isBlocked("2.2.2.2"))
    }

    @Test
    fun `성공하면 횟수를 지운다`() {
        val l = limiter()
        repeat(4) { l.recordFailure("a") }
        l.recordSuccess("a")
        repeat(4) { l.recordFailure("a") }
        assertFalse(l.isBlocked("a"), "성공으로 지웠으므로 다시 4회까지는 막지 않는다")
    }

    @Test
    fun `차단은 15분 뒤에 풀리고 그 뒤에 다시 5회 실패해야 막는다`() {
        val l = limiter()
        repeat(5) { l.recordFailure("a") }
        assertTrue(l.isBlocked("a"))
        clock.advance(Duration.ofMinutes(14)); assertTrue(l.isBlocked("a"))
        clock.advance(Duration.ofMinutes(2)); assertFalse(l.isBlocked("a"))
        repeat(4) { l.recordFailure("a") }
        assertFalse(l.isBlocked("a"))
    }

    @Test
    fun `막힌 동안의 실패는 차단 시간을 늘리지 않는다`() {
        val l = limiter()
        repeat(5) { l.recordFailure("a") }
        clock.advance(Duration.ofMinutes(10))
        repeat(10) { l.recordFailure("a") }
        clock.advance(Duration.ofMinutes(6))
        assertFalse(l.isBlocked("a"), "처음 막은 때부터 15분이다")
    }

    @Test
    fun `오래된 실패는 잊고 추적하는 주소 수는 한도가 있다`() {
        val l = limiter()
        repeat(3) { l.recordFailure("old") }
        clock.advance(Duration.ofMinutes(20))
        repeat(3) { l.recordFailure("old") }
        assertFalse(l.isBlocked("old"), "20분 전의 실패 3회는 잊는다")
        val small = TokenFailureLimiter(5, Duration.ofMinutes(15), clock, maxTracked = 100)
        repeat(1000) { small.recordFailure("ip$it") }
        assertTrue(small.trackedCount() <= 100)
    }

    @Test
    fun `한도가 0 이하이면 제한하지 않는다`() {
        val l = limiter(limit = 0)
        repeat(50) { l.recordFailure("a") }
        assertFalse(l.isBlocked("a"))
        assertEquals(0, l.trackedCount())
    }
}
