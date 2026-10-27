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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * 게스트 접두어 설정(`application.guest.user.login.id.prefix`)을 검증한다. v1.6 `LdapUser.isGuestUser`: 쉼표로 나눈 접두어로 시작하는 LDAP 로그인 ID는
 * 대소문자를 가리지 않고 게스트이다. v1.6은 설정 키 뒤에 공백이 있지만 Typesafe Config가 무시하므로 설정이 실제로 읽힌다(R-60).
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true", "yona.guest-user-login-id-prefix=PT_, Guest-"])
@Transactional
class LdapGuestPrefixTest : AbstractIntegrationTest() {

    @Autowired private lateinit var ldap: LdapAuthService
    @Autowired private lateinit var users: UserRepository
    @MockitoBean private lateinit var directory: LdapDirectory

    private fun found(login: String, email: String = "$login@corp.test") = LdapUser("엘디", email, login, "", "")

    @Test
    fun `접두어로 시작하는 LDAP 로그인 ID로 처음 로그인하면 게스트이다`() {
        Mockito.`when`(directory.authenticate("pt_alice", "pw")).thenReturn(found("pt_alice"))
        assertEquals(true, assertNotNull(ldap.authenticate("pt_alice", "pw").user).isGuest)
        Mockito.`when`(directory.authenticate("GUEST-bob", "pw")).thenReturn(found("GUEST-bob"))
        assertEquals(true, assertNotNull(ldap.authenticate("GUEST-bob", "pw").user).isGuest)
        Mockito.`when`(directory.authenticate("carol", "pw")).thenReturn(found("carol"))
        assertEquals(false, assertNotNull(ldap.authenticate("carol", "pw").user).isGuest)
    }

    @Test
    fun `기존 사용자는 로그인할 때마다 게스트 여부를 다시 정한다`() {
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "exist", name = "기존", email = "exist@corp.test", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("pw", salt), isGuest = true))
        Mockito.`when`(directory.authenticate("exist-ldap", "pw")).thenReturn(found("exist-ldap", "exist@corp.test"))
        // LDAP 로그인 ID가 접두어로 시작하지 않으므로 게스트가 풀린다
        assertEquals(false, assertNotNull(ldap.authenticate("exist-ldap", "pw").user).isGuest)
    }
}
