package dev.yona.domain.audit

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.TokenFailureLimiter
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 감사 로그의 경계 이벤트(로드맵 X-9 A3, `decisions.md` D-62)를 검증한다: 로그인 성공과 실패와 거부, 로그아웃, 토큰 인증 실패와 차단, git HTTP 인증 실패, 인가 거부(웹 403, API 범위).
 * 경계 이벤트는 별도 트랜잭션이라 시험이 롤백해도 남으므로 `@AfterEach`가 지운다.
 */
@AutoConfigureMockMvc
@Transactional
class AuditBoundaryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var limiter: TokenFailureLimiter
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var properties: YonaProperties
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private var sequence = 0
    private val stamp = System.nanoTime()

    @AfterEach
    fun cleanUp() {
        limiter.clear()
        TransactionTemplate(transactionManager).apply { propagationBehavior = org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW }
            .executeWithoutResult { logs.deleteAllInBatch(); heads.deleteAllInBatch() }
    }

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ab$n-$stamp", name = "경계$n", email = "ab$n-$stamp@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun events(): List<AuditLog> = logs.findAll().sortedBy { it.id }

    private fun eventsOf(action: String) = events().filter { it.action == action }

    private fun login(loginKey: String, password: String, json: Boolean = false, session: MockHttpSession = MockHttpSession()) =
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", loginKey).param("password", password).apply { if (json) accept(MediaType.APPLICATION_JSON) })

    // --- 로그인 ---

    @Test
    fun `로그인 성공은 사용자 행위자와 방식을 남긴다`() {
        val u = user()
        login(u.loginId!!, "1234").andExpect(status().isSeeOther)
        val e = eventsOf("auth.login_success").single()
        assertEquals(AuditActorType.USER, e.actorType); assertEquals(u.id, e.actorId); assertEquals(u.loginId, e.actorLogin)
        assertEquals(AuditOutcome.SUCCESS, e.outcome)
        assertTrue(e.details!!.contains("\"method\":\"password\""), e.details)
        assertEquals("user", e.targetType); assertEquals(u.loginId, e.targetLabel)
        assertNotNull(e.ip); assertNotNull(e.requestId)
    }

    @Test
    fun `틀린 비밀번호는 시도한 ID와 함께 실패로 남고 비밀번호는 남지 않는다`() {
        val u = user()
        login(u.loginId!!, "wrong-password-xyz").andExpect(status().isSeeOther)
        val e = eventsOf("auth.login_failure").single()
        assertEquals(AuditActorType.ANONYMOUS, e.actorType); assertEquals(u.loginId, e.actorLogin)
        assertEquals(AuditOutcome.FAILURE, e.outcome)
        assertTrue(e.details!!.contains("invalid_credentials"), e.details)
        assertFalse(e.details!!.contains("wrong-password-xyz") || e.actorLogin!!.contains("wrong-password-xyz"))
    }

    @Test
    fun `없는 사용자도 같은 이유로 남겨 존재 여부를 드러내지 않는다`() {
        login("nobody-$stamp", "1234").andExpect(status().isSeeOther)
        val e = eventsOf("auth.login_failure").single()
        assertEquals("nobody-$stamp", e.actorLogin); assertTrue(e.details!!.contains("invalid_credentials"))
    }

    @Test
    fun `AJAX 로그인 실패도 남긴다`() {
        val u = user()
        login(u.loginId!!, "bad", json = true).andExpect(status().isForbidden)
        assertEquals(1, eventsOf("auth.login_failure").size)
    }

    @Test
    fun `잠기거나 지워진 계정의 로그인은 거부로 남긴다`() {
        val locked = user(UserState.LOCKED); val deleted = user(UserState.DELETED)
        login(locked.loginId!!, "1234"); login(deleted.loginId!!, "1234")
        val rows = eventsOf("auth.login_failure")
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.outcome == AuditOutcome.DENIED })
        assertEquals(setOf("locked_after_authentication", "deleted"), rows.map { Regex("\"reason\":\"([a-z_]+)\"").find(it.details!!)!!.groupValues[1] }.toSet())
    }

    @Test
    fun `로그아웃은 로그인한 사용자 행위자로 남긴다`() {
        val u = user(); val session = MockHttpSession()
        login(u.loginId!!, "1234", session = session)
        mockMvc.perform(get("/users/logout").session(session)).andExpect(status().isSeeOther)
        val e = eventsOf("auth.logout").single()
        assertEquals(AuditActorType.USER, e.actorType); assertEquals(u.loginId, e.actorLogin)
    }

    @Test
    fun `로그인하지 않은 로그아웃은 남기지 않는다`() {
        mockMvc.perform(get("/users/logout")).andExpect(status().isSeeOther)
        assertTrue(eventsOf("auth.logout").isEmpty())
    }

    // --- 토큰 인증 ---

    @Test
    fun `틀린 API 토큰은 실패로 남고 토큰 값은 남지 않는다`() {
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", "token yona_pat_THIS-IS-A-SECRET-TOKEN-VALUE-0000000000")).andExpect(status().isUnauthorized)
        val e = eventsOf("auth.token_failure").single()
        assertEquals(AuditOutcome.FAILURE, e.outcome); assertEquals(AuditActorType.ANONYMOUS, e.actorType)
        assertFalse(logs.findAll().any { listOf(it.details, it.targetLabel, it.targetId, it.actorLogin).any { v -> v?.contains("THIS-IS-A-SECRET") == true } })
    }

    @Test
    fun `토큰 실패가 쌓여 막히면 차단 거부를 한 번 남긴다`() {
        repeat(properties.apiTokenFailureLimit) { limiter.recordFailure("127.0.0.1") }
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", "token yona_pat_abc")).andExpect(status().`is`(429))
        mockMvc.perform(get("/-_-api/v1/user").header("Authorization", "token yona_pat_abc")).andExpect(status().`is`(429))
        val rows = eventsOf("auth.token_blocked")
        assertTrue(rows.isNotEmpty()); assertTrue(rows.all { it.outcome == AuditOutcome.DENIED })
    }

    // --- git HTTP 인증 ---

    @Test
    fun `git HTTP의 맞지 않는 토큰은 인증 실패로 남기고 비밀번호는 남기지 않는다 (D-70)`() {
        val u = user()
        val p = projects.save(Project(owner = u.loginId, name = "gp$stamp", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        menuSettings.save(ProjectMenuSetting(project = p, code = true))
        fun basic(secret: String) = "Basic " + java.util.Base64.getEncoder().encodeToString("${u.loginId}:$secret".toByteArray())
        mockMvc.perform(get("/${u.loginId}/${p.name}/info/refs").param("service", "git-upload-pack").header("Authorization", basic("wrong")))
        assertTrue(eventsOf("auth.basic_failure").isEmpty(), "비밀번호는 확인하지 않으므로 인증 실패로 남기지 않는다")
        mockMvc.perform(get("/${u.loginId}/${p.name}/info/refs").param("service", "git-upload-pack").header("Authorization", basic("yona_pat_" + "x".repeat(40))))
        val e = eventsOf("auth.basic_failure").single()
        assertEquals(u.loginId, e.actorLogin); assertEquals(AuditOutcome.FAILURE, e.outcome)
        assertTrue(e.details!!.contains("git_http"))
    }

    // --- 인가 거부 ---

    @Test
    fun `비공개 프로젝트의 403은 접근 거부로 남는다`() {
        val owner = user(); val outsider = user()
        val p = projects.save(Project(owner = owner.loginId, name = "pv$stamp", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true))
        val session = MockHttpSession().also { login(outsider.loginId!!, "1234", session = it) }
        mockMvc.perform(get("/${owner.loginId}/${p.name}").session(session)).andExpect(status().isForbidden)
        val e = eventsOf("access.denied").single()
        assertEquals(AuditOutcome.DENIED, e.outcome); assertEquals(outsider.loginId, e.actorLogin); assertEquals(AuditActorType.USER, e.actorType)
        assertEquals("GET /${owner.loginId}/${p.name}", e.targetLabel)
        assertTrue(e.details!!.contains("\"status\":403"), e.details)
    }

    @Test
    fun `접근 거부의 대상에는 질의 문자열을 남기지 않는다`() {
        val owner = user(); val outsider = user()
        val p = projects.save(Project(owner = owner.loginId, name = "pq$stamp", vcs = "GIT", projectScope = ProjectScope.PRIVATE))
        menuSettings.save(ProjectMenuSetting(project = p, code = true))
        val session = MockHttpSession().also { login(outsider.loginId!!, "1234", session = it) }
        mockMvc.perform(get("/${owner.loginId}/${p.name}").param("secret", "s3cr3t-value").session(session)).andExpect(status().isForbidden)
        assertFalse(logs.findAll().any { listOf(it.details, it.targetLabel, it.targetId).any { v -> v?.contains("s3cr3t-value") == true } })
    }

    @Test
    fun `토큰 범위가 모자란 API 요청은 범위 거부로 한 번만 남는다`() {
        val u = user()
        ApiTokenSupport.grant(apiTokenRepository, u, "tk-$stamp", listOf("read:org"))
        mockMvc.perform(get("/-_-api/v1/user/issues").header("Authorization", "token tk-$stamp")).andExpect(status().isForbidden)
        val e = eventsOf("access.api_scope_denied").single()
        assertEquals(AuditActorType.TOKEN, e.actorType); assertEquals(u.loginId, e.actorLogin)
        assertTrue(e.tokenScopes!!.contains("read:org"))
        assertTrue(eventsOf("access.denied").isEmpty(), "같은 요청을 일반 접근 거부로 또 남기지 않는다")
    }

    @Test
    fun `정상 응답은 거부로 남기지 않는다`() {
        mockMvc.perform(get("/")).andExpect(status().isOk)
        assertNull(events().firstOrNull { it.action.startsWith("access.") })
    }
}
