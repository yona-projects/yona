package dev.yona.translation

import com.sun.net.httpserver.HttpServer
import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 외부 번역 서비스 호출을 검증한다.
 *
 * 원본: `app/controllers/api/IssueApi.java:1004-1080`(`translate`, `merge`, `getTranslations`).
 */
class TranslationClientTest {

    private lateinit var server: HttpServer
    private val received = mutableListOf<Triple<String, String?, String>>()
    private var response = """{"result":{"translatedText":"번역됨"}}"""

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/translate") { exchange ->
            received += Triple(exchange.requestHeaders.getFirst("Content-Type"), exchange.requestHeaders.getFirst("X-Key"), String(exchange.requestBody.readAllBytes()))
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun client(key: String = "X-Key", value: String = "secret") =
        TranslationClient(YonaProperties(translationApi = "http://127.0.0.1:${server.address.port}/translate", translationHeaderKey = key, translationHeaderValue = value))

    @Test
    fun `한 덩어리를 폼으로 보내고 번역문을 받는다`() {
        assertEquals("번역됨", client().translate("안녕"))
        val (type, key, body) = received.single()
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", type); assertEquals("secret", key)
        assertEquals("source=ko&target=en&text=안녕", body)
    }

    @Test
    fun `빈 텍스트는 호출하지 않고 null이다`() {
        assertNull(client().translate("")); assertNull(client().translate("   "))
        assertEquals(0, received.size)
    }

    @Test
    fun `응답에 번역문이 없으면 null이다`() {
        response = """{"result":{}}"""
        assertNull(client().translate("x"))
    }

    @Test
    fun `긴 글은 4500자 안쪽 덩어리로 나눠 보내고 줄바꿈으로 잇는다`() {
        response = """{"result":{"translatedText":"T"}}"""
        val lines = List(10) { "x".repeat(1000) }
        val result = client().translateLines(lines)
        assertEquals(3, received.size)
        assertEquals(List(3) { "T" }.joinToString("\r\n"), result)
    }

    @Test
    fun `덩어리 나누기는 합이 한도 미만일 때만 이어 붙인다`() {
        assertEquals(listOf("a\r\nb\r\n"), TranslationClient.merge(listOf("a", "b")))
        assertEquals(listOf("${"a".repeat(4499)}\r\n", "b\r\n"), TranslationClient.merge(listOf("a".repeat(4499), "b")))
        assertEquals(listOf("\r\n"), TranslationClient.merge(listOf("")))
    }
}
