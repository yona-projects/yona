package dev.yona.domain.sso

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserCredentialRepository
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSO 신원을 계정에 맞추는 규칙(X-16 단계 1, `decisions.md` D-80 6~10번)을 검증한다. 안정 식별자(발급자와 subject)로 먼저 찾고, 처음이면 IdP가 이메일을 확인했다고 알릴 때만
 * 같은 이메일의 기존 계정에 연결하며, 없으면 설정에 따라 새 사용자를 만든다(로그인 ID 충돌 회피, 이름 정리, 허용 이메일 도메인). 잠긴 사용자와 이메일이 없는 신원은 거절한다.
 */
@Transactional
@TestPropertySource(properties = ["yona.allowed-sending-mail-domains=corp.example"])
class SsoAccountServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: SsoAccountService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var credentials: UserCredentialRepository
    @Autowired private lateinit var linked: LinkedAccountRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000
    private val issuer = "https://idp.corp.example"

    private fun local(email: String = "u${++n}-$stamp@corp.example", loginId: String = "su$n-$stamp", state: UserState = UserState.ACTIVE) =
        users.save(User(loginId = loginId, name = "로컬 $n", email = email, state = state, passwordSalt = "salt", password = LegacyPasswordHasher.hash("pw", "salt")))

    private fun identity(
        subject: String = "sub-${++n}-$stamp", email: String? = "x$n-$stamp@corp.example", verified: Boolean = true, name: String? = "홍길동", preferred: String? = null,
        protocol: SsoProtocol = SsoProtocol.OIDC, issuer: String = this.issuer
    ) = SsoIdentity(protocol, issuer, subject, email, verified, name, preferred)

    private fun success(result: SsoSignInResult) = assertIs<SsoSignInResult.Success>(result)
    private fun rejected(result: SsoSignInResult) = assertIs<SsoSignInResult.Rejected>(result).reason

    // --- 안정 식별자와 연결 ---

    @Test
    fun `처음 보는 신원은 확인된 같은 이메일의 기존 계정에 연결하고 이후에는 이메일이 바뀌어도 같은 사람이다`() {
        val u = local(); val id = identity(email = u.email)
        val first = success(service.signIn(id, SsoPolicy()))
        assertEquals(u.id, first.user.id); assertTrue(first.linked); assertFalse(first.created)
        val account = linked.findAll().single { it.providerUserId == "$issuer|${id.subject}" }
        assertEquals("sso-oidc", account.providerKey); assertEquals(u.loginId, account.userCredential!!.loginId); assertEquals(u.id, account.userCredential!!.user!!.id)
        val again = success(service.signIn(id.copy(email = "changed-$stamp@corp.example"), SsoPolicy()))
        assertEquals(u.id, again.user.id); assertFalse(again.linked)
    }

    @Test
    fun `같은 subject라도 발급자가 다르거나 프로토콜이 다르면 다른 신원이다`() {
        val a = local(); val b = local(); val c = local()
        success(service.signIn(identity(subject = "same", email = a.email), SsoPolicy()))
        assertEquals(b.id, success(service.signIn(identity(subject = "same", email = b.email, issuer = "https://other.corp.example"), SsoPolicy())).user.id)
        assertEquals(c.id, success(service.signIn(identity(subject = "same", email = c.email, protocol = SsoProtocol.SAML2), SsoPolicy())).user.id)
        assertEquals(3, linked.findAll().count { it.providerUserId!!.endsWith("|same") })
    }

    @Test
    fun `이메일을 확인하지 않았으면 연결하지 않고 설정으로 허용하면 연결한다`() {
        val u = local(); val id = identity(email = u.email, verified = false)
        assertEquals(SsoRejection.EMAIL_UNVERIFIED, rejected(service.signIn(id, SsoPolicy())))
        assertTrue(linked.findAll().none { it.providerUserId == "$issuer|${id.subject}" })
        assertEquals(u.id, success(service.signIn(id, SsoPolicy(allowUnverifiedEmail = true))).user.id)
    }

    @Test
    fun `이메일 연결을 끄면 같은 이메일의 계정이 있을 때 거절한다`() {
        val u = local()
        assertEquals(SsoRejection.EMAIL_IN_USE, rejected(service.signIn(identity(email = u.email), SsoPolicy(linkByEmail = false))))
    }

    @Test
    fun `잠긴 사용자는 로그인시키지 않고 이메일이 없으면 거절한다`() {
        val locked = local(state = UserState.LOCKED)
        assertEquals(SsoRejection.LOCKED, rejected(service.signIn(identity(email = locked.email), SsoPolicy())))
        val id = identity(email = local().email); success(service.signIn(id, SsoPolicy()))
        users.findByEmail(id.email!!)!!.let { it.state = UserState.LOCKED; users.save(it) }
        assertEquals(SsoRejection.LOCKED, rejected(service.signIn(id, SsoPolicy())))
        assertEquals(SsoRejection.EMAIL_MISSING, rejected(service.signIn(identity(email = null), SsoPolicy())))
        assertEquals(SsoRejection.EMAIL_MISSING, rejected(service.signIn(identity(email = "  "), SsoPolicy())))
    }

    // --- 새 사용자 ---

    @Test
    fun `새 사용자는 활성이고 이메일 앞부분으로 로그인 ID를 만들며 비밀번호로는 로그인할 수 없다`() {
        val id = identity(email = "kim.minsu-$stamp@corp.example", name = "김민수")
        val result = success(service.signIn(id, SsoPolicy()))
        assertTrue(result.created && result.linked)
        val user = result.user
        assertEquals("kim.minsu-$stamp", user.loginId); assertEquals("김민수", user.name); assertEquals(UserState.ACTIVE, user.state); assertEquals(id.email, user.email)
        assertFalse(LegacyPasswordHasher.matches("", user.passwordSalt!!, user.password!!)); assertNotNull(user.password); assertTrue(user.password!!.isNotEmpty())
        assertTrue(credentials.findAll().single { it.user?.id == user.id }.emailValidated == true)
        assertTrue(logs.findAll().any { it.action == "account.signup" && it.targetLabel == user.loginId && it.details!!.contains("sso-oidc") })
    }

    @Test
    fun `설정한 클레임이 있으면 로그인 ID 후보로 쓰고 형식에 맞게 정리하며 겹치면 번호를 붙인다`() {
        local(loginId = "taken-$stamp")
        assertEquals("taken-$stamp-yona", success(service.signIn(identity(preferred = "taken-$stamp"), SsoPolicy())).user.loginId)
        assertEquals("김-민수-$stamp", success(service.signIn(identity(preferred = "김 민수 $stamp"), SsoPolicy())).user.loginId)
        assertEquals("a-b-c-$stamp", success(service.signIn(identity(preferred = "a/b\\c $stamp"), SsoPolicy())).user.loginId)
        assertTrue(dev.yona.domain.user.LoginIdRules.isValidFormat(success(service.signIn(identity(preferred = "!!!", email = "!!!-$stamp@corp.example"), SsoPolicy())).user.loginId!!))
    }

    @Test
    fun `예약어는 로그인 ID로 쓰지 않는다`() {
        val user = success(service.signIn(identity(preferred = "sites"), SsoPolicy())).user
        assertNotEquals("sites", user.loginId); assertFalse(dev.yona.domain.user.LoginIdRules.isReserved(user.loginId))
    }

    @Test
    fun `이름은 HTML을 정리한다`() {
        val user = success(service.signIn(identity(name = "<script>alert(1)</script>홍길동"), SsoPolicy())).user
        assertFalse(user.name!!.contains("<script")); assertTrue(user.name!!.contains("홍길동"))
    }

    @Test
    fun `자동 생성을 끄면 새 사용자를 만들지 않지만 기존 계정에는 연결한다`() {
        assertEquals(SsoRejection.USER_NOT_FOUND, rejected(service.signIn(identity(), SsoPolicy(autoCreate = false))))
        val u = local(); assertEquals(u.id, success(service.signIn(identity(email = u.email), SsoPolicy(autoCreate = false))).user.id)
    }

    @Test
    fun `허용되지 않은 이메일 도메인은 새 사용자를 만들지 않는다`() {
        assertEquals(SsoRejection.EMAIL_DOMAIN, rejected(service.signIn(identity(email = "who-$stamp@elsewhere.example"), SsoPolicy())))
        assertNull(users.findByEmail("who-$stamp@elsewhere.example"))
    }

    @Test
    fun `이메일을 확인하지 않은 신원은 새 사용자도 만들지 않는다`() {
        assertEquals(SsoRejection.EMAIL_UNVERIFIED, rejected(service.signIn(identity(verified = false), SsoPolicy())))
        assertTrue(success(service.signIn(identity(verified = false), SsoPolicy(allowUnverifiedEmail = true))).created)
    }

    // --- 이름 동기화 ---

    @Test
    fun `이름 동기화는 켠 때만 로그인마다 IdP 값으로 바꾼다`() {
        val u = local(); val id = identity(email = u.email, name = "처음 이름"); success(service.signIn(id, SsoPolicy()))
        assertEquals(u.name, success(service.signIn(id.copy(name = "바뀐 이름"), SsoPolicy(nameSync = false))).user.name)
        assertEquals("바뀐 이름", success(service.signIn(id.copy(name = "바뀐 이름"), SsoPolicy(nameSync = true))).user.name)
    }
}
