package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.ldap.LdapAuthResult
import dev.yona.domain.ldap.LdapAuthService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals

/**
 * LDAP을 켠 사이트에서 [LocalAuthenticator]가 판정하는 **순서**를 고정하는 특성화 시험이다(로드맵 X-43 1단계 작업 1-1, `decisions.md` D-129).
 * 로컬 계정이 삭제 상태이면 LDAP에 묻기 전에 거절하고, 로컬 계정이 없어도 LDAP이 판정하며, LDAP이 맞다고 해도 로컬 계정이 잠겼으면 인증 뒤 잠김이다.
 * LDAP 서버 자체의 동작은 `LdapAuthServiceTest` 등이 확인하므로 여기서는 [LdapAuthService]를 대역으로 바꾼다.
 */
@Transactional
class LocalAuthenticatorLdapOrderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var authenticator: LocalAuthenticator
    @Autowired private lateinit var users: UserRepository
    @MockitoBean private lateinit var ldap: LdapAuthService

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun local(state: UserState): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "lo$n-$stamp", name = "lo$n", email = "lo$n-$stamp@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("pw", salt)))
    }

    @Test
    fun `삭제된 로컬 계정은 LDAP에 묻기 전에 거절한다`() {
        `when`(ldap.enabled).thenReturn(true)
        val deleted = local(UserState.DELETED)
        assertEquals(LoginResult.Deleted, authenticator.authenticate(deleted.loginId!!, "pw"))
        verify(ldap, never()).authenticate(deleted.loginId!!, "pw")
    }

    @Test
    fun `로컬 계정이 없어도 LDAP이 인증하면 그 사용자로 성공한다`() {
        `when`(ldap.enabled).thenReturn(true)
        val ldapUser = local(UserState.ACTIVE)
        `when`(ldap.authenticate("new-ldap-user", "pw")).thenReturn(LdapAuthResult(user = ldapUser, flash = mapOf("info" to "user.created")))
        assertEquals(LoginResult.Success(ldapUser, mapOf("info" to "user.created")), authenticator.authenticate("new-ldap-user", "pw"))
    }

    @Test
    fun `LDAP이 거절하면 안내가 없으면 잘못된 자격이고 안내가 있으면 안내 동반 실패이다`() {
        `when`(ldap.enabled).thenReturn(true)
        `when`(ldap.authenticate("nobody", "pw")).thenReturn(LdapAuthResult())
        assertEquals(LoginResult.InvalidCredentials, authenticator.authenticate("nobody", "pw"))
        `when`(ldap.authenticate("pending", "pw")).thenReturn(LdapAuthResult(flash = mapOf("warning" to "user.signup.requested")))
        assertEquals(LoginResult.InvalidWithNotice(mapOf("warning" to "user.signup.requested")), authenticator.authenticate("pending", "pw"))
    }

    @Test
    fun `LDAP이 맞다고 해도 로컬 계정이 잠겼으면 인증 뒤 잠김이다`() {
        `when`(ldap.enabled).thenReturn(true)
        val locked = local(UserState.LOCKED)
        `when`(ldap.authenticate(locked.loginId!!, "pw")).thenReturn(LdapAuthResult(user = locked))
        assertEquals(LoginResult.LockedAfterAuthentication, authenticator.authenticate(locked.loginId!!, "pw"))
    }
}
