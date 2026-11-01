package dev.yona.observability

import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 수집기 주소(`OTEL_EXPORTER_OTLP_ENDPOINT`)를 설정하면 지표, 추적, 로그 내보내기를 켠다(D-64 7번). 명시한 설정이 우선한다.
 */
class ObservabilityActivationTest {

    private val keys = listOf("management.otlp.metrics.export.enabled", "management.opentelemetry.tracing.export.otlp.enabled", "management.logging.export.otlp.enabled")

    private fun apply(env: org.springframework.core.env.ConfigurableEnvironment) = ObservabilityActivation().postProcessEnvironment(env, org.springframework.boot.SpringApplication())

    @Test
    fun `주소가 없으면 아무것도 켜지 않는다`() {
        val env = MockEnvironment()
        apply(env)
        keys.forEach { assertNull(env.getProperty(it), it) }
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
        keys.forEach { assertNull(env.getProperty(it), it) }
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
}
