package dev.yona.ssh

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.sshkey.DeployKeyAddition
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyAddition
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.sshkey.TestSshKey
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSH 접속의 인증과 인가(X-3 5단계, `decisions.md` D-66)를 검증한다. 인증은 접속한 키의 지문으로 사용자 키나 배포 키를 찾는 일이고, 인가는 HTTP와 같은 접근 규칙으로
 * 저장소를 읽거나 쓸 수 있는지 판정하는 일이다. 읽을 수 없는 저장소는 있는지 없는지 알려 주지 않는 `NotFound`이다(GitHub 방식).
 */
@Transactional
class SshAccessTest : AbstractIntegrationTest() {

    @Autowired private lateinit var access: SshAccess
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE) = users.save(User(loginId = "sa${++sequence}", name = "sa$sequence", email = "sa$sequence@example.com", state = state))

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC, vcs: String = "GIT", memberOnly: Boolean = false) =
        projects.save(Project(owner = "sao${++sequence}", name = "sap$sequence", vcs = vcs, projectScope = scope, isCodeAccessibleMemberOnly = memberOnly))

    private fun join(user: User, project: Project, role: RoleType) { projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get())) }

    private fun keyOf(user: User): TestSshKey = TestSshKey.generate().also { assertTrue(sshKeys.add(user, "k", it.publicLine) is SshKeyAddition.Added) }

    private fun deployKey(project: Project, writable: Boolean): TestSshKey = TestSshKey.generate().also { assertTrue(deployKeys.add(project, "d", it.publicLine, writable) is DeployKeyAddition.Added) }

    private fun read(p: Project) = SshRepositoryRequest("GIT", p.owner!!, p.name!!, write = false)

    private fun write(p: Project) = SshRepositoryRequest("GIT", p.owner!!, p.name!!, write = true)

    private fun allowed(result: SshAuthorization) = (result as SshAuthorization.Allowed)

    // --- 인증 ---

    @Test
    fun `등록하지 않은 키는 인증하지 못한다`() = assertNull(access.authenticate(TestSshKey.generate().fingerprint))

    @Test
    fun `사용자 키와 배포 키로 인증한다`() {
        val u = user(); val userKey = keyOf(u); val p = project(); val dk = deployKey(p, false)
        assertEquals(u.id, access.authenticate(userKey.fingerprint)!!.userId)
        assertEquals(null, access.authenticate(userKey.fingerprint)!!.deployKeyId)
        assertNotNull(access.authenticate(dk.fingerprint)!!.deployKeyId)
        assertNull(access.authenticate(dk.fingerprint)!!.userId)
    }

    @Test
    fun `잠기거나 삭제된 사용자의 키는 인증하지 못한다`() {
        assertNull(access.authenticate(keyOf(user(UserState.LOCKED)).fingerprint))
        assertNull(access.authenticate(keyOf(user(UserState.DELETED)).fingerprint))
        assertNotNull(access.authenticate(keyOf(user(UserState.GUEST)).fingerprint))
    }

    // --- 사용자 키의 인가 ---

    @Test
    fun `공개 프로젝트는 구성원이 아니어도 읽을 수 있고 쓰려면 권한이 있어야 한다`() {
        val u = user(); val p = project(); val principal = access.authenticate(keyOf(u).fingerprint)!!
        assertEquals(p.id, allowed(access.authorize(principal, read(p))).project.id)
        assertEquals(u.id, allowed(access.authorize(principal, read(p))).sender?.id)
        assertTrue(access.authorize(principal, write(p)) is SshAuthorization.Denied)
    }

    @Test
    fun `구성원은 쓸 수 있다`() {
        val u = user(); val p = project(); join(u, p, RoleType.MEMBER)
        assertTrue(access.authorize(access.authenticate(keyOf(u).fingerprint)!!, write(p)) is SshAuthorization.Allowed)
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아니면 없는 것처럼 NotFound이다`() {
        val outsider = user(); val member = user(); val p = project(ProjectScope.PRIVATE); join(member, p, RoleType.MEMBER)
        assertTrue(access.authorize(access.authenticate(keyOf(outsider).fingerprint)!!, read(p)) is SshAuthorization.NotFound)
        assertTrue(access.authorize(access.authenticate(keyOf(outsider).fingerprint)!!, write(p)) is SshAuthorization.NotFound)
        assertTrue(access.authorize(access.authenticate(keyOf(member).fingerprint)!!, read(p)) is SshAuthorization.Allowed)
    }

    @Test
    fun `코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 읽을 수 없다`() {
        val outsider = user(); val p = project(memberOnly = true)
        assertTrue(access.authorize(access.authenticate(keyOf(outsider).fingerprint)!!, read(p)) is SshAuthorization.NotFound)
    }

    @Test
    fun `없는 프로젝트와 저장소 종류가 다른 프로젝트는 NotFound이다`() {
        val principal = access.authenticate(keyOf(user()).fingerprint)!!
        assertTrue(access.authorize(principal, SshRepositoryRequest("GIT", "nobody", "none", write = false)) is SshAuthorization.NotFound)
        val svn = project(vcs = "Subversion")
        assertTrue(access.authorize(principal, SshRepositoryRequest("GIT", svn.owner!!, svn.name!!, write = false)) is SshAuthorization.NotFound)
    }

    @Test
    fun `인증한 뒤 사용자가 잠기면 인가하지 못한다`() {
        val u = user(); val p = project(); val principal = access.authenticate(keyOf(u).fingerprint)!!
        u.state = UserState.LOCKED; users.save(u)
        assertTrue(access.authorize(principal, read(p)) is SshAuthorization.NotFound)
    }

    // --- 배포 키의 인가 ---

    @Test
    fun `읽기 전용 배포 키는 자기 저장소를 읽을 수 있고 쓰려면 거부된다`() {
        val p = project(ProjectScope.PRIVATE); val principal = access.authenticate(deployKey(p, false).fingerprint)!!
        assertNull(allowed(access.authorize(principal, read(p))).sender, "배포 키는 푸시한 사용자가 없다")
        val denied = access.authorize(principal, write(p)) as SshAuthorization.Denied
        assertTrue(denied.message.contains("read-only"), denied.message)
    }

    @Test
    fun `쓰기 배포 키는 자기 저장소에 쓸 수 있다`() {
        val p = project(ProjectScope.PRIVATE); val principal = access.authenticate(deployKey(p, true).fingerprint)!!
        assertTrue(access.authorize(principal, write(p)) is SshAuthorization.Allowed)
    }

    @Test
    fun `배포 키는 다른 저장소에는 공개 저장소여도 쓸 수 없고 비공개 저장소는 NotFound이다`() {
        val mine = project(ProjectScope.PRIVATE); val publicOther = project(); val privateOther = project(ProjectScope.PRIVATE)
        val principal = access.authenticate(deployKey(mine, true).fingerprint)!!
        assertTrue(access.authorize(principal, write(publicOther)) is SshAuthorization.NotFound)
        assertTrue(access.authorize(principal, read(publicOther)) is SshAuthorization.NotFound, "배포 키는 자기 저장소에만 접근한다")
        assertTrue(access.authorize(principal, read(privateOther)) is SshAuthorization.NotFound)
    }

    @Test
    fun `배포 키를 지우면 이미 인증한 접속도 인가하지 못한다`() {
        val p = project(); val key = deployKey(p, true); val principal = access.authenticate(key.fingerprint)!!
        deployKeys.delete(p, deployKeys.list(p).single().id!!)
        assertTrue(access.authorize(principal, read(p)) is SshAuthorization.NotFound)
    }
}
