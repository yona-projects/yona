package dev.yona.domain.audit

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.deviceauth.DeviceAuthorizationService
import dev.yona.domain.deviceauth.DeviceStart
import dev.yona.domain.deviceauth.DevicePoll
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.AccountService
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.SignupForm
import dev.yona.domain.user.SignupService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.PasswordResetTokens
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 계정과 토큰 이벤트(로드맵 X-9 A4, `decisions.md` D-62)를 검증한다: 가입, 비밀번호 변경과 재설정, 이메일, 프로필, API 토큰 만들기와 고치기와 다시 만들기와 지우기, 기기 승인과 거절.
 * 변경과 같은 트랜잭션에서 기록하므로(fail-closed) 롤백하면 기록도 사라지고, 거부된 요청은 기록하지 않는다. 비밀 값(비밀번호, 해시, 토큰 값)은 어떤 필드에도 남지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class AuditAccountTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var signup: SignupService
    @Autowired private lateinit var account: AccountService
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var devices: DeviceAuthorizationService
    @Autowired private lateinit var passwordTokens: PasswordResetTokens
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0
    private val stamp = System.nanoTime()

    // 이 시험의 기록은 모두 시험 트랜잭션 안에서 만들어져 롤백으로 사라진다. 별도 트랜잭션으로 지우려 하면 시험 트랜잭션이 쥔 사슬의 끝 잠금을 기다려 멈춘다.

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ac$n-$stamp", name = "계정$n", email = "ac$n-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), createdDate = Date()))
    }

    private fun eventsOf(action: String) = logs.findAll().sortedBy { it.id }.filter { it.action == action }

    private fun allText(): String = logs.findAll().joinToString("\n") { listOfNotNull(it.details, it.targetId, it.targetLabel, it.actorLogin).joinToString(" ") }

    // --- 가입 ---

    @Test
    fun `가입은 대상 사용자와 상태를 남긴다`() {
        val u = signup.create(SignupForm("sg$stamp", "가입자", "sg$stamp@example.com", "Secret#Pass77"))
        val e = eventsOf("account.signup").single()
        assertEquals("user", e.targetType); assertEquals(u.id.toString(), e.targetId); assertEquals("sg$stamp", e.targetLabel)
        assertTrue(e.details!!.contains("\"state\":\"ACTIVE\""), e.details)
        assertFalse(allText().contains("Secret#Pass77") || allText().contains(u.password!!))
    }

    // --- 비밀번호 ---

    @Test
    fun `비밀번호 변경은 남기되 새 비밀번호와 해시는 남기지 않는다`() {
        val u = user()
        account.resetPassword(u, "NewPass#99")
        val e = eventsOf("account.password_changed").single()
        assertEquals(u.id.toString(), e.targetId); assertTrue(e.details!!.contains("\"via\":\"settings\""), e.details)
        assertFalse(allText().contains("NewPass#99") || allText().contains(u.password!!))
    }

    @Test
    fun `비밀번호 분실 요청은 맞으면 성공 틀리면 실패로 남는다`() {
        val u = user()
        mockMvc.perform(post("/lostPassword").param("loginId", u.loginId!!).param("emailAddress", u.email!!))
        mockMvc.perform(post("/lostPassword").param("loginId", u.loginId!!).param("emailAddress", "wrong@example.com"))
        val rows = eventsOf("account.password_reset_requested")
        assertEquals(listOf(AuditOutcome.SUCCESS, AuditOutcome.FAILURE), rows.map { it.outcome })
        assertTrue(rows.all { it.targetLabel == u.loginId })
    }

    @Test
    fun `메일의 재설정 링크로 비밀번호를 바꾸면 남기되 링크 값은 남기지 않는다`() {
        val u = user()
        val hash = passwordTokens.issue(u.loginId!!)
        mockMvc.perform(post("/resetPassword").param("hashString", hash).param("password", "Another#Pass55"))
        val e = eventsOf("account.password_changed").single()
        assertTrue(e.details!!.contains("\"via\":\"reset_token\""), e.details)
        assertEquals(AuditActorType.ANONYMOUS, e.actorType)
        assertFalse(allText().contains(hash) || allText().contains("Another#Pass55"))
    }

    @Test
    fun `틀린 재설정 링크는 남기지 않는다`() {
        mockMvc.perform(post("/resetPassword").param("hashString", "bogus").param("password", "x"))
        assertTrue(eventsOf("account.password_changed").isEmpty())
    }

    // --- 이메일과 프로필 ---

    @Test
    fun `보조 이메일을 더하고 인증하고 대표로 바꾸고 지우는 일을 남긴다`() {
        val u = user()
        assertEquals(null, account.addEmail(u, "sub$stamp@example.com"))
        val email = emails.findByUserIdOrderById(u.id!!).single()
        assertEquals("sub$stamp@example.com", eventsOf("account.email_added").single().targetLabel)
        account.requestValidationEmail(email)
        assertTrue(account.confirmEmail(email, email.token!!))
        assertEquals(1, eventsOf("account.email_confirmed").size)
        account.setAsMainEmail(u, email)
        assertTrue(eventsOf("account.email_main_changed").single().details!!.contains("sub$stamp@example.com"))
        val other = Email(user = u, email = "del$stamp@example.com", valid = true).let { emails.save(it) }
        account.deleteEmail(other)
        assertEquals("del$stamp@example.com", eventsOf("account.email_deleted").single().targetLabel)
    }

    @Test
    fun `거부된 이메일 추가와 틀린 인증 토큰은 남기지 않는다`() {
        val u = user()
        assertEquals("error.email", account.addEmail(u, "not-an-email"))
        assertEquals(0, eventsOf("account.email_added").size)
        val email = emails.save(Email(user = u, email = "x$stamp@example.com", valid = false, token = "123"))
        assertFalse(account.confirmEmail(email, "wrong"))
        assertEquals(0, eventsOf("account.email_confirmed").size)
    }

    @Test
    fun `프로필에서 이름이나 이메일이 바뀐 것만 남긴다`() {
        val u = user()
        account.updateProfile(u, u.name, u.email, null)
        assertTrue(eventsOf("account.profile_changed").isEmpty(), "바뀐 것이 없으면 남기지 않는다")
        account.updateProfile(u, "새 이름", "changed$stamp@example.com", null)
        val e = eventsOf("account.profile_changed").single()
        assertTrue(e.details!!.contains("\"name\"") && e.details!!.contains("\"email\"") && e.details!!.contains("changed$stamp@example.com"), e.details)
    }

    // --- API 토큰 ---

    @Test
    fun `토큰을 만들면 이름과 범위를 남기고 값은 남기지 않는다`() {
        val u = user()
        val created = tokens.create(u, "ci-token", setOf("repo", "read:org"), null) as TokenCreation.Created
        val e = eventsOf("token.created").single()
        assertEquals("token", e.targetType); assertEquals(created.token.id.toString(), e.targetId); assertEquals("ci-token", e.targetLabel)
        assertTrue(e.details!!.contains("\"owner\":\"${u.loginId}\""), "토큰 주인을 상세에 남긴다: ${e.details}")
        assertTrue(e.details!!.contains("read:org") && e.details!!.contains("repo"), e.details)
        assertFalse(allText().contains(created.value) || allText().contains(created.value.take(20)))
    }

    @Test
    fun `토큰의 이름과 범위를 바꾸면 전후를 남긴다`() {
        val u = user()
        val created = tokens.create(u, "old-name", setOf("repo"), null) as TokenCreation.Created
        tokens.update(u, created.token.id!!, "new-name", setOf("public_repo"))
        val e = eventsOf("token.updated").single()
        assertTrue(e.details!!.contains("old-name") && e.details!!.contains("new-name") && e.details!!.contains("public_repo"), e.details)
    }

    @Test
    fun `토큰을 다시 만들고 지우는 일을 남긴다`() {
        val u = user()
        val created = tokens.create(u, "rot", setOf("repo"), null) as TokenCreation.Created
        val regenerated = tokens.regenerate(u, created.token.id!!, null) as TokenCreation.Created
        assertEquals(1, eventsOf("token.regenerated").size)
        assertTrue(tokens.delete(u, created.token.id!!))
        assertEquals(created.token.id.toString(), eventsOf("token.deleted").single().targetId)
        assertFalse(allText().contains(regenerated.value))
    }

    @Test
    fun `거부된 토큰 만들기와 남의 토큰 지우기는 남기지 않는다`() {
        val u = user(); val other = user()
        assertTrue(tokens.create(u, "", setOf("repo"), null) is TokenCreation.Rejected)
        val created = tokens.create(other, "mine", setOf("repo"), null) as TokenCreation.Created
        assertFalse(tokens.delete(u, created.token.id!!))
        assertEquals(0, eventsOf("token.deleted").size); assertEquals(1, eventsOf("token.created").size)
    }

    // --- 기기 인증 ---

    @Test
    fun `기기를 승인하고 거절하는 일을 남긴다`() {
        val u = user()
        val issued = (devices.start("My CLI", listOf("repo")) as DeviceStart.Issued).code
        val auth = devices.findByUserCode(issued.userCode)!!
        devices.approve(auth, u)
        val approved = eventsOf("token.device_approved").single()
        assertEquals("My CLI", approved.targetLabel); assertTrue(approved.details!!.contains("repo"), approved.details)
        val second = (devices.start("Other", listOf("repo")) as DeviceStart.Issued).code
        devices.deny(devices.findByUserCode(second.userCode)!!)
        assertEquals("Other", eventsOf("token.device_denied").single().targetLabel)
        assertFalse(allText().contains(issued.deviceCode))
    }

    @Test
    fun `승인한 기기가 토큰을 받으면 토큰 만들기로도 남는다`() {
        val u = user()
        val issued = (devices.start("Exchange CLI", listOf("repo")) as DeviceStart.Issued).code
        devices.approve(devices.findByUserCode(issued.userCode)!!, u)
        val granted = devices.exchange(issued.deviceCode) as DevicePoll.Granted
        val created = eventsOf("token.created").single { it.targetLabel!!.contains("Exchange CLI") }
        // 기기가 인증 없이 토큰을 받아 가므로 행위자는 토큰 주인(승인한 사용자)으로 남기고 기기를 통했음을 상세에 둔다
        assertEquals(AuditActorType.USER, created.actorType); assertEquals(u.loginId, created.actorLogin)
        assertTrue(created.details!!.contains("\"via\":\"device\""), created.details)
        assertFalse(allText().contains(granted.token))
    }
}
