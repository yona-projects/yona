package dev.yona.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사용 현황 집계(GA4) 설정을 검증한다. 로드맵 X-21이며 v1.6에는 없다. 원본은 `next` 브랜치의 `AnalyticsSettings`이다.
 * 켜고 끄는 스위치는 v1.6의 `yona.send-yona-usage`이고 태그만 UA에서 GA4로 바꿨다 (`decisions.md` A-186).
 */
class AnalyticsSettingsTest {

    @TempDir
    lateinit var dir: File

    private fun settings(sendYonaUsage: Boolean = true, measurementId: String = "G-TEST", name: String = "a") =
        AnalyticsSettings(YonaProperties(sendYonaUsage = sendYonaUsage, analytics = YonaProperties.Analytics(measurementId, File(dir, "$name/install-id").path)))

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

    @Test
    fun `설치 ID는 처음 한 번 만들어 파일에 저장하고 다시 쓴다`() {
        val first = settings(name = "persist").installId
        assertTrue(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matches(first))
        assertEquals(first, File(dir, "persist/install-id").readText())
        assertEquals(first, settings(name = "persist").installId)
    }
}
