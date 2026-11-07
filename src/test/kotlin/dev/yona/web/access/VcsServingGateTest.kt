package dev.yona.web.access

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 저장소 HTTP 서빙의 공통 앞단([VcsServingGate])을 확인한다(Mercurial 도입을 위한 공통화 4단계). 인증, 토큰 범위, 권한, 401과 403 응답이 Git 서블릿 없이 동작하므로
 * Mercurial 서빙이 같은 앞단을 쓴다. Git 전체 경로는 `GitHttpTest`가 본다.
 */
@Transactional
class VcsServingGateTest : AbstractIntegrationTest() {

    @Autowired private lateinit var gate: VcsServingGate
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(owner: User, scope: ProjectScope, vcs: String = "Mercurial"): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = owner.loginId, name = "gate$n", vcs = vcs, projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        return p
    }

    private fun request(authorization: String? = null) = MockHttpServletRequest("GET", "/o/p").also { r -> authorization?.let { r.addHeader("Authorization", it) } }

    @Test
    fun `자격이 없으면 익명으로 통과하고 형식이 틀리면 400이다`() {
        val anonymous = gate.enter("hg", request(), MockHttpServletResponse())
        assertTrue(anonymous is VcsServingGate.Entry.Passed); assertNull(anonymous.identity.user)
        val malformed = gate.enter("hg", request("Basic"), MockHttpServletResponse())
        assertTrue(malformed is VcsServingGate.Entry.Stopped); assertEquals(HttpStatus.BAD_REQUEST, malformed.response.statusCode)
    }

    @Test
    fun `익명은 공개 프로젝트를 읽을 수 있지만 쓰기와 비공개 읽기는 401이다`() {
        val owner = user("go")
        val open = project(owner, ProjectScope.PUBLIC)
        val anonymous = BasicIdentity(null)
        assertNull(gate.admit(open, anonymous, VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse()))
        val writeResponse = MockHttpServletResponse()
        val denied = gate.admit(open, anonymous, VcsServingGate.Access(write = true), "hg", request(), writeResponse)
        assertNotNull(denied); assertEquals(HttpStatus.UNAUTHORIZED, denied.statusCode)
        assertEquals("Basic realm=\"Yona\"", writeResponse.getHeader("WWW-Authenticate"))
        val closed = project(owner, ProjectScope.PRIVATE)
        assertEquals(HttpStatus.UNAUTHORIZED, gate.admit(closed, anonymous, VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse())!!.statusCode)
    }

    @Test
    fun `로그인했지만 권한이 없으면 403과 안내이고 구성원은 통과한다`() {
        val owner = user("go"); val outsider = user("gx")
        val closed = project(owner, ProjectScope.PRIVATE)
        val forbidden = gate.admit(closed, BasicIdentity(outsider), VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse())
        assertNotNull(forbidden); assertEquals(HttpStatus.FORBIDDEN, forbidden.statusCode)
        assertTrue(forbidden.body.toString().contains(outsider.loginId!!), forbidden.body.toString())
        assertNull(gate.admit(closed, BasicIdentity(owner), VcsServingGate.Access(write = true), "hg", request(), MockHttpServletResponse()))
    }

    @Test
    fun `코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 읽기도 수정 권한을 요구한다`() {
        val owner = user("go"); val outsider = user("gx")
        val open = project(owner, ProjectScope.PUBLIC).also { it.isCodeAccessibleMemberOnly = true; projects.save(it) }
        val denied = gate.admit(open, BasicIdentity(outsider), VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse())
        assertEquals(HttpStatus.FORBIDDEN, denied!!.statusCode)
        assertNull(gate.admit(open, BasicIdentity(owner), VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse()))
    }

    @Test
    fun `토큰 범위가 저장소 접근을 허용하지 않으면 403이다`() {
        val owner = user("go")
        val open = project(owner, ProjectScope.PUBLIC)
        // read:repo 토큰으로는 읽기만 된다
        val readOnly = BasicIdentity(owner, tokenScopes = setOf("read:repo"))
        assertNull(gate.admit(open, readOnly, VcsServingGate.Access(write = false), "hg", request(), MockHttpServletResponse()))
        val denied = gate.admit(open, readOnly, VcsServingGate.Access(write = true), "hg", request(), MockHttpServletResponse())
        assertEquals(HttpStatus.FORBIDDEN, denied!!.statusCode); assertEquals("The token scope does not allow repository access", denied.body)
    }

    private companion object {
        val counter = AtomicInteger()
    }
}
