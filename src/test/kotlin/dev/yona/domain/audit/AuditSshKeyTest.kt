package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.sshkey.DeployKeyAddition
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyAddition
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.sshkey.TestSshKey
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
 * 감사 로그의 SSH 키와 배포 키 이벤트(X-3 6단계, `decisions.md` D-66)를 검증한다: 키 등록과 삭제. 변경과 같은 트랜잭션에서 기록하고, 거절된 등록은 기록하지 않으며,
 * 공개키 본문은 기록에 넣지 않는다(지문, 종류만 남긴다). 배포 키 이벤트는 저장소(프로젝트)를 가리켜 프로젝트의 감사 화면에 보인다.
 */
@Transactional
class AuditSshKeyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0

    private fun user() = users.save(User(loginId = "as${++sequence}-${System.nanoTime()}", name = "as$sequence", email = "as$sequence@example.com", state = UserState.ACTIVE))

    private fun project() = projects.save(Project(owner = "aso${++sequence}", name = "asp$sequence", vcs = "GIT"))

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    @Test
    fun `SSH 키 등록은 지문과 종류를 남기고 공개키 본문은 남기지 않는다`() {
        val u = user(); val source = TestSshKey.generate("laptop@home")
        val key = (sshKeys.add(u, "내 키", source.publicLine) as SshKeyAddition.Added).key
        val e = eventsOf("ssh_key.added").single()
        assertEquals("ssh_key", e.targetType); assertEquals(key.id.toString(), e.targetId); assertEquals("내 키", e.targetLabel)
        assertTrue(e.details!!.contains("\"fingerprint\":\"${source.fingerprint}\"") && e.details!!.contains("\"keyType\":\"ssh-ed25519\""), e.details)
        assertFalse(e.details!!.contains("ssh-ed25519 AAAA"), "공개키 본문은 남기지 않는다")
    }

    @Test
    fun `거절된 SSH 키 등록은 기록하지 않는다`() {
        val u = user()
        assertTrue(sshKeys.add(u, "x", "not a key") is SshKeyAddition.Rejected)
        val source = TestSshKey.generate()
        sshKeys.add(u, "a", source.publicLine)
        assertTrue(sshKeys.add(u, "b", source.publicLine) is SshKeyAddition.Rejected)
        assertEquals(1, eventsOf("ssh_key.added").size)
    }

    @Test
    fun `SSH 키 삭제를 남기고 남의 키 삭제는 남기지 않는다`() {
        val u = user(); val other = user()
        val key = (sshKeys.add(u, "삭제할 키", TestSshKey.generate().publicLine) as SshKeyAddition.Added).key
        assertFalse(sshKeys.delete(other, key.id!!))
        assertTrue(eventsOf("ssh_key.deleted").isEmpty())
        assertTrue(sshKeys.delete(u, key.id!!))
        val e = eventsOf("ssh_key.deleted").single()
        assertEquals("삭제할 키", e.targetLabel)
        assertTrue(e.details!!.contains("\"fingerprint\":\"${key.fingerprint}\""), e.details)
    }

    @Test
    fun `배포 키 등록과 삭제는 저장소와 쓰기 허용 여부를 남기고 프로젝트를 가리킨다`() {
        val p = project(); val source = TestSshKey.generate()
        val key = (deployKeys.add(p, "배포 서버", source.publicLine, writable = true) as DeployKeyAddition.Added).key
        val added = eventsOf("deploy_key.added").single()
        assertEquals("deploy_key", added.targetType); assertEquals("배포 서버", added.targetLabel); assertEquals(p.id, added.projectId)
        assertTrue(added.details!!.contains("\"readOnly\":false") && added.details!!.contains("\"project\":\"${p.owner}/${p.name}\""), added.details)
        assertFalse(added.details!!.contains("ssh-ed25519 AAAA"))
        assertTrue(deployKeys.delete(p, key.id!!))
        assertEquals(p.id, eventsOf("deploy_key.deleted").single().projectId)
    }

    @Test
    fun `거절된 배포 키 등록은 기록하지 않는다`() {
        val p = project()
        assertTrue(deployKeys.add(p, "x", "not a key", false) is DeployKeyAddition.Rejected)
        assertTrue(eventsOf("deploy_key.added").isEmpty())
    }
}
