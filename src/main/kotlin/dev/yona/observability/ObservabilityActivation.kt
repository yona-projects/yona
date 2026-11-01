package dev.yona.observability

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * 수집기 주소(`OTEL_EXPORTER_OTLP_ENDPOINT` 환경 변수나 `otel.exporter.otlp.endpoint` 속성)를 설정하면 OTLP 지표, 추적, 로그 내보내기를 켠다
 * (로드맵 X-10, `decisions.md` D-64 7번). 주소가 없으면 아무것도 켜지 않는다(`application.yml`이 세 신호를 꺼 둔다).
 *
 * 켜는 값은 가장 낮은 우선순위의 기본값으로 더하므로 `management.*.enabled`를 직접 정한 값이 우선한다. 예를 들어 주소는 주면서 로그만 보내지 않으려면
 * `management.logging.export.otlp.enabled=false`를 정하면 된다.
 */
class ObservabilityActivation : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        if (environment.propertySources.contains(SOURCE_NAME)) return
        val endpoint = ENDPOINT_KEYS.firstNotNullOfOrNull { environment.getProperty(it)?.trim()?.takeIf { value -> value.isNotEmpty() } } ?: return
        environment.propertySources.addLast(MapPropertySource(SOURCE_NAME, ENABLE_KEYS.associateWith { "true" } + (ENDPOINT_MARK to endpoint)))
    }

    companion object {
        /** 더한 기본값 묶음의 이름이다. 두 번 더하지 않는 표지로도 쓴다. */
        const val SOURCE_NAME = "yonaObservabilityActivation"
        private const val ENDPOINT_MARK = "yona.observability.endpoint-detected"
        private val ENDPOINT_KEYS = listOf("OTEL_EXPORTER_OTLP_ENDPOINT", "otel.exporter.otlp.endpoint")
        private val ENABLE_KEYS = listOf(
            "management.otlp.metrics.export.enabled", "management.opentelemetry.tracing.export.otlp.enabled", "management.logging.export.otlp.enabled"
        )
    }
}
