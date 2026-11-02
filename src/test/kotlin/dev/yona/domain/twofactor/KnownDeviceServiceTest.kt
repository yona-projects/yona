package dev.yona.domain.twofactor

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** 알려진 기기(X-2 4단계, `decisions.md` D-69)를 검증한다: 무작위 값 발급과 해시 저장, 30일 만료, 다른 사용자 거부, 20대 한도, 삭제. */
@Transactional
class KnownDeviceServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: KnownDeviceService
    @Autowired private lateinit var devices: KnownDeviceRepository
    @Autowired private lateinit var users: UserRepository

    private var n = 0
    private val now = Instant.parse("2026-11-02T00:00:00Z")

    private fun user() = users.save(User(loginId = "kd${++n}-${System.nanoTime()}", name = "kd", email = "kd$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE))

    @Test
    fun `발급한 값은 해시로만 저장하고 같은 사용자에게만 통한다`() {
        val a = user(); val b = user()
        val token = service.issue(a, "Firefox on Linux", "10.0.0.1", now)
        assertTrue(devices.findAll().none { it.tokenHash == token } && token.length >= 40)
        assertTrue(service.isKnown(a, token, now.plusSeconds(60)))
        assertFalse(service.isKnown(b, token, now.plusSeconds(60)), "다른 사용자의 기기 값")
        assertFalse(service.isKnown(a, "엉뚱한 값", now))
        assertFalse(service.isKnown(a, null, now))
    }

    @Test
    fun `30일이 지나면 통하지 않는다`() {
        val a = user(); val token = service.issue(a, "x", "1.1.1.1", now)
        assertTrue(service.isKnown(a, token, now.plusSeconds(29L * 86400)))
        assertFalse(service.isKnown(a, token, now.plusSeconds(31L * 86400)))
    }

    @Test
    fun `발급할 때마다 값이 다르고 사용자당 20대를 넘으면 가장 오래 쓰지 않은 기기부터 지운다`() {
        val a = user(); val first = service.issue(a, "d0", "1.1.1.1", now)
        val tokens = (1..20).map { service.issue(a, "d$it", "1.1.1.1", now.plusSeconds(it.toLong())) }
        assertEquals(21, tokens.size + 1); assertEquals(tokens.size, tokens.toSet().size); assertNotEquals(first, tokens[0])
        assertEquals(20L, devices.countByUserId(a.id!!))
        assertFalse(service.isKnown(a, first, now.plusSeconds(100)), "가장 오래된 기기가 밀려났다")
        assertTrue(service.isKnown(a, tokens.last(), now.plusSeconds(100)))
    }

    @Test
    fun `목록에서 기기를 지우면 쓸 수 없고 남의 기기는 지우지 못한다`() {
        val a = user(); val b = user(); val token = service.issue(a, "x", "1.1.1.1", now)
        val id = devices.findByUserIdOrderByLastUsedDateDescIdDesc(a.id!!).single().id!!
        assertFalse(service.remove(b, id)); assertTrue(service.isKnown(a, token, now))
        assertTrue(service.remove(a, id)); assertFalse(service.isKnown(a, token, now))
    }

    @Test
    fun `사용하면 마지막 사용 시각이 갱신된다`() {
        val a = user(); val token = service.issue(a, "x", "1.1.1.1", now)
        service.isKnown(a, token, now.plusSeconds(3600))
        assertEquals(now.plusSeconds(3600).toEpochMilli(), devices.findByUserIdOrderByLastUsedDateDescIdDesc(a.id!!).single().lastUsedDate!!.time)
    }
}
