package dev.yona.domain.audit

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.OrganizationService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.ApiTokenSupport
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 감사 로그 REST(로드맵 X-9 A6, `decisions.md` D-62)를 검증한다: 사이트 전체(`site_admin`), 조직(`admin:org`), 사용자 본인(`read:user`)의 조회, 내보내기 형식, 무결성 검증.
 */
@AutoConfigureMockMvc
@Transactional
class AuditApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var organizations: OrganizationService
    @Autowired private lateinit var recorder: AuditRecorder
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository

    private var sequence = 0
    private val stamp = System.nanoTime()
    private val p = "r$stamp"
    private val tokens = mutableMapOf<Long, String>()

    private fun user(scopes: List<String>, siteAdmin: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "aa$n-$stamp", name = "API$n", email = "aa$n-$stamp@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (siteAdmin) siteAdmins.save(SiteAdmin(admin = u))
        tokens[u.id!!] = "at$n$stamp"
        ApiTokenSupport.grant(apiTokenRepository, u, tokens.getValue(u.id!!), scopes)
        return u
    }

    private fun call(path: String, u: User?) = mockMvc.perform(get("/-_-api/v1$path").accept(MediaType.APPLICATION_JSON).apply { u?.let { header("Authorization", "token ${tokens.getValue(it.id!!)}") } })

    private fun rec(action: String, orgId: Long? = null) =
        recorder.record(AuditEvent("$p.$action", targetType = "project", targetLabel = "acme/web", organizationId = orgId, actor = AuditActor(AuditActorType.USER, 1L, "seed")))

    // --- 사이트 ---

    @Test
    fun `사이트 관리자 토큰은 감사 로그를 JSON으로 조회한다`() {
        rec("a"); rec("b")
        val admin = user(listOf("site_admin"), siteAdmin = true)
        call("/admin/audit?action=$p.&size=1", admin).andExpect(status().isOk)
            .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.page").value(1)).andExpect(jsonPath("$.size").value(1))
            .andExpect(jsonPath("$.events.length()").value(1)).andExpect(jsonPath("$.events[0].action").value("$p.b"))
            .andExpect(jsonPath("$.events[0].hash").isNotEmpty).andExpect(jsonPath("$.events[0].actor_login").value("seed"))
        call("/admin/audit?action=$p.&size=1&page=2", admin).andExpect(jsonPath("$.events[0].action").value("$p.a"))
    }

    @Test
    fun `필터를 줄 수 있다`() {
        rec("a")
        val admin = user(listOf("site_admin"), siteAdmin = true)
        call("/admin/audit?action=$p.&outcome=DENIED", admin).andExpect(jsonPath("$.total").value(0))
        call("/admin/audit?action=$p.&actor=zzz", admin).andExpect(jsonPath("$.total").value(0))
        call("/admin/audit?action=$p.&actor=see&targetType=project&target=ACME", admin).andExpect(jsonPath("$.total").value(1))
    }

    @Test
    fun `CSV와 JSON 줄로 내려받는다`() {
        rec("a")
        val admin = user(listOf("site_admin"), siteAdmin = true)
        val csv = call("/admin/audit?action=$p.&format=csv", admin).andExpect(status().isOk).andReturn().response
        assertTrue(csv.contentType!!.startsWith("text/csv")); assertTrue(csv.contentAsString.startsWith(AuditCsv.HEADER))
        val jsonl = call("/admin/audit?action=$p.&format=jsonl", admin).andExpect(status().isOk).andReturn().response
        assertTrue(jsonl.contentType!!.contains("ndjson")); assertEquals(1, jsonl.contentAsString.trim().lines().size)
        call("/admin/audit?format=xml", admin).andExpect(status().isBadRequest)
    }

    @Test
    fun `무결성 검증을 한다`() {
        rec("a")
        val admin = user(listOf("site_admin"), siteAdmin = true)
        call("/admin/audit/verify", admin).andExpect(status().isOk).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.checked").isNumber)
    }

    @Test
    fun `사이트 감사 로그는 범위와 관리자 자격이 모두 있어야 한다`() {
        val noScope = user(listOf("repo"), siteAdmin = true)
        val notAdmin = user(listOf("site_admin"))
        call("/admin/audit", noScope).andExpect(status().isForbidden)
        call("/admin/audit", notAdmin).andExpect(status().isForbidden)
        call("/admin/audit", null).andExpect(status().isUnauthorized)
    }

    // --- 조직 ---

    @Test
    fun `조직 관리자 토큰은 자기 조직의 기록만 조회한다`() {
        val owner = user(listOf("admin:org")); val other = user(listOf("admin:org"))
        val org = organizations.create("api$stamp", null, owner); val otherOrg = organizations.create("apj$stamp", null, other)
        rec("mine", org.id); rec("theirs", otherOrg.id)
        call("/orgs/${org.name}/audit?action=$p.", owner).andExpect(status().isOk).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.events[0].action").value("$p.mine"))
        call("/orgs/${org.name}/audit", other).andExpect(status().isForbidden)
        call("/orgs/${org.name}/audit?format=csv&action=$p.", owner).andExpect(status().isOk)
        call("/orgs/nope$stamp/audit", owner).andExpect(status().isNotFound)
    }

    @Test
    fun `조직 감사 로그에는 조직 관리 범위가 필요하다`() {
        val owner = user(listOf("read:org"))
        val org = organizations.create("apk$stamp", null, owner)
        call("/orgs/${org.name}/audit", owner).andExpect(status().isForbidden)
    }

    // --- 사용자 ---

    @Test
    fun `사용자는 자기 보안 기록을 조회한다`() {
        val me = user(listOf("read:user")); val other = user(listOf("read:user"))
        recorder.record(AuditEvent("auth.login_success", targetType = "user", targetId = me.id.toString(), targetLabel = me.loginId, actor = AuditActor(AuditActorType.USER, me.id, me.loginId)))
        recorder.record(AuditEvent("auth.login_success", targetType = "user", targetId = other.id.toString(), targetLabel = other.loginId, actor = AuditActor(AuditActorType.USER, other.id, other.loginId)))
        call("/user/audit", me).andExpect(status().isOk).andExpect(jsonPath("$.events[0].actor_login").value(me.loginId!!))
        call("/user/audit", user(listOf("repo"))).andExpect(status().isForbidden)
    }
}
