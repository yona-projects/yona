package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.gpgkey.GpgKeyAddition
import dev.yona.domain.gpgkey.GpgKeyService
import dev.yona.domain.gpgkey.TestGpgKey
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 감사 로그의 GPG 키 이벤트(X-4 6단계, `decisions.md` D-65)를 검증한다: 키 등록과 삭제. 변경과 같은 트랜잭션에서 기록하고, 거절된 등록은 기록하지 않으며,
 * 공개키 본문은 기록에 넣지 않는다(지문, 키 ID, 하위 키 수, 이메일 수만 남긴다).
 */
@Transactional
class AuditGpgKeyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: GpgKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0

    private fun user() = users.save(User(loginId = "ag${++sequence}-${System.nanoTime()}", name = "ag$sequence", email = "ag$sequence@example.com", state = UserState.ACTIVE))

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    @Test
    fun `키 등록은 지문과 키 ID와 하위 키 수를 남기고 공개키 본문은 남기지 않는다`() {
        val u = user()
        val source = TestGpgKey.generate(userId = "A <a@example.com>", signingSubkey = true)
        val key = (service.add(u, "내 키", source.publicArmored) as GpgKeyAddition.Added).key
        val e = eventsOf("gpg_key.added").single()
        assertEquals("gpg_key", e.targetType); assertEquals(key.id.toString(), e.targetId); assertEquals("내 키", e.targetLabel)
        assertTrue(e.details!!.contains("\"fingerprint\":\"${source.fingerprint}\""), e.details)
        assertTrue(e.details!!.contains("\"keyId\":\"${source.keyId}\"") && e.details!!.contains("\"subkeys\":1"), e.details)
        assertFalse(e.details!!.contains("BEGIN PGP"), "공개키 본문은 남기지 않는다")
    }

    @Test
    fun `거절된 등록은 기록하지 않는다`() {
        val u = user()
        assertTrue(service.add(u, "x", "not a key") is GpgKeyAddition.Rejected)
        val source = TestGpgKey.generate()
        service.add(u, "a", source.publicArmored)
        assertTrue(service.add(u, "b", source.publicArmored) is GpgKeyAddition.Rejected)
        assertEquals(1, eventsOf("gpg_key.added").size)
    }

    @Test
    fun `키 삭제를 남기고 없는 키나 남의 키 삭제는 남기지 않는다`() {
        val u = user(); val other = user()
        val key = (service.add(u, "삭제할 키", TestGpgKey.generate().publicArmored) as GpgKeyAddition.Added).key
        assertFalse(service.delete(other, key.id!!))
        assertTrue(eventsOf("gpg_key.deleted").isEmpty())
        assertTrue(service.delete(u, key.id!!))
        val e = eventsOf("gpg_key.deleted").single()
        assertEquals("gpg_key", e.targetType); assertEquals("삭제할 키", e.targetLabel)
        assertTrue(e.details!!.contains("\"fingerprint\":\"${key.fingerprint}\""), e.details)
    }
}
