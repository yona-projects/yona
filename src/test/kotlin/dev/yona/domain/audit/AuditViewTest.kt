package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 감사 로그 화면(로드맵 X-9 A6, `decisions.md` D-62)을 검증한다: 사이트 관리자의 전체 조회와 내보내기와 무결성 검증, 조직 관리자의 조직 범위 조회, 사용자 본인의 보안 기록.
 * 시험마다 조치 이름에 다른 접두사를 붙여 거르고 화면의 필터(`action`)로 그 기록만 본다.
 */
@AutoConfigureMockMvc
@Transactional
class AuditViewTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var organizations: OrganizationService
    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var logs: AuditLogRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

    private var sequence = 0
    private val stamp = System.nanoTime()
    private val p = "v$stamp"

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "av$n-$stamp", name = "화면$n", email = "av$n-$stamp@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun admin(): MockHttpSession = login(user().also { siteAdmins.save(SiteAdmin(admin = it)) })

    private fun rec(action: String, outcome: AuditOutcome = AuditOutcome.SUCCESS, orgId: Long? = null, label: String? = null) =
        recorder.record(AuditEvent("$p.$action", outcome, targetType = "project", targetLabel = label, organizationId = orgId, actor = AuditActor(AuditActorType.USER, 1L, "seed")))

    private fun page(path: String, session: MockHttpSession) = Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    // --- 사이트 ---

    @Test
    fun `사이트 관리자는 감사 로그를 표로 보고 필터로 거른다`() {
        rec("created", label = "acme/web"); rec("denied", AuditOutcome.DENIED, label = "acme/api")
        val doc = page("/sites/audit?action=$p.", admin())
        assertEquals(listOf("$p.denied", "$p.created"), doc.select("tr.audit-row .audit-action").map { it.text() })
        val denied = page("/sites/audit?action=$p.&outcome=DENIED", admin())
        assertEquals(listOf("$p.denied"), denied.select("tr.audit-row .audit-action").map { it.text() })
        assertEquals(listOf("acme/api"), denied.select("tr.audit-row .audit-target").map { it.text() })
        assertNotNull(doc.selectFirst("form.audit-filter input[name=action]"))
        assertEquals("$p.", doc.selectFirst("form.audit-filter input[name=action]")!!.attr("value"))
    }

    @Test
    fun `쪽 나누기 링크가 필터를 이어 받는다`() {
        repeat(3) { rec("e$it") }
        val first = page("/sites/audit?action=$p.&size=2", admin())
        assertEquals(2, first.select("tr.audit-row").size)
        val next = first.selectFirst("a.audit-next")!!.attr("href")
        assertTrue(next.contains("page=2") && next.contains("action=$p.") && next.contains("size=2"), next)
        assertEquals(1, page(next, admin()).select("tr.audit-row").size)
    }

    @Test
    fun `기록이 없으면 비었다고 알린다`() {
        assertNotNull(page("/sites/audit?action=none-$p", admin()).selectFirst(".audit-empty"))
    }

    @Test
    fun `사이트 관리자가 아니면 볼 수 없다`() {
        mockMvc.perform(get("/sites/audit").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/audit/export")).andExpect(status().is4xxClientError)
        mockMvc.perform(get("/sites/audit/verify").session(login(user()))).andExpect(status().isForbidden)
    }

    @Test
    fun `내려받기는 CSV와 JSON 줄과 JSON을 주고 내려받은 일도 남긴다`() {
        rec("export", label = "acme/web")
        val session = admin()
        val csv = mockMvc.perform(get("/sites/audit/export?format=csv&action=$p.").session(session)).andExpect(status().isOk).andReturn().response
        assertTrue(csv.contentType!!.startsWith("text/csv"), csv.contentType)
        assertTrue(csv.getHeader("Content-Disposition")!!.contains("attachment") && csv.getHeader("Content-Disposition")!!.contains(".csv"))
        val lines = csv.contentAsString.trim().lines()
        assertEquals(AuditCsv.HEADER, lines.first()); assertTrue(lines[1].contains("$p.export"), lines[1])
        val jsonl = mockMvc.perform(get("/sites/audit/export?format=jsonl&action=$p.").session(session)).andExpect(status().isOk).andReturn().response
        assertTrue(jsonl.contentType!!.contains("ndjson")); assertEquals(1, jsonl.contentAsString.trim().lines().size)
        val json = mockMvc.perform(get("/sites/audit/export?format=json&action=$p.").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(json.startsWith("{\"events\":[") && json.contains("$p.export"), json)
        assertTrue(logs.findAll().count { it.action == "audit.exported" } >= 3)
    }

    @Test
    fun `알 수 없는 내려받기 형식은 400이다`() {
        mockMvc.perform(get("/sites/audit/export?format=xml").session(admin())).andExpect(status().isBadRequest)
    }

    @Test
    fun `무결성 검증은 온전하면 통과하고 고치면 깨진 기록을 알린다`() {
        rec("a"); rec("b")
        val session = admin()
        val ok = mockMvc.perform(get("/sites/audit/verify").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(ok.contains("\"ok\":true"), ok)
        val broken = logs.findAll().first { it.action == "$p.a" }.id
        entityManager.flush()
        jdbc.update("update audit_log set action = 'forged' where id = ?", broken)
        // 시험 트랜잭션의 영속성 컨텍스트가 옛 값을 쥐고 있으므로 비워서 DB의 값을 다시 읽게 한다
        entityManager.clear()
        val bad = mockMvc.perform(get("/sites/audit/verify").session(session)).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(bad.contains("\"ok\":false") && bad.contains("\"brokenId\":$broken"), bad)
    }

    @Test
    fun `사이트 관리 사이드바에 감사 로그 링크가 있다`() {
        assertNotNull(page("/sites/audit?action=$p.", admin()).selectFirst("a[href=/sites/audit]"))
    }

    // --- 조직 ---

    @Test
    fun `조직 관리자는 자기 조직의 기록만 본다`() {
        val owner = user(); val other = user()
        val org = organizations.create("ao$stamp", null, owner); val otherOrg = organizations.create("ap$stamp", null, other)
        rec("mine", orgId = org.id); rec("theirs", orgId = otherOrg.id); rec("global")
        val doc = page("/organizations/${org.name}/audit?action=$p.", login(owner))
        assertEquals(listOf("$p.mine"), doc.select("tr.audit-row .audit-action").map { it.text() })
        assertNotNull(page("/organizations/${org.name}/members", login(owner)).selectFirst("a[href=/organizations/${org.name}/audit]"))
    }

    @Test
    fun `조직 관리자가 아니면 조직 기록을 볼 수 없다`() {
        val owner = user(); val outsider = user()
        val org = organizations.create("ar$stamp", null, owner)
        mockMvc.perform(get("/organizations/${org.name}/audit").session(login(outsider))).andExpect(status().isForbidden)
        mockMvc.perform(get("/organizations/${org.name}/audit/export").session(login(outsider))).andExpect(status().isForbidden)
    }

    @Test
    fun `조직 내려받기는 그 조직 기록만 담는다`() {
        val owner = user(); val org = organizations.create("ae$stamp", null, owner)
        rec("mine", orgId = org.id); rec("theirs", orgId = org.id!! + 100000)
        val body = mockMvc.perform(get("/organizations/${org.name}/audit/export?format=csv&action=$p.").session(login(owner))).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("$p.mine") && !body.contains("$p.theirs"), body)
    }

    // --- 사용자 ---

    @Test
    fun `사용자는 자기 로그인과 토큰 기록만 보고 탭이 보인다`() {
        val me = user(); val other = user()
        recorder.record(AuditEvent("auth.login_success", targetType = "user", targetId = me.id.toString(), targetLabel = me.loginId, actor = AuditActor(AuditActorType.USER, me.id, me.loginId)))
        recorder.record(AuditEvent("auth.login_success", targetType = "user", targetId = other.id.toString(), targetLabel = other.loginId, actor = AuditActor(AuditActorType.USER, other.id, other.loginId)))
        recorder.record(AuditEvent("project.created", targetType = "project", actor = AuditActor(AuditActorType.USER, me.id, me.loginId)))
        val doc = page("/user/editform/audit", login(me))
        val actors = doc.select("tr.audit-row .audit-actor").map { it.text() }
        assertTrue(actors.isNotEmpty() && actors.all { it == me.loginId }, actors.toString())
        assertFalse(doc.select("tr.audit-row .audit-action").any { it.text() == "project.created" })
        assertNotNull(doc.selectFirst(".nav-tabs a[href=/user/editform/audit]"))
    }

    @Test
    fun `로그인하지 않으면 사용자 기록 화면은 열리지 않는다`() {
        mockMvc.perform(get("/user/editform/audit")).andExpect(status().is3xxRedirection)
    }
}
