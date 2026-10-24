package dev.yona.domain.site

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 진단(`DiagnosticService`, `ConfigDiagnostic`)을 검증한다. 원본: `app/utils/Diagnostic.java:32-60`, `app/utils/Config.java:26-39`. */
class DiagnosticServiceTest {

    @Test
    fun `모든 점검의 문제를 등록 순서대로 모은다`() {
        val service = DiagnosticService(listOf(DiagnosticCheck { listOf("a", "b") }, DiagnosticCheck { emptyList() }, DiagnosticCheck { listOf("c") }))
        assertEquals(listOf("a", "b", "c"), service.checkAll())
    }

    @Test
    fun `점검이 예외를 던지면 그 점검 대신 실패를 문제로 알린다`() {
        val service = DiagnosticService(listOf(DiagnosticCheck { throw IllegalStateException("boom") }, DiagnosticCheck { listOf("after") }))
        val errors = service.checkAll()
        assertEquals(2, errors.size)
        assertTrue(errors[0].startsWith("Failed to diagnose: ") && errors[0].contains("boom"))
        assertEquals("after", errors[1])
    }

    @Test
    fun `포트만 정하고 호스트 이름이 없으면 문제이다`() {
        assertEquals(listOf("application.port may be ignored because application.hostname is not configured."), ConfigDiagnostic(YonaProperties(port = 9000)).check())
        assertEquals(emptyList(), ConfigDiagnostic(YonaProperties(port = 9000, hostname = "yona.example.com")).check())
        assertEquals(emptyList(), ConfigDiagnostic(YonaProperties()).check())
    }
}
