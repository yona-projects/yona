package dev.yona.domain.gpgkey

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 서명 판정에 사용자와 이메일 확인을 더한 서비스를 검증한다(X-4 4단계, `decisions.md` D-65). Verified이려면 서명이 맞고, 서명한 키의 주인이 활성 사용자이며,
 * 커밋터(태그를 단 사람) 이메일이 키의 사용자 ID에 있고, 그 이메일이 주인이 확인한 이메일이어야 한다.
 */
@Transactional
class GpgVerificationServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var verifier: GpgVerificationService
    @Autowired private lateinit var keyService: GpgKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private val data = "tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\nauthor A <a@example.com> 1700000000 +0900\ncommitter A <a@example.com> 1700000000 +0900\n\n메시지\n".toByteArray()

    private fun user(state: UserState = UserState.ACTIVE, email: String = "vs${++sequence}@example.com") = users.save(User(loginId = "vs$sequence", name = "vs$sequence", email = email, state = state))

    /** [owner]에게 [key]를 등록한다. */
    private fun register(owner: User, key: TestGpgKey) = (keyService.add(owner, "k", key.publicArmored) as GpgKeyAddition.Added).key.also { em.flush() }

    private fun payload(key: TestGpgKey, signerKeyId: Long? = null) = SignedPayload(data, key.sign(data, signerKeyId).toByteArray())

    @Test
    fun `서명이 맞고 이메일이 키와 사용자에게 모두 있으면 valid이고 서명한 사용자를 알려 준다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>")
        register(owner, key)
        val result = verifier.verifyCommit(payload(key), "alice@example.com")
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(true, result.verified)
        assertEquals(owner.id, result.signerId)
        assertEquals(key.keyId, result.keyId)
        assertEquals(key.fingerprint, result.fingerprint)
    }

    @Test
    fun `커밋터 이메일은 대소문자를 구분하지 않는다`() {
        val owner = user(email = "Alice@Example.com")
        val key = TestGpgKey.generate(userId = "Alice <ALICE@example.com>")
        register(owner, key)
        assertEquals(GpgReason.VALID, verifier.verifyCommit(payload(key), "alice@EXAMPLE.com").reason)
    }

    @Test
    fun `서명 하위 키로 서명해도 키 주인을 찾는다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>", signingSubkey = true)
        register(owner, key)
        val result = verifier.verifyCommit(payload(key, key.subkeyId(0)), "alice@example.com")
        assertEquals(GpgReason.VALID, result.reason)
        assertEquals(key.fingerprint, result.fingerprint, "표시하는 지문은 주 키의 것이다")
    }

    @Test
    fun `보조 이메일이 확인되었으면 그 이메일로도 valid이다`() {
        val owner = user(email = "main@example.com")
        emails.save(Email(user = owner, email = "work@example.com", valid = true))
        val key = TestGpgKey.generate(userId = "Alice <work@example.com>")
        register(owner, key)
        assertEquals(GpgReason.VALID, verifier.verifyCommit(payload(key), "work@example.com").reason)
    }

    @Test
    fun `보조 이메일이 아직 확인되지 않았으면 unverified_email이다`() {
        val owner = user(email = "main@example.com")
        emails.save(Email(user = owner, email = "work@example.com", valid = false))
        val key = TestGpgKey.generate(userId = "Alice <work@example.com>")
        register(owner, key)
        assertEquals(GpgReason.UNVERIFIED_EMAIL, verifier.verifyCommit(payload(key), "work@example.com").reason)
    }

    @Test
    fun `커밋터 이메일이 키의 사용자 ID에 없으면 bad_email이다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>")
        register(owner, key)
        assertEquals(GpgReason.BAD_EMAIL, verifier.verifyCommit(payload(key), "other@example.com").reason)
    }

    @Test
    fun `키 주인이 삭제되었거나 잠겼으면 no_user이다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>")
        register(owner, key)
        owner.state = UserState.LOCKED
        users.save(owner)
        assertEquals(GpgReason.NO_USER, verifier.verifyCommit(payload(key), "alice@example.com").reason)
    }

    @Test
    fun `등록되지 않은 키는 unknown_key이고 서명이 없으면 unsigned이다`() {
        val key = TestGpgKey.generate()
        val result = verifier.verifyCommit(payload(key), "tester@example.com")
        assertEquals(GpgReason.UNKNOWN_KEY, result.reason)
        assertEquals(key.keyId, result.keyId, "서명이 밝힌 키 ID를 알려 준다")
        assertNull(result.signerId)
        assertEquals(GpgReason.UNSIGNED, verifier.verifyCommit(null, "tester@example.com").reason)
        assertEquals(false, verifier.verifyCommit(null, "tester@example.com").verified)
    }

    @Test
    fun `내용이 바뀌었으면 invalid이다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>")
        register(owner, key)
        val tampered = SignedPayload("바뀐 내용".toByteArray(), key.sign(data).toByteArray())
        assertEquals(GpgReason.INVALID, verifier.verifyCommit(tampered, "alice@example.com").reason)
    }

    @Test
    fun `검증 시점에 키가 만료되었으면 expired_key이다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>", created = Date(System.currentTimeMillis() - 5 * 86_400_000L), validSeconds = 10 * 86_400L)
        register(owner, key)
        val signed = payload(key)
        assertEquals(GpgReason.VALID, verifier.verifyCommit(signed, "alice@example.com").reason)
        assertEquals(GpgReason.EXPIRED_KEY, verifier.verifyCommit(signed, "alice@example.com", Instant.now().plusSeconds(30 * 86_400L)).reason)
    }

    @Test
    fun `키를 지우면 같은 서명이 unknown_key가 된다`() {
        val owner = user(email = "alice@example.com")
        val key = TestGpgKey.generate(userId = "Alice <alice@example.com>")
        val row = register(owner, key)
        val signed = payload(key)
        assertEquals(GpgReason.VALID, verifier.verifyCommit(signed, "alice@example.com").reason)
        keyService.delete(owner, row.id!!)
        em.flush()
        assertEquals(GpgReason.UNKNOWN_KEY, verifier.verifyCommit(signed, "alice@example.com").reason)
    }
}
