package dev.yona.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

/**
 * OAuth2 인가 서버의 인가 코드 + PKCE 흐름(X-1 2단계, `decisions.md` D-71)을 처음부터 끝까지 검증한다: 메타데이터, 로그인 이동과 `/oauth2/resume`, 승인 요청과 승인 화면 이동, 승인,
 * 코드를 토큰으로 바꾸기(`yona_oat_`, `yona_ort_`), 갱신 토큰 회전, 기밀 앱의 비밀 값 인증, 취소, 오류 사례.
 */
@AutoConfigureMockMvc
@Transactional
class OAuth2AuthorizationFlowTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository

    private val json = JsonMapper()
    private var n = 0
    private val redirect = "https://app.example.com/callback"

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "of$k-${System.nanoTime()}", name = "of$k", email = "of$k@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun app(owner: User, confidential: Boolean = false, scopes: Set<String> = setOf("read:user", "repo")): Pair<OAuthClient, String?> {
        val created = clientService.create(owner, "테스트 앱", "", "", redirect, confidential, scopes) as ClientCreation.Created
        return created.client to created.secret
    }

    private fun challenge(verifier: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private val verifier = "a".repeat(64)

    private fun authorize(session: MockHttpSession?, clientId: String, scope: String = "read:user", redirectUri: String? = redirect, challengeValue: String? = challenge(verifier), method: String? = "S256", state: String = "xyz"): MvcResult {
        val request = get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId).queryParam("scope", scope).queryParam("state", state)
        redirectUri?.let { request.queryParam("redirect_uri", it) }; challengeValue?.let { request.queryParam("code_challenge", it) }; method?.let { request.queryParam("code_challenge_method", it) }
        session?.let { request.session(it) }
        return mockMvc.perform(request).andReturn()
    }

    private fun query(location: String): Map<String, String> = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }

    /** 승인까지 마쳐 인가 코드를 돌려준다. 이미 허용한 범위이면 승인 화면 없이 곧바로 코드가 나온다. */
    private fun approve(session: MockHttpSession, client: OAuthClient, scope: String = "read:user"): String {
        val first = authorize(session, client.clientId!!, scope)
        assertEquals(302, first.response.status)
        var location = first.response.getHeader("Location")!!
        if (location.contains("/oauth2/consent")) {
            val state = query(location)["state"]!!
            val done = mockMvc.perform(post("/oauth2/authorize").session(session).param("client_id", client.clientId!!).param("state", state).param("scope", scope)).andReturn()
            assertEquals(302, done.response.status, done.response.contentAsString)
            location = done.response.getHeader("Location")!!
        }
        assertTrue(location.startsWith("$redirect?"), location)
        assertEquals("xyz", query(location)["state"])
        return query(location)["code"]!!
    }

    private fun exchange(client: OAuthClient, code: String, secret: String? = null, verifierValue: String = verifier, redirectUri: String = redirect): MvcResult {
        val request = post("/oauth2/token").param("grant_type", "authorization_code").param("code", code).param("redirect_uri", redirectUri).param("code_verifier", verifierValue)
        if (secret == null) request.param("client_id", client.clientId!!) else request.with { it.addHeader("Authorization", "Basic " + Base64.getEncoder().encodeToString("${client.clientId}:$secret".toByteArray())); it }
        return mockMvc.perform(request).andReturn()
    }

    private fun body(result: MvcResult): Map<String, Any?> = json.readValue(result.response.contentAsString, object : tools.jackson.core.type.TypeReference<Map<String, Any?>>() {})

    @Test
    fun `메타데이터가 인가 서버의 주소와 PKCE S256과 지원 방식을 알린다`() {
        mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk)
            .andExpect(jsonPath("$.authorization_endpoint").value(org.hamcrest.Matchers.endsWith("/oauth2/authorize")))
            .andExpect(jsonPath("$.token_endpoint").value(org.hamcrest.Matchers.endsWith("/oauth2/token")))
            .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
            .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
            .andExpect(jsonPath("$.registration_endpoint").doesNotExist())
    }

    @Test
    fun `로그인하지 않고 승인 요청을 하면 로그인 화면으로 가고 로그인 뒤 resume이 요청으로 돌려보낸다`() {
        val owner = user(); val (client) = app(owner)
        val anonymous = MockHttpSession()
        val first = authorize(anonymous, client.clientId!!)
        assertEquals(302, first.response.status)
        assertTrue(first.response.getHeader("Location")!!.endsWith("/users/loginform?redirectUrl=/oauth2/resume"), first.response.getHeader("Location"))
        mockMvc.perform(post("/users/login").session(anonymous).param("loginIdOrEmail", owner.loginId!!).param("password", "1234"))
        val resumed = mockMvc.perform(get("/oauth2/resume").session(anonymous)).andExpect(status().is3xxRedirection).andReturn()
        assertTrue(resumed.response.getHeader("Location")!!.startsWith("/oauth2/authorize?"), resumed.response.getHeader("Location"))
        mockMvc.perform(get("/oauth2/resume").session(anonymous)).andExpect(header().string("Location", "/")) // 한 번만 쓴다
    }

    @Test
    fun `처음 승인 요청은 승인 화면으로 보내고 승인하면 코드가 나오고 코드를 토큰으로 바꾼다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val code = approve(s, client)
        val result = exchange(client, code)
        assertEquals(200, result.response.status, result.response.contentAsString)
        val token = body(result)
        assertTrue((token["access_token"] as String).startsWith("yona_oat_") && (token["refresh_token"] as String).startsWith("yona_ort_"))
        assertEquals("Bearer", token["token_type"]); assertEquals("read:user", token["scope"])
        assertEquals(3600, (token["expires_in"] as Number).toLong().let { if (it in 3590..3600) 3600 else it })
        val stored = authorizations.findAll().single()
        assertTrue(stored.accessTokenHash!!.length == 64 && stored.accessTokenHash != token["access_token"] && stored.refreshTokenHash != token["refresh_token"], "토큰 값이 아니라 해시만 저장한다")
        assertEquals(owner.id, stored.userId)
    }

    @Test
    fun `이미 허용한 범위만 다시 요청하면 승인 화면 없이 곧바로 코드가 나온다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        approve(s, client)
        val again = authorize(s, client.clientId!!)
        assertEquals(302, again.response.status)
        assertTrue(again.response.getHeader("Location")!!.startsWith("$redirect?code="), again.response.getHeader("Location"))
    }

    @Test
    fun `인가 코드는 한 번만 쓸 수 있고 틀린 PKCE 검증값과 다른 리디렉션 주소는 거부한다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val bad = approve(s, client)
        assertEquals(400, exchange(client, bad, verifierValue = "b".repeat(64)).response.status)
        val good = approve(s, client)
        assertEquals(400, exchange(client, good, redirectUri = "https://evil.example/cb").response.status)
        val third = approve(s, client)
        assertEquals(200, exchange(client, third).response.status)
        assertEquals(400, exchange(client, third).response.status, "같은 코드를 다시 쓰면 거부한다")
    }

    @Test
    fun `PKCE가 없거나 S256이 아니면 거부한다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val missing = authorize(s, client.clientId!!, challengeValue = null, method = null)
        assertTrue(missing.response.status == 302 && missing.response.getHeader("Location")!!.contains("error=invalid_request"), missing.response.getHeader("Location"))
        val plain = authorize(s, client.clientId!!, method = "plain")
        assertTrue(plain.response.status == 302 && plain.response.getHeader("Location")!!.contains("error="), plain.response.getHeader("Location"))
    }

    @Test
    fun `등록하지 않은 리디렉션 주소와 모르는 앱은 사용자를 이동시키지 않고 오류를 낸다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val badRedirect = authorize(s, client.clientId!!, redirectUri = "https://evil.example/cb")
        assertEquals(400, badRedirect.response.status)
        val unknown = authorize(s, "yona_app_nope")
        assertEquals(400, unknown.response.status)
    }

    @Test
    fun `앱의 허용 범위 밖의 범위를 요청하면 오류이다`() {
        val owner = user(); val (client) = app(owner, scopes = setOf("read:user")); val s = login(owner)
        val result = authorize(s, client.clientId!!, scope = "repo")
        assertTrue(result.response.status == 302 && result.response.getHeader("Location")!!.contains("error=invalid_scope"), result.response.getHeader("Location"))
    }

    @Test
    fun `루프백 리디렉션은 포트가 달라도 통과한다`() {
        val owner = user(); val created = clientService.create(owner, "로컬 앱", "", "", "http://localhost/callback", false, setOf("read:user")) as ClientCreation.Created
        val s = login(owner)
        val result = authorize(s, created.client.clientId!!, redirectUri = "http://127.0.0.1:51234/callback")
        assertEquals(302, result.response.status)
        assertTrue(result.response.getHeader("Location")!!.contains("/oauth2/consent"), result.response.getHeader("Location"))
    }

    @Test
    fun `갱신 토큰으로 새 토큰을 받고 이전 갱신 토큰은 다시 쓸 수 없다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val first = body(exchange(client, approve(s, client)))
        fun refresh(value: String) = mockMvc.perform(post("/oauth2/token").param("grant_type", "refresh_token").param("refresh_token", value).param("client_id", client.clientId!!)).andReturn()
        val second = refresh(first["refresh_token"] as String)
        assertEquals(200, second.response.status, second.response.contentAsString)
        val renewed = body(second)
        assertTrue((renewed["refresh_token"] as String) != first["refresh_token"] && (renewed["access_token"] as String).startsWith("yona_oat_"))
        assertEquals(400, refresh(first["refresh_token"] as String).response.status, "회전한 이전 갱신 토큰")
    }

    @Test
    fun `기밀 앱은 비밀 값으로 인증하고 틀린 비밀 값은 거부한다`() {
        val owner = user(); val (client, secret) = app(owner, confidential = true); val s = login(owner)
        val code = approve(s, client)
        assertEquals(401, exchange(client, code, secret = "yona_cs_wrong").response.status)
        val result = exchange(client, approve(s, client), secret = secret)
        assertEquals(200, result.response.status, result.response.contentAsString)
    }

    @Test
    fun `토큰을 취소하면 더 쓸 수 없다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val token = body(exchange(client, approve(s, client)))
        mockMvc.perform(post("/oauth2/revoke").param("token", token["access_token"] as String).param("token_type_hint", "access_token").param("client_id", client.clientId!!)).andExpect(status().isOk)
        val stored = authorizations.findAll().single()
        assertTrue(stored.accessTokenMetadata!!.contains("\"metadata.token.invalidated\":true"), stored.accessTokenMetadata)
    }

    @Test
    fun `앱을 지우면 그 앱의 인가와 승인도 지워지고 토큰은 더 쓸 수 없다`() {
        val owner = user(); val (client) = app(owner); val s = login(owner)
        val token = body(exchange(client, approve(s, client)))
        assertEquals(1, authorizations.count())
        assertTrue(clientService.delete(owner, client.id!!))
        assertEquals(0, authorizations.count())
        val refresh = mockMvc.perform(post("/oauth2/token").param("grant_type", "refresh_token").param("refresh_token", token["refresh_token"] as String).param("client_id", client.clientId!!)).andReturn()
        assertEquals(401, refresh.response.status, "지운 앱은 인증하지 못한다")
    }
}
