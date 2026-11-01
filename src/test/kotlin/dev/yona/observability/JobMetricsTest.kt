package dev.yona.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * 백그라운드 작업 공통 지표(로드맵 X-10 C5b, `decisions.md` A-320 7번)를 검증한다. 작업마다 실행 시간(결과별), 마지막 성공 시각, 실행 중 수를 남긴다.
 * 마지막 성공 시각이 있어야 "작업이 조용히 멈춘 것"을 알림으로 잡을 수 있다.
 */
class JobMetricsTest {

    private val registry = SimpleMeterRegistry()
    private var now = Instant.parse("2026-11-01T00:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now
    }
    private val jobs = JobMetrics(registry, clock)

    private fun count(job: String, result: String) = registry.find("yona.job.duration").tags("task", job, "result", result).timer()?.count() ?: 0L
    private fun lastSuccess(job: String) = registry.find("yona.job.last.success").tag("task", job).gauge()?.value()

    @Test
    fun `성공하면 결과를 돌려주고 성공 횟수와 마지막 성공 시각을 남긴다`() {
        now = Instant.parse("2026-11-01T00:00:10Z")
        assertEquals(42, jobs.run("sample") { 42 })
        assertEquals(1, count("sample", "success"))
        assertEquals(now.epochSecond.toDouble(), lastSuccess("sample"))
    }

    @Test
    fun `실패하면 예외를 다시 던지고 실패 횟수만 올리며 마지막 성공 시각은 그대로이다`() {
        now = Instant.parse("2026-11-01T00:00:10Z"); jobs.run("sample") { }
        val before = lastSuccess("sample")
        now = Instant.parse("2026-11-01T00:05:00Z")
        assertFailsWith<IllegalStateException> { jobs.run("sample") { throw IllegalStateException("boom") } }
        assertEquals(1, count("sample", "failure")); assertEquals(1, count("sample", "success"))
        assertEquals(before, lastSuccess("sample"))
    }

    @Test
    fun `알려진 작업은 한 번도 안 돌았어도 마지막 성공 시각이 0으로 있다`() {
        JobMetrics.KNOWN.forEach { assertEquals(0.0, lastSuccess(it), it) }
        assertEquals(true, JobMetrics.KNOWN.containsAll(listOf("notification_mail", "notification_cleanup", "temporary_files_cleanup", "audit_retention", "audit_stream", "update_check")))
    }

    @Test
    fun `실행 중인 수를 알린다`() {
        var during = -1.0
        jobs.run("sample") { during = registry.find("yona.job.active").tag("task", "sample").gauge()!!.value() }
        assertEquals(1.0, during)
        assertEquals(0.0, registry.find("yona.job.active").tag("task", "sample").gauge()!!.value())
        runCatching { jobs.run("sample") { throw IllegalStateException() } }
        assertEquals(0.0, registry.find("yona.job.active").tag("task", "sample").gauge()!!.value())
    }

    @Test
    fun `모르는 작업 이름도 쓸 수 있다`() {
        jobs.run("another") { }
        assertEquals(1, count("another", "success"))
    }
}
