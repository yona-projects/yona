package dev.yona.domain.audit

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 외부 스트리밍이 아직 보내지 않은 기록은 보존 처리가 지우거나 비우지 않는다(X-9 B6, `decisions.md` D-63).
 * 웹훅 주소만 설정하고(보내지는 않는다) 커서를 직접 정해 보존 처리를 돌린다.
 */
@TestPropertySource(
    properties = [
        "yona.data-dir=build/tmp/it-audit-stream-retention", "yona.audit-file-enabled=false", "yona.audit-retention-days=30", "yona.audit-git-retention-days=10",
        "yona.audit-stream-webhook-url=http://127.0.0.1:1/none"
    ]
)
class AuditStreamRetentionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var retention: AuditRetention
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var cursors: AuditStreamCursorRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    @AfterEach
    fun cleanUp() {
        recorder.clock = System::currentTimeMillis
        tx.executeWithoutResult { cursors.deleteAllInBatch(); logs.deleteAllInBatch(); heads.deleteAllInBatch() }
        File("build/tmp/it-audit-stream-retention/audit").deleteRecursively()
    }

    private fun rec(action: String, ageDays: Long) {
        recorder.clock = { now - ageDays * day }
        tx.executeWithoutResult { recorder.record(AuditEvent(action, targetLabel = "t", details = mapOf("k" to "v"))) }
        recorder.clock = System::currentTimeMillis
    }

    private fun cursor(lastId: Long) = tx.executeWithoutResult { cursors.save(AuditStreamCursor(name = "webhook", lastId = lastId)) }

    @Test
    fun `보내지 않은 기록은 기간이 지나도 지우지 않는다`() {
        rec("a", 100); rec("b", 100); rec("c", 100)
        cursor(1)
        val result = retention.purge(now)
        assertEquals(1L, result.purged)
        assertEquals(listOf(2L, 3L), logs.findAll().filter { it.action != "audit.purged" }.map { it.id }.sorted())
    }

    @Test
    fun `보낸 뒤에는 지운다`() {
        rec("a", 100); rec("b", 100)
        cursor(2)
        assertEquals(2L, retention.purge(now).purged)
    }

    @Test
    fun `보내지 않은 git 기록은 기간이 지나도 비우지 않는다`() {
        rec("git.push", 50); rec("git.push", 50)
        cursor(1)
        assertEquals(1, retention.redactExpired(now))
        assertNotNull(logs.findById(1L).get().redactedAt)
        assertNull(logs.findById(2L).get().redactedAt)
    }

    @Test
    fun `커서가 아직 없으면 제한하지 않는다`() {
        rec("a", 100)
        assertEquals(1L, retention.purge(now).purged)
    }
}
