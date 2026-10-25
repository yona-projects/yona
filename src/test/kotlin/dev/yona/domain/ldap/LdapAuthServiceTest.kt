package dev.yona.domain.ldap

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import javax.naming.AuthenticationException
import javax.naming.CommunicationException
import javax.naming.NamingException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * LDAP 인증 결과를 로컬 사용자와 맞추는 규칙을 검증한다. 원본: `UserApp.authenticateWithLdap`, `createNewUser(String, LdapUser)`
 * (`app/controllers/UserApp.java:1156-1200`).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true", "yona.ldap.fallback-to-local-login=true"])
@Transactional
class LdapAuthServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var ldap: LdapAuthService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var authenticator: dev.yona.security.LocalAuthenticator
    @MockitoBean private lateinit var directory: LdapDirectory

    private var sequence = 0

    private fun ldapUser(login: String = "ldap${++sequence}", email: String = "$login@corp.test", name: String = "엘디", department: String = "", english: String = "") =
        LdapUser(name, email, login, department, english)

    private fun local(email: String, loginId: String, password: String = "old-pw", state: UserState = UserState.ACTIVE): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "예전 이름", email = email, state = state, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash(password, salt)))
    }

    @Test
    fun `처음 로그인하면 LDAP 로그인 ID와 입력한 비밀번호로 로컬 사용자를 만든다`() {
        val found = ldapUser(login = "newldap", name = "새 사용자", department = "개발")
        Mockito.`when`(directory.authenticate("newldap", "pw1")).thenReturn(found)
        val result = ldap.authenticate("newldap", "pw1")
        val user = assertNotNull(result.user)
        assertEquals("newldap", user.loginId)
        assertEquals("새 사용자 [개발]", user.name)
        assertEquals("newldap@corp.test", user.email)
        assertEquals(UserState.ACTIVE, user.state)
        assertTrue(LegacyPasswordHasher.matches("pw1", user.passwordSalt!!, user.password!!))
        assertEquals(false, user.isGuest)
    }

    @Test
    fun `같은 이메일의 사용자가 있으면 이름과 영문 이름을 갱신하고 비밀번호가 다르면 바꾼다`() {
        val existing = local("same@corp.test", "samelocal", password = "old-pw")
        Mockito.`when`(directory.authenticate("sameldap", "new-pw")).thenReturn(ldapUser(login = "sameldap", email = "same@corp.test", name = "바뀐 이름", english = "Changed"))
        val user = assertNotNull(ldap.authenticate("sameldap", "new-pw").user)
        assertEquals(existing.id, user.id)
        assertEquals("바뀐 이름", user.name)
        assertEquals("Changed", user.englishName)
        assertTrue(LegacyPasswordHasher.matches("new-pw", user.passwordSalt!!, user.password!!))
        assertEquals("samelocal", user.loginId)
    }

    @Test
    fun `영문 이름이 비어 있으면 기존 영문 이름을 그대로 둔다`() {
        val existing = local("keep-en@corp.test", "keepen")
        existing.englishName = "Keep"
        users.save(existing)
        Mockito.`when`(directory.authenticate("keepen", "old-pw")).thenReturn(ldapUser(login = "keepen", email = "keep-en@corp.test"))
        assertEquals("Keep", ldap.authenticate("keepen", "old-pw").user?.englishName)
    }

    @Test
    fun `통신 오류이면 로컬 비밀번호로 다시 시도한다`() {
        local("fb@corp.test", "fallbackuser", password = "local-pw")
        Mockito.`when`(directory.authenticate("fallbackuser", "local-pw")).thenThrow(CommunicationException("down"))
        Mockito.`when`(directory.authenticate("fallbackuser", "wrong")).thenThrow(CommunicationException("down"))
        assertEquals("fallbackuser", ldap.authenticate("fallbackuser", "local-pw").user?.loginId)
        assertNull(ldap.authenticate("fallbackuser", "wrong").user)
    }

    @Test
    fun `인증 오류이면 안내를 남기고 로컬 비밀번호로 다시 시도한다`() {
        local("fb2@corp.test", "fallbackuser2", password = "local-pw")
        Mockito.`when`(directory.authenticate("fallbackuser2", "local-pw")).thenThrow(AuthenticationException("bad"))
        val ok = ldap.authenticate("fallbackuser2", "local-pw")
        assertEquals("fallbackuser2", ok.user?.loginId)
        // v1.6는 폴백이 성공해도 인증 실패 안내를 남긴다.
        assertEquals("user.login.invalid", ok.flash["warning"])
    }

    @Test
    fun `이메일로 입력하면 로컬 폴백은 로그인 ID로만 찾는다`() {
        local("byemail@corp.test", "byemailuser", password = "local-pw")
        Mockito.`when`(directory.authenticate("byemail@corp.test", "local-pw")).thenThrow(CommunicationException("down"))
        assertNull(ldap.authenticate("byemail@corp.test", "local-pw").user)
    }

    @Test
    fun `그 밖의 이름 오류는 실패이고 폴백하지 않는다`() {
        local("other@corp.test", "otheruser", password = "local-pw")
        Mockito.`when`(directory.authenticate("otheruser", "local-pw")).thenThrow(NamingException("x"))
        assertNull(ldap.authenticate("otheruser", "local-pw").user)
    }

    @Test
    fun `찾은 사용자가 없으면 예외이다`() {
        Mockito.`when`(directory.authenticate("ghost", "pw")).thenReturn(null)
        assertFailsWith<IllegalStateException> { ldap.authenticate("ghost", "pw") }
    }

    @Test
    fun `이메일이 없는 LDAP 사용자도 로그인 ID가 있으면 이메일 없이 만든다`() {
        Mockito.`when`(directory.authenticate("nomail", "pw")).thenReturn(ldapUser(login = "nomail", email = ""))
        val user = assertNotNull(ldap.authenticate("nomail", "pw").user)
        assertEquals("nomail", user.loginId)
        assertEquals("", user.email)
    }

    @Test
    fun `로그인 ID를 로컬 사용자가 이미 쓰고 있으면 사용자를 만들 수 없다`() {
        local("other-mail@corp.test", "taken")
        Mockito.`when`(directory.authenticate("taken", "pw")).thenReturn(ldapUser(login = "taken", email = "taken@corp.test"))
        assertFailsWith<Exception> { ldap.authenticate("taken", "pw"); users.flush() }
    }

    @Test
    fun `Git 기본 인증도 LDAP으로 확인한다`() {
        Mockito.`when`(directory.authenticate("gitldap", "pw")).thenReturn(ldapUser(login = "gitldap"))
        assertEquals("gitldap", authenticator.authenticateBasic("gitldap", "pw")?.loginId)
        Mockito.`when`(directory.authenticate("gitbad", "pw")).thenThrow(AuthenticationException("bad"))
        assertNull(authenticator.authenticateBasic("gitbad", "pw"))
    }
}
