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
@TestPropertySource(properties = ["yona.test.otlp-export=true"])
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OtlpExportTest : AbstractIntegrationTest() {

    companion object {
        private val received = ConcurrentHashMap<String, MutableList<Map<String, String>>>()
        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                exchange.requestBody.readAllBytes()
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
}
