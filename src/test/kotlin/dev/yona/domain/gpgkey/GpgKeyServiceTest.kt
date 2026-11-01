package dev.yona.domain.gpgkey

import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GPG 키 등록 서비스를 검증한다(X-4 2단계, `decisions.md` D-65). 주 키 한 행과 하위 키 행들을 저장하고, 지문은 사이트 전체에서 유일하며,
 * 삭제는 주인만 할 수 있다.
 */
@Transactional
class GpgKeyServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: GpgKeyService
    @Autowired private lateinit var keys: GpgKeyRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteUsers: dev.yona.domain.site.SiteUserService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "gk${++sequence}", name = "gk$sequence", email = "gk$sequence@example.com"))

    private fun added(result: GpgKeyAddition) = (result as GpgKeyAddition.Added).key

    private fun rejectedCode(result: GpgKeyAddition) = (result as GpgKeyAddition.Rejected).code

    @Test
    fun `공개키를 등록하면 주 키 한 행과 하위 키 행이 저장된다`() {
        val u = user()
        val source = TestGpgKey.generate(signingSubkey = true, encryptionSubkey = true)
        val key = added(service.add(u, "내 노트북", source.publicArmored))
        em.flush()
        assertEquals("내 노트북", key.name)
        assertEquals(source.fingerprint, key.fingerprint)
        assertEquals(source.keyId, key.keyId)
        assertEquals(u.id, key.user?.id)
        assertNull(key.parent)
        assertEquals("tester@example.com", key.emails)
        assertTrue(key.canSign == true)
        assertNotNull(key.createdDate)
        assertTrue(key.publicKey!!.startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----"))
        val subkeys = keys.findByParentIdOrderById(key.id!!)
        assertEquals(2, subkeys.size)
        assertEquals(listOf(true, false), subkeys.map { it.canSign })
        assertTrue(subkeys.all { it.user?.id == u.id && it.publicKey == null })
    }

    @Test
    fun `이름을 비우면 이메일로 정한다`() {
        val u = user()
        assertEquals("tester@example.com", added(service.add(u, "  ", TestGpgKey.generate().publicArmored)).name)
        assertEquals("Just A Name", added(service.add(u, null, TestGpgKey.generate(userId = "Just A Name").publicArmored)).name)
    }

    @Test
    fun `이름은 100자까지이다`() {
        val u = user()
        assertEquals("gpgKey.error.name", rejectedCode(service.add(u, "x".repeat(101), TestGpgKey.generate().publicArmored)))
        assertTrue(service.add(u, "x".repeat(100), TestGpgKey.generate().publicArmored) is GpgKeyAddition.Added)
    }

    @Test
    fun `받을 수 없는 키는 이유 코드로 거절한다`() {
        val u = user()
        assertEquals("gpgKey.error.empty", rejectedCode(service.add(u, "a", "")))
        assertEquals("gpgKey.error.malformed", rejectedCode(service.add(u, "a", "ssh-ed25519 AAAA user@host")))
        assertEquals("gpgKey.error.revoked", rejectedCode(service.add(u, "a", TestGpgKey.generate().revoked().publicArmored)))
        assertEquals("gpgKey.error.no_signing_key", rejectedCode(service.add(u, "a", TestGpgKey.generate(primaryCanSign = false, encryptionSubkey = true).publicArmored)))
    }

    @Test
    fun `같은 키는 사이트 전체에서 한 번만 등록한다`() {
        val a = user()
        val b = user()
        val source = TestGpgKey.generate(signingSubkey = true)
        added(service.add(a, "a", source.publicArmored))
        assertEquals("gpgKey.error.duplicate", rejectedCode(service.add(a, "again", source.publicArmored)))
        assertEquals("gpgKey.error.duplicate", rejectedCode(service.add(b, "other user", source.publicArmored)))
    }

    @Test
    fun `사용자당 키 개수에 한도가 있다`() {
        val u = user()
        repeat(GpgKeyService.MAX_KEYS) { added(service.add(u, "k$it", TestGpgKey.generate().publicArmored)) }
        assertEquals("gpgKey.error.limit", rejectedCode(service.add(u, "over", TestGpgKey.generate().publicArmored)))
    }

    @Test
    fun `목록은 주 키만 최근에 등록한 순서로 보인다`() {
        val u = user()
        val first = added(service.add(u, "first", TestGpgKey.generate(signingSubkey = true).publicArmored))
        val second = added(service.add(u, "second", TestGpgKey.generate().publicArmored))
        em.flush()
        assertEquals(listOf(second.id, first.id), service.list(u).map { it.id })
        assertTrue(service.list(user()).isEmpty())
    }

    @Test
    fun `삭제하면 하위 키 행도 함께 지워지고 지문은 다시 등록할 수 있다`() {
        val u = user()
        val source = TestGpgKey.generate(signingSubkey = true)
        val key = added(service.add(u, "a", source.publicArmored))
        em.flush()
        assertTrue(service.delete(u, key.id!!))
        em.flush(); em.clear()
        assertEquals(0L, keys.count())
        assertTrue(service.add(u, "again", source.publicArmored) is GpgKeyAddition.Added)
    }

    @Test
    fun `다른 사용자의 키나 하위 키 행은 지울 수 없다`() {
        val owner = user()
        val key = added(service.add(owner, "a", TestGpgKey.generate(signingSubkey = true).publicArmored))
        em.flush()
        assertFalse(service.delete(user(), key.id!!))
        val subkey = keys.findByParentIdOrderById(key.id!!).single()
        assertFalse(service.delete(owner, subkey.id!!), "하위 키 행은 주 키와 함께만 지운다")
        assertFalse(service.delete(owner, 999_999L))
        assertEquals(2L, keys.count())
    }

    @Test
    fun `주 키와 하위 키의 키 ID로 소유자와 키 묶음을 찾는다`() {
        val u = user()
        val source = TestGpgKey.generate(signingSubkey = true)
        added(service.add(u, "a", source.publicArmored))
        em.flush()
        val byPrimary = service.findByKeyId(source.keyId)
        assertEquals(u.id, byPrimary.single().user?.id)
        val byUpper = service.findByKeyId("%016X".format(source.subkeyId(0)))
        assertEquals(source.fingerprint, byUpper.single().parent?.fingerprint, "하위 키 행에서 주 키로 이어진다")
        assertEquals(u.id, byUpper.single().user?.id)
        assertTrue(service.findByKeyId("0000000000000000").isEmpty())
    }

    @Test
    fun `지문은 대문자 16진수로 저장해 일치 검사가 대소문자에 흔들리지 않는다`() {
        val u = user()
        val key = added(service.add(u, "a", TestGpgKey.generate().publicArmored))
        assertEquals(key.fingerprint, key.fingerprint?.uppercase())
        assertEquals(40, key.fingerprint?.length)
    }

    @Test
    fun `사용자를 삭제 처리하면 그 사용자의 키가 하위 키까지 모두 지워진다`() {
        val gone = user()
        val stay = user()
        added(service.add(gone, "a", TestGpgKey.generate(signingSubkey = true).publicArmored))
        val kept = added(service.add(stay, "b", TestGpgKey.generate(signingSubkey = true).publicArmored))
        em.flush()
        siteUsers.deleteUser(gone)
        em.flush(); em.clear()
        assertEquals(0L, (em.createNativeQuery("select count(*) from gpg_key where user_id = ${gone.id}").singleResult as Number).toLong())
        assertEquals(2L, keys.count(), "다른 사용자의 키는 그대로이다")
        assertEquals(kept.id, service.list(users.findById(stay.id!!).get()).single().id)
    }
}
