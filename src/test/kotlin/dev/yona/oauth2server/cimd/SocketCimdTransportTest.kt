package dev.yona.oauth2server.cimd

import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CIMD 문서를 가져오는 전송(X-1 5단계, `decisions.md` D-71, IETF 초안 5절과 8.6절)을 실제 TLS 서버로 검증한다: 정상 응답, 리디렉션을 따라가지 않기, 크기와 시간 제한, 상태 코드, 청크 응답,
 * 캐시 시간, 공인 주소가 아닌 곳으로 풀리는 이름 거부.
 */
class SocketCimdTransportTest {

    private lateinit var server: HttpsServer
    private lateinit var context: SSLContext
    private var port = 0

    @BeforeEach
    fun start() {
        val keyStore = KeyStore.getInstance("PKCS12")
        SocketCimdTransportTest::class.java.getResourceAsStream("/oauth2/cimd-test.p12").use { keyStore.load(it, "changeit".toCharArray()) }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(keyStore, "changeit".toCharArray()) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keyStore) }
        context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trust.trustManagers, null) }
        server = HttpsServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { httpsConfigurator = HttpsConfigurator(context) }
        fun reply(path: String, status: Int, body: String, vararg headers: Pair<String, String>, contentType: String = "application/json") {
            server.createContext(path) { ex ->
                val bytes = body.toByteArray(); ex.responseHeaders.add("Content-Type", contentType); headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
                ex.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong()); if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
            }
        }
        reply("/ok", 200, """{"client_id":"x"}""", "Cache-Control" to "max-age=600")
        reply("/nocache", 200, """{"a":1}""", "Cache-Control" to "no-store")
        reply("/redirect", 302, "", "Location" to "https://localhost/ok")
        reply("/error", 500, "boom")
        reply("/notfound", 404, "no")
        reply("/big", 200, "x".repeat(20_000))
        reply("/html", 200, "<html></html>", contentType = "text/html")
        server.createContext("/chunked") { ex ->
            ex.responseHeaders.add("Content-Type", "application/json"); ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { it.write("""{"chunk""".toByteArray()); it.flush(); it.write("""ed":true}""".toByteArray()) }
        }
        server.createContext("/slow") { ex -> Thread.sleep(8_000); ex.sendResponseHeaders(200, -1); ex.close() }
        server.start(); port = server.address.port
    }

    @AfterEach
    fun stop() { server.stop(0) }

    private fun transport(allowPrivate: Boolean = true, resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() }) =
        SocketCimdTransport(sslContext = context, allowPrivateHosts = allowPrivate, resolver = resolver)

    private fun url(path: String) = "https://localhost:$port$path"

    @Test
    fun `정상 응답은 본문과 캐시 시간을 돌려준다`() {
        val r = transport().fetch(url("/ok"))
        assertEquals("""{"client_id":"x"}""", r.body); assertEquals(600L, r.maxAgeSeconds)
        assertNull(transport().fetch(url("/nocache")).maxAgeSeconds.takeIf { it != 0L }, "no-store는 캐시하지 않는다(0)")
    }

    @Test
    fun `청크로 보낸 응답도 읽는다`() {
        assertEquals("""{"chunked":true}""", transport().fetch(url("/chunked")).body)
    }

    @Test
    fun `리디렉션은 따라가지 않고 200이 아니면 실패한다`() {
        for (path in listOf("/redirect", "/error", "/notfound")) assertFailsWith<CimdFetchException>(path) { transport().fetch(url(path)) }
    }

    @Test
    fun `본문이 너무 크거나 JSON이 아닌 응답은 실패한다`() {
        assertTrue(assertFailsWith<CimdFetchException> { transport().fetch(url("/big")) }.message!!.contains("크"))
        assertFailsWith<CimdFetchException> { transport().fetch(url("/html")) }
    }

    @Test
    fun `응답이 늦으면 시간 제한으로 실패한다`() {
        val started = System.currentTimeMillis()
        assertFailsWith<CimdFetchException> { transport().fetch(url("/slow")) }
        assertTrue(System.currentTimeMillis() - started < 7_500, "5초쯤에 끊는다")
    }

    @Test
    fun `인증서를 믿을 수 없으면 실패한다`() {
        val untrusted = SocketCimdTransport(sslContext = SSLContext.getDefault(), allowPrivateHosts = true, resolver = { InetAddress.getAllByName(it).toList() })
        assertFailsWith<CimdFetchException> { untrusted.fetch(url("/ok")) }
    }

    @Test
    fun `공인 주소가 아닌 곳으로 풀리는 이름은 연결하지 않는다`() {
        val strict = transport(allowPrivate = false)
        assertTrue(assertFailsWith<CimdFetchException> { strict.fetch(url("/ok")) }.message!!.contains("공인"), "루프백은 기본으로 막는다")
        val mixed = transport(allowPrivate = false) { listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("169.254.169.254")) }
        assertFailsWith<CimdFetchException> { mixed.fetch("https://rebind.example/doc.json") }
        assertFailsWith<CimdFetchException> { transport(allowPrivate = false) { emptyList() }.fetch("https://nowhere.example/doc.json") }
    }

    @Test
    fun `https가 아니거나 잘못된 주소는 거부한다`() {
        for (bad in listOf("http://localhost:$port/ok", "https://localhost:$port", "ftp://example.com/a", "not a url")) assertFailsWith<CimdFetchException>(bad) { transport().fetch(bad) }
    }
}
