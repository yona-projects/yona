package dev.yona.support

import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 시험에서 Yona를 OIDC 제공자로 쓰는 외부 앱(예: Woodpecker)을 흉내 낸다. 승인 요청을 보내고, 사용자가 어떤 방법으로든 로그인한 뒤 이어진 요청이 인가 코드와 ID 토큰으로 끝나는지 확인한다.
 * 로그인 수단(비밀번호, LDAP, SAML, 다른 OIDC)에 상관없이 같은 흐름을 따라야 한다.
 */
class OAuthProviderFlow(private val mockMvc: MockMvc, private val users: UserRepository, clients: OAuthClientService, ownerSuffix: String) {

    val redirect = "https://ci.example.com/authorize/callback"
    private val verifier = "w".repeat(64)
    private val json = JsonMapper()
    val client: OAuthClient

    init {
        val owner = users.save(User(loginId = "wp-$ownerSuffix", name = "관리자", email = "wp-$ownerSuffix@example.com", state = UserState.ACTIVE, passwordSalt = "s", password = LegacyPasswordHasher.hash("pw", "s")))
        client = (clients.create(owner, "Woodpecker", "", "", redirect, false, setOf("openid", "profile", "email")) as ClientCreation.Created).client
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20")
    fun query(location: String): Map<String, String> = location.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('=', ""), StandardCharsets.UTF_8) }
    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    private fun authorizeUri(): java.net.URI {
        val params = linkedMapOf("response_type" to "code", "client_id" to client.clientId!!, "scope" to "openid profile email", "state" to "wp-state", "redirect_uri" to redirect,
            "code_challenge" to challenge(), "code_challenge_method" to "S256", "nonce" to "wp-nonce")
        return java.net.URI.create("/oauth2/authorize?" + params.entries.joinToString("&") { "${it.key}=${enc(it.value)}" })
    }

    /** 로그인하지 않은 사용자가 보낸 승인 요청이다. 로그인 화면 주소(`redirectUrl`이 이어진다)를 돌려준다. */
    fun begin(session: MockHttpSession): String {
        val location = mockMvc.perform(get(authorizeUri()).session(session)).andReturn().response.getHeader("Location")!!
        assertTrue(location.startsWith("/users/loginform"), location); assertEquals("/oauth2/resume", query(location)["redirectUrl"])
        return location
    }

    /** 로그인을 마친 세션으로 보관한 승인 요청을 이어 코드를 받고 토큰으로 바꿔 ID 토큰의 클레임을 돌려준다. 승인 화면이 나오면 승인한다. */
    fun finish(session: MockHttpSession): JWTClaimsSet {
        val resumed = mockMvc.perform(get("/oauth2/resume").session(session)).andReturn().response.getHeader("Location")!!
        assertTrue(resumed.startsWith("/oauth2/authorize"), "승인 요청이 이어지지 않았다: $resumed")
        var location = mockMvc.perform(get(java.net.URI.create(resumed)).session(session)).andReturn().response.getHeader("Location")!!
        if (location.contains("/oauth2/consent")) {
            location = mockMvc.perform(post("/oauth2/authorize").session(session).param("client_id", client.clientId!!).param("state", query(location)["state"]!!).param("scope", "openid", "profile", "email"))
                .andReturn().response.getHeader("Location")!!
        }
        assertTrue(location.startsWith("$redirect?"), location); assertEquals("wp-state", query(location)["state"])
        val result = mockMvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", query(location)["code"]!!).param("redirect_uri", redirect)
            .param("code_verifier", verifier).param("client_id", client.clientId!!)).andReturn().response.contentAsString
        @Suppress("UNCHECKED_CAST") val tokens = json.readValue(result, Map::class.java) as Map<String, Any?>
        val claims = SignedJWT.parse(tokens["id_token"] as String).jwtClaimsSet
        assertEquals("wp-nonce", claims.getStringClaim("nonce"))
        return claims
    }
}
