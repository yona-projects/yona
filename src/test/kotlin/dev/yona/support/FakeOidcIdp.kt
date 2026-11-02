package dev.yona.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Date

/**
 * 시험용 가짜 OIDC IdP이다(X-16). JDK 내장 HTTP 서버가 발견 문서(`/.well-known/openid-configuration`), 공개키(`/jwks`), 토큰 엔드포인트(`/token`), 사용자 정보(`/userinfo`)를 낸다.
 * 시험이 [nextIdToken]에 만들어 둔 ID 토큰을 토큰 엔드포인트가 그대로 돌려준다(nonce는 로그인 시작 때 알게 되므로 시험이 그때 토큰을 만든다).
 */
class FakeOidcIdp(val clientId: String = "yona-client", val clientSecret: String = "yona-secret") : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    val key: RSAKey = RSAKeyGenerator(2048).keyID("fake-key").generate()
    val otherKey: RSAKey = RSAKeyGenerator(2048).keyID("other-key").generate()

    /** 토큰 엔드포인트가 돌려줄 ID 토큰이다. */
    @Volatile var nextIdToken: String = ""

    /** 토큰 엔드포인트가 받은 마지막 요청 본문이다(`code`, `code_verifier`, `redirect_uri` 확인용). */
    @Volatile var lastTokenRequest: String = ""

    val issuer: String get() = "http://127.0.0.1:${server.address.port}"

    /** 발견 문서의 `issuer` 값을 바꾼다(발급자 불일치 시험). */
    @Volatile var discoveryIssuer: String? = null

    init {
        server.createContext("/.well-known/openid-configuration") { ex ->
            val body = """{"issuer":"${discoveryIssuer ?: issuer}","authorization_endpoint":"$issuer/authorize","token_endpoint":"$issuer/token","jwks_uri":"$issuer/jwks","userinfo_endpoint":"$issuer/userinfo",""" +
                """"response_types_supported":["code"],"subject_types_supported":["public"],"id_token_signing_alg_values_supported":["RS256"],"scopes_supported":["openid","profile","email"],""" +
                """"token_endpoint_auth_methods_supported":["client_secret_basic","client_secret_post"],"code_challenge_methods_supported":["S256"]}"""
            respond(ex, body)
        }
        server.createContext("/jwks") { ex -> respond(ex, JWKSet(key.toPublicJWK()).toString()) }
        server.createContext("/token") { ex ->
            lastTokenRequest = ex.requestBody.readBytes().toString(StandardCharsets.UTF_8)
            respond(ex, """{"access_token":"at-${System.nanoTime()}","token_type":"Bearer","expires_in":300,"id_token":"$nextIdToken"}""")
        }
        server.createContext("/userinfo") { ex -> respond(ex, "{}") }
        server.start()
    }

    private fun respond(ex: com.sun.net.httpserver.HttpExchange, body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.add("Content-Type", "application/json")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    /** 서명한 ID 토큰을 만든다. 기본값은 정상이고 시험이 일부를 바꿔 오류 경우를 만든다. */
    fun idToken(
        nonce: String?, subject: String = "user-1", email: String? = "user1@corp.example", emailVerified: Boolean? = true, name: String? = "홍길동", audience: String = clientId,
        issuer: String = this.issuer, expiresInSeconds: Long = 300, signWith: RSAKey = key, extra: Map<String, Any> = emptyMap()
    ): String {
        val now = Date()
        val claims = JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience).issueTime(now).expirationTime(Date(now.time + expiresInSeconds * 1000)).apply {
            nonce?.let { claim("nonce", it) }; email?.let { claim("email", it) }; emailVerified?.let { claim("email_verified", it) }; name?.let { claim("name", it) }
            extra.forEach { (k, v) -> claim(k, v) }
        }.build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signWith.keyID).build(), claims).also { it.sign(RSASSASigner(signWith)) }.serialize()
    }

    override fun close() { server.stop(0) }
}
