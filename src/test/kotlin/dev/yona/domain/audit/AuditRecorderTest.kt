package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.security.YonaAuthentication
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 감사 기록기(로드맵 X-9 A2, `decisions.md` D-62)를 검증한다: 행위자와 요청 문맥의 자동 채움, 비밀 값 가리기, 해시 사슬과 검증, 트랜잭션 정책(변경과 같은 트랜잭션, 경계 이벤트는 별도),
 * JSON 줄 파일. 시험이 커밋하는 기록은 `@AfterEach`가 지운다.
 */
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-audit"])
class AuditRecorderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var verifier: AuditVerifier
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var heads: AuditChainHeadRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    private val transaction by lazy { TransactionTemplate(transactionManager) }
    private var sequence = 0

    @AfterEach
    fun cleanUp() {
        SecurityContextHolder.clearContext()
        RequestContextHolder.resetRequestAttributes()
        transaction.executeWithoutResult { logs.deleteAllInBatch(); heads.deleteAllInBatch() }
        File("build/tmp/it-audit/audit").deleteRecursively()
    }

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "au$n-${System.nanoTime()}", name = "감사$n", email = "au$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun record(action: String = "test.event", block: AuditEvent.() -> AuditEvent = { this }) =
        transaction.execute { recorder.record(AuditEvent(action).block()) }!!

    private fun request(configure: MockHttpServletRequest.() -> Unit = {}): MockHttpServletRequest =
        MockHttpServletRequest().also { it.remoteAddr = "203.0.113.7"; it.configure(); RequestContextHolder.setRequestAttributes(ServletRequestAttributes(it)) }

    // --- 기록 ---

    @Test
    fun `요청 문맥이 없으면 시스템이 행위자이고 번호는 1부터 이어진다`() {
        // 스프링 시험 환경은 요청 문맥을 미리 깔아 두므로 요청 밖(스케줄러)을 재현하려면 지운다
        RequestContextHolder.resetRequestAttributes()
        val first = record(); val second = record("test.second")
        assertEquals(AuditActorType.SYSTEM, first.actorType)
        assertEquals(AuditOutcome.SUCCESS, first.outcome)
        assertEquals(1L, first.id); assertEquals(2L, second.id)
        assertTrue(first.createdMillis > 0)
        assertNull(first.ip)
    }

    @Test
    fun `로그인한 사용자는 사용자 행위자이고 IP와 사용자 에이전트와 요청 번호가 채워진다`() {
        val u = user()
        SecurityContextHolder.getContext().authentication = YonaAuthentication.of(u)
        request { addHeader("User-Agent", "JUnit/1.0"); addHeader("X-Request-Id", "req-123") }
        val log = record()
        assertEquals(AuditActorType.USER, log.actorType)
        assertEquals(u.id, log.actorId); assertEquals(u.loginId, log.actorLogin)
        assertEquals("203.0.113.7", log.ip); assertEquals("JUnit/1.0", log.userAgent); assertEquals("req-123", log.requestId)
        assertNull(log.tokenId)
    }

    @Test
    fun `토큰으로 인증했으면 토큰 행위자이고 토큰 번호와 범위가 남는다`() {
        val u = user()
        SecurityContextHolder.getContext().authentication = YonaAuthentication.of(u, viaToken = true, scopes = setOf("repo", "read:org"), tokenId = 77L)
        request()
        val log = record()
        assertEquals(AuditActorType.TOKEN, log.actorType)
        assertEquals(77L, log.tokenId)
        assertEquals("read:org,repo", log.tokenScopes)
    }

    @Test
    fun `요청 번호가 없으면 만들어 같은 요청의 기록이 함께 쓴다`() {
        request()
        val a = record(); val b = record()
        assertNotNull(a.requestId); assertEquals(a.requestId, b.requestId)
        RequestContextHolder.resetRequestAttributes(); request()
        assertNotEquals(a.requestId, record().requestId)
    }

    @Test
    fun `행위자를 직접 줄 수 있다(로그인 실패의 시도한 ID)`() {
        val log = record("auth.login_failure") { copy(outcome = AuditOutcome.FAILURE, actor = AuditActor(AuditActorType.ANONYMOUS, null, "tried-id")) }
        assertEquals(AuditActorType.ANONYMOUS, log.actorType); assertEquals("tried-id", log.actorLogin); assertEquals(AuditOutcome.FAILURE, log.outcome)
    }

    @Test
    fun `대상과 소속을 남긴다`() {
        val log = record("project.deleted") { copy(targetType = "project", targetId = "12", targetLabel = "acme/web", organizationId = 3L, projectId = 12L) }
        assertEquals(listOf("project", "12", "acme/web", 3L, 12L), listOf(log.targetType, log.targetId, log.targetLabel, log.organizationId, log.projectId))
    }

    // --- 상세와 비밀 값 ---

    @Test
    fun `상세는 JSON 글로 남고 비밀 값 이름의 항목은 가린다`() {
        val log = record { copy(details = mapOf("name" to "web", "password" to "p", "apiToken" to "t", "webhookSecret" to "s", "nested" to mapOf("Authorization" to "Bearer x", "ok" to 1))) }
        val details = log.details!!
        assertTrue(details.contains("\"name\":\"web\""), details)
        assertFalse(details.contains("\"p\"") || details.contains("Bearer x") || details.contains("\"t\"") || details.contains("\"s\""), details)
        assertTrue(details.contains("[redacted]"), details)
        assertTrue(details.contains("\"ok\":1"), details)
    }

    @Test
    fun `너무 긴 상세는 잘라 표시한다`() {
        val log = record { copy(details = mapOf("text" to "가".repeat(20000))) }
        assertTrue(log.details!!.length <= AuditDetails.MAX_LENGTH + 40, log.details!!.length.toString())
        assertTrue(log.details!!.contains("truncated"))
    }

    @Test
    fun `상세가 없으면 null이다`() {
        assertNull(record().details)
    }

    // --- 해시 사슬 ---

    @Test
    fun `첫 기록의 이전 해시는 0이고 다음 기록은 앞 기록의 해시를 잇는다`() {
        val first = record(); val second = record()
        assertEquals(AuditHash.GENESIS, first.prevHash)
        assertEquals(first.hash, second.prevHash)
        assertEquals(64, first.hash.length)
        assertEquals(first.hash, AuditHash.of(first))
    }

    @Test
    fun `손대지 않은 사슬은 검증을 통과한다`() {
        repeat(5) { record("test.e$it") }
        val result = verifier.verify()
        assertTrue(result.ok, result.toString()); assertEquals(5L, result.checked)
        assertTrue(verifier.verify().ok)
    }

    @Test
    fun `비어 있어도 통과한다`() {
        val result = verifier.verify()
        assertTrue(result.ok); assertEquals(0L, result.checked)
    }

    @Test
    fun `기록의 내용을 고치면 그 기록에서 검증이 깨진다`() {
        repeat(4) { record("test.e$it") }
        jdbc.update("update audit_log set action = 'forged.action' where id = 2")
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals(2L, result.brokenId)
    }

    @Test
    fun `가운데 기록을 지우면 다음 기록에서 검증이 깨진다`() {
        repeat(4) { record("test.e$it") }
        jdbc.update("delete from audit_log where id = 2")
        val result = verifier.verify()
        assertFalse(result.ok); assertEquals(3L, result.brokenId)
    }

    @Test
    fun `끝 기록을 지우면 사슬의 끝과 맞지 않아 깨진다`() {
        repeat(3) { record("test.e$it") }
        jdbc.update("delete from audit_log where id = 3")
        val result = verifier.verify()
        assertFalse(result.ok)
    }

    // --- 트랜잭션 정책 ---

    @Test
    fun `변경과 같은 트랜잭션이라 롤백하면 기록도 사라진다`() {
        transaction.execute { status -> recorder.record(AuditEvent("test.rolled_back")); status.setRollbackOnly() }
        assertEquals(0L, logs.count())
        record()
        assertEquals(1L, logs.count()); assertEquals(1L, logs.findAll().single().id)
    }

    @Test
    fun `경계 이벤트는 별도 트랜잭션이라 바깥이 롤백해도 남는다`() {
        transaction.execute { status -> recorder.recordIndependent(AuditEvent("auth.login_failure", AuditOutcome.FAILURE)); status.setRollbackOnly() }
        assertEquals(1L, logs.count())
        assertEquals("auth.login_failure", logs.findAll().single().action)
    }

    @Test
    fun `경계 이벤트를 기록하지 못해도 예외를 밖으로 던지지 않는다`() {
        class Bomb { override fun toString(): String = throw IllegalStateException("boom") }
        recorder.recordIndependent(AuditEvent("auth.login_failure", details = mapOf("x" to Bomb())))
        assertTrue(verifier.verify().ok)
    }

    // --- JSON 줄 파일 ---

    @Test
    fun `커밋하면 JSON 줄 파일에 한 줄이 더해진다`() {
        record("file.event") { copy(targetType = "project", targetLabel = "acme/web") }
        val file = File("build/tmp/it-audit/audit/audit-${LocalDate.now(ZoneOffset.UTC)}.jsonl")
        assertTrue(file.isFile, file.path)
        val line = file.readLines().single()
        assertTrue(line.startsWith("{") && line.contains("\"action\":\"file.event\"") && line.contains("\"hash\":\""), line)
    }

    @Test
    fun `롤백한 기록은 파일에 남지 않는다`() {
        transaction.execute { status -> recorder.record(AuditEvent("file.rolled_back")); status.setRollbackOnly() }
        val file = File("build/tmp/it-audit/audit/audit-${LocalDate.now(ZoneOffset.UTC)}.jsonl")
        assertTrue(!file.exists() || file.readText().isBlank())
    }
}
