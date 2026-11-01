package dev.yona.observability

import dev.yona.domain.audit.AuditChainHead
import dev.yona.domain.audit.AuditChainHeadRepository
import dev.yona.domain.audit.AuditStreamCursor
import dev.yona.domain.audit.AuditStreamCursorRepository
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 감사 스트리밍의 밀린 기록 수와 실패 횟수와 마지막 성공 시각 지표(로드맵 X-10 C5b)를 검증한다. 웹훅 주소만 설정하고 보내지는 않는다. */
@Transactional
@TestPropertySource(properties = ["yona.audit-stream-webhook-url=http://127.0.0.1:1/none", "yona.audit-file-enabled=false"])
class AuditStreamMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var cursors: AuditStreamCursorRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository

    private fun gauge(name: String, target: String = "webhook") = registry.find("yona.audit.stream.$name").tag("target", target).gauge()

    @Test
    fun `설정된 대상의 밀린 기록 수와 실패 횟수와 마지막 성공 시각을 알린다`() {
        heads.save(AuditChainHead(lastId = 10))
        cursors.save(AuditStreamCursor(name = "webhook", lastId = 4, lastSentAt = 1_700_000_000_500L, failures = 3, lastError = "x"))
        assertEquals(6.0, gauge("backlog")!!.value())
        assertEquals(3.0, gauge("failures")!!.value())
        assertEquals(1_700_000_000.0, gauge("last.success")!!.value())
        assertEquals("seconds", gauge("last.success")!!.id.baseUnit)
    }

    @Test
    fun `아직 보낸 적이 없으면 마지막 성공 시각은 0이다`() {
        cursors.save(AuditStreamCursor(name = "webhook", lastId = 0))
        assertEquals(0.0, gauge("last.success")!!.value())
    }

    @Test
    fun `설정하지 않은 대상의 지표는 만들지 않는다`() {
        assertNotNull(gauge("backlog"))
        assertNull(gauge("backlog", "syslog"))
    }
}
