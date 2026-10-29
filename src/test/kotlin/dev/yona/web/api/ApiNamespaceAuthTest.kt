package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * 외부 API(`/-_-api/v1`)는 토큰 전용이고 그 밖의 경로는 세션 전용이라는 규칙을 검증한다(로드맵 X-5, `decisions.md` D-36,
 * `architecture/api-auth-split.md`). 이름 공간이 인증 출처를 정하고, 범위는 대응표로 검사하며, 토큰 인증에 5회 실패한 주소는 막는다.
 */
@AutoConfigureMockMvc
@Transactional
class ApiNamespaceAuthTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var tokens: ApiTokenService

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ns$n", name = "ns$n", email = "ns$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun token(user: User, vararg scopes: String): String = (tokens.create(user, "t", scopes.toSet(), null, siteManager = true) as TokenCreation.Created).value

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(scope: ProjectScope) = projects.save(Project(owner = "nso${++sequence}", name = "nsp$sequence", vcs = "GIT", projectScope = scope))

    private val mine = "/-_-api/v1/user/issues"

    @Test
    fun `외부 API는 토큰이 없으면 401이고 세션 로그인만으로는 통과하지 못한다`() {
        val u = user()
        mockMvc.perform(get(mine)).andExpect(status().isUnauthorized)
        mockMvc.perform(get(mine).session(login(u))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `repo 범위 토큰으로 통과하고 Bearer와 Yona-Token 헤더도 받는다`() {
        val u = user(); val t = token(u, "repo")
        mockMvc.perform(get(mine).header("Authorization", "token $t")).andExpect(status().isOk)
        mockMvc.perform(get(mine).header("Authorization", "Bearer $t")).andExpect(status().isOk)
        mockMvc.perform(get(mine).header("Yona-Token", t)).andExpect(status().isOk)
    }

    @Test
    fun `토큰과 다른 사용자의 세션이 함께 오면 토큰의 사용자와 범위가 적용된다`() {
        val tokenOwner = user(); val other = user()
        val narrow = token(tokenOwner, "read:user")
        mockMvc.perform(get(mine).session(login(other)).header("Authorization", "token $narrow")).andExpect(status().isForbidden)
        val wide = token(tokenOwner, "repo")
        mockMvc.perform(get(mine).session(login(other)).header("Authorization", "token $wide")).andExpect(status().isOk)
    }

    @Test
    fun `범위가 모자라면 403이고 형식이 틀린 헤더와 틀린 토큰은 401이다`() {
        val u = user()
        mockMvc.perform(get(mine).header("Authorization", "token ${token(u, "user")}")).andExpect(status().isForbidden)
        mockMvc.perform(get(mine).header("Authorization", "token${token(u, "repo")}")).andExpect(status().isUnauthorized)
        mockMvc.perform(get(mine).header("Authorization", "token yona_pat_nope")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `만료된 토큰과 잠긴 사용자의 토큰은 401이다`() {
        val u = user(); val t = token(u, "repo")
        u.state = UserState.LOCKED; users.save(u)
        mockMvc.perform(get(mine).header("Authorization", "token $t")).andExpect(status().isUnauthorized)
        u.state = UserState.ACTIVE; users.save(u)
        tokens.list(u).forEach { it.expiresDate = Date(System.currentTimeMillis() - 1000) }
        mockMvc.perform(get(mine).header("Authorization", "token $t")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `public repo 토큰은 공개 프로젝트만 통과하고 repo 토큰은 비공개도 통과한다`() {
        val u = user(); val pub = project(ProjectScope.PUBLIC); val priv = project(ProjectScope.PRIVATE)
        val limited = token(u, "public_repo")
        fun path(p: Project) = "/-_-api/v1/owners/${p.owner}/projects/${p.name}/issues/1"
        assertNotEquals(403, mockMvc.perform(get(path(pub)).header("Authorization", "token $limited")).andReturn().response.status, "공개 프로젝트는 범위를 통과한다")
        mockMvc.perform(get(path(priv)).header("Authorization", "token $limited")).andExpect(status().isForbidden)
        assertNotEquals(403, mockMvc.perform(get(path(priv)).header("Authorization", "token ${token(u, "repo")}")).andReturn().response.status)
    }

    @Test
    fun `대응표에 없는 외부 API 경로는 기본 거부이고 hello는 인증 없이 열려 있다`() {
        mockMvc.perform(get("/-_-api/v1/hello")).andExpect(status().isOk).andExpect(jsonPath("$.ok").value(true))
        val t = token(user(), "repo")
        val status = mockMvc.perform(get("/-_-api/v1/nothing/here").header("Authorization", "token $t")).andReturn().response.status
        assertEquals(true, status == 404 || status == 403, "대응표에도 핸들러에도 없는 경로는 열리지 않는다: $status")
    }

    @Test
    fun `외부 API 밖의 경로는 토큰 헤더를 인정하지 않는다`() {
        val u = user(); val t = token(u, "repo", "user")
        // 로그인해야 하는 화면 경로는 토큰만으로는 로그인 화면으로 보낸다
        val r = mockMvc.perform(get("/user/editform").header("Authorization", "token $t")).andReturn().response
        assertEquals(true, r.status in 300..399 || r.status == 403 || r.status == 401, "토큰만으로는 통과하지 못한다: ${r.status}")
        mockMvc.perform(get("/user/editform").session(login(u))).andExpect(status().isOk)
    }

    @Test
    fun `토큰 인증에 5회 실패한 주소는 막히고 다른 주소는 영향이 없다`() {
        val u = user(); val good = token(u, "repo")
        fun from(ip: String, header: String) = get(mine).header("Authorization", header).with { it.remoteAddr = ip; it }
        repeat(5) { mockMvc.perform(from("10.9.9.9", "token yona_pat_bad$it")).andExpect(status().isUnauthorized) }
        mockMvc.perform(from("10.9.9.9", "token $good")).andExpect(status().isTooManyRequests)
        mockMvc.perform(from("10.8.8.8", "token $good")).andExpect(status().isOk)
    }
}
