package dev.yona.util

import dev.yona.config.YonaProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals

/**
 * 사이트 주소의 스킴을 검증한다 (v1.6 `utils.Config.getScheme`).
 *
 * 설정 `application.scheme`이 있으면 그 값이고, 없으면 요청 안팎 모두 `http`이다. 요청이 https여도, `X-Forwarded-Proto`가 있어도 보지 않는다.
 * 가입 인증 메일만 `request().secure()`로 고르고 호스트는 설정 호스트 이름(없으면 `localhost:9000`)이다 (`UserApp.getServeIndexPageUrl`).
 */
class SiteUrlsTest {

    @AfterEach
    fun clear() = RequestContextHolder.resetRequestAttributes()

    private fun request(secure: Boolean) {
        val request = MockHttpServletRequest().apply { scheme = if (secure) "https" else "http"; isSecure = secure; serverName = "example.org"; addHeader("Host", "example.org") }
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @Test
    fun `설정이 없으면 https 요청이어도 http이다`() {
        request(secure = true)
        assertEquals("http://example.org/a", SiteUrls(YonaProperties()).absolute("/a"))
        assertEquals("http", SiteUrls(YonaProperties()).scheme())
    }

    @Test
    fun `설정이 있으면 그 값이다`() {
        request(secure = false)
        assertEquals("https://example.org/a", SiteUrls(YonaProperties(scheme = "https")).absolute("/a"))
    }

    @Test
    fun `요청 밖에서도 http이다`() {
        assertEquals("http", SiteUrls(YonaProperties()).scheme())
    }

    @Test
    fun `가입 인증 메일만 요청의 https 여부로 고르고 호스트는 설정 또는 localhost 9000이다`() {
        request(secure = true)
        assertEquals("https://localhost:9000", SiteUrls(YonaProperties()).signupMailOrigin())
        assertEquals("https://yona.example.com:8080", SiteUrls(YonaProperties(hostname = "yona.example.com", port = 8080)).signupMailOrigin())
        request(secure = false)
        assertEquals("http://yona.example.com", SiteUrls(YonaProperties(hostname = "yona.example.com", port = 80)).signupMailOrigin())
    }

    @Test
    fun `목록으로 돌아가는 주소는 호스트와 포트와 경로가 모두 같을 때만 Referer이다 (v1_6 urlToList)`() {
        val urls = SiteUrls(YonaProperties(scheme = "http", hostname = "yona.example.com", port = 8080))
        val default = "/o/p/posts"
        assertEquals("/o/p/posts?pageNum=2", urls.urlToList("/o/p/posts?pageNum=2", default))
        assertEquals("http://yona.example.com:8080/o/p/posts?x=1", urls.urlToList("http://yona.example.com:8080/o/p/posts?x=1", default))
        // 호스트가 다르거나 포트가 다르면 기본 주소이다
        assertEquals(default, urls.urlToList("http://10.0.0.1:8080/o/p/posts?x=1", default))
        assertEquals(default, urls.urlToList("http://yona.example.com:9090/o/p/posts", default))
        assertEquals(default, urls.urlToList("http://yona.example.com:8080/o/p/other", default))
        assertEquals(default, urls.urlToList("http://bad host/", default))
    }
}
