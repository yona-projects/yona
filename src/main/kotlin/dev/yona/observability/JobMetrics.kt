package dev.yona.observability

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 백그라운드 작업(`@Scheduled`)에 공통으로 붙이는 지표이다(로드맵 X-10 C5b, `decisions.md` A-320 7번). 작업마다 다음을 남긴다.
 * - `yona.job.duration{task, result=success|failure}`: 실행 시간과 횟수
 * - `yona.job.last.success{task}`: 마지막 성공 시각(에폭 초). 한 번도 성공하지 않았으면 0이다. 알림은 `time() - 이 값`이 커지면 "작업이 조용히 멈춤"으로 본다
 * - `yona.job.active{task}`: 지금 실행 중인 수
 *
 * 태그 이름은 `task`이다. `job`과 `instance`는 Prometheus의 예약 레이블(서비스 이름과 인스턴스)이라 같은 이름의 태그가 사라진다(OTLP로 보내 실제로 확인했다).
 * 작업 이름은 낮은 카디널리티의 고정 값이다([KNOWN]). 알려진 작업은 시작할 때 값을 미리 만들어 둔다(한 번도 안 돌아도 알림이 잡을 수 있게).
 * 작업이 예외를 던지면 기록한 뒤 다시 던진다(각 작업이 기존대로 로그를 남긴다).
 */
@Component
class JobMetrics(private val registry: MeterRegistry, private val clock: Clock) {

    private val lastSuccess = ConcurrentHashMap<String, AtomicLong>()
    private val active = ConcurrentHashMap<String, AtomicInteger>()

    init {
        KNOWN.forEach { register(it) }
    }

    private fun register(job: String) {
        lastSuccess.computeIfAbsent(job) { AtomicLong(0).also { value -> Gauge.builder("yona.job.last.success", value) { it.get().toDouble() }.tag("task", job).baseUnit("seconds").description("작업의 마지막 성공 시각(에폭 초), 성공한 적이 없으면 0").register(registry) } }
        active.computeIfAbsent(job) { AtomicInteger(0).also { value -> Gauge.builder("yona.job.active", value) { it.get().toDouble() }.tag("task", job).description("지금 실행 중인 수").register(registry) } }
    }

    /** [block]을 [job] 작업으로 실행하며 지표를 남긴다. */
    fun <T> run(job: String, block: () -> T): T {
        register(job)
        val running = active.getValue(job)
        running.incrementAndGet()
        val sample = Timer.start(registry)
        try {
            val result = block()
            sample.stop(timer(job, "success"))
            lastSuccess.getValue(job).set(clock.instant().epochSecond)
            return result
        } catch (e: Throwable) {
            sample.stop(timer(job, "failure"))
            throw e
        } finally {
            running.decrementAndGet()
        }
    }

    private fun timer(job: String, result: String): Timer = Timer.builder("yona.job.duration").tag("task", job).tag("result", result).description("작업 실행 시간").register(registry)

    companion object {
        /** 알려진 작업 이름이다. 값이 늘지 않도록 고정한다. */
        val KNOWN = listOf("notification_mail", "notification_cleanup", "temporary_files_cleanup", "audit_retention", "audit_stream", "update_check", "storage_scan")
    }
}
