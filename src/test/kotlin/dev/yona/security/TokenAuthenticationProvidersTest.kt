package dev.yona.security

import dev.yona.domain.apitoken.ApiScopes
import dev.yona.domain.apitoken.ApiToken
import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.OAuthAccessTokenService
import dev.yona.domain.oauth2server.OAuthGrant
import dev.yona.domain.user.User
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.InsufficientAuthenticationException
import org.springframework.security.authentication.ProviderManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부 API의 토큰 인증을 스프링 `AuthenticationProvider`로 판정하는 두 제공자를 검증한다(로드맵 X-43 2단계 작업 2-2, `decisions.md` D-129).
 * [ApiTokenAuthenticationProvider]는 개인용 토큰을, [OAuthAccessTokenAuthenticationProvider]는 OAuth 액세스 토큰(접두사 `yona_oat_`)을 맡고, 서로 자기 것이 아니면 `null`을 돌려줘
 * 같은 `ProviderManager`에서 차례로 쓰인다. 필터가 하던 판정(범위, 토큰 번호, 앱 번호, 대상(RFC 8707) 확인)과 같은 결과를 내야 한다.
 */
class TokenAuthenticationProvidersTest {

    private val apiTokens = mock(ApiTokenService::class.java)
    private val oauthTokens = mock(OAuthAccessTokenService::class.java)
    private val manager = ProviderManager(OAuthAccessTokenAuthenticationProvider(oauthTokens), ApiTokenAuthenticationProvider(apiTokens))

    private val resource = "http://localhost/-_-api/v1"
    private val owner = User(id = 7, loginId = "tk", name = "tk", email = "tk@example.com", state = UserState.ACTIVE)

    private fun token(value: String, resource: String = this.resource) = ApiTokenAuthenticationToken(value, resource)

    /** `authenticate(value, now = Instant.now())`는 기본 인자라 호출 시각이 달라 값으로는 맞출 수 없다. 두 번째 인자는 무엇이든 받는다. */
    private fun stubOAuth(value: String, grant: OAuthGrant?) {
        `when`(oauthTokens.authenticate(ArgumentMatchers.eq(value) ?: value, ArgumentMatchers.any(Instant::class.java) ?: Instant.now())).thenReturn(grant)
    }

    @Test
    fun `토큰 인증 요청만 지원한다`() {
        assertTrue(ApiTokenAuthenticationProvider(apiTokens).supports(ApiTokenAuthenticationToken::class.java))
        assertFalse(ApiTokenAuthenticationProvider(apiTokens).supports(AnonymousAuthenticationToken::class.java))
        assertTrue(OAuthAccessTokenAuthenticationProvider(oauthTokens).supports(ApiTokenAuthenticationToken::class.java))
    }

    @Test
    fun `개인용 토큰이 맞으면 토큰의 범위와 번호를 담은 YonaAuthentication을 돌려준다`() {
        val stored = ApiToken(id = 11, user = owner, name = "t", isLegacy = false, scopes = linkedSetOf("repo", "user"))
        `when`(apiTokens.authenticate("yona_pat_good")).thenReturn(stored)
        `when`(apiTokens.scopesOf(stored)).thenReturn(setOf("repo", "user"))
        val authentication = assertIs<YonaAuthentication>(manager.authenticate(token("yona_pat_good")))
        assertEquals(7, authentication.principal.userId)
        assertTrue(authentication.viaToken)
        assertEquals(setOf("repo", "user"), authentication.scopes)
        assertEquals(11, authentication.tokenId)
        assertNull(authentication.oauthClientRowId)
    }

    @Test
    fun `범위 행이 없는 옛 토큰은 범위 제한 없음으로 인증한다`() {
        val legacy = ApiToken(id = 12, user = owner, name = "이전 토큰", isLegacy = true)
        val everything = ApiScopes.selectable(siteManager = true).toSet()
        `when`(apiTokens.authenticate("legacyvalue")).thenReturn(legacy)
        `when`(apiTokens.scopesOf(legacy)).thenReturn(everything)
        assertEquals(everything, assertIs<YonaAuthentication>(manager.authenticate(token("legacyvalue"))).scopes)
    }

    @Test
    fun `틀린 개인용 토큰은 잘못된 자격 예외이다`() {
        `when`(apiTokens.authenticate("nope")).thenReturn(null)
        assertFailsWith<BadCredentialsException> { manager.authenticate(token("nope")) }
    }

    @Test
    fun `OAuth 액세스 토큰이 맞으면 승인한 범위와 앱 번호를 담아 인증하고 개인용 토큰 쪽은 부르지 않는다`() {
        val grant = OAuthGrant(owner, setOf("read:user"), "auth-1", "client-1", "앱", 33)
        stubOAuth("yona_oat_good", grant)
        val authentication = assertIs<YonaAuthentication>(manager.authenticate(token("yona_oat_good")))
        assertEquals(setOf("read:user"), authentication.scopes)
        assertEquals(33, authentication.oauthClientRowId)
        assertNull(authentication.tokenId)
        assertTrue(authentication.viaToken)
    }

    @Test
    fun `틀린 OAuth 토큰은 잘못된 자격 예외이다`() {
        stubOAuth("yona_oat_bad", null)
        assertFailsWith<BadCredentialsException> { manager.authenticate(token("yona_oat_bad")) }
    }

    @Test
    fun `다른 자원을 가리켜 발급된 OAuth 토큰은 외부 API에서 인증하지 않되 실패로 세지 않는 예외이다`() {
        val grant = OAuthGrant(owner, setOf("read:user"), "auth-1", "client-1", "앱", 33, resources = setOf("http://localhost/mcp"))
        stubOAuth("yona_oat_mcp", grant)
        assertFailsWith<InsufficientAuthenticationException> { manager.authenticate(token("yona_oat_mcp")) }
    }
}
