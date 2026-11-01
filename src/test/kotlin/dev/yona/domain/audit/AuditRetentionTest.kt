package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import dev.yona.repository.DataDirectory
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 감사 로그 보존(로드맵 X-9 A7, `decisions.md` D-62)을 검증한다: 기간이 지난 앞부분만 보관 파일(JSON 줄)로 옮긴 뒤 지우고, 사슬이 끊기지 않게 지운 마지막 해시를 남긴다.
 * 시간을 되돌리려고 기록기의 시계(`clock`)를 바꿔 옛 시각의 기록을 만든다. 시험이 커밋하는 기록은 `@AfterEach`가 지운다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-audit-retention", "yona.audit-file-enabled=false", "yona.audit-retention-days=30"])
class AuditRetentionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var retention: AuditRetention
    @Autowired private lateinit var verifier: AuditVerifier
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var properties: YonaProperties
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()

    @AfterEach
    fun cleanUp() {
        recorder.clock = System::currentTimeMillis
        tx.executeWithoutResult { logs.deleteAllInBatch(); heads.deleteAllInBatch() }
        File("build/tmp/it-audit-retention/audit").deleteRecursively()
    }

    /** [ageDays]일 전 시각으로 기록 하나를 남긴다. */
    private fun rec(action: String, ageDays: Long) {
        recorder.clock = { now - ageDays * day }
        tx.executeWithoutResult { recorder.record(AuditEvent(action)) }
        recorder.clock = System::currentTimeMillis
    }

    private fun archives(): List<File> = File("build/tmp/it-audit-retention/audit/archive").listFiles()?.sortedBy { it.name }.orEmpty()

    @Test
    fun `보존 기간이 지난 앞부분만 보관 파일로 옮기고 지운다`() {
        rec("old1", 100); rec("old2", 60); rec("new1", 5); rec("new2", 0)
        val result = retention.purge(now)
        assertEquals(2L, result.purged)
        assertEquals(listOf("old1", "old2"), archives().flatMap { f -> f.readLines().map { Regex("\"action\":\"([^\"]+)\"").find(it)!!.groupValues[1] } })
        assertEquals(setOf("new1", "new2", "audit.purged"), logs.findAll().map { it.action }.toSet())
    }

    @Test
    fun `지운 뒤에도 사슬 검증이 통과하고 새 기록이 이어진다`() {
        rec("old", 100); rec("keep", 1)
        retention.purge(now)
        val after = verifier.verify()
        assertTrue(after.ok, after.toString())
        tx.executeWithoutResult { recorder.record(AuditEvent("later")) }
        assertTrue(verifier.verify().ok)
        assertEquals(3, logs.count().toInt(), "keep, audit.purged, later")
    }

    @Test
    fun `모든 기록이 기간을 넘어 모두 지워져도 사슬은 이어진다`() {
        rec("a", 90); rec("b", 80)
        retention.purge(now)
        // 지우고 남긴 `audit.purged`만 있다. 사슬은 지운 마지막 기록의 해시에서 이어진다
        assertEquals(listOf("audit.purged"), logs.findAll().map { it.action })
        assertTrue(verifier.verify().ok)
        assertEquals(heads.findById(1L).get().prunedHash, logs.findAll().single().prevHash)
    }

    @Test
    fun `기간 안의 기록 뒤에 있는 옛 시각의 기록은 앞부분이 아니므로 지우지 않는다`() {
        // 번호 순서가 사슬의 순서이므로 첫 번째가 기간 안이면 그 뒤는 시각이 옛 것이어도 지우지 않는다(가운데를 비우면 사슬이 끊긴다)
        rec("fresh", 1); rec("stale", 100)
        assertEquals(0L, retention.purge(now).purged)
        assertEquals(setOf("fresh", "stale"), logs.findAll().map { it.action }.toSet())
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `지울 것이 없으면 보관 파일도 이벤트도 만들지 않는다`() {
        rec("new", 1)
        val result = retention.purge(now)
        assertEquals(0L, result.purged)
        assertTrue(archives().isEmpty())
        assertEquals(listOf("new"), logs.findAll().map { it.action })
    }

    @Test
    fun `보존 일수가 0이면 지우지 않는다`() {
        rec("old", 400)
        assertEquals(0L, retention.purge(now, retentionDays = 0).purged)
        assertEquals(listOf("old"), logs.findAll().map { it.action })
    }

    @Test
    fun `묶음보다 많아도 모두 지우고 보관 파일은 번호 범위로 나뉜다`() {
        repeat(5) { rec("old$it", 100) }
        rec("keep", 0)
        val result = retention.purge(now, batchSize = 2)
        assertEquals(5L, result.purged)
        assertEquals(3, archives().size)
        assertEquals(5, archives().sumOf { it.readLines().size })
        assertTrue(archives().first().name.matches(Regex("audit-archive-1-2\\.jsonl")), archives().map { it.name }.toString())
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `보관 파일을 쓰지 못하면 지우지 않는다`() {
        rec("old", 100)
        // 보관 디렉터리 자리에 파일을 두어 디렉터리를 만들 수 없게 한다
        val audit = File("build/tmp/it-audit-retention/audit").apply { mkdirs() }
        File(audit, "archive").writeText("not a directory")
        assertFailsWithAny { retention.purge(now) }
        assertEquals(listOf("old"), logs.findAll().map { it.action })
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `지운 일은 시스템 행위자의 이벤트로 남고 범위와 건수를 담는다`() {
        rec("old1", 100); rec("old2", 99); rec("keep", 0)
        retention.purge(now)
        val e = logs.findAll().single { it.action == "audit.purged" }
        assertEquals(AuditActorType.SYSTEM, e.actorType)
        assertTrue(e.details!!.contains("\"count\":2") && e.details!!.contains("\"fromId\":1") && e.details!!.contains("\"toId\":2"), e.details)
    }

    @Test
    fun `지운 뒤에도 남은 기록을 고치면 검증이 잡는다`() {
        rec("old", 100); rec("keep1", 1); rec("keep2", 0)
        retention.purge(now)
        val target = logs.findAll().first { it.action == "keep1" }.id
        tx.executeWithoutResult { logs.findById(target).get().action = "forged" }
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals(target, result.brokenId)
    }

    private fun assertFailsWithAny(block: () -> Unit) {
        val failed = try { block(); false } catch (e: Exception) { true }
        assertTrue(failed, "예외가 나야 한다")
    }
}
