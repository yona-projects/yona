package dev.yona.domain.social

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.net.URLDecoder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 소셜 로그인 제공자(GitHub, Google) 클라이언트를 로컬 스텁 서버로 검증한다.
 * 원본: play-authenticate 0.6.9의 `GithubAuthProvider`, `GoogleAuthProvider`(`conf/social-login.conf.default`의 설정을 따른다).
 */
class SocialProviderTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<Captured>()
    private var tokenBody = """{"access_token":"tok-1","token_type":"bearer"}"""
    private var tokenStatus = 200
    private var userBody = """{"id":12345,"login":"octo","name":"옥토","email":"octo@example.com"}"""
    private var userStatus = 200

    data class Captured(val method: String, val path: String, val query: String?, val headers: Map<String, String>, val body: String)

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { reply(it, tokenStatus, tokenBody) }
        server.createContext("/user") { reply(it, userStatus, userBody) }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun reply(exchange: HttpExchange, status: Int, body: String) {
        requests += Captured(
            exchange.requestMethod, exchange.requestURI.path, exchange.requestURI.rawQuery,
            exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }, exchange.requestBody.readBytes().decodeToString()
        )
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun base() = "http://127.0.0.1:${server.address.port}"

    private fun config(scope: String = "user:email") = YonaProperties.OAuthProvider(
        clientId = "cid", clientSecret = "csecret", scope = scope,
        authorizationUrl = "https://provider.example/authorize", accessTokenUrl = "${base()}/token", userInfoUrl = "${base()}/user"
    )

    private fun form(body: String) = body.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), Charsets.UTF_8) }

    // --- 인증 화면 주소 ---

    @Test
    fun `인증 화면 주소에 클라이언트와 돌아올 주소와 권한과 state를 담는다`() {
        val uri = OAuth2SocialProvider.github(config()).authorizationUri("http://yona.test/authenticate/github", "st-1")
        assertTrue(uri.startsWith("https://provider.example/authorize?"))
        val query = form(uri.substringAfter("?"))
        assertEquals("cid", query["client_id"])
        assertEquals("http://yona.test/authenticate/github", query["redirect_uri"])
        assertEquals("user:email", query["scope"])
        assertEquals("code", query["response_type"])
        assertEquals("st-1", query["state"])
    }

    @Test
    fun `주소에 이미 질의가 있으면 이어 붙인다`() {
        val uri = OAuth2SocialProvider.google(config("profile email").copy(authorizationUrl = "https://provider.example/auth?prompt=select")).authorizationUri("http://y/a", "s")
        assertTrue(uri.startsWith("https://provider.example/auth?prompt=select&client_id=cid"))
    }

    // --- GitHub ---

    @Test
    fun `GitHub는 코드를 토큰으로 바꾸고 사용자 정보를 읽는다`() {
        val identity = OAuth2SocialProvider.github(config()).identityOf("code-1", "http://yona.test/authenticate/github")
        assertEquals(SocialIdentity("github", "12345", "octo@example.com", "옥토"), identity)
        val token = requests.first { it.path == "/token" }
        assertEquals("POST", token.method)
        val body = form(token.body)
        assertEquals(mapOf("client_id" to "cid", "client_secret" to "csecret", "code" to "code-1", "redirect_uri" to "http://yona.test/authenticate/github", "grant_type" to "authorization_code"), body)
        assertEquals("application/json", token.headers["accept"])
        val user = requests.first { it.path == "/user" }
        assertEquals("Bearer tok-1", user.headers["authorization"])
    }

    @Test
    fun `JSON null이거나 필드가 없으면 없음(null)으로 읽는다 (R-59-11)`() {
        // v1.6 play-authenticate는 JSON null의 asText()가 문자열 "null"이라 이름이 "null"이 되었다. 없음으로 읽는다
        userBody = """{"id":7,"login":"nameless","name":null,"email":null}"""
        val identity = OAuth2SocialProvider.github(config()).identityOf("c", "r")
        assertNull(identity.name)
        assertNull(identity.email)
        userBody = """{"id":8,"login":"bare"}"""
        val bare = OAuth2SocialProvider.github(config()).identityOf("c", "r")
        assertNull(bare.name)
        assertNull(bare.email)
    }

    @Test
    fun `토큰 응답의 만료 시간으로 만료 시각을 계산한다`() {
        tokenBody = """{"access_token":"tok-2","expires_in":3600}"""
        val before = System.currentTimeMillis()
        val identity = OAuth2SocialProvider.google(config("profile email")).identityOf("c", "r")
        assertTrue(identity.expires in (before + 3_600_000)..(System.currentTimeMillis() + 3_600_000))
    }

    @Test
    fun `응답의 error 문구가 예외의 문구가 된다`() {
        tokenBody = """{"error":"bad_verification_code"}"""
        assertEquals("bad_verification_code", assertFailsWith<SocialLoginException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }.message)
        tokenBody = """{"access_token":"t"}"""
        userBody = """{"error":"invalid_token"}"""
        assertEquals("invalid_token", assertFailsWith<SocialLoginException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }.message)
    }

    // --- Google ---

    @Test
    fun `Google은 id와 이메일과 이름을 읽는다`() {
        userBody = """{"id":"1098","email":"g@example.com","verified_email":true,"name":"구글 사용자"}"""
        val identity = OAuth2SocialProvider.google(config("profile email")).identityOf("c", "r")
        assertEquals(SocialIdentity("google", "1098", "g@example.com", "구글 사용자"), identity)
    }

    @Test
    fun `Google이 확인하지 않은 이메일은 쓰지 않는다 (R-59-3)`() {
        // v1.6은 제공자가 확인했는지 보지 않고 이메일을 확인된 것으로 보아 같은 이메일의 기존 계정으로 로그인시켰다
        for (body in listOf("""{"id":"1","email":"g@example.com","verified_email":false,"name":"x"}""", """{"id":"2","email":"g@example.com","name":"x"}""")) {
            userBody = body
            assertNull(OAuth2SocialProvider.google(config("profile email")).identityOf("c", "r").email, body)
        }
    }

    // --- 오류 ---

    @Test
    fun `토큰을 받지 못하면 예외이다`() {
        tokenBody = """{"error":"bad_verification_code"}"""
        assertFailsWith<SocialLoginException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }
        tokenStatus = 500
        tokenBody = "oops"
        assertFailsWith<SocialLoginException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }
    }

    @Test
    fun `사용자 정보를 받지 못하거나 id가 없으면 일반 예외이다 (문구 본문이 아니라 오류 화면 500)`() {
        // error 항목이 있는 응답만 SocialLoginException(문구 본문)이고, 나머지는 v1.6에서 NPE라 일반 예외이다
        userStatus = 401
        userBody = """{"message":"Bad credentials"}"""
        assertFailsWith<IllegalStateException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }
        userStatus = 200
        userBody = """{"login":"noid"}"""
        assertFailsWith<IllegalStateException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }
        userBody = """{"error":"bad"}"""
        assertFailsWith<SocialLoginException> { OAuth2SocialProvider.github(config()).identityOf("c", "r") }
    }
}
