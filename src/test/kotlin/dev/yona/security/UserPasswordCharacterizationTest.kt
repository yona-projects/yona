package dev.yona.security

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sso.SsoIdentity
import dev.yona.domain.sso.SsoPolicy
import dev.yona.domain.sso.SsoProtocol
import dev.yona.domain.sso.SsoAccountService
import dev.yona.domain.sso.SsoSignInResult
import dev.yona.domain.user.AccountService
import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 비밀번호를 만들고 바꾸고 확인하는 모든 길의 **현재 동작**을 고정하는 특성화 시험이다(로드맵 X-43 1단계 작업 1-1, `decisions.md` D-129).
 * 해시 처리를 `UserPasswordService` 한 곳으로 옮기는 동안 이 시험이 수정 없이 통과해야 한다. 고정하는 것은 다음이다.
 *
 * - 가입은 사용자마다 새 솔트(Base64 24글자)를 만들고 `Shiro SHA-256 1024회` 해시를 그 솔트로 저장하며, 평문은 저장하지 않는다.
 * - 설정 화면의 변경, 재설정 주소, 관리자의 임시 비밀번호는 **솔트를 그대로 두고 해시만 바꾼다**(v1.6 `User.resetPassword`).
 * - SSO로 만든 사용자는 알 수 없는 무작위 비밀번호를 갖는다(로컬 비밀번호 로그인 불가).
 * - 설정 화면의 현재 비밀번호 확인(`AccountService.isValidPassword`)과 로그인 판정(`LocalAuthenticator`)은 같은 입력에 같은 결과를 낸다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.allowed-sending-mail-domains=corp.example"])
class UserPasswordCharacterizationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var signup: SignupService
    @Autowired private lateinit var account: AccountService
    @Autowired private lateinit var authenticator: LocalAuthenticator
    @Autowired private lateinit var sso: SsoAccountService
    @Autowired private lateinit var tokens: PasswordResetTokens
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @MockitoBean private lateinit var mailSender: org.springframework.mail.javamail.JavaMailSender

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun registered(password: String = "old-pass"): User {
        val n = ++sequence
        return signup.create(SignupForm("pc$n-$stamp", "pc$n", "pc$n-$stamp@example.com", password)).also { it.state = UserState.ACTIVE; users.save(it) }
    }

    private fun matches(user: User, raw: String) = user.password == LegacyPasswordHasher.hash(raw, requireNotNull(user.passwordSalt))

    // --- 만들기 ---

    @Test
    fun `가입은 사용자마다 새 솔트를 만들고 그 솔트로 해시를 저장하며 평문은 저장하지 않는다`() {
        val a = registered("same-pass"); val b = registered("same-pass")
        assertEquals(24, a.passwordSalt!!.length); assertEquals(24, b.passwordSalt!!.length)
        assertNotEquals(a.passwordSalt, b.passwordSalt, "솔트는 사용자마다 다르다")
        assertNotEquals(a.password, b.password, "같은 비밀번호라도 솔트가 달라 해시가 다르다")
        assertTrue(matches(a, "same-pass")); assertTrue(matches(b, "same-pass"))
        assertNotEquals("same-pass", a.password, "평문을 저장하지 않는다")
        assertFalse(matches(a, "other-pass"))
    }

    @Test
    fun `SSO로 만든 사용자는 알 수 없는 무작위 비밀번호를 가져 로컬 비밀번호로 로그인할 수 없다`() {
        fun created(): User {
            val n = ++sequence
            val identity = SsoIdentity(SsoProtocol.OIDC, "https://idp.corp.example", "sub-$n-$stamp", "sso$n-$stamp@corp.example", true, "홍길동", "sso$n-$stamp")
            return assertIs<SsoSignInResult.Success>(sso.signIn(identity, SsoPolicy())).user
        }
        val a = created(); val b = created()
        assertEquals(24, a.passwordSalt!!.length); assertNotNull(a.password)
        assertNotEquals(a.passwordSalt, b.passwordSalt); assertNotEquals(a.password, b.password)
        listOf("", "password", "1234", a.loginId!!).forEach { guess ->
            assertFalse(matches(a, guess), "추측 \"$guess\"")
            assertEquals(LoginResult.InvalidCredentials, authenticator.authenticate(a.loginId!!, guess))
        }
    }

    // --- 바꾸기: 솔트는 그대로 ---

    @Test
    fun `설정 화면의 변경은 솔트를 유지하고 해시만 바꾼다`() {
        val user = registered(); val salt = user.passwordSalt; val before = user.password
        account.resetPassword(user, "new-pass")
        assertEquals(salt, user.passwordSalt, "솔트는 그대로")
        assertNotEquals(before, user.password)
        assertTrue(matches(user, "new-pass")); assertFalse(matches(user, "old-pass"))
    }

    @Test
    fun `재설정 주소로 바꾸면 솔트를 유지하고 해시만 바꾼다`() {
        val user = registered(); val salt = user.passwordSalt
        val token = tokens.issue(user.loginId!!)
        mockMvc.perform(post("/resetPassword").param("hashString", token).param("password", "new-pass")).andExpect(status().isOk)
        users.flush()
        val saved = users.findById(user.id!!).get()
        assertEquals(salt, saved.passwordSalt, "솔트는 그대로")
        assertTrue(matches(saved, "new-pass")); assertFalse(matches(saved, "old-pass"))
    }

    @Test
    fun `사이트 관리자의 임시 비밀번호는 솔트를 유지하고 여섯 글자 값으로 해시를 바꾼다`() {
        val manager = registered("admin-pass").also { siteAdmins.save(SiteAdmin(admin = it)) }
        val target = registered(); val salt = target.passwordSalt
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", manager.loginId!!).param("password", "admin-pass")) }
        val body = mockMvc.perform(post("/${target.loginId}").param("action", "resetPassword").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        val temporary = Regex("\"newPassword\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertEquals(6, temporary.length)
        users.flush()
        val saved = users.findById(target.id!!).get()
        assertEquals(salt, saved.passwordSalt, "솔트는 그대로")
        assertTrue(matches(saved, temporary)); assertFalse(matches(saved, "old-pass"))
    }

    // --- 확인: 두 길이 같은 판정 ---

    @Test
    fun `설정 화면의 현재 비밀번호 확인과 로그인 판정은 같은 입력에 같은 결과를 낸다`() {
        val user = registered("right-pass")
        listOf("right-pass", "wrong-pass", "", "RIGHT-PASS", " right-pass").forEach { raw ->
            val viaSettings = account.isValidPassword(user, raw)
            val viaLogin = authenticator.authenticate(user.loginId!!, raw) is LoginResult.Success
            assertEquals(viaSettings, viaLogin, "입력 \"$raw\"")
            assertEquals(raw == "right-pass", viaSettings, "입력 \"$raw\"")
        }
    }
}
