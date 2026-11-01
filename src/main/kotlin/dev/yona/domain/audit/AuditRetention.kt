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
 */
@Service
class AuditRetention(
    private val logs: AuditLogRepository,
    private val heads: AuditChainHeadRepository,
    private val recorder: AuditRecorder,
    private val sink: AuditFileSink,
    private val properties: YonaProperties,
    private val transactionManager: PlatformTransactionManager
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** [now] 기준으로 [retentionDays]일이 지난 기록을 [batchSize]건씩 옮기고 지운다. [retentionDays]가 0 이하이면 아무것도 하지 않는다. */
    fun purge(now: Long = System.currentTimeMillis(), retentionDays: Int = properties.auditRetentionDays, batchSize: Int = DEFAULT_BATCH): AuditPurgeResult {
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

    private class Batch(val count: Int, val firstId: Long, val lastId: Long, val archive: String)

    /** 한 묶음이다. 사슬의 끝을 잠그고 앞에서부터 기간을 넘긴 기록을 모은다. 보관 파일을 디스크에 쓴 뒤에 지우고 사슬의 끝에 지운 해시를 남긴다. */
    private fun purgeBatch(cutoff: Long, batchSize: Int): Batch? {
        val head = heads.lockHead() ?: return null
        val old = logs.findByIdGreaterThanOrderByIdAsc(head.prunedThroughId, PageRequest.of(0, batchSize)).takeWhile { it.createdMillis < cutoff }
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
class AuditRetentionScheduler(private val retention: AuditRetention) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(initialDelayString = "PT10M", fixedDelayString = "PT6H")
    fun run() {
        try {
            retention.purge()
        } catch (e: Exception) {
            log.error("감사 로그 보존 처리에 실패했습니다", e)
        }
    }
}
