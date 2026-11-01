package dev.yona.observability

import dev.yona.domain.audit.AuditBoundary
import dev.yona.domain.user.User
import dev.yona.support.AbstractIntegrationTest
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 인증과 접근 거부의 지표(로드맵 X-10 C5d)를 검증한다. 감사 로그의 경계 이벤트와 같은 지점에서 센다. 급증은 공격 신호이다.
 * 감사 기록은 같은 주소의 차단을 1분에 한 번만 남기지만 지표는 매번 센다. 태그에는 로그인 ID, 주소, 경로를 넣지 않는다.
 */
class SecurityMetricsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var boundary: AuditBoundary
    @Autowired private lateinit var registry: MeterRegistry

    private fun count(event: String, vararg tags: String) = registry.find("yona.security.events").tags("event", event, *tags).counters().sumOf { it.count() }

    @Test
    fun `로그인 성공과 실패와 로그아웃을 센다`() {
        val success = count("login_success"); val failure = count("login_failure"); val denied = count("login_failure", "denied", "true"); val logout = count("logout")
        val user = User(loginId = "zzsec-user")
        boundary.loginSuccess(user, "password")
        boundary.loginFailure("zzsec-someone", "unknown", denied = false)
        boundary.loginFailure("zzsec-someone", "blocked", denied = true)
        boundary.logout(user); boundary.logout(null)
        assertEquals(success + 1, count("login_success"))
        assertEquals(failure + 2, count("login_failure"))
        assertEquals(denied + 1, count("login_failure", "denied", "true"))
        assertEquals(logout + 1, count("logout"))
    }

    @Test
    fun `토큰 실패와 Basic 실패와 차단을 채널별로 센다`() {
        val token = count("token_failure", "channel", "api"); val basic = count("basic_failure", "channel", "git_http"); val blocked = count("blocked", "channel", "git_http")
        boundary.tokenFailure("api"); boundary.basicFailure("zzsec-someone", "git_http", "invalid_credentials")
        boundary.blocked("10.9.9.9", "git_http"); boundary.blocked("10.9.9.9", "git_http"); boundary.blocked("10.9.9.9", "git_http")
        assertEquals(token + 1, count("token_failure", "channel", "api"))
        assertEquals(basic + 1, count("basic_failure", "channel", "git_http"))
        assertEquals(blocked + 3, count("blocked", "channel", "git_http"), "감사 기록은 1분에 한 번이지만 지표는 매번 센다")
    }

    @Test
    fun `접근 거부와 API 범위 거부를 센다`() {
        val web = count("access_denied", "via", "web"); val api = count("access_denied", "via", "api"); val scope = count("api_scope_denied")
        boundary.accessDenied("GET", "/zzsec-owner/zzsec-project/settings", 403, "web")
        boundary.accessDenied("GET", "/-_-api/v1/zzsec", 403, "api")
        boundary.apiScopeDenied("GET", "/-_-api/v1/zzsec", "scope")
        assertEquals(web + 1, count("access_denied", "via", "web")); assertEquals(api + 1, count("access_denied", "via", "api")); assertEquals(scope + 1, count("api_scope_denied"))
    }

    @Test
    fun `채널은 알려진 값만 태그가 되고 나머지는 other이다`() {
        boundary.tokenFailure("zz-evil-channel-${"x".repeat(100)}")
        assertTrue(count("token_failure", "channel", "other") >= 1)
    }

    @Test
    fun `태그에 로그인 ID와 주소와 경로가 들어가지 않는다`() {
        boundary.loginFailure("zzsec-leak-login", "unknown", false); boundary.blocked("10.8.8.8", "api"); boundary.accessDenied("GET", "/zzsec-leak-path", 403, "web")
        val leaked = registry.meters.filter { it.id.name == "yona.security.events" }.flatMap { m -> m.id.tags.map { it.value } }.filter { it.contains("zzsec-leak") || it.contains("10.8.8.8") }
        assertEquals(emptyList(), leaked)
    }
}
