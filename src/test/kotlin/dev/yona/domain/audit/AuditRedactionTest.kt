package dev.yona.domain.audit

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 해시 v2와 비움(로드맵 X-9 B2, `decisions.md` D-63)을 검증한다. 기록은 내용 해시(`content_hash`)를 따로 갖고 `hash = SHA-256(이전 해시 + 내용 해시)`이다.
 * 만료한 `git.*` 이벤트는 보관 파일로 옮긴 뒤 개인정보 필드를 비우고(행은 남는다) 내용 해시로 사슬이 이어져 검증을 통과한다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-audit-redact", "yona.audit-file-enabled=false", "yona.audit-retention-days=365", "yona.audit-git-retention-days=90"])
class AuditRedactionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var retention: AuditRetention
    @Autowired private lateinit var verifier: AuditVerifier
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val tx by lazy { TransactionTemplate(transactionManager) }
    private val day = 86_400_000L
    private val now = System.currentTimeMillis()
    private val mapper = JsonMapper.builder().build()

    @AfterEach
    fun cleanUp() {
        recorder.clock = System::currentTimeMillis
        tx.executeWithoutResult { logs.deleteAllInBatch(); heads.deleteAllInBatch() }
        File("build/tmp/it-audit-redact/audit").deleteRecursively()
    }

    private fun rec(action: String, ageDays: Long, details: Map<String, Any?> = mapOf("secret-ish" to "x"), login: String? = "alice") {
        recorder.clock = { now - ageDays * day }
        tx.executeWithoutResult { recorder.record(AuditEvent(action, targetType = "project", targetId = "7", targetLabel = "acme/web", projectId = 7L, details = details, actor = AuditActor(AuditActorType.USER, 1L, login))) }
        recorder.clock = System::currentTimeMillis
    }

    private fun byAction(action: String) = logs.findAll().filter { it.action == action }

    // --- 해시 v2 ---

    @Test
    fun `기록은 내용 해시를 갖고 해시는 이전 해시와 내용 해시로 만든다`() {
        rec("project.created", 0); rec("project.deleted", 0)
        val rows = logs.findAll().sortedBy { it.id }
        assertTrue(rows.all { it.contentHash != null && it.contentHash!!.length == 64 })
        assertEquals(AuditHash.chain(AuditHash.GENESIS, rows[0].contentHash!!), rows[0].hash)
        assertEquals(AuditHash.chain(rows[0].hash, rows[1].contentHash!!), rows[1].hash)
        assertEquals(AuditHash.contentOf(rows[0]), rows[0].contentHash)
        assertNotNullAll(rows.map { it.hash })
    }

    @Test
    fun `내용을 고치면 내용 해시가 달라 검증이 깨진다`() {
        rec("project.created", 0); rec("project.deleted", 0)
        jdbc.update("update audit_log set target_label = 'forged' where id = 1")
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals(1L, result.brokenId); assertEquals("content", result.reason)
    }

    @Test
    fun `내용 해시를 같이 고쳐도 내용을 다시 계산해 맞추므로 깨진다`() {
        rec("project.created", 0)
        jdbc.update("update audit_log set target_label = 'forged', content_hash = ? where id = 1", "f".repeat(64))
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals("content", result.reason)
    }

    // --- 비움 ---

    @Test
    fun `git 이벤트만 보존 기간이 지나면 보관 파일로 옮기고 개인정보를 비운다`() {
        rec("git.push", 100, mapOf("ref" to "refs/heads/main", "commits" to 3)); rec("project.created", 100); rec("git.push", 10); rec("git.force_push", 91)
        val redacted = retention.redactExpired(now)
        assertEquals(2, redacted)
        val stale = byAction("git.push").sortedBy { it.id }.first()
        assertNotNull(stale.redactedAt)
        assertEquals(listOf(null, null, null, null, null, null, null), listOf(stale.actorId, stale.actorLogin, stale.targetId, stale.targetLabel, stale.ip, stale.requestId, stale.details))
        assertEquals("git.push", stale.action); assertTrue(stale.createdMillis > 0)
        // 보안 이벤트와 기간 안의 git 이벤트는 그대로이다
        assertNull(byAction("project.created").single().redactedAt)
        assertNull(byAction("git.push").sortedBy { it.id }.last().redactedAt)
        assertEquals("alice", byAction("project.created").single().actorLogin)
    }

    @Test
    fun `비운 뒤에도 사슬 검증이 통과하고 새 기록이 이어진다`() {
        rec("project.created", 100); rec("git.push", 100); rec("project.deleted", 1)
        retention.redactExpired(now)
        assertTrue(verifier.verify().ok, verifier.verify().toString())
        tx.executeWithoutResult { recorder.record(AuditEvent("later")) }
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `비운 행의 내용 해시를 고치면 검증이 잡는다`() {
        rec("git.push", 100); rec("project.created", 1)
        retention.redactExpired(now)
        jdbc.update("update audit_log set content_hash = ? where action = 'git.push'", "a".repeat(64))
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals("hash", result.reason)
    }

    @Test
    fun `비우기 전의 전체 내용은 보관 파일에 남고 내용 해시를 다시 계산할 수 있다`() {
        rec("git.push", 100, mapOf("ref" to "refs/heads/main"))
        val before = byAction("git.push").single()
        val hash = before.contentHash
        retention.redactExpired(now)
        val archive = File("build/tmp/it-audit-redact/audit/archive").listFiles { f -> f.name.startsWith("audit-redact-") }!!.single()
        val line = mapper.readTree(archive.readLines().single())
        assertEquals("alice", line.get("actor_login").asString()); assertTrue(line.get("details").asString().contains("refs/heads/main"))
        assertEquals(hash, line.get("content_hash").asString())
        val rebuilt = AuditLog(
            id = line.get("id").asLong(), createdMillis = before.createdMillis, actorType = AuditActorType.valueOf(line.get("actor_type").asString()), actorId = line.get("actor_id").asLong(),
            actorLogin = line.get("actor_login").asString(), action = line.get("action").asString(), outcome = AuditOutcome.valueOf(line.get("outcome").asString()),
            targetType = line.get("target_type").asString(), targetId = line.get("target_id").asString(), targetLabel = line.get("target_label").asString(),
            organizationId = null, projectId = line.get("project_id").asLong(), ip = before.ip, userAgent = before.userAgent, requestId = before.requestId, tokenId = null, tokenScopes = null,
            details = line.get("details").asString(), prevHash = line.get("prev_hash").asString()
        )
        assertEquals(hash, AuditHash.contentOf(rebuilt))
    }

    @Test
    fun `이미 비운 행은 다시 비우지 않고 지울 것이 없으면 파일도 만들지 않는다`() {
        rec("git.push", 100)
        assertEquals(1, retention.redactExpired(now))
        val files = File("build/tmp/it-audit-redact/audit/archive").listFiles()!!.size
        assertEquals(0, retention.redactExpired(now))
        assertEquals(files, File("build/tmp/it-audit-redact/audit/archive").listFiles()!!.size)
    }

    @Test
    fun `git 보존 일수가 0이면 따로 비우지 않는다`() {
        rec("git.push", 400)
        assertEquals(0, retention.redactExpired(now, gitRetentionDays = 0))
        assertNull(byAction("git.push").single().redactedAt)
    }

    @Test
    fun `보존 처리는 먼저 비우고 그 뒤 앞부분을 지운다`() {
        rec("project.created", 400); rec("git.push", 100); rec("git.push", 1)
        // 번호 순서: 1 보안(400일), 2 git(100일), 3 git(1일). 2는 비우고 앞부분 삭제는 1만 지운다
        val result = retention.purge(now)
        assertEquals(1L, result.purged)
        assertNotNull(byAction("git.push").single { it.id == 2L }.redactedAt)
        assertNull(byAction("git.push").single { it.id == 3L }.redactedAt)
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `보안 이벤트가 앞을 막고 있어도 git 이벤트는 따로 비워진다`() {
        // 앞의 보안 이벤트(100일, 보존 365일)가 앞부분 삭제를 막아도 뒤의 git 이벤트(100일, 보존 90일)는 비워진다
        rec("project.created", 100); rec("git.push", 100)
        val result = retention.purge(now)
        assertEquals(0L, result.purged)
        assertNotNull(byAction("git.push").single().redactedAt)
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `화면 내려받기 필드에 내용 해시와 비운 시각이 있다`() {
        assertEquals(listOf("content_hash", "redacted_at"), AuditExport.COLUMNS.takeLast(2).let { it })
    }

    private fun assertNotNullAll(values: List<Any?>) = values.forEach { assertNotNull(it) }
}
