package dev.yona.domain.twofactor

import dev.yona.domain.audit.AuditLogRepository
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 2단계 인증 서비스(X-2 2단계, `decisions.md` D-69)를 검증한다: 인증 앱 등록과 확인, 로그인 코드 확인(재사용 방지), 백업 코드(한 번만), 끄기와 관리자 해제, 감사 기록.
 */
@Transactional
class TwoFactorServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: TwoFactorService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var credentials: TwoFactorCredentialRepository
    @Autowired private lateinit var backupRows: TwoFactorBackupCodeRepository
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var siteUsers: dev.yona.domain.site.SiteUserService
    @Autowired private lateinit var devices: KnownDeviceRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0
    private val at = Instant.parse("2026-11-02T00:00:00Z")

    private fun user() = users.save(User(loginId = "tf${++sequence}-${System.nanoTime()}", name = "tf$sequence", email = "tf$sequence@example.com", state = UserState.ACTIVE))

    private fun codeAt(secret: ByteArray, time: Instant) = Totp.code(secret, time.epochSecond / Totp.PERIOD_SECONDS)

    /** 등록을 시작하고 확인까지 마친다. 비밀 값과 백업 코드를 돌려준다. */
    private fun enable(u: User): Pair<ByteArray, List<String>> {
        val enrollment = assertNotNull(service.beginTotp(u, "Yona", u.loginId!!))
        val secret = Base32.decode(enrollment.secret)
        val confirmed = assertIs<TotpConfirmation.Confirmed>(service.confirmTotp(u, codeAt(secret, at), at))
        em.flush(); em.clear()
        return secret to confirmed.backupCodes
    }

    private fun events(action: String) = logs.findAll().filter { it.action == action }

    @Test
    fun `등록을 시작하면 확인하기 전에는 켜지지 않고 비밀 값은 암호화해 저장한다`() {
        val u = user(); val enrollment = assertNotNull(service.beginTotp(u, "Yona", "alice"))
        assertFalse(service.isEnabled(u))
        val stored = credentials.findByUserIdOrderById(u.id!!).single()
        assertEquals(false, stored.confirmed)
        assertTrue(!stored.secretEncrypted!!.contains(enrollment.secret), "평문 비밀 값이 저장되면 안 된다")
        assertTrue(enrollment.otpauthUri.startsWith("otpauth://totp/Yona:alice?") && enrollment.otpauthUri.contains("secret=${enrollment.secret}"))
    }

    @Test
    fun `맞는 코드로 확인하면 켜지고 백업 코드 열 개를 한 번 돌려준다`() {
        val u = user(); val (_, codes) = enable(u)
        assertTrue(service.isEnabled(u))
        assertEquals(10, codes.size); assertEquals(10L, service.backupCodesRemaining(u))
        assertTrue(backupRows.findAll().none { row -> codes.any { it == row.codeHash } }, "코드 자체는 저장하지 않는다")
        val event = events("two_factor.enabled").single()
        assertEquals(u.loginId, event.targetLabel)
        assertTrue(codes.none { event.details.orEmpty().contains(it) } && !event.details.orEmpty().contains("secret"))
    }

    @Test
    fun `틀린 코드와 시작하지 않은 확인은 켜지 못한다`() {
        val u = user()
        assertEquals(TotpConfirmation.NotStarted, service.confirmTotp(u, "123456", at))
        val enrollment = assertNotNull(service.beginTotp(u, "Yona", "a")); val secret = Base32.decode(enrollment.secret)
        assertEquals(TotpConfirmation.InvalidCode, service.confirmTotp(u, "000000".takeIf { it != codeAt(secret, at) } ?: "111111", at))
        assertFalse(service.isEnabled(u))
    }

    @Test
    fun `등록을 다시 시작하면 확인하지 않은 이전 등록은 사라지고 이미 켜져 있으면 시작하지 못한다`() {
        val u = user()
        val first = assertNotNull(service.beginTotp(u, "Yona", "a")); val second = assertNotNull(service.beginTotp(u, "Yona", "a"))
        assertTrue(first.secret != second.secret); assertEquals(1, credentials.findByUserIdOrderById(u.id!!).size)
        val secret = Base32.decode(second.secret)
        assertIs<TotpConfirmation.Confirmed>(service.confirmTotp(u, codeAt(secret, at), at))
        assertNull(service.beginTotp(u, "Yona", "a"), "켜져 있으면 먼저 꺼야 한다")
    }

    @Test
    fun `로그인 코드는 맞으면 통과하고 같은 코드는 다시 쓰지 못하며 다음 구간 코드는 통과한다`() {
        val u = user(); val (secret, _) = enable(u)
        val later = at.plusSeconds(30)
        assertEquals(TwoFactorResult.INVALID, service.verify(u, codeAt(secret, at), later), "등록 확인에 쓴 구간은 다시 쓰지 못한다")
        assertEquals(TwoFactorResult.OK_TOTP, service.verify(u, codeAt(secret, later), later))
        em.flush(); em.clear()
        assertEquals(TwoFactorResult.INVALID, service.verify(u, codeAt(secret, later), later), "같은 코드의 재사용")
        assertEquals(TwoFactorResult.OK_TOTP, service.verify(u, codeAt(secret, later.plusSeconds(30)), later.plusSeconds(30)))
    }

    @Test
    fun `틀린 코드와 켜지 않은 사용자는 통과하지 못한다`() {
        val u = user(); val (secret, _) = enable(u)
        val wrong = (codeAt(secret, at.plusSeconds(60)).toInt() + 1).mod(1_000_000).toString().padStart(6, '0')
        assertEquals(TwoFactorResult.INVALID, service.verify(u, wrong, at.plusSeconds(60)))
        assertEquals(TwoFactorResult.NOT_ENABLED, service.verify(user(), "123456", at))
    }

    @Test
    fun `확인하지 않은 등록은 로그인에 쓰이지 않는다`() {
        val u = user(); val secret = Base32.decode(assertNotNull(service.beginTotp(u, "Yona", "a")).secret)
        assertEquals(TwoFactorResult.NOT_ENABLED, service.verify(u, codeAt(secret, at), at))
    }

    @Test
    fun `백업 코드는 한 번만 쓰고 남은 수가 줄며 감사 기록은 코드를 남기지 않는다`() {
        val u = user(); val (_, codes) = enable(u)
        assertEquals(TwoFactorResult.OK_BACKUP_CODE, service.verify(u, codes[0].uppercase(), at))
        assertEquals(9L, service.backupCodesRemaining(u))
        assertEquals(TwoFactorResult.INVALID, service.verify(u, codes[0], at), "한 번 쓴 코드")
        assertEquals(TwoFactorResult.OK_BACKUP_CODE, service.verify(u, codes[1].replace("-", ""), at))
        val used = events("two_factor.backup_code_used")
        assertEquals(2, used.size); assertTrue(used.none { e -> codes.any { e.details.orEmpty().contains(it) } })
        assertEquals(TwoFactorResult.INVALID, service.verify(u, "zzzzz-zzzzz", at), "없는 코드")
    }

    @Test
    fun `다른 사용자의 백업 코드는 쓰지 못한다`() {
        val a = user(); val b = user(); val (_, codesA) = enable(a); enable(b)
        assertEquals(TwoFactorResult.INVALID, service.verify(b, codesA[0], at))
    }

    @Test
    fun `백업 코드를 다시 만들면 이전 코드는 쓸 수 없고 꺼져 있으면 만들지 못한다`() {
        val u = user(); val (_, old) = enable(u)
        val fresh = assertNotNull(service.regenerateBackupCodes(u))
        assertEquals(10, fresh.size); assertTrue(fresh.intersect(old.toSet()).isEmpty())
        assertEquals(TwoFactorResult.INVALID, service.verify(u, old[0], at))
        assertEquals(TwoFactorResult.OK_BACKUP_CODE, service.verify(u, fresh[0], at))
        assertEquals(1, events("two_factor.backup_codes_regenerated").size)
        assertNull(service.regenerateBackupCodes(user()))
    }

    @Test
    fun `끄면 수단과 백업 코드가 모두 지워지고 감사 기록이 남는다`() {
        val u = user(); enable(u)
        assertTrue(service.disable(u)); em.flush(); em.clear()
        assertFalse(service.isEnabled(u)); assertEquals(0L, service.backupCodesRemaining(u)); assertTrue(credentials.findByUserIdOrderById(u.id!!).isEmpty())
        assertTrue(events("two_factor.disabled").single().details!!.contains("\"by\":\"user\""))
        assertFalse(service.disable(u), "이미 꺼져 있다")
    }

    @Test
    fun `관리자 해제는 확인하지 않은 등록까지 지우고 감사 기록의 구분이 admin이다`() {
        val u = user(); enable(u)
        assertTrue(service.resetByAdmin(u)); em.flush(); em.clear()
        assertFalse(service.isEnabled(u))
        assertTrue(events("two_factor.disabled").single().details!!.contains("\"by\":\"admin\""))
        val pending = user(); service.beginTotp(pending, "Yona", "a")
        assertTrue(service.resetByAdmin(pending)); assertFalse(service.resetByAdmin(user()))
    }

    @Test
    fun `사이트 비밀 키가 바뀌어 비밀 값을 읽지 못하면 예외 없이 인증 실패이다`() {
        val u = user(); val (secret, _) = enable(u)
        val stored = credentials.findByUserIdOrderById(u.id!!).single()
        stored.secretEncrypted = SecretCipher("다른 사이트 비밀 키").encrypt(secret); credentials.save(stored); em.flush(); em.clear()
        assertEquals(TwoFactorResult.INVALID, service.verify(u, codeAt(secret, at.plusSeconds(60)), at.plusSeconds(60)))
    }

    @Test
    fun `사용자를 삭제 처리하면 2단계 인증 데이터가 모두 지워지고 다른 사용자의 것은 그대로이다`() {
        val gone = user(); val stay = user(); enable(gone); enable(stay)
        devices.save(KnownDevice(user = gone, tokenHash = "h-${System.nanoTime()}", name = "Firefox", createdDate = java.util.Date(), expiresDate = java.util.Date()))
        em.flush()
        siteUsers.deleteUser(gone); em.flush(); em.clear()
        assertEquals(0L, (em.createNativeQuery("select count(*) from two_factor_credential where user_id = ${gone.id}").singleResult as Number).toLong())
        assertEquals(0L, (em.createNativeQuery("select count(*) from two_factor_backup_code where user_id = ${gone.id}").singleResult as Number).toLong())
        assertEquals(0L, (em.createNativeQuery("select count(*) from known_device where user_id = ${gone.id}").singleResult as Number).toLong())
        assertTrue(service.isEnabled(users.findById(stay.id!!).get()))
    }
}
