package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** 보존 처리의 결과이다. [purged]는 지운 기록 수, [archives]는 만든 보관 파일의 이름이다. */
data class AuditPurgeResult(val purged: Long, val archives: List<String>)

/**
 * 감사 로그를 보존 기간(`yona.audit-retention-days`, 기본 365, 0이면 영구)에 맞춰 정리한다(추가 개발 항목 X-9 A7, `decisions.md` D-62).
 *
 * 기간이 지난 **앞부분**(번호 순서로 처음부터 기간을 넘긴 기록만)을 `<데이터>/audit/archive/audit-archive-<처음 번호>-<끝 번호>.jsonl`로 옮기고(JSON 줄, 줄 파일과 같은 모양)
 * 디스크에 기록한 뒤에야 지운다. 보관 파일을 쓰지 못하면 아무것도 지우지 않는다. 지울 때 사슬의 끝 행([AuditChainHead])에 지운 마지막 번호와 해시를 남겨 남은 기록의
 * 사슬이 그 해시에서 이어지게 한다([AuditVerifier]). 번호 순서의 앞부분만 지우는 이유는 가운데를 비우면 사슬이 끊기기 때문이다. 지운 일은 시스템 행위자의
 * `audit.purged` 이벤트로 남는다. 한 묶음은 한 트랜잭션이고 그동안 사슬의 끝을 잠근다.
 *
 * 외부 스트리밍(웹훅, syslog)이 아직 보내지 않은 기록은 기간이 지나도 지우거나 비우지 않는다([AuditStreamer.minUnsentId], D-63). 보내는 쪽이 오래 막히면 그만큼 밀린다.
 */
