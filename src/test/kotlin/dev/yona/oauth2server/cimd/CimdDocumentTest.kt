package dev.yona.oauth2server.cimd

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 클라이언트 ID 메타데이터 문서의 검증(IETF 초안 3~4절, X-1 5단계, `decisions.md` D-71)을 확인한다: 주소 모양, 문서 내용, 금지 필드. */
class CimdDocumentTest {

    private val url = "https://claude.example/oauth/client.json"
    private fun doc(extra: String = "", id: String = url, redirects: String = """["http://localhost/callback","http://127.0.0.1/callback"]""") =
        """{"client_id":"$id","client_name":"Claude Code","redirect_uris":$redirects$extra}"""

    private fun parse(json: String, clientUrl: String = url) = CimdDocument.parse(clientUrl, json)

    @Test
    fun `클라이언트 ID 주소는 https이고 경로가 있고 조각과 사용자 정보와 점 경로가 없어야 한다`() {
        assertNull(CimdDocument.urlProblem(url))
        assertNull(CimdDocument.urlProblem("https://example.com:8443/a/b.json"))
        for (bad in listOf("http://example.com/a", "https://example.com", "https://example.com/", "https://user@example.com/a", "https://example.com/a#x", "https://example.com/a/../b", "https://example.com/./a", "https://example.com/a?x=1", "https:///a", "ftp://example.com/a", "example.com/a", "https://exa mple.com/a", "https://example.com/" + "a".repeat(600))) {
            assertTrue(CimdDocument.urlProblem(bad) != null, "거부해야 한다: $bad")
        }
    }

    @Test
    fun `올바른 문서는 이름과 리디렉션 주소와 범위를 읽는다`() {
        val ok = assertIs<CimdDocument.Result.Valid>(parse(doc(""","client_uri":"https://claude.example","scope":"read:user repo site_admin read:ssh_key","token_endpoint_auth_method":"none","grant_types":["authorization_code","refresh_token"],"response_types":["code"]""")))
        assertEquals("Claude Code", ok.name); assertEquals("https://claude.example", ok.homeUrl)
        assertEquals(listOf("http://localhost/callback", "http://127.0.0.1/callback"), ok.redirectUris)
        assertEquals(setOf("read:user", "repo"), ok.scopes, "OAuth에 주지 않는 범위는 문서에 있어도 뺀다")
    }

    @Test
    fun `범위가 없으면 줄 수 있는 모든 범위가 허용 범위이다`() {
        val ok = assertIs<CimdDocument.Result.Valid>(parse(doc()))
        assertTrue("repo" in ok.scopes && "read:user" in ok.scopes && ok.scopes.none { it == "site_admin" || it.endsWith("_key") })
    }

    @Test
    fun `이름이 없으면 호스트 이름을 쓰고 너무 길면 줄인다`() {
        assertEquals("claude.example", assertIs<CimdDocument.Result.Valid>(parse("""{"client_id":"$url","redirect_uris":["https://claude.example/cb"]}""")).name)
        assertEquals(100, assertIs<CimdDocument.Result.Valid>(parse(doc().replace("Claude Code", "가".repeat(300)))).name.length)
    }

    @Test
    fun `client_id가 주소와 다르거나 JSON이 아니거나 객체가 아니면 거부한다`() {
        assertIs<CimdDocument.Result.Invalid>(parse(doc(id = "https://evil.example/x.json")))
        assertIs<CimdDocument.Result.Invalid>(parse("not json")); assertIs<CimdDocument.Result.Invalid>(parse("[]")); assertIs<CimdDocument.Result.Invalid>(parse(""))
        assertIs<CimdDocument.Result.Invalid>(parse("""{"redirect_uris":["https://a.example/cb"]}"""))
    }

    @Test
    fun `리디렉션 주소가 없거나 많거나 잘못되면 거부한다`() {
        assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = "[]"))); assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = "\"https://a.example/cb\"")))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = (1..11).joinToString(",", "[", "]") { "\"https://a$it.example/cb\"" })))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = """["http://example.com/cb"]""")))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = """["javascript:alert(1)"]""")))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(redirects = "[1]")))
    }

    @Test
    fun `공유 비밀 값 인증과 비밀 값과 개인 키는 거부한다`() {
        for (extra in listOf(""","token_endpoint_auth_method":"client_secret_basic"""", ""","token_endpoint_auth_method":"client_secret_post"""", ""","token_endpoint_auth_method":"client_secret_jwt"""", ""","client_secret":"x"""", ""","client_secret_expires_at":0""", ""","jwks":{"keys":[{"kty":"RSA","d":"secret"}]}""")) {
            assertIs<CimdDocument.Result.Invalid>(parse(doc(extra)), extra)
        }
        assertIs<CimdDocument.Result.Valid>(parse(doc(""","token_endpoint_auth_method":"none"""")))
    }

    @Test
    fun `지원하지 않는 승인 방식이나 응답 방식이 있으면 거부한다`() {
        assertIs<CimdDocument.Result.Invalid>(parse(doc(""","grant_types":["client_credentials"]""")))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(""","grant_types":["implicit"]""")))
        assertIs<CimdDocument.Result.Invalid>(parse(doc(""","response_types":["token"]""")))
    }

    @Test
    fun `홈 주소가 http 또는 https가 아니면 비워 둔다`() {
        assertEquals("", assertIs<CimdDocument.Result.Valid>(parse(doc(""","client_uri":"javascript:alert(1)""""))).homeUrl)
    }
}
