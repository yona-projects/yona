package dev.yona.domain.site

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.time.Duration
import kotlin.test.assertEquals

/** 백그라운드 업데이트 확인이 `check` 설정을 따르는지 검증한다(R-55-1). v1.6은 이 설정과 관계없이 돌았다. */
class UpdateCheckSchedulerTest {

    private fun scheduler(check: Boolean): UpdateCheckScheduler {
        val secret = Mockito.mock(dev.yona.security.SiteSecret::class.java)
        val properties = YonaProperties(update = YonaProperties.Update(check = check, interval = Duration.ofHours(1)))
        return UpdateCheckScheduler(properties, Mockito.mock(UpdateCheckService::class.java), secret, Mockito.mock(dev.yona.observability.JobMetrics::class.java))
    }

    @Test
    fun `확인을 끄면 백그라운드 확인을 시작하지 않는다`() {
        val off = scheduler(check = false); off.start()
        assertEquals(false, off.running); off.stop()
        val on = scheduler(check = true); on.start()
        assertEquals(true, on.running); on.stop()
    }
}
