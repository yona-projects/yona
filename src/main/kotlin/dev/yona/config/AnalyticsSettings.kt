package dev.yona.config

import org.springframework.stereotype.Component

/**
 * 사용 현황 집계(GA4) 설정을 화면에 알려 준다 (로드맵 X-21, `decisions.md` A-186).
 *
 * 브라우저 태그(`gtag`)만 쓰며 서버가 GA로 직접 보내는 이벤트는 없다. 템플릿은 `@analyticsSettings`로 이 빈을 읽는다.
 * 스위치는 v1.6의 `yona.send-yona-usage`를 그대로 쓰며, v1.6의 UA 태그는 이 GA4 태그로 대체했다.
 */
@Component
class AnalyticsSettings(private val properties: YonaProperties) {

    /** GA4 측정 ID이다. */
    val measurementId: String get() = properties.analytics.measurementId.trim()

    /** `yona.send-yona-usage`가 켜져 있고 측정 ID가 있을 때만 `true`이다. 이때만 태그를 낸다. */
    val clientEnabled: Boolean get() = properties.sendYonaUsage && measurementId.isNotEmpty()
}
