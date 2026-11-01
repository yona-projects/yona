package dev.yona.observability

import dev.yona.security.YonaAuthentication
import dev.yona.security.YonaPrincipal
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 로그와 추적을 잇는 요청 문맥(로드맵 X-10 C3, `decisions.md` D-64)을 검증한다. 요청 번호(`request_id`)는 MDC와 요청 속성에 같은 값으로 넣어
 * 감사 기록이 그대로 쓰게 하고, 사용자 번호(`user_id`)는 설정이 켜져 있을 때만 MDC에 넣는다(이름과 이메일은 넣지 않는다). 요청이 끝나면 모두 비운다.
 */
class RequestLogContextTest {

    @AfterEach
    fun cleanUp() {
        MDC.clear(); SecurityContextHolder.clearContext()
    }

    private fun run(filter: jakarta.servlet.Filter, request: MockHttpServletRequest = MockHttpServletRequest("GET", "/x"), seen: (MockHttpServletRequest) -> Unit = {}) {
        filter.doFilter(request, MockHttpServletResponse(), FilterChain { req, _ -> seen(req as MockHttpServletRequest) })
    }

    // --- 요청 번호 ---

    @Test
    fun `요청 번호를 새로 만들어 MDC와 요청 속성에 같은 값으로 넣는다`() {
        var inMdc: String? = null; var inAttr: String? = null
        run(RequestIdFilter()) { inMdc = MDC.get("request_id"); inAttr = it.getAttribute(RequestIds.ATTRIBUTE) as String? }
        assertNotNull(inMdc); assertEquals(inMdc, inAttr)
        assertTrue(inMdc!!.length in 8..64)
    }

    @Test
    fun `X-Request-Id 헤더가 올바르면 그대로 쓴다`() {
        val request = MockHttpServletRequest("GET", "/x").apply { addHeader("X-Request-Id", "abc-123_DEF.x") }
        var seen: String? = null
        run(RequestIdFilter(), request) { seen = MDC.get("request_id") }
        assertEquals("abc-123_DEF.x", seen)
    }

    @Test
    fun `헤더가 너무 길거나 허용하지 않는 글자가 있으면 새로 만든다`() {
        listOf("x".repeat(65), "has space", "semi;colon", "줄\\n바꿈", "").forEach { bad ->
            val request = MockHttpServletRequest("GET", "/x").apply { addHeader("X-Request-Id", bad) }
            var seen: String? = null
            run(RequestIdFilter(), request) { seen = MDC.get("request_id") }
            assertNotEquals(bad, seen, bad); assertNotNull(seen)
        }
    }

    @Test
    fun `요청이 끝나면 MDC를 비우고 예외가 나도 비운다`() {
        run(RequestIdFilter())
        assertNull(MDC.get("request_id"))
        assertFailsWith<IllegalStateException> {
            RequestIdFilter().doFilter(MockHttpServletRequest(), MockHttpServletResponse(), FilterChain { _, _ -> throw IllegalStateException("boom") })
        }
        assertNull(MDC.get("request_id"))
    }

    @Test
    fun `이미 정해진 요청 번호가 있으면 다시 만들지 않는다`() {
        val request = MockHttpServletRequest("GET", "/x").apply { setAttribute(RequestIds.ATTRIBUTE, "fixed-1") }
        var seen: String? = null
        run(RequestIdFilter(), request) { seen = MDC.get("request_id") }
        assertEquals("fixed-1", seen)
    }

    // --- 사용자 번호 ---

    private fun login(userId: Long) {
        SecurityContextHolder.getContext().authentication = YonaAuthentication(YonaPrincipal(userId, "alice", "앨리스"))
    }

    @Test
    fun `로그인한 사용자의 번호만 MDC에 넣고 이름과 로그인 ID는 넣지 않는다`() {
        login(42)
        var snapshot: Map<String, String>? = null
        run(UserLogContextFilter(enabled = true)) { snapshot = MDC.getCopyOfContextMap() }
        assertEquals("42", snapshot!!["user_id"])
        assertTrue(snapshot!!.values.none { it.contains("alice") || it.contains("앨리스") }, snapshot.toString())
        assertNull(MDC.get("user_id"))
    }

    @Test
    fun `로그인하지 않았으면 user_id를 넣지 않는다`() {
        var seen: String? = "unset"
        run(UserLogContextFilter(enabled = true)) { seen = MDC.get("user_id") }
        assertNull(seen)
    }

    @Test
    fun `설정을 끄면 user_id를 넣지 않는다`() {
        login(42)
        var seen: String? = "unset"
        run(UserLogContextFilter(enabled = false)) { seen = MDC.get("user_id") }
        assertNull(seen)
    }

    // --- 추적 속성 ---

    /** 끝난 span의 속성을 모으는 간이 수집기로 추적 SDK를 만든다(외부 시험 의존성 없이). */
    private class Collector : io.opentelemetry.sdk.trace.SpanProcessor {
        val ended = java.util.concurrent.CopyOnWriteArrayList<io.opentelemetry.sdk.trace.data.SpanData>()
        override fun onStart(parentContext: io.opentelemetry.context.Context, span: io.opentelemetry.sdk.trace.ReadWriteSpan) {}
        override fun isStartRequired() = false
        override fun onEnd(span: io.opentelemetry.sdk.trace.ReadableSpan) { ended += span.toSpanData() }
        override fun isEndRequired() = true
    }

    private fun <T> withSpan(block: (Collector) -> T): Collector {
        val collector = Collector()
        val provider = io.opentelemetry.sdk.trace.SdkTracerProvider.builder().addSpanProcessor(collector).build()
        val span = provider.get("test").spanBuilder("server").startSpan()
        span.makeCurrent().use { block(collector) }
        span.end()
        return collector
    }

    @Test
    fun `요청 번호를 현재 추적 span의 속성으로도 넣는다`() {
        val request = MockHttpServletRequest("GET", "/x").apply { addHeader("X-Request-Id", "trace-req-1") }
        val collector = withSpan { run(RequestIdFilter(), request) }
        assertEquals("trace-req-1", collector.ended.single().attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("request_id")))
    }

    @Test
    fun `사용자 번호를 현재 추적 span의 속성으로도 넣는다`() {
        login(42)
        val collector = withSpan { run(UserLogContextFilter(enabled = true)) }
        assertEquals("42", collector.ended.single().attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("user_id")))
    }

    @Test
    fun `설정을 끄면 사용자 번호를 span에도 넣지 않는다`() {
        login(42)
        val collector = withSpan { run(UserLogContextFilter(enabled = false)) }
        assertNull(collector.ended.single().attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey("user_id")))
    }

    @Test
    fun `추적이 없어도 문제없이 동작한다`() {
        login(42)
        run(RequestIdFilter()); run(UserLogContextFilter(enabled = true))
    }
}
