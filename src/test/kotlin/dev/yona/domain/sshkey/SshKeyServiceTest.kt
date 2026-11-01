package dev.yona.domain.sshkey

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
 * SSH 공개키 등록 서비스를 검증한다(X-3 2단계, `decisions.md` D-66). 지문은 사이트 전체(사용자 키와 배포 키)에서 유일하고, 삭제는 주인만 할 수 있다.
 */
@Transactional
class SshKeyServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: SshKeyService
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: dev.yona.domain.project.ProjectRepository
    @Autowired private lateinit var siteUsers: dev.yona.domain.site.SiteUserService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user() = users.save(User(loginId = "sk${++sequence}", name = "sk$sequence", email = "sk$sequence@example.com"))

    private fun added(result: SshKeyAddition) = (result as SshKeyAddition.Added).key

    private fun rejectedCode(result: SshKeyAddition) = (result as SshKeyAddition.Rejected).code

    @Test
    fun `공개키를 등록하면 종류와 지문과 이름이 저장된다`() {
        val u = user(); val source = TestSshKey.generate("laptop@home")
        val key = added(service.add(u, "내 노트북", source.publicLine))
        em.flush()
        assertEquals("내 노트북", key.name)
        assertEquals("ssh-ed25519", key.keyType)
        assertEquals(source.fingerprint, key.fingerprint)
        assertEquals(source.publicLine.substringBeforeLast(" "), key.publicKey)
        assertEquals(u.id, key.user?.id)
        assertNotNull(key.createdDate)
    }

    @Test
    fun `이름을 비우면 키의 주석으로 정하고 주석도 없으면 지문으로 정한다`() {
        val u = user()
        assertEquals("laptop@home", added(service.add(u, "  ", TestSshKey.generate("laptop@home").publicLine)).name)
        val bare = TestSshKey.generate("").publicLine
        assertEquals(SshKeyParser.parse(bare).fingerprint, added(service.add(u, null, bare)).name)
    }

    @Test
    fun `이름은 100자까지이다`() {
        val u = user()
        assertEquals("sshKey.error.name", rejectedCode(service.add(u, "x".repeat(101), TestSshKey.generate().publicLine)))
        assertTrue(service.add(u, "x".repeat(100), TestSshKey.generate().publicLine) is SshKeyAddition.Added)
    }

    @Test
    fun `받을 수 없는 키는 이유 코드로 거절한다`() {
        val u = user()
        assertEquals("sshKey.error.empty", rejectedCode(service.add(u, "a", " ")))
        assertEquals("sshKey.error.malformed", rejectedCode(service.add(u, "a", "hello")))
    }

    @Test
    fun `같은 지문은 사이트 전체에서 한 번만 등록할 수 있다`() {
        val source = TestSshKey.generate()
        assertTrue(service.add(user(), "a", source.publicLine) is SshKeyAddition.Added)
        assertEquals("sshKey.error.duplicate", rejectedCode(service.add(user(), "b", source.publicLine)))
    }

    @Test
    fun `배포 키로 쓰는 키는 사용자 키로 등록할 수 없다`() {
        val source = TestSshKey.generate()
        val project = projects.save(dev.yona.domain.project.Project(name = "dk${++sequence}", owner = "o$sequence"))
        assertTrue(deployKeys.add(project, "d", source.publicLine, false) is DeployKeyAddition.Added)
        assertEquals("sshKey.error.duplicate", rejectedCode(service.add(user(), "a", source.publicLine)))
    }

    @Test
    fun `사용자당 최대 50개까지이다`() {
        val u = user()
        repeat(SshKeyService.MAX_KEYS) { assertTrue(service.add(u, "k$it", TestSshKey.generate().publicLine) is SshKeyAddition.Added) }
        assertEquals("sshKey.error.limit", rejectedCode(service.add(u, "x", TestSshKey.generate().publicLine)))
        assertTrue(service.add(user(), "x", TestSshKey.generate().publicLine) is SshKeyAddition.Added)
    }

    @Test
    fun `목록은 최근에 등록한 순서이고 자기 키만 보인다`() {
        val a = user(); val b = user()
        val first = added(service.add(a, "first", TestSshKey.generate().publicLine, java.util.Date(1_000)))
        val second = added(service.add(a, "second", TestSshKey.generate().publicLine, java.util.Date(2_000)))
        service.add(b, "other", TestSshKey.generate().publicLine)
        assertEquals(listOf(second.id, first.id), service.list(a).map { it.id })
        assertNotNull(service.find(a, first.id!!)); assertNull(service.find(b, first.id!!))
    }

    @Test
    fun `삭제는 주인만 할 수 있고 지운 뒤에는 같은 키를 다시 등록할 수 있다`() {
        val a = user(); val b = user(); val source = TestSshKey.generate()
        val key = added(service.add(a, "mine", source.publicLine))
        assertFalse(service.delete(b, key.id!!))
        assertNotNull(service.find(a, key.id!!))
        assertTrue(service.delete(a, key.id!!))
        assertNull(service.find(a, key.id!!))
        assertTrue(service.add(b, "again", source.publicLine) is SshKeyAddition.Added)
    }

    @Test
    fun `지문으로 키와 주인을 찾는다`() {
        val u = user(); val source = TestSshKey.generate()
        val key = added(service.add(u, "k", source.publicLine))
        assertEquals(key.id, service.findByFingerprint(source.fingerprint)?.id)
        assertNull(service.findByFingerprint("SHA256:none"))
    }

    @Test
    fun `사용자를 지우면 키도 지워진다`() {
        val u = user(); val source = TestSshKey.generate()
        service.add(u, "k", source.publicLine)
        em.flush(); em.clear()
        siteUsers.deleteUser(u)
        assertNull(service.findByFingerprint(source.fingerprint))
    }
}
