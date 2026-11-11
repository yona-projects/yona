package dev.yona.security

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration

/**
 * 실패 횟수와 잠금이 DB에 저장되는지 검증한다(usersec-F12, 선생님 결정 2026-11-11). 메모리에만 두면 서버를 다시 시작해 잠금을 풀 수 있었다.
 * 한 서버의 시험 안에서 "다시 시작"은 새 [TwoFactorAttempts] 객체를 만들어 같은 저장소를 읽는 것으로 흉내 낸다.
 */
class AttemptLimiterStoreTest : AbstractIntegrationTest() {

    @Autowired private lateinit var attempts: TwoFactorAttempts
    @Autowired private lateinit var records: AttemptRecordRepository

    @Test
    fun `한도에 닿으면 잠금이 DB에 남고 새 객체도 잠긴 것으로 본다`() {
        val key = "test:store-1"
        repeat(TwoFactorAttempts.LIMIT - 1) { assertFalse(attempts.recordFailure(key)) }
        assertTrue(attempts.recordFailure(key))
        assertTrue(records.findById(key).get().lockedUntil > attempts.now())
        val restarted = TwoFactorAttempts(records)
        assertTrue(restarted.isLocked(key), "다시 시작해도 잠금이 남아야 한다")
        attempts.clear(key)
        assertFalse(TwoFactorAttempts(records).isLocked(key))
    }

    @Test
    fun `잠금 시간이 지나면 풀리고 횟수는 다시 센다`() {
        val key = "test:store-2"
        repeat(TwoFactorAttempts.LIMIT) { attempts.recordFailure(key) }
        assertTrue(attempts.isLocked(key))
        attempts.advanceClock(Duration.ofMinutes(16))
        try {
            assertFalse(attempts.isLocked(key))
            assertFalse(attempts.recordFailure(key))
            assertEquals(1, records.findById(key).get().failures)
        } finally { attempts.resetClock(); attempts.clear(key) }
    }

    @Test
    fun `사용자 번호로 부르는 기존 메서드는 같은 저장소의 2fa 키를 쓴다`() {
        attempts.clear(4242L)
        repeat(TwoFactorAttempts.LIMIT) { attempts.recordFailure(4242L) }
        assertTrue(records.findById("2fa:4242").get().lockedUntil > 0)
        attempts.clear(4242L)
    }
}
