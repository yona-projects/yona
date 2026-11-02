package dev.yona.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 토큰의 대상(RFC 8707, X-6 단계 4, `decisions.md` D-72 6번)을 검증한다. 승인 요청의 `resource`는 이 서버의 보호 자원(`<출처>/mcp`, `<출처>/-_-api/v1`) 가운데 하나여야 하고,
 * 토큰은 그 자원에서만 쓸 수 있다. `resource` 없이 발급된 토큰은 외부 API에서만 쓸 수 있고 `/mcp`에서는 쓸 수 없다.
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-oauth-resource-indicator"])
class OAuthResourceIndicatorTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService

    private val json = JsonMapper()
    private var n = 0
    private val redirect = "https://app.example.com/callback"
    private val verifier = "b".repeat(64)
    private val mcp = "http://localhost/mcp"
    private val api = "http://localhost/-_-api/v1"

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ri$k-${System.nanoTime() % 100000}", name = "ri$k", email = "ri$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private fun query(location: String): Map<String, String> = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }

    private class Session(val http: MockHttpSession, val client: OAuthClient)

    private fun session(): Session {
        val u = user()
        val http = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }
        val client = (clientService.create(u, "자원 앱", "", "", redirect, false, setOf("read:user", "read:repo", "repo")) as ClientCreation.Created).client
        return Session(http, client)
    }

    /** 승인 요청을 보내고 최종 리디렉션 주소를 돌려준다(승인 화면이 끼면 승인한다). */
    private fun authorize(s: Session, vararg resources: String): String {
        val request = get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", s.client.clientId!!).queryParam("scope", "read:repo").queryParam("state", "st")
            .queryParam("redirect_uri", redirect).queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256").session(s.http)
        resources.forEach { request.queryParam("resource", it) }
        var location = mockMvc.perform(request).andReturn().response.getHeader("Location")!!
        if (location.contains("/oauth2/consent")) {
            location = mockMvc.perform(post("/oauth2/authorize").session(s.http).param("client_id", s.client.clientId!!).param("state", query(location)["state"]!!).param("scope", "read:repo"))
                .andReturn().response.getHeader("Location")!!
        }
        return location
    }

    private fun tokenFor(vararg resources: String): String {
        val s = session()
        val location = authorize(s, *resources)
        val code = query(location)["code"] ?: error("코드가 없다: $location")
        val result = mockMvc.perform(
            post("/oauth2/token").param("grant_type", "authorization_code").param("code", code).param("redirect_uri", redirect).param("code_verifier", verifier).param("client_id", s.client.clientId!!)
                .apply { resources.forEach { param("resource", it) } }
        ).andReturn()
        return json.readTree(result.response.contentAsString).get("access_token").asString()
    }

    private fun mcpStatus(token: String) = mockMvc.perform(
        post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer $token")
            .content("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
    ).andReturn().response.status

    private fun apiStatus(token: String) = mockMvc.perform(get("/-_-api/v1/user").header("Authorization", "Bearer $token")).andReturn().response.status

    @Test
    fun `mcp 자원으로 발급된 토큰은 mcp에서만 쓸 수 있다`() {
        val token = tokenFor(mcp)
        assertEquals(200, mcpStatus(token))
        assertEquals(401, apiStatus(token))
    }

    @Test
    fun `외부 API 자원으로 발급된 토큰은 외부 API에서만 쓸 수 있다`() {
        val token = tokenFor(api)
        assertEquals(200, apiStatus(token))
        assertEquals(401, mcpStatus(token))
    }

    @Test
    fun `자원 없이 발급된 토큰은 외부 API에서만 쓸 수 있다`() {
        val token = tokenFor()
        assertEquals(200, apiStatus(token))
        assertEquals(401, mcpStatus(token))
    }

    @Test
    fun `두 자원을 함께 요청하면 둘 다 쓸 수 있다`() {
        val token = tokenFor(mcp, api)
        assertEquals(200, apiStatus(token))
        assertEquals(200, mcpStatus(token))
    }

    @Test
    fun `이 서버의 보호 자원이 아닌 자원을 요청하면 invalid_target 오류로 돌려보낸다`() {
        val location = authorize(session(), "https://evil.example.com/mcp")
        assertTrue(location.startsWith("$redirect?"), location)
        assertEquals("invalid_target", query(location)["error"])
        assertEquals("st", query(location)["state"])
        val fragment = authorize(session(), "$mcp#frag")
        assertEquals("invalid_target", query(fragment)["error"])
    }
}
