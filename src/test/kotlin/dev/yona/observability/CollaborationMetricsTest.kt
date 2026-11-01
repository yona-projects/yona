package dev.yona.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 병합 결과, 메일, 웹훅, 검색의 지표(로드맵 X-10 C5d)를 검증한다. 태그는 고정된 값만 쓴다. */
class CollaborationMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = CollaborationMetrics(registry)

    private fun counter(name: String, vararg tags: String) = registry.find(name).tags(*tags).counter()?.count() ?: 0.0

    @Test
    fun `풀 리퀘스트 병합 결과를 센다`() {
        metrics.merge("MERGED"); metrics.merge("CONFLICT"); metrics.merge("CONFLICT")
        assertEquals(1.0, counter("yona.pull_requests.merge", "result", "merged"))
        assertEquals(2.0, counter("yona.pull_requests.merge", "result", "conflict"))
    }

    @Test
    fun `메일 발송 결과를 센다`() {
        metrics.mail("success"); metrics.mail("failure"); metrics.mail("skipped")
        assertEquals(1.0, counter("yona.mail.sent", "result", "success")); assertEquals(1.0, counter("yona.mail.sent", "result", "failure")); assertEquals(1.0, counter("yona.mail.sent", "result", "skipped"))
    }

    @Test
    fun `웹훅 결과는 상태를 분류해 센다`() {
        listOf(200, 204, 301, 404, 429, 500, 503).forEach { metrics.webhook(it) }
        metrics.webhookUnreachable()
        assertEquals(2.0, counter("yona.webhook.sent", "result", "success")); assertEquals(1.0, counter("yona.webhook.sent", "result", "redirect"))
        assertEquals(2.0, counter("yona.webhook.sent", "result", "client_error")); assertEquals(2.0, counter("yona.webhook.sent", "result", "server_error"))
        assertEquals(1.0, counter("yona.webhook.sent", "result", "unreachable"))
    }

    @Test
    fun `검색 시간은 작업 이름과 결과별로 센다`() {
        assertEquals(3, metrics.search("page") { 3 })
        assertFailsWith<IllegalStateException> { metrics.search("counts") { throw IllegalStateException() } }
        assertEquals(1, registry.find("yona.search.duration").tags("op", "page", "result", "success").timer()!!.count())
        assertEquals(1, registry.find("yona.search.duration").tags("op", "counts", "result", "failure").timer()!!.count())
    }

    @Test
    fun `알 수 없는 병합 결과는 other이다`() {
        metrics.merge("SOMETHING_NEW")
        assertEquals(1.0, counter("yona.pull_requests.merge", "result", "other"))
    }
}
