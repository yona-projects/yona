package dev.yona.web.user

import dev.yona.domain.audit.AuditLog
import dev.yona.domain.audit.AuditLogRepository
import dev.yona.domain.audit.AuditOutcome
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.UserPasswordService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 비밀번호 로그인(`POST /users/login`)의 **현재 동작**을 고정하는 특성화 시험이다(로드맵 X-43 3단계 작업 3-1, `decisions.md` D-129). 로그인 입구를
 * 스프링의 인증 필터로 옮기는 동안 이 시험이 수정 없이 통과해야 한다. `LoginControllerTest`, `LoginFormPageTest`, `TwoFactorLoginTest`, `SsoLoginPageTest`가
 * 고정하지 않은 것만 다룬다: 세션 식별자 회전(세션 고정 공격 방지), 모든 응답의 캐시 금지 헤더, 로그인 감사 기록의 내용.
 */
@AutoConfigureMockMvc
@Transactional
class LoginFlowCharacterizationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var passwords: UserPasswordService
    @Autowired private lateinit var logs: AuditLogRepository

    private var sequence = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val credentials = passwords.newCredentials("1234")
        return users.save(User(loginId = "lf$n-$stamp", name = "lf$n", email = "lf$n-$stamp@example.com", state = state, passwordSalt = credentials.salt, password = credentials.hash))
    }

    private fun login(key: String, password: String, session: MockHttpSession = MockHttpSession(), json: Boolean = false) =
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", key).param("password", password).apply { if (json) accept("application/json") })

    /** 이 시험이 만든 로그인 ID에 대한 감사 기록이다. 감사는 별도 트랜잭션으로 남으므로 로그인 ID로 가려낸다. */
    private fun auditOf(key: String): List<AuditLog> = logs.findAll().filter { it.targetLabel == key }.sortedBy { it.id }

    // --- 세션 식별자 ---

    @Test
    fun `로그인에 성공하면 로그인 전에 쓰던 세션 식별자를 버린다`() {
        val u = user(); val session = MockHttpSession(); val before = session.id
        login(u.loginId!!, "1234", session).andExpect(status().is3xxRedirection)
        assertNotEquals(before, session.id, "세션 고정 공격을 막으려고 식별자를 바꾼다")
    }

    @Test
    fun `로그인에 실패하면 세션 식별자를 바꾸지 않는다`() {
        val u = user(); val session = MockHttpSession(); val before = session.id
        login(u.loginId!!, "wrong", session).andExpect(status().is3xxRedirection)
        assertEquals(before, session.id)
    }

    // --- 캐시 금지 헤더 ---

    private fun assertNoCache(result: org.springframework.test.web.servlet.ResultActions) {
        result.andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andExpect(header().string("Pragma", "no-cache")).andExpect(header().string("Expires", "0"))
    }

    @Test
    fun `성공, 실패, 입력 누락의 모든 응답에 캐시 금지 헤더가 붙는다`() {
        val u = user()
        assertNoCache(login(u.loginId!!, "1234"))
        assertNoCache(login(u.loginId!!, "wrong"))
        assertNoCache(login(u.loginId!!, "wrong", json = true))
        assertNoCache(mockMvc.perform(post("/users/login").param("loginIdOrEmail", u.loginId!!)))
        assertNoCache(mockMvc.perform(post("/users/login").accept("application/json").param("loginIdOrEmail", u.loginId!!)))
    }

    // --- 감사 기록 ---

    @Test
    fun `비밀번호 로그인 성공은 방법이 password인 로그인 성공으로 남는다`() {
        val u = user()
        login(u.loginId!!, "1234")
        val rows = auditOf(u.loginId!!).filter { it.action == "auth.login_success" }
        assertEquals(1, rows.size)
        assertEquals(AuditOutcome.SUCCESS, rows.single().outcome)
        assertTrue(rows.single().details!!.contains("\"method\":\"password\""), rows.single().details)
    }

    @Test
    fun `틀린 비밀번호는 거부되지 않은 실패로, 없는 사용자와 이유가 같다`() {
        val u = user()
        login(u.loginId!!, "wrong")
        login("nobody-$stamp", "1234")
        val known = auditOf(u.loginId!!).single { it.action == "auth.login_failure" }
        val unknown = auditOf("nobody-$stamp").single { it.action == "auth.login_failure" }
        assertEquals(AuditOutcome.FAILURE, known.outcome)
        assertEquals(AuditOutcome.FAILURE, unknown.outcome)
        assertTrue(known.details!!.contains("invalid_credentials") && unknown.details!!.contains("invalid_credentials"))
    }

    @Test
    fun `삭제된 계정과 인증 뒤 잠긴 계정은 거부로 이유를 남긴다`() {
        val deleted = user(UserState.DELETED); val locked = user(UserState.LOCKED)
        login(deleted.loginId!!, "1234"); login(locked.loginId!!, "1234")
        val d = auditOf(deleted.loginId!!).single { it.action == "auth.login_failure" }
        val l = auditOf(locked.loginId!!).single { it.action == "auth.login_failure" }
        assertEquals(AuditOutcome.DENIED, d.outcome); assertTrue(d.details!!.contains("\"reason\":\"deleted\""))
        assertEquals(AuditOutcome.DENIED, l.outcome); assertTrue(l.details!!.contains("locked_after_authentication"))
    }

    @Test
    fun `성공해도 실패 기록은 남기지 않고 실패해도 성공 기록은 남기지 않는다`() {
        val ok = user(); val bad = user()
        login(ok.loginId!!, "1234"); login(bad.loginId!!, "wrong")
        assertEquals(emptyList(), auditOf(ok.loginId!!).filter { it.action == "auth.login_failure" })
        assertEquals(emptyList(), auditOf(bad.loginId!!).filter { it.action == "auth.login_success" })
    }
}
