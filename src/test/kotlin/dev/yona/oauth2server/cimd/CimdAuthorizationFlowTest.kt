package dev.yona.oauth2server.cimd

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

/**
 * CIMD 클라이언트의 인가 코드 + PKCE 흐름(X-1 5단계, `decisions.md` D-71)을 처음부터 끝까지 검증한다: 메타데이터의 지원 표시, 문서에서 가져온 앱으로 승인과 토큰 교환(루프백 포트 무시),
 * 승인 화면의 문서 호스트 안내, 문서의 범위 밖 요청 거부, 설정으로 끄면 거부.
 */
@Import(FakeCimdConfig::class)
@AutoConfigureMockMvc
@Transactional
class CimdAuthorizationFlowTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var transport: FakeCimdTransport
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var limiter: CimdRateLimiter

    private val clientUrl = "https://claude.example/oauth/client.json"
    private val verifier = "c".repeat(64)
    private var n = 0

    @BeforeEach
    fun prepare() {
        transport.reset(); limiter.reset(); settings.setOauthCimdEnabled(true)
        transport.documents[clientUrl] = CimdResponse(
            """{"client_id":"$clientUrl","client_name":"Claude Code","client_uri":"https://claude.example","redirect_uris":["http://localhost/callback","http://127.0.0.1/callback"],"token_endpoint_auth_method":"none","scope":"read:user repo"}""", 600
        )
    }

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "cf$k-${System.nanoTime()}", name = "cf$k", email = "cf$k@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private fun authorize(session: MockHttpSession, scope: String = "read:user", redirect: String = "http://127.0.0.1:51234/callback", clientId: String = clientUrl): MvcResult =
        mockMvc.perform(
            get("/oauth2/authorize").session(session).queryParam("response_type", "code").queryParam("client_id", clientId).queryParam("scope", scope).queryParam("state", "xyz")
                .queryParam("redirect_uri", redirect).queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")
        ).andReturn()

    private fun query(location: String) = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }

    @Test
    fun `메타데이터가 CIMD 지원을 알리고 설정으로 끄면 거짓이 된다`() {
        mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk).andExpect(jsonPath("$.client_id_metadata_document_supported").value(true))
        settings.setOauthCimdEnabled(false)
        mockMvc.perform(get("/.well-known/oauth-authorization-server")).andExpect(status().isOk).andExpect(jsonPath("$.client_id_metadata_document_supported").value(false))
    }

    @Test
    fun `문서에서 가져온 앱으로 승인하고 토큰을 받는다`() {
        val s = login(user())
        val first = authorize(s)
        assertEquals(302, first.response.status, first.response.errorMessage)
        val consent = first.response.getHeader("Location")!!
        assertTrue(consent.contains("/oauth2/consent"), consent)
        val page = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(consent.substring(consent.indexOf("/oauth2/consent")))).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(page.text().contains("Claude Code"), "문서의 이름")
        assertTrue(page.selectFirst("#cimd-note")!!.text().contains("claude.example"), "문서를 게시한 호스트")
        assertNotNull(page.selectFirst("#loopback-warning"), "localhost로 돌아오는 앱")
        val done = mockMvc.perform(post("/oauth2/authorize").session(s).param("client_id", clientUrl).param("state", query(consent)["state"]!!).param("scope", "read:user")).andReturn()
        val code = query(done.response.getHeader("Location")!!)["code"]!!
        assertTrue(done.response.getHeader("Location")!!.startsWith("http://127.0.0.1:51234/callback?"), "요청한 포트로 돌아간다")
        val token = mockMvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code).param("redirect_uri", "http://127.0.0.1:51234/callback").param("client_id", clientUrl).param("code_verifier", verifier)).andReturn()
        assertEquals(200, token.response.status, token.response.contentAsString)
        assertTrue(token.response.contentAsString.contains("yona_oat_"))
    }

    @Test
    fun `문서에 없는 범위를 요청하면 오류이다`() {
        val s = login(user())
        val result = authorize(s, scope = "read:org")
        assertTrue(result.response.status == 302 && result.response.getHeader("Location")!!.contains("error=invalid_scope"), result.response.getHeader("Location"))
    }

    @Test
    fun `문서에 없는 리디렉션 주소와 설정으로 끈 경우는 거부한다`() {
        val s = login(user())
        assertEquals(400, authorize(s, redirect = "https://evil.example/cb").response.status)
        settings.setOauthCimdEnabled(false)
        assertEquals(400, authorize(s).response.status, "끄면 CIMD 앱은 없는 앱이다")
    }

    @Test
    fun `가져오지 못하는 문서의 앱은 없는 앱이다`() {
        val s = login(user())
        assertEquals(400, authorize(s, clientId = "https://nowhere.example/c.json").response.status)
    }
}
