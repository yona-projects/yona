package dev.yona.sso

import com.nimbusds.jwt.SignedJWT
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Yona가 OIDC 제공자이면서(X-40) 사내 IdP로 로그인하는 사이트(X-16)인 조합을 검증한다. Woodpecker 같은 외부 앱이 Yona로 로그인을 요청하면 Yona가 로그인 화면으로 보내고,
 * 사용자가 사내 IdP 단추로 로그인한 뒤 원래의 승인 요청이 이어져 외부 앱이 인가 코드와 ID 토큰을 받는다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-sso-provider", "yona.oauth2.oidc.signing-key-file=src/test/resources/oauth2/oidc-test-key.pem"])
class SsoWithOidcProviderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var sso: SsoSettingsService

    private val idp = FakeOidcIdp()
    private val json = JsonMapper()
    private val redirect = "https://ci.example.com/authorize/callback"
    private val verifier = "w".repeat(64)
    private val stamp = System.nanoTime() % 1_000_000

    @AfterEach
    fun stop() { idp.close() }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")
    private fun query(location: String): Map<String, String> = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }
    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private fun configureSso() = sso.saveOidc(
        OidcSsoForm(true, "사내 계정", idp.issuer, idp.clientId, idp.clientSecret, "openid profile email", "email", "name", "", true, true, false, false, false, 60), null
    )

    private fun woodpecker(): dev.yona.domain.oauth2server.OAuthClient {
        val owner = users.save(User(loginId = "wp-$stamp", name = "관리자", email = "wp-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = "s", password = LegacyPasswordHasher.hash("pw", "s")))
        return (clientService.create(owner, "Woodpecker", "", "", redirect, false, setOf("openid", "profile", "email")) as ClientCreation.Created).client
    }

    /** 외부 앱이 보내는 승인 요청 주소이다. */
    private fun authorizeUri(client: dev.yona.domain.oauth2server.OAuthClient): java.net.URI {
        val params = linkedMapOf("response_type" to "code", "client_id" to client.clientId!!, "scope" to "openid profile email", "state" to "wp-state", "redirect_uri" to redirect,
            "code_challenge" to challenge(), "code_challenge_method" to "S256", "nonce" to "wp-nonce")
        return java.net.URI.create("/oauth2/authorize?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" })
    }

    @Test
    fun `외부 앱의 로그인 요청은 사내 IdP 단추로 로그인한 뒤 이어져 인가 코드와 ID 토큰을 받는다`() {
        configureSso(); val client = woodpecker(); val session = MockHttpSession()

        // 1. 로그인하지 않은 사용자의 승인 요청은 로그인 화면으로 간다
        val toLogin = mockMvc.perform(get(authorizeUri(client)).session(session)).andReturn().response.getHeader("Location")!!
        assertTrue(toLogin.startsWith("/users/loginform"), toLogin); val resume = query(toLogin)["redirectUrl"]!!
        assertEquals("/oauth2/resume", resume)

        // 2. 로그인 화면에는 사내 IdP 단추가 있고 돌아갈 곳이 이어 붙는다
        val page = Jsoup.parse(mockMvc.perform(get(toLogin).session(session)).andReturn().response.contentAsString)
        val button = page.selectFirst(".page.full a.sso-login-btn")!!; val href = button.attr("href")
        assertTrue(href.startsWith("/sso/oidc/login"), href)

        // 3. 사내 IdP 로그인(가짜 IdP)
        val first = mockMvc.perform(get(href).session(session)).andReturn().response.getHeader("Location")!!
        val authorizeUrl = if (first.startsWith(idp.issuer)) first else mockMvc.perform(get(first).session(session)).andReturn().response.getHeader("Location")!!
        val params = UriComponentsBuilder.fromUriString(authorizeUrl).build().queryParams
        fun decoded(name: String) = URLDecoder.decode(params.getFirst(name)!!, StandardCharsets.UTF_8)
        idp.nextIdToken = idp.idToken(decoded("nonce"), subject = "corp-sub-1", email = "kim-$stamp@corp.example", name = "김사원")
        val back = mockMvc.perform(get("/sso/oidc/callback/oidc").session(session).param("code", "c1").param("state", decoded("state"))).andReturn().response
        assertEquals(302, back.status); assertEquals("/oauth2/resume", back.getHeader("Location"))

        // 4. 보관해 둔 승인 요청이 이어진다(승인 화면이 나오면 승인한다)
        val resumed = mockMvc.perform(get("/oauth2/resume").session(session)).andReturn().response.getHeader("Location")!!
        assertTrue(resumed.startsWith("/oauth2/authorize"), resumed)
        // 보관한 주소는 이미 인코딩되어 있으므로 URI로 보내야 다시 인코딩되지 않는다
        var location = mockMvc.perform(get(java.net.URI.create(resumed)).session(session)).andReturn().response.getHeader("Location")!!
        if (location.contains("/oauth2/consent")) {
            location = mockMvc.perform(post("/oauth2/authorize").session(session).param("client_id", client.clientId!!).param("state", query(location)["state"]!!).param("scope", "openid", "profile", "email"))
                .andReturn().response.getHeader("Location")!!
        }
        assertTrue(location.startsWith("$redirect?"), location); assertEquals("wp-state", query(location)["state"])

        // 5. 외부 앱이 코드를 토큰으로 바꾼다. ID 토큰의 주체는 SSO로 로그인한 Yona 사용자이다
        val result = mockMvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", query(location)["code"]!!).param("redirect_uri", redirect)
            .param("code_verifier", verifier).param("client_id", client.clientId!!)).andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST") val tokens = json.readValue(result, Map::class.java) as Map<String, Any?>
        val claims = SignedJWT.parse(tokens["id_token"] as String).jwtClaimsSet
        val user = users.findByEmail("kim-$stamp@corp.example")!!
        assertEquals(user.id.toString(), claims.subject); assertEquals("wp-nonce", claims.getStringClaim("nonce")); assertNotNull(tokens["access_token"])
    }

    @Test
    fun `사내 IdP 로그인이 실패하면 외부 앱에 코드를 주지 않는다`() {
        configureSso(); val client = woodpecker(); val session = MockHttpSession()
        mockMvc.perform(get(authorizeUri(client)).session(session))
        val first = mockMvc.perform(get("/sso/oidc/login?returnTo=/oauth2/resume").session(session)).andReturn().response.getHeader("Location")!!
        val authorizeUrl = if (first.startsWith(idp.issuer)) first else mockMvc.perform(get(first).session(session)).andReturn().response.getHeader("Location")!!
        val params = UriComponentsBuilder.fromUriString(authorizeUrl).build().queryParams
        fun decoded(name: String) = URLDecoder.decode(params.getFirst(name)!!, StandardCharsets.UTF_8)
        idp.nextIdToken = idp.idToken(decoded("nonce"), subject = "corp-sub-2", email = "unv-$stamp@corp.example", emailVerified = false)
        val back = mockMvc.perform(get("/sso/oidc/callback/oidc").session(session).param("code", "c1").param("state", decoded("state"))).andReturn().response
        assertTrue(back.getHeader("Location")!!.startsWith("/users/loginform"))
        val resumed = mockMvc.perform(get("/oauth2/resume").session(session)).andReturn().response.getHeader("Location")!!
        assertTrue(resumed.startsWith("/users/loginform"), "로그인하지 않았으면 승인 요청이 이어지지 않는다: $resumed")
    }
}
