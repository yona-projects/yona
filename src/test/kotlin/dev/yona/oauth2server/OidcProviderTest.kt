package dev.yona.oauth2server

import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.SignedJWT
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
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OIDC 제공자("Sign in with Yona", X-40, `decisions.md` D-77)를 검증한다: 발견 문서, 공개키 목록(JWKS), 승인 코드 흐름에서 받는 ID 토큰(서명, 클레임, nonce, 유효 시간),
 * 범위에 따른 클레임, `/oauth2/userinfo`. 서명 키는 운영자가 정해 준다(`yona.oauth2.oidc.signing-key-file`).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-oidc", "yona.oauth2.oidc.signing-key-file=src/test/resources/oauth2/oidc-test-key.pem"])
class OidcProviderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService

    private val json = JsonMapper()
    private var n = 0
    private val redirect = "https://app.example.com/callback"
    private val verifier = "c".repeat(64)

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "oi$k-${System.nanoTime() % 100000}", name = "오아이디$k", email = "oi$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
    private fun query(location: String): Map<String, String> = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }

    private class Login(val user: User, val session: MockHttpSession, val client: OAuthClient)

    private fun login(scopes: Set<String> = setOf("openid", "profile", "email", "read:user")): Login {
        val u = user()
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }
        val client = (clientService.create(u, "OIDC 앱", "", "", redirect, false, scopes) as ClientCreation.Created).client
        return Login(u, session, client)
    }

    /** 승인 요청부터 토큰 교환까지 마치고 토큰 응답을 돌려준다. */
    private fun tokens(l: Login, scope: String, nonce: String? = "n-0S6_WzA2Mj"): Map<String, Any?> {
        // 공백이 든 범위는 %20으로 인코딩한 주소 그대로 보내야 한다(MockMvc의 queryParam은 값을 다시 인코딩한다)
        fun enc(v: String) = java.net.URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")
        val params = linkedMapOf("response_type" to "code", "client_id" to l.client.clientId!!, "scope" to scope, "state" to "st", "redirect_uri" to redirect, "code_challenge" to challenge(), "code_challenge_method" to "S256")
        nonce?.let { params["nonce"] = it }
        val request = get(java.net.URI.create("/oauth2/authorize?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" })).session(l.session)
        var location = mockMvc.perform(request).andReturn().response.getHeader("Location")!!
        if (location.contains("/oauth2/consent")) {
            location = mockMvc.perform(post("/oauth2/authorize").session(l.session).param("client_id", l.client.clientId!!).param("state", query(location)["state"]!!).param("scope", *scope.split(" ").toTypedArray())).andReturn().response.getHeader("Location")!!
        }
        val code = query(location)["code"] ?: error("코드가 없다: $location")
        val result = mockMvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code).param("redirect_uri", redirect).param("code_verifier", verifier).param("client_id", l.client.clientId!!)).andReturn()
        @Suppress("UNCHECKED_CAST")
        return json.readValue(result.response.contentAsString, Map::class.java) as Map<String, Any?>
    }

    private fun publicKey(): RSAKey {
        val body = mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk).andReturn().response.contentAsString
        return JWKSet.parse(body).keys.single() as RSAKey
    }

    @Test
    fun `발견 문서가 발급자와 주소와 지원 방식을 알린다`() {
        mockMvc.perform(get("/.well-known/openid-configuration")).andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("http://localhost"))
            .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost/oauth2/authorize"))
            .andExpect(jsonPath("$.token_endpoint").value("http://localhost/oauth2/token"))
            .andExpect(jsonPath("$.userinfo_endpoint").value("http://localhost/oauth2/userinfo"))
            .andExpect(jsonPath("$.jwks_uri").value("http://localhost/oauth2/jwks"))
            .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
            .andExpect(jsonPath("$.subject_types_supported[0]").value("public"))
            .andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("RS256"))
            .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
            .andExpect(jsonPath("$.scopes_supported", org.hamcrest.Matchers.hasItems("openid", "profile", "email")))
            .andExpect(jsonPath("$.claims_supported", org.hamcrest.Matchers.hasItems("sub", "name", "preferred_username", "picture", "updated_at", "email")))
            .andExpect(jsonPath("$.claims_supported", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("email_verified"))))
    }

    @Test
    fun `공개키 목록은 서명용 RSA 공개키 하나이고 개인키 값은 없다`() {
        val body = mockMvc.perform(get("/oauth2/jwks")).andExpect(status().isOk).andReturn().response.contentAsString
        val keys = JWKSet.parse(body).keys
        assertEquals(1, keys.size)
        val key = keys.single() as RSAKey
        assertNotNull(key.keyID); assertEquals("sig", key.keyUse.value); assertFalse(key.isPrivate)
        assertFalse(body.contains("\"d\""), "개인키 성분이 공개키 목록에 나오면 안 된다")
    }

    @Test
    fun `승인 코드 흐름은 서명된 ID 토큰을 주고 클레임이 범위를 따른다`() {
        val l = login()
        val response = tokens(l, "openid profile email")
        val idToken = response["id_token"] as String
        val jwt = SignedJWT.parse(idToken)
        assertTrue(jwt.verify(RSASSAVerifier(publicKey())), "서명이 공개키로 검증되어야 한다")
        assertEquals("RS256", jwt.header.algorithm.name); assertEquals(publicKey().keyID, jwt.header.keyID)
        val claims = jwt.jwtClaimsSet
        assertEquals("http://localhost", claims.issuer)
        assertEquals(l.user.id.toString(), claims.subject)
        assertEquals(listOf(l.client.clientId), claims.audience)
        assertEquals("n-0S6_WzA2Mj", claims.getStringClaim("nonce"))
        assertEquals(l.user.name, claims.getStringClaim("name")); assertEquals(l.user.loginId, claims.getStringClaim("preferred_username")); assertEquals(l.user.email, claims.getStringClaim("email"))
        assertNull(claims.getClaim("email_verified"), "인증 표시가 없으므로 보내지 않는다")
        val lifetime = claims.expirationTime.time - claims.issueTime.time
        assertTrue(lifetime in 1..600_000, "ID 토큰은 10분 안에 만료된다: $lifetime")
        assertTrue((response["access_token"] as String).startsWith("yona_oat_"), "액세스 토큰은 불투명 토큰이다")
    }

    @Test
    fun `openid 범위만 요청하면 ID 토큰에 sub만 있고 profile과 email 클레임이 없다`() {
        val l = login()
        val claims = SignedJWT.parse(tokens(l, "openid")["id_token"] as String).jwtClaimsSet
        assertEquals(l.user.id.toString(), claims.subject)
        for (name in listOf("name", "preferred_username", "picture", "email", "updated_at")) assertNull(claims.getClaim(name), name)
    }

    @Test
    fun `openid 범위가 없으면 ID 토큰을 주지 않는다`() {
        val response = tokens(login(), "read:user")
        assertFalse(response.containsKey("id_token"))
    }

    @Test
    fun `nonce를 보내지 않으면 ID 토큰에도 nonce가 없다`() {
        val claims = SignedJWT.parse(tokens(login(), "openid", nonce = null)["id_token"] as String).jwtClaimsSet
        assertNull(claims.getClaim("nonce"))
    }

    @Test
    fun `사용자 정보는 범위에 따라 클레임을 돌려주고 GET과 POST를 모두 받는다`() {
        val l = login()
        val access = tokens(l, "openid profile email")["access_token"] as String
        for (request in listOf(get("/oauth2/userinfo"), post("/oauth2/userinfo"))) {
            mockMvc.perform(request.header("Authorization", "Bearer $access")).andExpect(status().isOk)
                .andExpect(jsonPath("$.sub").value(l.user.id.toString())).andExpect(jsonPath("$.name").value(l.user.name))
                .andExpect(jsonPath("$.preferred_username").value(l.user.loginId)).andExpect(jsonPath("$.email").value(l.user.email))
                .andExpect(jsonPath("$.email_verified").doesNotExist()).andExpect(jsonPath("$.picture").isString)
        }
        val onlyOpenId = tokens(login(), "openid")["access_token"] as String
        mockMvc.perform(get("/oauth2/userinfo").header("Authorization", "Bearer $onlyOpenId")).andExpect(status().isOk)
            .andExpect(jsonPath("$.sub").exists()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.name").doesNotExist())
        val profileOnly = tokens(login(), "openid profile")["access_token"] as String
        mockMvc.perform(get("/oauth2/userinfo").header("Authorization", "Bearer $profileOnly")).andExpect(jsonPath("$.name").exists()).andExpect(jsonPath("$.email").doesNotExist())
    }

    @Test
    fun `사용자 정보는 토큰이 없거나 틀리면 401이고 openid 범위가 없으면 403이다`() {
        mockMvc.perform(get("/oauth2/userinfo")).andExpect(status().isUnauthorized).andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")))
        mockMvc.perform(get("/oauth2/userinfo").header("Authorization", "Bearer yona_oat_" + "x".repeat(40))).andExpect(status().isUnauthorized)
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("invalid_token")))
        val noOpenId = tokens(login(), "read:user")["access_token"] as String
        mockMvc.perform(get("/oauth2/userinfo").header("Authorization", "Bearer $noOpenId")).andExpect(status().isForbidden)
            .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("insufficient_scope")))
    }

    @Test
    fun `OIDC 범위는 앱을 만들 때 고를 수 있다`() {
        val u = user()
        val created = clientService.create(u, "범위 시험", "", "", redirect, false, setOf("openid", "profile", "email"))
        assertTrue(created is ClientCreation.Created, "OIDC가 켜져 있으면 openid, profile, email만으로 앱을 만들 수 있다: $created")
    }

    @Test
    fun `앱 만들기 화면에 OIDC 범위가 보이고 승인 화면에 설명이 나온다`() {
        val l = login(setOf("openid", "profile", "email"))
        val form = mockMvc.perform(get("/user/editform/oauth-apps/new").session(l.session)).andExpect(status().isOk).andReturn().response.contentAsString
        for (scope in listOf("openid", "profile", "email")) assertTrue(form.contains("id=\"oauth-scope-$scope\""), "앱 만들기 화면에 $scope 범위 선택이 있어야 한다")
        val location = mockMvc.perform(
            get(java.net.URI.create("/oauth2/authorize?response_type=code&client_id=${l.client.clientId}&scope=openid%20profile%20email&state=st&redirect_uri=${java.net.URLEncoder.encode(redirect, StandardCharsets.UTF_8)}&code_challenge=${challenge()}&code_challenge_method=S256")).session(l.session)
        ).andReturn().response.getHeader("Location")!!
        assertTrue(location.contains("/oauth2/consent"), location)
        val consent = mockMvc.perform(get(java.net.URI.create(location)).session(l.session)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(consent.contains("Yona 계정으로 로그인") || consent.contains("Sign in with your Yona account"), "승인 화면에 openid 설명이 나와야 한다")
        assertFalse(consent.contains("??apiToken.scope"), "설명 키가 빠지면 안 된다")
    }
}
