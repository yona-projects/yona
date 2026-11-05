package dev.yona.domain.site

import dev.yona.config.YonaProperties
import dev.yona.domain.misc.PropertyService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 새 버전 알림 끄기가 서버를 다시 시작해도 유지되는지 검증한다. v1.6은 끈 상태를 메모리에만 두어 서버를 다시 시작하면 알림이 다시 켜졌다(R-55-2).
 * 서버 재시작은 같은 속성 저장소를 쓰는 새 서비스 인스턴스로 흉내 낸다.
 */
@Transactional
class UpdateNoticePersistenceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var propertyService: PropertyService

    private fun service() = UpdateCheckService(YonaProperties(), RemoteTags { listOf("refs/tags/v99.0.0") }, "1.0.0", propertyService).also { it.refresh() }

    @Test
    fun `알림을 끄면 서버를 다시 시작해도 꺼져 있다 (R-55-2)`() {
        val first = service()
        assertTrue(first.shouldNotify())
        first.unwatch()
        assertFalse(first.shouldNotify())
        val restarted = service()
        assertEquals("99.0.0", restarted.versionToUpdate)
        assertFalse(restarted.shouldNotify(), "재시작한 서버에서도 알림이 꺼져 있다")
    }

    @Test
    fun `끄지 않았으면 재시작해도 켜져 있다`() {
        service().reset()
        assertTrue(service().shouldNotify())
    }
}
