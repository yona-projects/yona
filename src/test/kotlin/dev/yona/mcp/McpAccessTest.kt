package dev.yona.mcp

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertTrue

/**
 * `/mcp`의 접근 규칙(X-6 단계 1, `decisions.md` D-72)을 검증한다: OAuth 액세스 토큰만 받고(개인용 토큰과 세션은 받지 않는다), 토큰이 없으면 401과 보호 자원 메타데이터 주소를 알리고,
 * 읽기 범위(`read:repo`)가 없으면 403이며, `Origin`과 프로토콜 버전을 확인하고, 관리자가 끄면 404이다.
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-access"])
class McpAccessTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository
    @Autowired private lateinit var apiTokens: ApiTokenService
    @Autowired private lateinit var settings: SiteSettingService

    private var n = 0

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mc$k-${System.nanoTime()}", name = "mc$k", email = "mc$k@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun client(owner: User): OAuthClient = (clientService.create(owner, "MCP 앱", "", "", "https://app.example.com/cb", false, setOf("read:user", "read:repo", "repo")) as ClientCreation.Created).client

    private fun grant(user: User, scopes: String): String {
        val token = "yona_oat_" + UUID.randomUUID().toString().replace("-", "")
        authorizations.save(
            OAuthAuthorization(
                id = UUID.randomUUID().toString(), clientRowId = client(user).id, userId = user.id, grantType = "authorization_code", authorizedScopes = scopes, attributes = "{\"resource\":\"http://localhost/mcp\"}",
                accessTokenHash = TokenHasher.sha256Hex(token), accessTokenIssuedDate = Date(), accessTokenExpiresDate = Date.from(Instant.now().plusSeconds(3600)), accessTokenScopes = scopes,
                accessTokenMetadata = "{\"metadata.token.invalidated\":false}", createdDate = Date()
            )
        )
        return token
    }

    private val initialize = """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}"""

    private fun rpc(token: String?, origin: String? = null, version: String? = null, body: String = initialize): ResultActions =
        mockMvc.perform(
            post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(body).apply {
                token?.let { header("Authorization", "Bearer $it") }
                origin?.let { header("Origin", it) }
                version?.let { header("MCP-Protocol-Version", it) }
            }
        )

    @Test
    fun `토큰이 없으면 401이고 보호 자원 메타데이터 주소를 알린다`() {
        rpc(null).andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", "Bearer resource_metadata=\"http://localhost/.well-known/oauth-protected-resource/mcp\""))
    }

    @Test
    fun `모르는 토큰도 401이다`() {
        rpc("yona_oat_" + "x".repeat(40)).andExpect(status().isUnauthorized).andExpect(header().exists("WWW-Authenticate"))
    }

    @Test
    fun `읽기 범위가 있는 OAuth 토큰이면 초기화에 답한다`() {
        val u = user()
        rpc(grant(u, "read:repo")).andExpect(status().isOk).andExpect(jsonPath("$.result.serverInfo.name").value("yona-mcp-server"))
        rpc(grant(u, "repo")).andExpect(status().isOk)
    }

    @Test
    fun `읽기 범위가 없는 토큰은 403이고 필요한 범위를 알린다`() {
        rpc(grant(user(), "read:user")).andExpect(status().isForbidden)
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("error=\"insufficient_scope\""), org.hamcrest.Matchers.containsString("scope=\"read:repo\""))))
    }

    @Test
    fun `개인용 토큰은 받지 않는다`() {
        val u = user()
        val pat = (apiTokens.create(u, "mcp", setOf("repo"), null) as TokenCreation.Created).value
        rpc(pat).andExpect(status().isUnauthorized)
    }

    @Test
    fun `세션 쿠키로는 인증하지 않는다`() {
        val u = user()
        val auth = dev.yona.security.YonaAuthentication.of(u)
        val session = org.springframework.mock.web.MockHttpSession().also {
            it.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, org.springframework.security.core.context.SecurityContextImpl(auth))
        }
        mockMvc.perform(post("/mcp").session(session).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).content(initialize)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `Origin이 있으면 같은 출처만 받고 없으면 받는다`() {
        val token = grant(user(), "read:repo")
        rpc(token, origin = "https://evil.example.com").andExpect(status().isForbidden)
        rpc(token, origin = "http://localhost").andExpect(status().isOk)
        rpc(token).andExpect(status().isOk)
    }

    @Test
    fun `프로토콜 버전 머리글이 모르는 값이면 400이고 없거나 알면 받는다`() {
        val token = grant(user(), "read:repo")
        rpc(token, version = "1999-01-01").andExpect(status().isBadRequest)
        rpc(token, version = "2025-06-18").andExpect(status().isOk)
        rpc(token, version = "2025-03-26").andExpect(status().isOk)
        rpc(token).andExpect(status().isOk)
    }

    @Test
    fun `도구 목록 요청에 일반 JSON으로 답하고 GET은 SSE를 제공하지 않아 405이다`() {
        val token = grant(user(), "read:repo")
        rpc(token, body = """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.tools").isArray)
        mockMvc.perform(get("/mcp").header("Authorization", "Bearer $token").accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `관리자가 끄면 404이다`() {
        val token = grant(user(), "read:repo")
        settings.setMcpEnabled(false)
        rpc(token).andExpect(status().isNotFound)
        rpc(null).andExpect(status().isNotFound)
        settings.setMcpEnabled(true)
        rpc(token).andExpect(status().isOk)
    }

    @Test
    fun `설정의 기본값은 서버와 쓰기 도구 모두 켬이다`() {
        assertTrue(settings.mcpEnabled())
        assertTrue(settings.mcpWriteEnabled())
        settings.setMcpWriteEnabled(false)
        assertTrue(!settings.mcpWriteEnabled())
    }

    @Test
    fun `보호 자원 메타데이터는 mcp 자원과 인가 서버를 알린다`() {
        mockMvc.perform(get("/.well-known/oauth-protected-resource/mcp")).andExpect(status().isOk)
            .andExpect(jsonPath("$.resource").value("http://localhost/mcp")).andExpect(jsonPath("$.authorization_servers[0]").value("http://localhost"))
            .andExpect(jsonPath("$.scopes_supported").isArray)
    }
}
