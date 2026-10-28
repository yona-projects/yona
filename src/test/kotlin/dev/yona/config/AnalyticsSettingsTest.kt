package dev.yona.config

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사용 현황 집계(GA4) 설정을 검증한다. 로드맵 X-21이며 v1.6에는 없다. 원본은 `next` 브랜치의 `AnalyticsSettings`이다.
 * 켜고 끄는 스위치는 v1.6의 `yona.send-yona-usage`이고 태그만 UA에서 GA4로 바꿨다 (`decisions.md` A-186).
 */
class AnalyticsSettingsTest {

    private fun settings(sendYonaUsage: Boolean = true, measurementId: String = "G-TEST") =
        AnalyticsSettings(YonaProperties(sendYonaUsage = sendYonaUsage, analytics = YonaProperties.Analytics(measurementId)))

    @Test
    fun `기본값은 v1_6처럼 켜짐이고 측정 ID가 들어 있다`() {
        assertTrue(YonaProperties().sendYonaUsage)
        assertTrue(AnalyticsSettings(YonaProperties()).clientEnabled)
        assertEquals("G-CKTN17HLPP", AnalyticsSettings(YonaProperties()).measurementId)
    }

    @Test
    fun `send-yona-usage를 끄거나 측정 ID가 비면 태그를 내지 않는다`() {
        assertTrue(settings().clientEnabled)
        assertFalse(settings(sendYonaUsage = false).clientEnabled)
        assertFalse(settings(measurementId = " ").clientEnabled)
    }
}
