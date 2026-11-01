package dev.yona.observability

import dev.yona.domain.audit.AuditRetentionScheduler
import dev.yona.domain.audit.AuditStreamScheduler
import dev.yona.domain.misc.TemporaryFileCleaner
import dev.yona.domain.notification.NotificationCleaner
import dev.yona.domain.pullrequest.BackgroundJobRunner
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 백그라운드 작업이 공통 지표(`JobMetrics`)에 실제로 연결됐는지 검증한다(로드맵 X-10 C5b). 각 작업을 한 번 실행하면 `yona.job.duration`이 늘고
 * 마지막 성공 시각이 0이 아니게 된다.
 */
class JobWiringTest : AbstractIntegrationTest() {

    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var notificationCleaner: NotificationCleaner
    @Autowired private lateinit var temporaryFileCleaner: TemporaryFileCleaner
    @Autowired private lateinit var auditRetention: AuditRetentionScheduler
    @Autowired private lateinit var auditStream: AuditStreamScheduler
    @Autowired private lateinit var background: BackgroundJobRunner

    private fun successCount(job: String) = registry.find("yona.job.duration").tags("task", job, "result", "success").timer()?.count() ?: 0L
    private fun lastSuccess(job: String) = registry.find("yona.job.last.success").tag("task", job).gauge()!!.value()

    private fun assertRecorded(job: String, run: () -> Unit) {
        val before = successCount(job)
        run()
        assertEquals(before + 1, successCount(job), "$job 실행이 지표에 기록되지 않았다")
        assertTrue(lastSuccess(job) > 0, "$job 마지막 성공 시각이 비어 있다")
    }

    @Test
    fun `알림 정리가 기록된다`() = assertRecorded("notification_cleanup") { notificationCleaner.scheduled() }

    @Test
    fun `임시 첨부 정리가 기록된다`() = assertRecorded("temporary_files_cleanup") { temporaryFileCleaner.scheduled() }

    @Test
    fun `감사 보존이 기록된다`() = assertRecorded("audit_retention") { auditRetention.run() }

    @Test
    fun `감사 스트리밍이 기록된다`() = assertRecorded("audit_stream") { auditStream.run() }

    @Test
    fun `푸시 뒤 작업과 병합 같은 백그라운드 일의 실행 시간과 결과를 기록한다`() {
        fun count(result: String) = registry.find("yona.background.duration").tag("result", result).timer()?.count() ?: 0L
        val ok = count("success"); val failed = count("failure")
        background.run("test job one", { })
        background.run("test job two", { throw IllegalStateException("boom") })
        assertEquals(ok + 1, count("success")); assertEquals(failed + 1, count("failure"))
        assertEquals(0.0, registry.find("yona.background.active").gauge()!!.value())
    }

    @Test
    fun `백그라운드 일의 설명은 지표 태그가 되지 않는다`() {
        background.run("job for project zzprivate-name", { })
        assertTrue(registry.meters.none { m -> m.id.tags.any { it.value.contains("zzprivate-name") } })
    }
}
