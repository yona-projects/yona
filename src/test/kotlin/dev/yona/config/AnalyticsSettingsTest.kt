package dev.yona.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 사용 현황 집계(GA4) 설정을 검증한다. 로드맵 X-21이며 v1.6에는 없다. 원본은 `next` 브랜치의 `AnalyticsSettings`이다.
 * 외부로 정보를 보내므로 기본값은 꺼짐이다 (`decisions.md` A-186).
 */
class AnalyticsSettingsTest {

    @TempDir
    lateinit var dir: File

    private fun settings(sendUsage: Boolean = true, measurementId: String = "G-TEST", name: String = "a") =
        AnalyticsSettings(YonaProperties(analytics = YonaProperties.Analytics(sendUsage, measurementId, File(dir, "$name/install-id").path)))

    @Test
    fun `기본값은 꺼짐이다`() {
        assertFalse(YonaProperties().analytics.sendUsage)
        assertFalse(AnalyticsSettings(YonaProperties()).clientEnabled)
    }

    @Test
    fun `켜져 있고 측정 ID가 있을 때만 태그를 낸다`() {
        assertTrue(settings().clientEnabled)
        assertFalse(settings(sendUsage = false).clientEnabled)
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
