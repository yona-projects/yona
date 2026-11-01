package dev.yona.observability

import com.sun.net.httpserver.HttpServer
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 수집기 주소를 설정하면 지표, 추적, 로그가 OTLP(HTTP)로 나가는지 모의 수집기로 확인한다(로드맵 X-10 C4, `decisions.md` D-64).
 * 신호별 경로(`/v1/metrics`, `/v1/traces`, `/v1/logs`)와 인증 헤더가 맞아야 한다.
 */
@AutoConfigureMockMvc
// 컨텍스트가 닫힐 때 `create-drop`이 테이블을 지우므로 같은 인메모리 DB(`yona`)를 쓰는 다른 시험의 컨텍스트를 깨뜨리지 않게 전용 DB를 쓴다
@TestPropertySource(properties = ["yona.test.otlp-export=true", "spring.datasource.url=jdbc:h2:mem:otlp-export;DB_CLOSE_DELAY=-1"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtlpExportTest : AbstractIntegrationTest() {

    companion object {
        private val received = ConcurrentHashMap<String, MutableList<Map<String, String>>>()

        /** 수집기가 받은 본문(모든 신호)이다. 비밀값이 새지 않았는지 검사한다. */
        private val bodies = java.util.Collections.synchronizedList(mutableListOf<ByteArray>())

        /** 신호별 본문이다(경로 -> 본문 목록). */
        private val bodiesByPath = ConcurrentHashMap<String, MutableList<ByteArray>>()
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val body = exchange.requestBody.readAllBytes()
                bodies += body
                bodiesByPath.computeIfAbsent(exchange.requestURI.path) { java.util.Collections.synchronizedList(mutableListOf()) } += body
                received.computeIfAbsent(exchange.requestURI.path) { java.util.Collections.synchronizedList(mutableListOf()) }
                    .add(exchange.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() })
                exchange.responseHeaders.add("Content-Type", "application/x-protobuf")
                exchange.sendResponseHeaders(200, -1); exchange.close()
            }
            start()
        }

        /**
         * 환경 후처리기(`ObservabilityActivation`)는 컨텍스트가 뜨기 전에 환경을 읽으므로 시스템 속성으로 준다(`@DynamicPropertySource`는 그 뒤에 들어간다).
         * 값은 컨텍스트가 만들어지기 전인 클래스 초기화 때 정해진다.
         */
        private val keys = mapOf(
            "otel.exporter.otlp.endpoint" to "http://127.0.0.1:${server.address.port}", "otel.exporter.otlp.headers" to "Authorization=Basic dGVzdA==",
            "management.otlp.metrics.export.step" to "2s", "management.tracing.sampling.probability" to "1.0"
        ).also { it.forEach { (k, v) -> System.setProperty(k, v) } }

        @JvmStatic
        @AfterAll
        fun stop() {
            keys.keys.forEach { System.clearProperty(it) }
            server.stop(0)
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc

    private fun await(path: String): List<Map<String, String>> {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            received[path]?.let { if (it.isNotEmpty()) return it.toList() }
            mockMvc.perform(get("/nosuch/nosuch/issues")); LoggerFactory.getLogger("dev.yona.test.OtlpExportTest").warn("otlp probe")
            Thread.sleep(500)
        }
        return emptyList()
    }

    @Test
    fun `추적이 수집기 경로로 나가고 인증 헤더가 실린다`() {
        val sent = await("/v1/traces")
        assertTrue(sent.isNotEmpty(), "추적이 나가지 않았다: ${received.keys}")
        assertEquals("Basic dGVzdA==", sent.first()["authorization"])
    }

    @Test
    fun `지표가 수집기 경로로 나가고 인증 헤더가 실린다`() {
        val sent = await("/v1/metrics")
        assertTrue(sent.isNotEmpty(), "지표가 나가지 않았다: ${received.keys}")
        assertEquals("Basic dGVzdA==", sent.first()["authorization"])
    }

    @Test
    fun `경고 로그가 수집기 경로로 나가고 인증 헤더가 실린다`() {
        val sent = await("/v1/logs")
        assertTrue(sent.isNotEmpty(), "로그가 나가지 않았다: ${received.keys}")
        assertEquals("Basic dGVzdA==", sent.first()["authorization"])
    }

    @Test
    fun `쿼리와 Authorization과 Cookie 헤더의 값이 어느 신호에도 새지 않는다`() {
        val secrets = listOf("SECRETQUERYVALUE", "SECRETAUTHVALUE", "SECRETCOOKIEVALUE")
        val deadline = System.currentTimeMillis() + 30_000
        var sawTrace = false
        while (System.currentTimeMillis() < deadline && !(sawTrace && received["/v1/metrics"].orEmpty().isNotEmpty() && received["/v1/logs"].orEmpty().isNotEmpty())) {
            mockMvc.perform(get("/nosuch/nosuch/issues?token=SECRETQUERYVALUE").header("Authorization", "token SECRETAUTHVALUE").header("Cookie", "yobi.session=SECRETCOOKIEVALUE"))
            LoggerFactory.getLogger("dev.yona.test.OtlpExportTest").warn("otlp secret probe")
            Thread.sleep(500)
            sawTrace = received["/v1/traces"].orEmpty().isNotEmpty()
        }
        assertTrue(sawTrace && bodies.isNotEmpty(), "나간 신호가 없다: ${received.keys}")
        synchronized(bodies) {
            // 검사가 의미 있으려면 본문에 요청 경로가 실려 있어야 한다(경로는 남기고 쿼리와 헤더만 새지 않는 것을 확인한다)
            assertTrue(bodies.any { String(it, Charsets.ISO_8859_1).contains("/nosuch/nosuch/issues") }, "추적 본문에 요청 경로가 없어 검사가 의미 없다")
            secrets.forEach { secret -> assertTrue(bodies.none { String(it, Charsets.ISO_8859_1).contains(secret) }, "$secret 이(가) 수집기로 나갔다") }
        }
    }

    @Test
    fun `요청 번호와 서비스 이름과 버전이 추적에 실린다`() {
        val deadline = System.currentTimeMillis() + 30_000
        fun traces() = bodiesByPath["/v1/traces"].orEmpty().toList().map { String(it, Charsets.ISO_8859_1) }
        while (System.currentTimeMillis() < deadline && traces().none { it.contains("trace-req-77") }) {
            mockMvc.perform(get("/nosuch/nosuch/issues").header("X-Request-Id", "trace-req-77")); Thread.sleep(500)
        }
        val text = traces()
        assertTrue(text.any { it.contains("trace-req-77") }, "추적에 요청 번호가 없다")
        assertTrue(text.any { it.contains("service.name") && it.contains("yona") }, "추적에 service.name이 없다")
        assertTrue(text.any { it.contains("service.version") }, "추적에 service.version이 없다")
    }
}
