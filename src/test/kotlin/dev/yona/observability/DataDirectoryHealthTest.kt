package dev.yona.observability

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.boot.health.contributor.Status
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 데이터 디렉터리 상태 확인(로드맵 X-10 C5a, `decisions.md` D-64 5번)을 검증한다. 쓰기가 되고 여유 용량이 한계(기본 5%) 이상이어야 UP이다.
 */
class DataDirectoryHealthTest {

    @TempDir
    lateinit var dir: File

    private fun indicator(minFreePercent: Int = 5, path: String = dir.path) =
        DataDirectoryHealthIndicator(DataDirectory(YonaProperties(dataDir = path)), YonaProperties(dataDir = path, observabilityMinFreePercent = minFreePercent))

    @Test
    fun `쓰기가 되고 여유가 있으면 UP이다`() {
        val health = indicator().health()
        assertEquals(Status.UP, health.status)
        assertTrue((health.details["freePercent"] as Number).toDouble() > 0.0)
    }

    @Test
    fun `디렉터리가 없으면 DOWN이다`() {
        val health = indicator(path = File(dir, "missing").path).health()
        assertEquals(Status.DOWN, health.status)
    }

    @Test
    fun `디렉터리가 파일이면 DOWN이다`() {
        val file = File(dir, "plain").apply { writeText("x") }
        assertEquals(Status.DOWN, indicator(path = file.path).health().status)
    }

    @Test
    fun `여유 용량이 한계보다 적으면 DOWN이고 상세에 값이 있다`() {
        val health = indicator(minFreePercent = 101).health()
        assertEquals(Status.DOWN, health.status)
        assertEquals(101, health.details["minFreePercent"])
    }

    @Test
    fun `한계를 0으로 하면 여유 용량은 보지 않는다`() {
        assertEquals(Status.UP, indicator(minFreePercent = 0).health().status)
    }

    @Test
    fun `진단용 파일을 남기지 않는다`() {
        indicator().health()
        assertEquals(emptyList(), dir.listFiles().orEmpty().toList())
    }
}
