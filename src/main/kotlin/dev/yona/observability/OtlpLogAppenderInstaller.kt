package dev.yona.observability

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 로그를 OTLP로 내보내는 Logback 연결(`OpenTelemetryAppender`, `logback-spring.xml`의 프로파일 `otlp-logs`)에 OpenTelemetry를 이어 준다.
 * Spring Boot는 이 연결을 설치하지 않는다. 이어 주기 전에 쌓인 로그는 연결이 보관했다가 이어지면 보낸다. 수집기 주소가 있을 때만(`ObservabilityActivation`이
 * 프로파일을 켠다) 쓰인다.
 */
@Component
@Profile(ObservabilityActivation.LOG_PROFILE)
class OtlpLogAppenderInstaller(private val openTelemetry: OpenTelemetry) : InitializingBean {

    override fun afterPropertiesSet() = OpenTelemetryAppender.install(openTelemetry)
}
