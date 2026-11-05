package dev.yona.domain.migration

import com.sun.net.httpserver.HttpServer
import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals

/**
 * GitHub 토큰 교환 응답의 해석을 검증한다(R-58-1). v1.6은 응답을 폼 형식 정규식(`access_token=([^&]+)`)으로만 읽고 상태 코드를 보지 않아,
 * GitHub가 JSON으로 응답하면 토큰이 비었고 오류 응답의 본문에서도 토큰을 읽으려 했다.
 */
class RestGithubTokenClientTest {

    private var server: HttpServer? = null

    @AfterEach
    fun stop() { server?.stop(0) }

    private fun client(status: Int, contentType: String, body: String): RestGithubTokenClient {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/token") { exchange ->
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        s.start(); server = s
        return RestGithubTokenClient(YonaProperties(github = YonaProperties.Github(accessTokenUrl = "http://127.0.0.1:${s.address.port}/token")))
    }

    @Test
    fun `JSON 응답에서 토큰을 읽는다`() {
        assertEquals("gho_abc123", client(200, "application/json", """{"access_token":"gho_abc123","token_type":"bearer","scope":"repo"}""").exchange("code"))
    }

    @Test
    fun `폼 형식 응답에서도 토큰을 읽는다`() {
        assertEquals("tok-1", client(200, "application/x-www-form-urlencoded", "access_token=tok-1&scope=repo&token_type=bearer").exchange("code"))
    }

    @Test
    fun `성공이 아닌 상태 코드는 본문에 토큰이 있어도 빈 토큰이다`() {
        assertEquals("", client(401, "application/json", """{"access_token":"must-not-be-used"}""").exchange("code"))
    }

    @Test
    fun `오류를 알리는 JSON 응답은 빈 토큰이다`() {
        assertEquals("", client(200, "application/json", """{"error":"bad_verification_code","error_description":"The code passed is incorrect or expired."}""").exchange("code"))
    }
}
