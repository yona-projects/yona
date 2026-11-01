package dev.yona.observability

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * VCS(git, svn, hg) 공통 지표(로드맵 X-10 C5c, `decisions.md` D-64 5번과 A-320)를 검증한다. 이름은 VCS 중립(`yona.vcs.*`)이고 `vcs` 태그만 다르다.
 * 태그는 고정된 낮은 카디널리티 값이어야 하며 프로젝트, 사용자, 경로 이름이 들어가지 않는다.
 */
class VcsMetricsTest {

    private val registry = SimpleMeterRegistry()
    private val metrics = VcsMetrics(registry)

    private fun timer(name: String, vararg tags: String) = registry.find(name).tags(*tags).timer()
    private fun counter(name: String, vararg tags: String) = registry.find(name).tags(*tags).counter()

    // --- 요청 ---

    @Test
    fun `요청의 시간과 횟수를 vcs와 서비스와 대상과 결과 태그로 남긴다`() {
        metrics.request("git", "upload_pack") { }
        assertEquals(1, timer("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "ok")!!.count())
    }

    @Test
    fun `결과를 바꿀 수 있고 위키 대상은 따로 센다`() {
        metrics.request("git", "receive_pack", target = "wiki") { it.result = "denied" }
        assertEquals(1, timer("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "wiki", "result", "denied")!!.count())
    }

    @Test
    fun `예외가 나면 error로 기록하고 다시 던진다`() {
        assertFailsWith<IllegalStateException> { metrics.request("svn", "MERGE") { throw IllegalStateException("boom") } }
        assertEquals(1, timer("yona.vcs.request", "vcs", "svn", "service", "MERGE", "result", "error")!!.count())
    }

    @Test
    fun `진행 중인 요청 수를 알린다`() {
        var during = -1.0
        metrics.request("git", "upload_pack") { during = registry.find("yona.vcs.request.active").tags("vcs", "git", "service", "upload_pack").gauge()!!.value() }
        assertEquals(1.0, during)
        assertEquals(0.0, registry.find("yona.vcs.request.active").tags("vcs", "git", "service", "upload_pack").gauge()!!.value())
        runCatching { metrics.request("git", "upload_pack") { throw IllegalStateException() } }
        assertEquals(0.0, registry.find("yona.vcs.request.active").tags("vcs", "git", "service", "upload_pack").gauge()!!.value())
    }

    @Test
    fun `HTTP 상태를 결과로 바꾼다`() {
        assertEquals("ok", VcsMetrics.resultOf(200)); assertEquals("ok", VcsMetrics.resultOf(207)); assertEquals("ok", VcsMetrics.resultOf(304))
        assertEquals("denied", VcsMetrics.resultOf(401)); assertEquals("denied", VcsMetrics.resultOf(403)); assertEquals("denied", VcsMetrics.resultOf(429))
        assertEquals("not_found", VcsMetrics.resultOf(404)); assertEquals("client_error", VcsMetrics.resultOf(400)); assertEquals("client_error", VcsMetrics.resultOf(409))
        assertEquals("error", VcsMetrics.resultOf(500)); assertEquals("error", VcsMetrics.resultOf(503))
    }

    @Test
    fun `프로젝트의 VCS 이름을 태그 값으로 바꾼다`() {
        assertEquals("git", VcsMetrics.vcsOf("GIT")); assertEquals("svn", VcsMetrics.vcsOf("Subversion")); assertEquals("hg", VcsMetrics.vcsOf("Mercurial"))
        assertEquals("other", VcsMetrics.vcsOf("whatever")); assertEquals("other", VcsMetrics.vcsOf(null))
    }

    @Test
    fun `svn 메서드는 알려진 WebDAV 메서드만 태그가 되고 나머지는 other이다`() {
        assertEquals("PROPFIND", VcsMetrics.svnServiceOf("propfind")); assertEquals("MERGE", VcsMetrics.svnServiceOf("MERGE")); assertEquals("GET", VcsMetrics.svnServiceOf("GET"))
        assertEquals("other", VcsMetrics.svnServiceOf("EVIL-$%^")); assertEquals("other", VcsMetrics.svnServiceOf("x".repeat(500)))
    }

    // --- 전송량 ---

    @Test
    fun `전송 바이트를 방향별로 더한다`() {
        metrics.transfer("git", "code", "in", 100); metrics.transfer("git", "code", "in", 50); metrics.transfer("git", "code", "out", 7); metrics.transfer("git", "code", "out", -5); metrics.transfer("git", "code", "out", 0)
        assertEquals(150.0, counter("yona.vcs.transfer", "vcs", "git", "direction", "in")!!.count())
        assertEquals(7.0, counter("yona.vcs.transfer", "vcs", "git", "direction", "out")!!.count())
        assertEquals("bytes", counter("yona.vcs.transfer", "direction", "in")!!.id.baseUnit)
    }

    @Test
    fun `세는 입력과 출력 스트림이 읽고 쓴 바이트를 더한다`() {
        val input = metrics.countedInput("git", "code", java.io.ByteArrayInputStream(ByteArray(10)))
        input.readAllBytes()
        val sink = java.io.ByteArrayOutputStream()
        val output = metrics.countedOutput("git", "code", sink)
        output.write(ByteArray(7)); output.write(1); output.flush()
        assertEquals(10.0, counter("yona.vcs.transfer", "direction", "in")!!.count())
        assertEquals(8.0, counter("yona.vcs.transfer", "direction", "out")!!.count())
        assertEquals(8, sink.size())
    }

    // --- 푸시 ---

    @Test
    fun `푸시 결과를 ref 종류별로 센다`() {
        metrics.push("git", "refs/heads/main", "ok"); metrics.push("git", "refs/tags/v1", "ok"); metrics.push("git", "refs/notes/x", "ok")
        metrics.push("git", "refs/heads/main", "rejected", reason = "ruleset_force")
        assertEquals(1.0, counter("yona.vcs.pushes", "kind", "branch", "result", "ok")!!.count())
        assertEquals(1.0, counter("yona.vcs.pushes", "kind", "tag", "result", "ok")!!.count())
        assertEquals(1.0, counter("yona.vcs.pushes", "kind", "other", "result", "ok")!!.count())
        assertEquals(1.0, counter("yona.vcs.pushes", "kind", "branch", "result", "rejected", "reason", "ruleset_force")!!.count())
    }

    // --- 훅과 작업 ---

    @Test
    fun `훅의 시간과 실패를 센다`() {
        metrics.hook("last_pushed") { }
        runCatching { metrics.hook("recent_branch") { throw IllegalStateException("x") } }
        assertEquals(1, timer("yona.vcs.hook.duration", "hook", "last_pushed", "result", "success")!!.count())
        assertEquals(1, timer("yona.vcs.hook.duration", "hook", "recent_branch", "result", "failure")!!.count())
    }

    @Test
    fun `저장소 작업의 시간을 센다`() {
        assertEquals(5, metrics.operation("GIT", "diff") { 5 })
        assertEquals(1, timer("yona.vcs.operation.duration", "vcs", "git", "op", "diff", "result", "success")!!.count())
        assertFailsWith<IllegalStateException> { metrics.operation("Subversion", "history") { throw IllegalStateException() } }
        assertEquals(1, timer("yona.vcs.operation.duration", "vcs", "svn", "op", "history", "result", "failure")!!.count())
    }

    // --- 추적 구간(D-64 4번: git과 svn 요청의 세부 구간) ---

    private class Collector : io.opentelemetry.sdk.trace.SpanProcessor {
        val ended = java.util.concurrent.CopyOnWriteArrayList<io.opentelemetry.sdk.trace.data.SpanData>()
        override fun onStart(parentContext: io.opentelemetry.context.Context, span: io.opentelemetry.sdk.trace.ReadWriteSpan) {}
        override fun isStartRequired() = false
        override fun onEnd(span: io.opentelemetry.sdk.trace.ReadableSpan) { ended += span.toSpanData() }
        override fun isEndRequired() = true
    }

    private fun traced(): Pair<VcsMetrics, Collector> {
        val collector = Collector()
        val sdk = io.opentelemetry.sdk.OpenTelemetrySdk.builder().setTracerProvider(io.opentelemetry.sdk.trace.SdkTracerProvider.builder().addSpanProcessor(collector).build()).build()
        return VcsMetrics(registry, sdk) to collector
    }

    private fun attr(span: io.opentelemetry.sdk.trace.data.SpanData, key: String) = span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey(key))

    @Test
    fun `구간은 이름과 속성을 가진 span이 되고 결과를 돌려준다`() {
        val (m, collector) = traced()
        assertEquals(7, m.span("vcs.open_repository", "vcs" to "git", "service" to "upload_pack") { 7 })
        val span = collector.ended.single()
        assertEquals("vcs.open_repository", span.name); assertEquals("git", attr(span, "vcs")); assertEquals("upload_pack", attr(span, "service"))
    }

    @Test
    fun `구간은 현재 span의 자식이다`() {
        val (m, collector) = traced()
        m.span("vcs.outer") { m.span("vcs.inner") { } }
        val inner = collector.ended.first { it.name == "vcs.inner" }; val outer = collector.ended.first { it.name == "vcs.outer" }
        assertEquals(outer.spanId, inner.parentSpanId); assertEquals(outer.traceId, inner.traceId)
    }

    @Test
    fun `예외가 나면 span에 오류를 기록하고 다시 던진다`() {
        val (m, collector) = traced()
        assertFailsWith<IllegalStateException> { m.span("vcs.transfer") { throw IllegalStateException("boom") } }
        val span = collector.ended.single()
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR, span.status.statusCode)
        assertEquals(true, span.events.any { it.name == "exception" })
    }

    @Test
    fun `추적이 없어도 구간 블록은 그대로 실행된다`() {
        assertEquals(3, metrics.span("vcs.x") { 3 })
    }

    @Test
    fun `훅은 hook 속성이 있는 span도 만든다`() {
        val (m, collector) = traced()
        m.hook("last_pushed") { }
        val span = collector.ended.single { it.name == "vcs.hook" }
        assertEquals("last_pushed", attr(span, "hook"))
    }

    @Test
    fun `span 속성에 이름과 경로를 넣지 않도록 호출자가 고정된 값만 준다`() {
        val (m, collector) = traced()
        m.span("vcs.authorize", "vcs" to "git") { }
        assertEquals(setOf("vcs"), collector.ended.single().attributes.asMap().keys.map { it.key }.toSet())
    }
}
