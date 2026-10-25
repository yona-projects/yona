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
import kotlin.test.assertNull

/**
 * 이메일 기반 LDAP 로그인과 가입 승인제의 규칙이다. 원본: `LdapService.guessedUser`, `UserApp.createUserDelegate`, `createNewUser(String, LdapUser)`.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret", "yona.ldap.enabled=true", "yona.ldap.use-email-base-login=true", "yona.signup.require-admin-confirm=true"])
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Transactional
class LdapEmailLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var ldap: LdapAuthService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var mockMvc: org.springframework.test.web.servlet.MockMvc
    @MockitoBean private lateinit var directory: LdapDirectory

    private fun local(email: String, loginId: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = loginId, email = email, state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("pw", salt)))
    }

    @Test
    fun `로그인 ID로 입력해도 그 사용자의 이메일로 인증한다`() {
        local("mailer@corp.test", "mailer")
        Mockito.`when`(directory.authenticate("mailer@corp.test", "pw")).thenReturn(LdapUser("메일러", "mailer@corp.test", "mailer", "", ""))
        assertEquals("mailer", ldap.authenticate("mailer", "pw").user?.loginId)
    }

    @Test
    fun `로컬에 없으면 입력한 값 그대로 인증하고 로그인 ID는 이메일의 앞부분으로 만든다`() {
        Mockito.`when`(directory.authenticate("someone", "pw")).thenReturn(LdapUser("누군가", "different.name@corp.test", "ldaplogin", "", ""))
        val result = ldap.authenticate("someone", "pw")
        // 가입 승인제이므로 만들어지되 잠기고 로그인시키지 않는다.
        assertNull(result.user)
        assertEquals("user.signup.requested", result.flash["info"])
        assertEquals(UserState.LOCKED, assertNotNull(users.findByLoginId("different.name")).state)
    }

    @Test
    fun `이메일이 없으면 로그인 ID를 만들 수 없어 예외이다`() {
        Mockito.`when`(directory.authenticate("nomail", "pw")).thenReturn(LdapUser("무메일", "", "nomail", "", ""))
        kotlin.test.assertFailsWith<IllegalStateException> { ldap.authenticate("nomail", "pw") }
    }

    @Test
    fun `가입 승인 대기 안내와 인증 실패 안내가 함께 남는다`() {
        Mockito.`when`(directory.authenticate("pending", "pw")).thenReturn(LdapUser("대기", "pending@corp.test", "pending", "", ""))
        val result = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/users/login").param("loginIdOrEmail", "pending").param("password", "pw")
        ).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isSeeOther).andReturn()
        assertEquals("user.signup.requested", result.flashMap["info"])
        assertEquals("user.login.invalid", result.flashMap["warning"])
    }
}
