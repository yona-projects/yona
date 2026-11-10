package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.site.SiteSettingService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 비밀번호 로그인을 스프링의 [org.springframework.security.authentication.AuthenticationProvider]로 판정하는 [YonaPasswordAuthenticationProvider]를 검증한다
 * (로드맵 X-43 1단계 작업 1-5, `decisions.md` D-129). 판정 순서와 결과는 [LocalAuthenticator]와 같고(그쪽 시험이 수정 없이 통과해야 한다), 거절은
 * 결과를 담은 [LoginDeniedException]으로, 성공은 [YonaAuthentication]으로 알린다.
 */
@Transactional
class YonaPasswordAuthenticationProviderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var properties: dev.yona.config.YonaProperties
    @Autowired private lateinit var ldap: dev.yona.domain.ldap.LdapAuthService
    @Autowired private lateinit var passwords: UserPasswordService
    @Autowired private lateinit var siteSettings: SiteSettingService
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var rememberCookies: RememberMeCookies

    /** 제공자는 빈이 아니다(전역 인증 관리자에 자동으로 등록되지 않게 한다). 시험이 같은 부품으로 직접 만든다. */
    private val provider by lazy { YonaPasswordAuthenticationProvider(users, properties, ldap, passwords, siteSettings, siteAdmins) }

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(state: UserState = UserState.ACTIVE, password: String = "1234"): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "yp$n-$stamp", name = "yp$n", email = "yp$n-$stamp@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash(password, salt)))
    }

    private fun token(key: String, password: String) = UsernamePasswordAuthenticationToken.unauthenticated(key, password)

    @Test
    fun `비밀번호 토큰만 지원한다`() {
        assertTrue(provider.supports(UsernamePasswordAuthenticationToken::class.java))
        assertFalse(provider.supports(AnonymousAuthenticationToken::class.java))
    }

    @Test
    fun `맞는 비밀번호이면 사용자를 담은 YonaAuthentication을 돌려준다`() {
        val user = user()
        val result = provider.authenticate(token(user.loginId!!, "1234"))
        val authentication = assertIs<YonaAuthentication>(result)
        assertEquals(user.id, authentication.principal.userId)
        assertTrue(authentication.isAuthenticated)
        val details = assertIs<PasswordLoginDetails>(authentication.details)
        assertEquals(user.id, details.user.id)
        assertEquals(emptyMap(), details.flash)
        assertEquals(null, authentication.scopes, "비밀번호 로그인은 범위 검사를 받지 않는다")
    }

    @Test
    fun `v1_6 해시 사용자가 로그인하면 해시를 argon2id로 바꿔 저장하고 이후에도 같은 비밀번호로 로그인한다`() {
        val user = user()
        assertTrue(passwords.needsUpgrade(user))
        provider.authenticate(token(user.loginId!!, "1234"))
        val saved = users.findById(user.id!!).get()
        assertTrue(saved.password!!.startsWith("\$argon2id\$"), "로그인에 성공하면 새 방식으로 바꾼다")
        assertFalse(passwords.needsUpgrade(saved))
        assertIs<YonaAuthentication>(provider.authenticate(token(user.loginId!!, "1234")))
    }

    @Test
    fun `해시를 바꾸면 로그인 유지 쿠키의 서명도 달라져 이전 쿠키는 한 번 무효가 된다`() {
        val user = user()
        val before = rememberCookies.create(user, secure = false).value
        provider.authenticate(token(user.loginId!!, "1234"))
        val after = rememberCookies.create(users.findById(user.id!!).get(), secure = false).value
        assertNotEquals(before, after, "쿠키 서명이 저장된 해시를 쓰므로 해시가 바뀌면 이전 쿠키는 통하지 않는다(비밀번호를 바꿀 때와 같다)")
    }

    @Test
    fun `비밀번호가 틀리면 해시를 바꾸지 않는다`() {
        val user = user(); val before = user.password
        assertFailsWith<LoginDeniedException> { provider.authenticate(token(user.loginId!!, "wrong")) }
        assertEquals(before, users.findById(user.id!!).get().password)
    }

    @Test
    fun `이메일로도 로그인한다`() {
        val user = user()
        assertIs<YonaAuthentication>(provider.authenticate(token(user.email!!, "1234")))
    }

    private fun denied(key: String, password: String): LoginResult = assertFailsWith<LoginDeniedException> { provider.authenticate(token(key, password)) }.result

    @Test
    fun `없는 계정과 틀린 비밀번호는 잘못된 자격이다`() {
        val user = user()
        assertEquals(LoginResult.InvalidCredentials, denied("nobody-$stamp", "1234"))
        assertEquals(LoginResult.InvalidCredentials, denied(user.loginId!!, "wrong"))
    }

    @Test
    fun `삭제된 계정은 비밀번호가 맞아도 삭제로 거절한다`() {
        assertEquals(LoginResult.Deleted, denied(user(UserState.DELETED).loginId!!, "1234"))
    }

    @Test
    fun `잠긴 계정은 비밀번호가 맞아야 인증 뒤 잠김이고 틀리면 잘못된 자격이다`() {
        val locked = user(UserState.LOCKED)
        assertEquals(LoginResult.LockedAfterAuthentication, denied(locked.loginId!!, "1234"))
        assertEquals(LoginResult.InvalidCredentials, denied(locked.loginId!!, "wrong"))
    }

    @Test
    fun `솔트가 없는 계정은 v1_6처럼 예외를 던진다`() {
        val n = ++sequence
        val broken = users.save(User(loginId = "ypb$n-$stamp", name = "b", email = "ypb$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = null, password = "h"))
        assertFailsWith<IllegalArgumentException> { provider.authenticate(token(broken.loginId!!, "1234")) }
    }

    @Test
    fun `SSO 전용 모드에서는 사이트 관리자가 아니면 비밀번호를 보기 전에 SSO 전용으로 거절한다`() {
        val settings = mock(SiteSettingService::class.java)
        `when`(settings.ssoOnly()).thenReturn(true)
        val ssoOnly = YonaPasswordAuthenticationProvider(users, properties, ldap, passwords, settings, siteAdmins)
        val normal = user(); val admin = user().also { siteAdmins.save(SiteAdmin(admin = it)) }
        fun result(key: String, password: String) = assertFailsWith<LoginDeniedException> { ssoOnly.authenticate(token(key, password)) }.result
        assertEquals(LoginResult.SsoOnly, result(normal.loginId!!, "1234"), "비밀번호가 맞아도 거절한다")
        assertEquals(LoginResult.SsoOnly, result("nobody-$stamp", "1234"), "없는 사용자도 같다")
        assertIs<YonaAuthentication>(ssoOnly.authenticate(token(admin.loginId!!, "1234")), "사이트 관리자의 로컬 로그인은 비상 경로로 남는다")
    }

    @Test
    fun `SSO 전용 모드가 아니면 사이트 관리자가 아니어도 로그인한다`() {
        val settings = mock(SiteSettingService::class.java)
        `when`(settings.ssoOnly()).thenReturn(false)
        val provider = YonaPasswordAuthenticationProvider(users, properties, ldap, passwords, settings, siteAdmins)
        assertIs<YonaAuthentication>(provider.authenticate(token(user().loginId!!, "1234")))
    }
}