@Service
class AuditRetention(
    private val logs: AuditLogRepository,
    private val heads: AuditChainHeadRepository,
    private val recorder: AuditRecorder,
    private val sink: AuditFileSink,
    private val properties: YonaProperties,
    private val transactionManager: PlatformTransactionManager,
    private val streamer: AuditStreamer
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 보존 처리 전체이다. 먼저 `git.*` 이벤트의 개인정보를 비우고([redactExpired], 번호 순서와 무관하게 범주별 보존 기간이 지난 것) 그 뒤 기간이 지난 앞부분을 지운다.
     * [now] 기준으로 [retentionDays]일이 지난 기록을 [batchSize]건씩 옮기고 지운다. [retentionDays]가 0 이하이면 지우지 않는다(비우기는 따로 판단한다).
     */
    fun purge(
        now: Long = System.currentTimeMillis(), retentionDays: Int = properties.auditRetentionDays, batchSize: Int = DEFAULT_BATCH,
        gitRetentionDays: Int = properties.auditGitRetentionDays
    ): AuditPurgeResult {
        redactExpired(now, gitRetentionDays, batchSize)
        if (retentionDays <= 0) return AuditPurgeResult(0, emptyList())
        val cutoff = now - retentionDays * DAY_MILLIS
        val template = TransactionTemplate(transactionManager)
        var total = 0L
        var firstId: Long? = null
        var lastId = 0L
        val archives = mutableListOf<String>()
        while (true) {
            val batch = template.execute { purgeBatch(cutoff, batchSize.coerceAtLeast(1)) } ?: break
            if (batch.count == 0) break
            total += batch.count
            if (firstId == null) firstId = batch.firstId
            lastId = batch.lastId
            archives += batch.archive
        }
        if (total > 0) {
            recorder.record(
                AuditEvent(
                    "audit.purged", targetType = "audit", actor = AuditActor(AuditActorType.SYSTEM, null, null),
                    details = mapOf("count" to total, "fromId" to firstId, "toId" to lastId, "retentionDays" to retentionDays, "archives" to archives)
                )
            )
            log.info("감사 로그 {}건을 보관 파일 {}개로 옮기고 지웠습니다(번호 {}~{})", total, archives.size, firstId, lastId)
        }
        return AuditPurgeResult(total, archives)
    }

    /**
     * 보존 기간(`yona.audit-git-retention-days`, [gitRetentionDays])이 지난 `git.*` 이벤트의 개인정보 필드를 비운다. 행은 남고 내용 해시(`content_hash`)로 사슬이 이어진다(D-63).
     * 비우기 전의 전체 내용은 `<데이터>/audit/archive/audit-redact-<처음 번호>-<끝 번호>.jsonl`에 `fsync`한 뒤에 비운다(쓰지 못하면 비우지 않는다). 이미 비운 행은 건너뛴다.
     * [gitRetentionDays]가 0 이하이면 따로 비우지 않는다. 비운 행 수를 돌려준다.
     */
    fun redactExpired(now: Long = System.currentTimeMillis(), gitRetentionDays: Int = properties.auditGitRetentionDays, batchSize: Int = DEFAULT_BATCH): Int {
        if (gitRetentionDays <= 0) return 0
        val cutoff = now - gitRetentionDays * DAY_MILLIS
        val template = TransactionTemplate(transactionManager)
        var total = 0
        while (true) {
            val count = template.execute { redactBatch(cutoff, now, batchSize.coerceAtLeast(1)) } ?: 0
            if (count == 0) break
            total += count
        }
        if (total > 0) log.info("보존 기간이 지난 git 감사 기록 {}건의 개인정보를 비웠습니다", total)
        return total
    }

    private fun redactBatch(cutoff: Long, now: Long, batchSize: Int): Int {
        heads.lockHead() ?: return 0
        val unsent = streamer.minUnsentId()
        val rows = logs.findByActionStartingWithAndCreatedMillisLessThanAndRedactedAtIsNullAndContentHashIsNotNullOrderByIdAsc("git.", cutoff, PageRequest.of(0, batchSize))
            .filter { unsent == null || it.id < unsent }
        if (rows.isEmpty()) return 0
        val directory = File(sink.directory(), "archive")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("보관 디렉터리를 만들지 못했습니다: $directory")
        val file = File(directory, "audit-redact-${rows.first().id}-${rows.last().id}.jsonl")
        FileOutputStream(file).use { out ->
            out.write(rows.joinToString("") { sink.line(it) + "\n" }.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        rows.forEach {
            it.actorId = null; it.actorLogin = null; it.targetId = null; it.targetLabel = null; it.organizationId = null; it.projectId = null
            it.ip = null; it.userAgent = null; it.requestId = null; it.tokenId = null; it.tokenScopes = null; it.details = null
            it.redactedAt = now
        }
        return rows.size
    }

    private class Batch(val count: Int, val firstId: Long, val lastId: Long, val archive: String)

    /** 한 묶음이다. 사슬의 끝을 잠그고 앞에서부터 기간을 넘긴 기록을 모은다. 보관 파일을 디스크에 쓴 뒤에 지우고 사슬의 끝에 지운 해시를 남긴다. */
    private fun purgeBatch(cutoff: Long, batchSize: Int): Batch? {
        val head = heads.lockHead() ?: return null
        val unsent = streamer.minUnsentId()
        val old = logs.findByIdGreaterThanOrderByIdAsc(head.prunedThroughId, PageRequest.of(0, batchSize)).takeWhile { it.createdMillis < cutoff && (unsent == null || it.id < unsent) }
        if (old.isEmpty()) return Batch(0, 0, 0, "")
        val first = old.first(); val last = old.last()
        val directory = File(sink.directory(), "archive")
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("보관 디렉터리를 만들지 못했습니다: $directory")
        val file = File(directory, "audit-archive-${first.id}-${last.id}.jsonl")
        FileOutputStream(file).use { out ->
            out.write(old.joinToString("") { sink.line(it) + "\n" }.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        logs.deleteAllByIdInBatch(old.map { it.id })
        head.prunedThroughId = last.id
        head.prunedHash = last.hash
        return Batch(old.size, first.id, last.id, file.name)
    }

    companion object {
        const val DEFAULT_BATCH = 1000
        private const val DAY_MILLIS = 86_400_000L
    }
}

/** 보존 처리를 6시간마다 돌린다(서버를 띄운 지 10분 뒤 처음). 실패해도 다음 주기에 다시 한다. */
@Component
class AuditRetentionScheduler(private val retention: AuditRetention, private val jobs: dev.yona.observability.JobMetrics) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    fun run() {
        try {
            jobs.run("audit_retention") { retention.purge() }
        } catch (e: Exception) {
            log.error("감사 로그 보존 처리에 실패했습니다", e)
        }
    }
}
