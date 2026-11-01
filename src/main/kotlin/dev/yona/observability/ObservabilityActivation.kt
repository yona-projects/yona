package dev.yona.observability

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * 수집기 주소(`OTEL_EXPORTER_OTLP_ENDPOINT` 환경 변수나 `otel.exporter.otlp.endpoint` 속성)를 설정하면 OTLP 지표, 추적, 로그 내보내기를 켠다
 * (로드맵 X-10, `decisions.md` D-64 7번). 주소가 없으면 세 신호를 모두 끈다(Spring Boot의 기본값은 켜짐이라 localhost:4318로 보내려 한다).
 *
 * 켤 때 다음을 가장 낮은 우선순위의 기본값으로 더하므로 직접 정한 `management.*` 설정이 우선한다.
 * - 세 신호의 내보내기 켜기. 주소는 주면서 로그만 보내지 않으려면 `management.logging.export.otlp.enabled=false`를 정한다.
 * - 신호별 주소: 주소 뒤에 `/v1/metrics`, `/v1/traces`, `/v1/logs`를 붙인다(Spring Boot의 지표 내보내기는 주소를 따로 받아야 한다).
 * - `OTEL_EXPORTER_OTLP_HEADERS`(`이름=값,이름=값`)가 있으면 세 신호의 헤더로 옮긴다(Grafana Cloud처럼 인증이 필요한 수집기).
 * - 로그를 내보내면 프로파일 `otlp-logs`를 켠다. 그래야 `logback-spring.xml`이 로그 연결(appender)을 붙인다.
 */
class ObservabilityActivation : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (environment.propertySources.contains(SOURCE_NAME)) return
        val endpoint = ENDPOINT_KEYS.firstNotNullOfOrNull { environment.getProperty(it)?.trim()?.takeIf { value -> value.isNotEmpty() } }?.trimEnd('/')
        if (endpoint == null) {
            // 수집기가 없으면 Spring Boot의 기본값(켜짐, localhost:4318로 보냄)을 쓰지 않도록 세 신호를 모두 끈다
            environment.propertySources.addLast(MapPropertySource(SOURCE_NAME, ENABLE_KEYS.associateWith { "false" }))
            return
        }
        val defaults = linkedMapOf<String, Any>()
        ENABLE_KEYS.forEach { defaults[it] = "true" }
        defaults[METRICS_URL] = "$endpoint/v1/metrics"
        defaults[TRACES_ENDPOINT] = "$endpoint/v1/traces"
        defaults[LOGS_ENDPOINT] = "$endpoint/v1/logs"
        val headers = (environment.getProperty("OTEL_EXPORTER_OTLP_HEADERS") ?: environment.getProperty("otel.exporter.otlp.headers"))?.let { parseHeaders(it) }.orEmpty()
        headers.forEach { (name, value) -> HEADER_PREFIXES.forEach { defaults["$it.$name"] = value } }
        environment.propertySources.addLast(MapPropertySource(SOURCE_NAME, defaults))
        // 사용자가 로그 내보내기를 직접 껐으면 로그 연결도 붙이지 않는다(기본값으로 더한 값은 낮은 우선순위라 위에서 읽은 값이 직접 정한 값이다)
        if (environment.getProperty(LOGS_ENABLED) == "true") environment.addActiveProfile(LOG_PROFILE)
    }

    private fun parseHeaders(text: String): Map<String, String> = text.split(',').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
    }.filter { it.first.isNotEmpty() }.toMap()

    companion object {
        /** 더한 기본값 묶음의 이름이다. 두 번 더하지 않는 표지로도 쓴다. */
        const val SOURCE_NAME = "yonaObservabilityActivation"

        /** 로그 연결(appender)을 `logback-spring.xml`에 붙이는 프로파일이다. */
        const val LOG_PROFILE = "otlp-logs"
        private const val METRICS_ENABLED = "management.otlp.metrics.export.enabled"
        private const val TRACES_ENABLED = "management.tracing.export.otlp.enabled"
        private const val LOGS_ENABLED = "management.logging.export.otlp.enabled"
        private const val METRICS_URL = "management.otlp.metrics.export.url"
        private const val TRACES_ENDPOINT = "management.opentelemetry.tracing.export.otlp.endpoint"
        private const val LOGS_ENDPOINT = "management.opentelemetry.logging.export.otlp.endpoint"
        private val ENDPOINT_KEYS = listOf("OTEL_EXPORTER_OTLP_ENDPOINT", "otel.exporter.otlp.endpoint")
        private val ENABLE_KEYS = listOf(METRICS_ENABLED, TRACES_ENABLED, LOGS_ENABLED)
        private val HEADER_PREFIXES = listOf("management.otlp.metrics.export.headers", "management.opentelemetry.tracing.export.otlp.headers", "management.opentelemetry.logging.export.otlp.headers")
    }
}
