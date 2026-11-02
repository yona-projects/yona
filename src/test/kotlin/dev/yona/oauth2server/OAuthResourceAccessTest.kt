package dev.yona.oauth2server

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OAuth 액세스 토큰으로 외부 API(`/-_-api/v1`)를 쓰는 길(X-1 4단계, `decisions.md` D-71)을 검증한다: 유효한 토큰은 개인용 토큰과 같은 범위 검사를 받고, 만료, 취소, 모르는 값, 잠긴 사용자는 401이다.
 * 401에는 보호 자원 메타데이터 주소(RFC 9728)가 실린다. **OAuth 토큰은 git과 SVN의 HTTP에 쓸 수 없다**(D-70, 선생님 결정).
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-oauth-resource"])
class OAuthResourceAccessTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository
    @Autowired private lateinit var projects: dev.yona.domain.project.ProjectRepository
    @Autowired private lateinit var projectUsers: dev.yona.domain.project.ProjectUserRepository
    @Autowired private lateinit var menuSettings: dev.yona.domain.project.ProjectMenuSettingRepository
    @Autowired private lateinit var roles: dev.yona.domain.user.RoleRepository
    @Autowired private lateinit var repositories: dev.yona.repository.RepositoryService

    private var n = 0

    private fun user(state: UserState = UserState.ACTIVE): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ra$k-${System.nanoTime()}", name = "ra$k", email = "ra$k@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun client(owner: User): OAuthClient = (clientService.create(owner, "자원 앱", "", "", "https://app.example.com/cb", false, setOf("read:user", "repo")) as ClientCreation.Created).client

    /** 라이브러리가 발급한 것과 같은 모양의 인가를 직접 저장하고 토큰 값을 돌려준다. */
    private fun grant(user: User, client: OAuthClient, scopes: String = "read:user", expires: Instant = Instant.now().plusSeconds(3600), invalidated: Boolean = false): String {
        val token = "yona_oat_" + UUID.randomUUID().toString().replace("-", "")
        authorizations.save(
            OAuthAuthorization(
                id = UUID.randomUUID().toString(), clientRowId = client.id, userId = user.id, grantType = "authorization_code", authorizedScopes = scopes, attributes = "{}",
                accessTokenHash = TokenHasher.sha256Hex(token), accessTokenIssuedDate = Date(), accessTokenExpiresDate = Date.from(expires), accessTokenScopes = scopes,
                accessTokenMetadata = "{\"metadata.token.invalidated\":$invalidated}", createdDate = Date()
            )
        )
        return token
    }

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `유효한 OAuth 토큰은 범위가 허용하는 외부 API를 쓸 수 있다`() {
        val u = user(); val token = grant(u, client(u))
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(token))).andExpect(status().isOk).andExpect(jsonPath("$.loginId").value(u.loginId))
    }

    @Test
    fun `범위 밖의 요청은 403이고 토큰 없는 요청과 모르는 토큰은 401이다`() {
        val u = user(); val token = grant(u, client(u), scopes = "read:user")
        mockMvc.perform(post("/-_-api/v1/user/projects").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden)
        mockMvc.perform(get("/-_-api/v1/user")).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer("yona_oat_" + "x".repeat(40)))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `만료되었거나 취소했거나 잠긴 사용자의 토큰은 401이다`() {
        val u = user(); val c = client(u)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(grant(u, c, expires = Instant.now().minusSeconds(5))))).andExpect(status().isUnauthorized)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(grant(u, c, invalidated = true)))).andExpect(status().isUnauthorized)
        val locked = user(); val lockedToken = grant(locked, client(locked))
        locked.state = UserState.LOCKED; users.save(locked)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(lockedToken))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `앱을 지우면 발급한 토큰은 곧바로 쓸 수 없다`() {
        val u = user(); val c = client(u); val token = grant(u, c)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(token))).andExpect(status().isOk)
        clientService.delete(u, c.id!!)
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", bearer(token))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `401에는 보호 자원 메타데이터 주소가 실린다`() {
        val result = mockMvc.perform(get("/-_-api/v1/user")).andExpect(status().isUnauthorized).andExpect(header().exists("WWW-Authenticate")).andReturn()
        val challenge = result.response.getHeader("WWW-Authenticate")!!
        assertTrue(challenge.startsWith("Bearer") && challenge.contains("resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/-_-api/v1\""), challenge)
    }

    @Test
    fun `보호 자원 메타데이터는 자원과 인가 서버와 지원 범위를 알린다`() {
        for (path in listOf("/.well-known/oauth-protected-resource/-_-api/v1", "/.well-known/oauth-protected-resource")) {
            mockMvc.perform(get(path)).andExpect(status().isOk)
                .andExpect(jsonPath("$.resource").value("http://localhost/-_-api/v1")).andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost"))
                .andExpect(jsonPath("$.bearer_methods_supported[0]").value("header"))
                .andExpect(jsonPath("$.scopes_supported").isArray)
        }
        val scopes = mockMvc.perform(get("/.well-known/oauth-protected-resource")).andReturn().response.contentAsString
        assertTrue(scopes.contains("read:user") && !scopes.contains("site_admin") && !scopes.contains("ssh_key"), "OAuth에 주지 않는 범위는 알리지 않는다")
    }

    @Test
    fun `OAuth 토큰은 git의 HTTP에 쓸 수 없고 비밀번호처럼 401로 거부한다`() {
        val u = user(); val p = projects.save(dev.yona.domain.project.Project(owner = u.loginId, name = "op${++n}", vcs = "GIT", projectScope = dev.yona.domain.enumeration.ProjectScope.PRIVATE))
        menuSettings.save(dev.yona.domain.project.ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(dev.yona.domain.project.ProjectUser(user = u, project = p, role = roles.findById(dev.yona.domain.enumeration.RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        val token = grant(u, client(u), scopes = "repo")
        val basic = "Basic " + Base64.getEncoder().encodeToString("${u.loginId}:$token".toByteArray())
        for (attempt in 1..7) {
            val git = mockMvc.perform(get("/${p.owner}/${p.name}/info/refs").param("service", "git-upload-pack").header("Authorization", basic)).andReturn()
            assertEquals(401, git.response.status, "시도 $attempt")
        }
        assertEquals(401, mockMvc.perform(get("/${p.owner}/${p.name}/info/refs").param("service", "git-upload-pack").header("Authorization", basic)).andReturn().response.status, "비밀번호처럼 거부할 뿐 실패로 세지 않아 막히지 않는다(429가 아니다)")
    }
}
