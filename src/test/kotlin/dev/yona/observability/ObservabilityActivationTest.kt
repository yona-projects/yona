package dev.yona.observability

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals

/**
 * 수집기 주소(`OTEL_EXPORTER_OTLP_ENDPOINT`)를 설정하면 지표, 추적, 로그 내보내기를 켠다(D-64 7번). 명시한 설정이 우선한다.
 */
class ObservabilityActivationTest {

    private val keys = listOf("management.otlp.metrics.export.enabled", "management.tracing.export.otlp.enabled", "management.logging.export.otlp.enabled")

    private fun apply(env: org.springframework.core.env.ConfigurableEnvironment) = ObservabilityActivation().postProcessEnvironment(env, org.springframework.boot.SpringApplication())

    @Test
    fun `주소가 없으면 세 신호를 모두 끈다`() {
        val env = MockEnvironment()
        apply(env)
        keys.forEach { assertEquals("false", env.getProperty(it), it) }
    }

    @Test
    fun `환경 변수 OTEL_EXPORTER_OTLP_ENDPOINT가 있으면 세 신호를 켠다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318")
        apply(env)
        keys.forEach { assertEquals("true", env.getProperty(it), it) }
    }

    @Test
    fun `속성 otel exporter otlp endpoint로도 켠다`() {
        val env = MockEnvironment().withProperty("otel.exporter.otlp.endpoint", "http://alloy:4318")
        apply(env)
        keys.forEach { assertEquals("true", env.getProperty(it), it) }
    }

    @Test
    fun `비어 있는 주소는 설정하지 않은 것이다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "  ")
        apply(env)
        keys.forEach { assertEquals("false", env.getProperty(it), it) }
    }

    @Test
    fun `명시한 설정이 우선한다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318").withProperty("management.logging.export.otlp.enabled", "false")
        apply(env)
        assertEquals("false", env.getProperty("management.logging.export.otlp.enabled"))
        assertEquals("true", env.getProperty("management.otlp.metrics.export.enabled"))
    }

    @Test
    fun `이미 처리한 환경에 다시 적용해도 같다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318")
        apply(env); apply(env)
        assertEquals(1, env.propertySources.filter { it.name == ObservabilityActivation.SOURCE_NAME }.size)
    }

    // --- 신호별 주소와 헤더 파생 (C4) ---

    @Test
    fun `수집기 주소에서 신호별 주소를 만든다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318/")
        apply(env)
        assertEquals("http://alloy:4318/v1/metrics", env.getProperty("management.otlp.metrics.export.url"))
        assertEquals("http://alloy:4318/v1/traces", env.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
        assertEquals("http://alloy:4318/v1/logs", env.getProperty("management.opentelemetry.logging.export.otlp.endpoint"))
    }

    @Test
    fun `명시한 신호별 주소가 우선한다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318").withProperty("management.otlp.metrics.export.url", "http://mimir:9009/otlp/v1/metrics")
        apply(env)
        assertEquals("http://mimir:9009/otlp/v1/metrics", env.getProperty("management.otlp.metrics.export.url"))
    }

    @Test
    fun `OTEL_EXPORTER_OTLP_HEADERS를 세 신호의 헤더로 옮긴다`() {
        val env = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "https://gw.example.com/otlp").withProperty("OTEL_EXPORTER_OTLP_HEADERS", "Authorization=Basic YWJj, X-Scope-OrgID=tenant1")
        apply(env)
        listOf("management.otlp.metrics.export.headers", "management.opentelemetry.tracing.export.otlp.headers", "management.opentelemetry.logging.export.otlp.headers").forEach {
            assertEquals("Basic YWJj", env.getProperty("$it.Authorization"), it)
            assertEquals("tenant1", env.getProperty("$it.X-Scope-OrgID"), it)
        }
    }

    @Test
    fun `로그를 내보낼 때만 otlp-logs 프로파일을 켠다`() {
        val on = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318")
        apply(on)
        assertEquals(true, on.activeProfiles.contains("otlp-logs"))
        val off = MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy:4318").withProperty("management.logging.export.otlp.enabled", "false")
        apply(off)
        assertEquals(false, off.activeProfiles.contains("otlp-logs"))
        val none = MockEnvironment()
        apply(none)
        assertEquals(false, none.activeProfiles.contains("otlp-logs"))
    }
}
