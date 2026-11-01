package dev.yona.observability

import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HTTP 지표의 카디널리티를 검증한다(로드맵 X-10 C4, `decisions.md` D-64, A-320 2번). `uri` 태그는 요청 경로가 아니라 라우트 템플릿이어야 하고
 * 사용자 이름, 프로젝트 이름, 번호, 모르는 경로가 태그 값을 늘리면 안 된다.
 */
@AutoConfigureMockMvc
class ObservabilityMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var registry: MeterRegistry

    private fun uriTags(): Set<String> = registry.find("http.server.requests").meters().mapNotNull { it.id.getTag("uri") }.toSet()

    @Test
    fun `uri 태그는 사용자와 프로젝트 이름을 담지 않는다`() {
        val names = listOf("zzuser1" to "zzproj1", "zzuser2" to "zzproj2", "zzuser3" to "zzproj3")
        names.forEach { (u, p) ->
            mockMvc.perform(get("/$u/$p/issues")); mockMvc.perform(get("/$u/$p")); mockMvc.perform(get("/$u/$p/issue/7")); mockMvc.perform(get("/$u"))
            mockMvc.perform(get("/$u/$p.git/info/refs?service=git-upload-pack")); mockMvc.perform(post("/$u/$p.git/git-receive-pack"))
            mockMvc.perform(get("/-_-api/v1/owners/$u/projects/$p"))
        }
        val tags = uriTags()
        assertTrue(tags.isNotEmpty(), "http.server.requests 지표가 기록되지 않았다")
        val leaked = tags.filter { t -> names.any { (u, p) -> t.contains(u) || t.contains(p) } }
        assertTrue(leaked.isEmpty(), "경로의 이름이 태그에 새었다: $leaked (전체 $tags)")
    }

    @Test
    fun `모르는 경로가 태그를 늘리지 않는다`() {
        repeat(30) { mockMvc.perform(get("/scan/$it/a/b/c/$it/d")); mockMvc.perform(get("/wp-login-$it.php")) }
        val tags = uriTags()
        assertTrue(tags.isNotEmpty(), "http.server.requests 지표가 기록되지 않았다")
        assertTrue(tags.none { it.contains("wp-login") || it.contains("/scan/") }, "모르는 경로가 태그로 남았다: $tags")
    }

    @Test
    fun `태그 값의 종류가 요청 수와 무관하게 작다`() {
        repeat(50) { mockMvc.perform(get("/u$it/p$it/issues")); mockMvc.perform(get("/u$it/p$it/pulls/$it")) }
        val before = uriTags().size
        assertTrue(before > 0, "http.server.requests 지표가 기록되지 않았다")
        repeat(50) { mockMvc.perform(get("/v$it/q$it/issues")); mockMvc.perform(get("/v$it/q$it/pulls/$it")) }
        assertTrue(uriTags().size == before, "요청이 늘자 uri 태그 종류가 늘었다: $before -> ${uriTags().size}")
    }

    @Autowired private lateinit var env: org.springframework.core.env.Environment

    /**
     * p95를 `histogram_quantile`로 계산하려면 히스토그램 버킷을 내보내야 한다. 대시보드와 알림이 쓰는 타이머만 켠다(시리즈 수를 아끼려고 모두 켜지는 않는다).
     * 시험용 `SimpleMeterRegistry`는 합산 가능한 퍼센타일을 지원하지 않아 버킷을 보지 못하므로 설정이 바인딩됐는지만 확인한다. 실제 효과는 OTLP로 Prometheus에
     * 내보내 `le` 레이블로 확인했다.
     */
    @Test
    fun `대시보드가 쓰는 타이머는 히스토그램 버킷을 켠다`() {
        listOf("http.server.requests", "yona.vcs.request", "yona.vcs.operation.duration", "yona.job.duration", "yona.background.duration", "yona.ldap.authenticate", "yona.search.duration")
            .forEach { assertEquals("true", env.getProperty("management.metrics.distribution.percentiles-histogram.$it"), it) }
    }
}
