package dev.yona.domain.audit

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 감사 로그 조회(로드맵 X-9 A6, `decisions.md` D-62)를 검증한다: 필터(행위자, 조치 접두사, 결과, 대상, IP, 프로젝트, 기간), 범위(사이트 전체, 조직, 사용자 본인), 쪽 나누기, 내보내기(CSV, JSON 줄).
 * 시험끼리 섞이지 않게 조치 이름에 시험마다 다른 접두사를 붙여 거른다.
 */
@Transactional
class AuditQueryTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var query: AuditQueryService

    private val p = "q${System.nanoTime()}"

    private fun rec(
        action: String, outcome: AuditOutcome = AuditOutcome.SUCCESS, login: String? = "alice", actorId: Long? = 1L, targetType: String? = null, targetId: String? = null,
        targetLabel: String? = null, orgId: Long? = null, projectId: Long? = null
    ) = recorder.record(AuditEvent("$p.$action", outcome, targetType, targetId, targetLabel, orgId, projectId, actor = AuditActor(AuditActorType.USER, actorId, login)))

    private fun site(filter: AuditFilter = AuditFilter(action = "$p."), page: Int = 0, size: Int = 50) = query.search(filter, AuditScope.Site, page, size)

    // --- 필터 ---

    @Test
    fun `최신순으로 쪽을 나눠 돌려준다`() {
        repeat(5) { rec("e$it") }
        val first = site(size = 2); val second = site(page = 1, size = 2); val third = site(page = 2, size = 2)
        assertEquals(5L, first.total)
        assertEquals(listOf("$p.e4", "$p.e3"), first.items.map { it.action })
        assertEquals(listOf("$p.e2", "$p.e1"), second.items.map { it.action })
        assertEquals(listOf("$p.e0"), third.items.map { it.action })
    }

    @Test
    fun `쪽 크기는 1에서 200 사이로 맞춘다`() {
        rec("a")
        assertEquals(1, site(size = 0).size); assertEquals(200, site(size = 10000).size); assertEquals(0, site(page = -3).page)
    }

    @Test
    fun `조치는 접두사나 정확한 이름으로 거른다`() {
        rec("project.created"); rec("project.deleted"); rec("org.created")
        assertEquals(2, site(AuditFilter(action = "$p.project.")).total)
        assertEquals(1, site(AuditFilter(action = "$p.org.created")).total)
        assertEquals(1, site(AuditFilter(action = "$p.project.deleted")).total)
    }

    @Test
    fun `결과와 행위자와 대상으로 거른다`() {
        rec("a", AuditOutcome.DENIED, login = "Mallory", targetType = "project", targetLabel = "acme/web")
        rec("b", AuditOutcome.SUCCESS, login = "alice", targetType = "user", targetLabel = "bob")
        val base = AuditFilter(action = "$p.")
        assertEquals(listOf("$p.a"), site(base.copy(outcome = AuditOutcome.DENIED)).items.map { it.action })
        assertEquals(listOf("$p.a"), site(base.copy(actor = "mallo")).items.map { it.action })
        assertEquals(listOf("$p.b"), site(base.copy(targetType = "user")).items.map { it.action })
        assertEquals(listOf("$p.a"), site(base.copy(target = "ACME")).items.map { it.action })
        assertEquals(0L, site(base.copy(actor = "nobody")).total)
    }

    @Test
    fun `프로젝트 번호와 IP로 거른다`() {
        rec("a", projectId = 7L); rec("b", projectId = 8L)
        assertEquals(listOf("$p.a"), site(AuditFilter(action = "$p.", projectId = 7L)).items.map { it.action })
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest().also { it.remoteAddr = "198.51.100.9" }))
        rec("c")
        assertEquals(listOf("$p.c"), site(AuditFilter(action = "$p.", ip = "198.51.100.9")).items.map { it.action })
    }

    @Test
    fun `기간으로 거른다`() {
        rec("a")
        val now = System.currentTimeMillis()
        assertEquals(1L, site(AuditFilter(action = "$p.", fromMillis = now - 60_000, toMillis = now + 60_000)).total)
        assertEquals(0L, site(AuditFilter(action = "$p.", fromMillis = now + 60_000)).total)
        assertEquals(0L, site(AuditFilter(action = "$p.", toMillis = now - 60_000)).total)
    }

    // --- 범위 ---

    @Test
    fun `조직 범위는 그 조직 번호의 기록만 본다`() {
        rec("a", orgId = 5L); rec("b", orgId = 6L); rec("c")
        val result = query.search(AuditFilter(action = "$p."), AuditScope.Organization(5L), 0, 50)
        assertEquals(listOf("$p.a"), result.items.map { it.action })
    }

    @Test
    fun `사용자 범위는 자기 계정의 로그인과 토큰과 계정 기록만 본다`() {
        // 범주 규칙(`auth.`, `token.`, `account.`)을 시험하므로 진짜 조치 이름을 쓰고, 번호와 로그인 ID가 시험마다 다르다는 것으로 서로 가린다
        val id = 900_000_000L + System.nanoTime() % 1_000_000; val login = "me$p"; val other = id + 1
        fun real(action: String, actorId: Long?, actorLogin: String?, targetType: String? = null, targetId: String? = null) =
            recorder.record(AuditEvent(action, targetType = targetType, targetId = targetId, actor = AuditActor(AuditActorType.USER, actorId, actorLogin)))
        real("auth.login_success", id, login)
        real("token.created", id, login)
        real("account.password_changed", 7L, "admin-$p", "user", id.toString())
        real("auth.login_failure", null, login)
        real("project.created", id, login)
        real("auth.login_success", other, "other$p")
        val result = query.search(AuditFilter(), AuditScope.User(id, login), 0, 50)
        assertEquals(setOf("auth.login_success", "token.created", "account.password_changed", "auth.login_failure"), result.items.map { it.action }.toSet())
        assertEquals(4L, result.total)
    }

    // --- 내보내기 ---

    @Test
    fun `내보내기는 시간순으로 한도까지 돌려준다`() {
        repeat(5) { rec("e$it") }
        val seen = mutableListOf<String>()
        query.stream(AuditFilter(action = "$p."), AuditScope.Site, 3) { seen += it.action }
        assertEquals(listOf("$p.e0", "$p.e1", "$p.e2"), seen)
    }

    @Test
    fun `CSV는 머리글과 이스케이프를 지키고 수식으로 읽히는 칸은 막는다`() {
        rec("csv", login = "=cmd|' /C calc'!A0", targetLabel = "a,b \"q\"\nline")
        val rows = mutableListOf<String>()
        query.stream(AuditFilter(action = "$p.csv"), AuditScope.Site, 10) { rows += AuditCsv.row(it) }
        assertEquals("id,time,actor_type,actor_id,actor_login,action,outcome,target_type,target_id,target_label,organization_id,project_id,ip,user_agent,request_id,token_id,token_scopes,details,prev_hash,hash", AuditCsv.HEADER)
        val line = rows.single()
        assertTrue(line.contains("\"'=cmd|' /C calc'!A0\"") || line.contains("'=cmd|' /C calc'!A0"), line)
        assertTrue(line.contains("\"a,b \"\"q\"\"\nline\""), line)
        assertFalse(line.contains(",=cmd"))
    }

    @Test
    fun `JSON 줄은 한 줄에 한 건이고 해시를 담는다`() {
        rec("json"); rec("json")
        val lines = mutableListOf<String>()
        query.stream(AuditFilter(action = "$p.json"), AuditScope.Site, 10) { lines += query.jsonLine(it) }
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.startsWith("{") && !it.contains("\n") && it.contains("\"hash\":\"") && it.contains("\"action\":\"$p.json\"") })
    }
}
