package dev.yona.observability

import dev.yona.markdown.RenderedMarkdownCache
import dev.yona.security.SessionUserCache
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * 렌더링 캐시(`markdown_render`)와 세션 사용자 캐시(`session_user`)의 적중 지표를 등록한다(로드맵 X-10 C5d). Micrometer의 Caffeine 지표(`cache.gets{cache, result}`,
 * `cache.size`, `cache.evictions` 등)를 쓴다. 적중률이 떨어지면 마크다운 렌더링과 권한 계산 비용이 늘어난다.
 */
@Component
class CacheMetricsBinder(private val markdown: RenderedMarkdownCache, private val sessions: SessionUserCache) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        markdown.bindMetrics(registry, "markdown_render")
        sessions.bindMetrics(registry, "session_user")
    }
}
