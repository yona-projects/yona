package dev.yona.web.git

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
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
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Git의 HTTP 접근에서 개인용 API 토큰을 비밀번호 대신 쓰는 것을 검증한다(로드맵 X-5, `decisions.md` D-36, `architecture/api-token.md` 4절).
 * 사용자 이름(로그인 ID)과 비밀번호 칸의 토큰이 같은 사용자의 것이어야 하고, 범위가 `repo`나 공개 프로젝트의 `public_repo`여야 하며,
 * 새 형식 토큰은 확인에 실패해도 비밀번호로 넘기지 않는다. 같은 주소에서 5회 실패하면 막는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-git-token"])
class GitHttpTokenTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var tokenRepository: dev.yona.domain.apitoken.ApiTokenRepository

    private var sequence = 0

    private fun user(password: String = "1234"): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "gt$n", name = "gt$n", email = "gt$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash(password, salt)))
    }

    private fun project(owner: User, scope: ProjectScope): Project {
        val p = projects.save(Project(owner = owner.loginId, name = "gp${++sequence}", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = owner, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun newToken(user: User, vararg scopes: String): String = (tokens.create(user, "git", scopes.toSet(), null) as TokenCreation.Created).value

    private fun basic(user: String, password: String) = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())

    private fun refs(p: Project, authorization: String?, service: String = "git-receive-pack", ip: String? = null) =
        mockMvc.perform(
            get("/${p.owner}/${p.name}/info/refs").param("service", service).apply {
                authorization?.let { header("Authorization", it) }
                ip?.let { addr -> with { it.remoteAddr = addr; it } }
            }
        )

    @Test
    fun `새 토큰을 비밀번호 칸에 넣으면 푸시 권한이 있는 구성원으로 인증하고 세션 쿠키는 만들지 않는다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE); val t = newToken(owner, "repo")
        val result = refs(p, basic(owner.loginId!!, t)).andExpect(status().isOk).andReturn()
        assertNull(result.request.getSession(false), "토큰 인증은 세션을 만들지 않는다")
        assertNull(result.response.getHeader("Set-Cookie"))
    }

    @Test
    fun `사용자 이름이 토큰 주인과 다르면 인증하지 못한다`() {
        val owner = user(); val other = user(); val p = project(owner, ProjectScope.PRIVATE)
        val t = newToken(owner, "repo")
        refs(p, basic(other.loginId!!, t)).andExpect(status().isUnauthorized)
        refs(p, basic("nobody", t)).andExpect(status().isUnauthorized)
        refs(p, basic(owner.loginId!!.uppercase(), t)).andExpect(status().isOk)
    }

    @Test
    fun `토큰은 사용자의 권한을 넘지 못한다`() {
        val owner = user(); val outsider = user(); val p = project(owner, ProjectScope.PRIVATE)
        refs(p, basic(outsider.loginId!!, newToken(outsider, "repo"))).andExpect(status().isForbidden)
    }

    @Test
    fun `public repo 토큰은 공개 프로젝트에서만 쓸 수 있다`() {
        val owner = user(); val pub = project(owner, ProjectScope.PUBLIC); val priv = project(owner, ProjectScope.PRIVATE)
        val t = newToken(owner, "public_repo")
        refs(pub, basic(owner.loginId!!, t)).andExpect(status().isOk)
        refs(priv, basic(owner.loginId!!, t)).andExpect(status().isForbidden)
    }

    @Test
    fun `저장소 범위가 없는 토큰은 코드 접근에 쓸 수 없다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE)
        refs(p, basic(owner.loginId!!, newToken(owner, "user", "read:org"))).andExpect(status().isForbidden)
    }

    @Test
    fun `새 형식 토큰이 틀리면 비밀번호로 넘기지 않고 거절한다`() {
        val fake = "yona_pat_" + "A".repeat(40)
        val owner = user(password = fake); val p = project(owner, ProjectScope.PRIVATE)
        refs(p, basic(owner.loginId!!, fake)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `이관한 옛 토큰도 비밀번호 칸에 넣어 쓸 수 있다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE)
        val legacy = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 3 + 1).toByte() })
        ApiTokenSupport.grant(tokenRepository, owner, legacy)
        refs(p, basic(owner.loginId!!, legacy)).andExpect(status().isOk)
    }

    @Test
    fun `옛 토큰과 모양이 같은 비밀번호는 토큰이 없으면 비밀번호로 확인한다`() {
        val lookalike = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 5 + 2).toByte() })
        val owner = user(password = lookalike); val p = project(owner, ProjectScope.PRIVATE)
        refs(p, basic(owner.loginId!!, lookalike)).andExpect(status().isOk)
    }

    @Test
    fun `비밀번호 인증은 그대로 되고 세션 쿠키를 심는다 (v1_6)`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE)
        val result = refs(p, basic(owner.loginId!!, "1234")).andExpect(status().isOk).andReturn()
        assertEquals(true, result.request.getSession(false) != null, "v1.6은 비밀번호 인증에 세션을 만든다")
    }

    @Test
    fun `같은 주소에서 5회 실패하면 막고 다른 주소는 영향이 없다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE); val t = newToken(owner, "repo")
        repeat(5) { refs(p, basic(owner.loginId!!, "wrong$it"), ip = "10.7.7.7").andExpect(status().isUnauthorized) }
        refs(p, basic(owner.loginId!!, t), ip = "10.7.7.7").andExpect(status().isTooManyRequests)
        refs(p, basic(owner.loginId!!, t), ip = "10.6.6.6").andExpect(status().isOk)
    }

    @Test
    fun `성공하면 실패 횟수를 지운다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE); val t = newToken(owner, "repo")
        repeat(4) { refs(p, basic(owner.loginId!!, "wrong$it"), ip = "10.5.5.5").andExpect(status().isUnauthorized) }
        refs(p, basic(owner.loginId!!, t), ip = "10.5.5.5").andExpect(status().isOk)
        repeat(4) { refs(p, basic(owner.loginId!!, "again$it"), ip = "10.5.5.5").andExpect(status().isUnauthorized) }
        refs(p, basic(owner.loginId!!, t), ip = "10.5.5.5").andExpect(status().isOk)
    }

    @Test
    fun `익명 요청은 실패로 세지 않는다`() {
        val owner = user(); val p = project(owner, ProjectScope.PRIVATE)
        repeat(10) { refs(p, null, ip = "10.4.4.4").andExpect(status().isUnauthorized) }
        refs(p, basic(owner.loginId!!, "1234"), ip = "10.4.4.4").andExpect(status().isOk)
    }
}
