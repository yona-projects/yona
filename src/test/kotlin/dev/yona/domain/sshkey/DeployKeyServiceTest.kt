package dev.yona.domain.sshkey

import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
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
 * 배포 키(저장소 단위 SSH 공개키) 등록 서비스를 검증한다(X-3 2단계, `decisions.md` D-66). 기본은 읽기 전용이고 등록할 때 쓰기를 허용할 수 있다.
 * 지문은 사용자 키와 합쳐 사이트 전체에서 유일하다.
 */
@Transactional
class DeployKeyServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: DeployKeyService
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun project() = projects.save(Project(name = "dk${++sequence}", owner = "dkowner"))

    private fun added(result: DeployKeyAddition) = (result as DeployKeyAddition.Added).key

    private fun rejectedCode(result: DeployKeyAddition) = (result as DeployKeyAddition.Rejected).code

    @Test
    fun `등록하면 종류와 지문과 쓰기 허용 여부가 저장된다`() {
        val p = project(); val source = TestSshKey.generate("ci@build")
        val key = added(service.add(p, "배포 서버", source.publicLine, true))
        em.flush()
        assertEquals("배포 서버", key.name)
        assertEquals("ssh-ed25519", key.keyType)
        assertEquals(source.fingerprint, key.fingerprint)
        assertEquals(false, key.readOnly)
        assertEquals(p.id, key.project?.id)
        assertNotNull(key.createdDate)
        assertEquals(true, added(service.add(p, "읽기", TestSshKey.generate().publicLine, false)).readOnly)
    }

    @Test
    fun `이름을 비우면 주석이나 지문으로 정하고 100자를 넘으면 거절한다`() {
        val p = project()
        assertEquals("ci@build", added(service.add(p, " ", TestSshKey.generate("ci@build").publicLine, false)).name)
        assertEquals("deployKey.error.name", rejectedCode(service.add(p, "x".repeat(101), TestSshKey.generate().publicLine, false)))
    }

    @Test
    fun `받을 수 없는 키는 거절한다`() = assertEquals("deployKey.error.malformed", rejectedCode(service.add(project(), "a", "hello", false)))

    @Test
    fun `같은 지문은 다른 저장소에도 사용자 키로도 등록할 수 없다`() {
        val source = TestSshKey.generate()
        assertTrue(service.add(project(), "a", source.publicLine, false) is DeployKeyAddition.Added)
        assertEquals("deployKey.error.duplicate", rejectedCode(service.add(project(), "b", source.publicLine, false)))
        val owned = TestSshKey.generate()
        sshKeys.add(users.save(User(loginId = "dku${++sequence}", name = "n", email = "dku$sequence@example.com")), "u", owned.publicLine)
        assertEquals("deployKey.error.duplicate", rejectedCode(service.add(project(), "c", owned.publicLine, false)))
    }

    @Test
    fun `저장소당 최대 50개까지이다`() {
        val p = project()
        repeat(DeployKeyService.MAX_KEYS) { assertTrue(service.add(p, "k$it", TestSshKey.generate().publicLine, false) is DeployKeyAddition.Added) }
        assertEquals("deployKey.error.limit", rejectedCode(service.add(p, "x", TestSshKey.generate().publicLine, false)))
    }

    @Test
    fun `목록은 최근에 등록한 순서이고 저장소별로 나뉜다`() {
        val a = project(); val b = project()
        val first = added(service.add(a, "first", TestSshKey.generate().publicLine, false, java.util.Date(1_000)))
        val second = added(service.add(a, "second", TestSshKey.generate().publicLine, false, java.util.Date(2_000)))
        service.add(b, "other", TestSshKey.generate().publicLine, false)
        assertEquals(listOf(second.id, first.id), service.list(a).map { it.id })
        assertNotNull(service.find(a, first.id!!)); assertNull(service.find(b, first.id!!))
    }

    @Test
    fun `삭제하면 같은 키를 다시 등록할 수 있고 다른 저장소의 키는 지울 수 없다`() {
        val a = project(); val b = project(); val source = TestSshKey.generate()
        val key = added(service.add(a, "k", source.publicLine, false))
        assertFalse(service.delete(b, key.id!!))
        assertTrue(service.delete(a, key.id!!))
        assertNull(service.findByFingerprint(source.fingerprint))
        assertTrue(service.add(b, "again", source.publicLine, true) is DeployKeyAddition.Added)
    }

    @Test
    fun `등록한 사용자를 남기고 지정하지 않으면 비운다`() {
        val p = project(); val owner = users.save(User(loginId = "dkc${++sequence}", name = "dkc", email = "dkc$sequence@example.com"))
        assertEquals(owner.id, added(service.add(p, "a", TestSshKey.generate().publicLine, false, createdBy = owner)).createdById)
        assertNull(added(service.add(p, "b", TestSshKey.generate().publicLine, false)).createdById)
    }

    @Test
    fun `지문으로 배포 키와 저장소를 찾는다`() {
        val p = project(); val source = TestSshKey.generate()
        service.add(p, "k", source.publicLine, false)
        assertEquals(p.id, service.findByFingerprint(source.fingerprint)?.project?.id)
    }
}
