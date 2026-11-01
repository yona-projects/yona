package dev.yona.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * 인증과 접근 거부의 지표이다(로드맵 X-10 C5d). 감사 로그의 경계 이벤트([dev.yona.domain.audit.AuditBoundary])와 같은 지점에서 센다. 로그인 실패율, 토큰과 Basic 인증
 * 실패율, 차단, 접근 거부(403) 비율의 급증은 공격 신호이다. 감사 기록은 같은 주소의 차단을 1분에 한 번만 남기지만 지표는 매번 센다.
 *
 * `yona.security.events{event, channel, via, denied}`: `event`는 `login_success`, `login_failure`, `logout`, `token_failure`, `basic_failure`, `blocked`, `access_denied`,
 * `api_scope_denied`이다. 태그는 고정된 값만 쓰며 로그인 ID, 주소, 경로를 넣지 않는다. 알 수 없는 채널은 `other`이다.
 */
@Component
class SecurityMetrics(private val registry: MeterRegistry) {

    fun event(event: String, channel: String = NONE, via: String = NONE, denied: Boolean = false) {
        Counter.builder("yona.security.events")
            .tags("event", event, "channel", if (channel == NONE || channel in CHANNELS) channel else "other", "via", if (via == NONE || via in VIAS) via else "other", "denied", denied.toString())
            .description("인증과 접근 거부 이벤트 수").register(registry).increment()
    }

    private companion object {
        const val NONE = "none"
        val CHANNELS = setOf("api", "git_http", "svn", "web")
        val VIAS = setOf("web", "api")
    }
}
