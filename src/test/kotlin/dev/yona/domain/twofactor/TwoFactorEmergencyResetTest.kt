package dev.yona.domain.twofactor

import dev.yona.config.YonaProperties
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 긴급 해제(`yona.two-factor.reset-user`, X-2, `decisions.md` D-69)를 검증한다: 지정한 사용자의 2단계 인증을 시작할 때 지우고, 없는 사용자나 빈 값은 아무 일도 하지 않는다. */
@Transactional
class TwoFactorEmergencyResetTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var service: TwoFactorService
    @Autowired private lateinit var reset: TwoFactorEmergencyReset

    private fun enabledUser(): User {
        val u = users.save(User(loginId = "er-${System.nanoTime()}", name = "er", email = "er${System.nanoTime()}@example.com"))
        val secret = Base32.decode(service.beginTotp(u, "Yona", u.loginId!!)!!.secret)
        val past = Instant.now().minusSeconds(300)
        service.confirmTotp(u, Totp.code(secret, past.epochSecond / 30), past)
        return u
    }

    @Test
    fun `지정한 사용자의 2단계 인증을 지운다`() {
        val u = enabledUser(); val other = enabledUser()
        assertTrue(reset.resetUser(u.loginId!!))
        assertFalse(service.isEnabled(u)); assertTrue(service.isEnabled(other), "다른 사용자는 그대로이다")
    }

    @Test
    fun `없는 사용자나 빈 값이나 이미 꺼진 사용자는 아무 일도 하지 않는다`() {
        assertFalse(reset.resetUser("없는-사용자")); assertFalse(reset.resetUser("  "))
        val u = users.save(User(loginId = "er2-${System.nanoTime()}", name = "er", email = "er2${System.nanoTime()}@example.com"))
        assertFalse(reset.resetUser(u.loginId!!))
    }
}
