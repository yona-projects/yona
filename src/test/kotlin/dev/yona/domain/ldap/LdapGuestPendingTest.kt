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
 * 가입 승인제 사이트에서 게스트 접두어에 맞는 LDAP 신규 사용자는 잠긴 채 만들어져도 게스트 여부가 저장된다.
 *
 * 원본: `UserApp.createNewUser(String, LdapUser)`가 `CandidateUser`에 `ldapUser.isGuestUser()`를 담아 `createUserDelegate`로 만들고,
 * 만들어진 사용자가 `LOCKED`이면 그 뒤에 안내를 남긴다(`UserApp.java:1202-1216`). 게스트 여부는 만들 때 이미 저장되어 있다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true", "yona.guest-user-login-id-prefix=PT_, Guest-", "yona.signup.require-admin-confirm=true"])
@Transactional
class LdapGuestPendingTest : AbstractIntegrationTest() {

    @Autowired private lateinit var ldap: LdapAuthService
    @Autowired private lateinit var users: UserRepository
    @MockitoBean private lateinit var directory: LdapDirectory

    @Test
    fun `승인 대기로 잠긴 신규 사용자도 접두어에 맞으면 게스트로 저장된다`() {
        Mockito.`when`(directory.authenticate("pt_pending", "pw")).thenReturn(LdapUser("대기", "pending@corp.test", "pt_pending", "", ""))
        val result = ldap.authenticate("pt_pending", "pw")
        assertEquals(null, result.user, "잠긴 사용자는 로그인시키지 않는다")
        val saved = assertNotNull(users.findByLoginId("pt_pending"))
        assertEquals(UserState.LOCKED, saved.state)
        assertEquals(true, saved.isGuest)
    }
}
