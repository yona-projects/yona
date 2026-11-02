package dev.yona.domain.oauth2server

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OAuth 앱의 리디렉션 주소 규칙(X-1, `decisions.md` D-71, `architecture/oauth2-server-plan.md` 3-6, 3-9)을 검증한다: 등록할 수 있는 주소의 모양, 요청한 주소와 등록한 주소의 일치(루프백은 포트를 무시하고 `localhost`, `127.0.0.1`, `[::1]`를 같게 본다).
 */
class RedirectUrisTest {

    @Test
    fun `https 주소와 루프백 http 주소는 등록할 수 있다`() {
        for (ok in listOf("https://example.com/callback", "https://app.example.com:8443/cb?x=1", "http://localhost/callback", "http://localhost:8080/callback", "http://127.0.0.1:3000/cb", "http://[::1]:5000/cb", "https://antigravity.google/oauth-callback")) {
            assertNull(RedirectUris.problem(ok), ok)
        }
    }

    @Test
    fun `루프백이 아닌 http와 위험한 스킴과 조각과 사용자 정보와 빈 값은 거부한다`() {
        for (bad in listOf("http://example.com/cb", "javascript:alert(1)", "data:text/html,x", "file:///etc/passwd", "ftp://example.com/cb", "https://example.com/cb#frag", "https://user:pw@example.com/cb", "https:///cb", "//example.com/cb", "/relative", "", "   ", "https://exa mple.com/cb", "https://example.com/" + "a".repeat(600))) {
            assertTrue(RedirectUris.problem(bad) != null, "거부해야 한다: $bad")
        }
    }

    @Test
    fun `요청한 주소가 등록한 주소와 정확히 같으면 통과한다`() {
        assertTrue(RedirectUris.matches(listOf("https://example.com/cb"), "https://example.com/cb"))
        assertFalse(RedirectUris.matches(listOf("https://example.com/cb"), "https://example.com/cb/"))
        assertFalse(RedirectUris.matches(listOf("https://example.com/cb"), "https://example.com/cb?x=1"))
        assertFalse(RedirectUris.matches(listOf("https://example.com/cb"), "https://evil.example.com/cb"))
        assertFalse(RedirectUris.matches(emptyList(), "https://example.com/cb"))
    }

    @Test
    fun `등록한 루프백 주소는 포트를 무시하고 localhost와 127_0_0_1과 대괄호 IPv6를 같게 본다`() {
        val registered = listOf("http://localhost/callback")
        assertTrue(RedirectUris.matches(registered, "http://localhost:51234/callback"))
        assertTrue(RedirectUris.matches(registered, "http://127.0.0.1:51234/callback"))
        assertTrue(RedirectUris.matches(registered, "http://[::1]:51234/callback"))
        assertFalse(RedirectUris.matches(registered, "http://localhost:51234/other"), "경로는 같아야 한다")
        assertFalse(RedirectUris.matches(registered, "https://localhost:51234/callback"), "스킴은 같아야 한다")
        assertFalse(RedirectUris.matches(registered, "http://localhost.evil.com:51234/callback"))
    }

    @Test
    fun `포트를 적어 등록한 루프백 주소도 포트를 무시한다`() {
        assertTrue(RedirectUris.matches(listOf("http://127.0.0.1:8080/cb"), "http://127.0.0.1:9999/cb"))
        assertTrue(RedirectUris.matches(listOf("http://localhost:8080/cb"), "http://127.0.0.1:9999/cb"))
    }

    @Test
    fun `루프백이 아닌 주소는 포트까지 정확히 같아야 한다`() {
        assertFalse(RedirectUris.matches(listOf("https://example.com/cb"), "https://example.com:444/cb"))
        assertTrue(RedirectUris.matches(listOf("https://example.com:8443/cb"), "https://example.com:8443/cb"))
    }

    @Test
    fun `줄 단위 글을 목록으로 읽고 정리한다`() {
        assertEquals(listOf("https://a.example/cb", "http://localhost/cb"), RedirectUris.parse("  https://a.example/cb \r\n\n http://localhost/cb \n https://a.example/cb "))
        assertEquals(emptyList(), RedirectUris.parse(null))
        assertEquals("https://a.example/cb\nhttp://localhost/cb", RedirectUris.format(listOf("https://a.example/cb", "http://localhost/cb")))
    }
}
