package dev.yona.observability

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/**
 * 협업 흐름과 외부 연동의 지표이다(로드맵 X-10 C5d). 태그는 모두 고정된 값이며 프로젝트, 사용자, 주소 이름을 넣지 않는다.
 *
 * | 지표 | 태그 | 설명 |
 * |---|---|---|
 * | `yona.pull_requests.merge` | `result`(merged, conflict, method_not_allowed, not_reviewed, updates_restricted, other) | 병합 시도의 결과 |
 * | `yona.mail.sent` | `result`(success, failure, skipped) | 알림 메일 발송 결과(skipped는 메일 서버를 설정하지 않음) |
 * | `yona.webhook.sent` | `result`(success, redirect, client_error, server_error, unreachable) | 프로젝트 웹훅 발송 결과 |
 * | `yona.search.duration` | `op`(counts, page), `result` | 통합 검색의 시간 |
 */
@Component
class CollaborationMetrics(private val registry: MeterRegistry) {

    fun merge(result: String) = count("yona.pull_requests.merge", "result", result.lowercase().takeIf { it in MERGE_RESULTS } ?: "other", "병합 시도 결과")

    fun mail(result: String) = count("yona.mail.sent", "result", result, "알림 메일 발송 결과")

    /** 웹훅이 응답한 [status]를 분류해 센다. */
    fun webhook(status: Int) = count(
        "yona.webhook.sent", "result", when (status) { in 200..299 -> "success"; in 300..399 -> "redirect"; in 400..499 -> "client_error"; else -> "server_error" }, "웹훅 발송 결과"
    )

    /** 웹훅 주소에 연결하지 못했다. */
    fun webhookUnreachable() = count("yona.webhook.sent", "result", "unreachable", "웹훅 발송 결과")

    /** 검색 작업 [op]을 실행하며 시간을 남긴다. 예외는 기록하고 다시 던진다. */
    fun <T> search(op: String, block: () -> T): T {
        val sample = Timer.start(registry)
        var result = "success"
        try {
            return block()
        } catch (e: Throwable) {
            result = "failure"
            throw e
        } finally {
            sample.stop(Timer.builder("yona.search.duration").tags("op", op, "result", result).description("통합 검색 시간").register(registry))
        }
    }

    private fun count(name: String, tagKey: String, tagValue: String, description: String) =
        Counter.builder(name).tag(tagKey, tagValue).description(description).register(registry).increment()

    private companion object {
        val MERGE_RESULTS = setOf("merged", "conflict", "method_not_allowed", "not_reviewed", "updates_restricted")
    }
}
