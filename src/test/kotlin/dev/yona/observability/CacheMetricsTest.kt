package dev.yona.observability

import dev.yona.markdown.RenderedMarkdownCache
import dev.yona.security.SessionUserCache
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 렌더링 캐시와 세션 사용자 캐시의 적중 지표(로드맵 X-10 C5d)를 검증한다. */
class CacheMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var registry: MeterRegistry
    @Autowired private lateinit var markdown: RenderedMarkdownCache
    @Autowired private lateinit var sessions: SessionUserCache

    private fun gets(cache: String, result: String) = registry.find("cache.gets").tags("cache", cache, "result", result).functionCounter()?.count() ?: 0.0

    @Test
    fun `마크다운 렌더링 캐시의 적중과 미적중을 센다`() {
        val hit = gets("markdown_render", "hit"); val miss = gets("markdown_render", "miss")
        val source = "zz-cache-metrics-${System.nanoTime()}"
        markdown.getOrRender(source) { "rendered" }
        markdown.getOrRender(source) { "rendered" }
        assertEquals(miss + 1, gets("markdown_render", "miss")); assertEquals(hit + 1, gets("markdown_render", "hit"))
    }

    @Test
    fun `세션 사용자 캐시의 지표가 있다`() {
        assertNotNull(registry.find("cache.size").tag("cache", "session_user").gauge())
        val before = gets("session_user", "miss") + gets("session_user", "hit")
        sessions.contains(987_654_321L)
        assertEquals(before + 1, gets("session_user", "miss") + gets("session_user", "hit"))
    }

    @Test
    fun `통계를 켠 캐시를 직접 감시해도 같은 이름을 쓴다`() {
        val local = SimpleMeterRegistry()
        RenderedMarkdownCache().also { it.bindMetrics(local, "markdown_render") }.getOrRender("x") { "y" }
        assertNotNull(local.find("cache.gets").tags("cache", "markdown_render", "result", "miss").functionCounter())
    }
}
