package dev.yona.domain.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Organization
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직의 OAuth 앱 정책(X-39, `decisions.md` D-78)을 검증한다: 기본은 제한 없음, 제한하면 승인하지 않은 앱은 막히고 그 조직이 소유한 앱은 자동으로 승인이며,
 * 관리자는 조직 구성원이 승인한 적이 있는 앱 목록에서 앱마다 승인과 거부를 정한다. 조직이나 앱을 지우면 정책 행도 지워진다.
 */
@Transactional
class OAuthOrgPolicyServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: OAuthOrgPolicyService
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService
    @Autowired private lateinit var management: dev.yona.domain.project.OrganizationManagementService
    @Autowired private lateinit var approvals: OrganizationOAuthApprovalRepository
    @Autowired private lateinit var policies: OrganizationOAuthPolicyRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var em: jakarta.persistence.EntityManager

    private var n = 0

    private fun user() = users.save(User(loginId = "op${++n}-${System.nanoTime()}", name = "op$n", email = "op$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE))

    private fun organization(admin: User): Organization = organizationService.create("op${++n}${System.nanoTime() % 1000000}", null, admin)

    private fun app(owner: User, name: String = "외부 앱"): OAuthClient =
        (clientService.create(owner, name, "", "", "https://a.example/cb", false, setOf("read:user")) as ClientCreation.Created).client

    private fun orgApp(org: Organization, admin: User): OAuthClient =
        (clientService.createFor(ClientOwner.of(org), admin, "조직 앱", "", "", "https://a.example/cb", false, setOf("read:user")) as ClientCreation.Created).client

    private fun consent(user: User, client: OAuthClient) { consents.save(OAuthConsent(clientRowId = client.id, userId = user.id, scopes = "read:user", createdDate = Date())) }

    @Test
    fun `기본은 제한 없음이라 모든 앱이 허용된다`() {
        val admin = user(); val org = organization(admin); val client = app(user())
        assertFalse(service.restricted(org.id!!)); assertTrue(service.allows(org.id!!, client.id!!)); assertTrue(service.deniedOrganizations(client.id!!, listOf(org.id!!)).isEmpty())
    }

    @Test
    fun `제한하면 승인하지 않은 앱은 막히고 그 조직이 소유한 앱은 자동으로 허용된다`() {
        val admin = user(); val org = organization(admin); val other = organization(admin); val outside = app(user()); val own = orgApp(org, admin); val ofOther = orgApp(other, admin)
        service.setRestricted(org, true, admin)
        assertTrue(service.restricted(org.id!!))
        assertFalse(service.allows(org.id!!, outside.id!!)); assertTrue(service.allows(org.id!!, own.id!!)); assertFalse(service.allows(org.id!!, ofOther.id!!))
        assertEquals(setOf(org.id), service.deniedOrganizations(outside.id!!, listOf(org.id!!, other.id!!))); assertTrue(service.allows(other.id!!, outside.id!!))
    }

    @Test
    fun `조직 구성원이 승인한 적이 있는 앱만 후보이고 소유한 앱과 구성원이 아닌 사람의 승인은 뺀다`() {
        val admin = user(); val member = user(); val stranger = user(); val org = organization(admin); management.addMember(org, admin, member.loginId!!)
        val seen = app(user(), "구성원이 쓰는 앱"); val unseen = app(user(), "바깥 사람만 쓰는 앱"); val own = orgApp(org, admin)
        consent(member, seen); consent(stranger, unseen); consent(admin, own); consent(admin, seen)
        val rows = service.candidates(org)
        assertEquals(listOf("구성원이 쓰는 앱"), rows.map { it.client.name }); assertEquals(2, rows.single().members); assertNull(rows.single().state)
    }

    @Test
    fun `후보 앱을 승인하면 허용되고 거부하거나 결정을 지우면 다시 막힌다`() {
        val admin = user(); val member = user(); val org = organization(admin); management.addMember(org, admin, member.loginId!!)
        val client = app(user()); consent(member, client); service.setRestricted(org, true, admin)
        assertTrue(service.decide(org, client.id!!, ApprovalState.APPROVED, admin)); assertTrue(service.allows(org.id!!, client.id!!)); assertEquals(ApprovalState.APPROVED, service.candidates(org).single().state)
        assertTrue(service.decide(org, client.id!!, ApprovalState.DENIED, admin)); assertFalse(service.allows(org.id!!, client.id!!)); assertEquals(ApprovalState.DENIED, service.candidates(org).single().state)
        assertTrue(service.decide(org, client.id!!, ApprovalState.APPROVED, admin)); assertTrue(service.decide(org, client.id!!, null, admin))
        assertFalse(service.allows(org.id!!, client.id!!)); assertNull(service.candidates(org).single().state); assertEquals(0, approvals.findAll().size)
    }

    @Test
    fun `후보가 아닌 앱은 결정할 수 없고 제한을 풀면 승인 결정이 남아 있어도 모두 허용된다`() {
        val admin = user(); val org = organization(admin); val stranger = app(user())
        assertFalse(service.decide(org, stranger.id!!, ApprovalState.APPROVED, admin)); assertEquals(0, approvals.findAll().size)
        service.setRestricted(org, true, admin); service.setRestricted(org, false, admin)
        assertTrue(service.allows(org.id!!, stranger.id!!))
    }

    @Test
    fun `정책 변경과 승인 결정은 조직에 속한 감사 기록을 남긴다`() {
        val admin = user(); val member = user(); val org = organization(admin); management.addMember(org, admin, member.loginId!!)
        val client = app(user()); consent(member, client)
        service.setRestricted(org, true, admin); service.decide(org, client.id!!, ApprovalState.APPROVED, admin)
        val changed = logs.findAll().single { it.action == "oauth.org_policy_changed" }; val decided = logs.findAll().single { it.action == "oauth.org_app_decided" }
        assertEquals(org.id, changed.organizationId); assertEquals(admin.id, changed.actorId); assertTrue(changed.details!!.contains("\"restricted\":true"))
        assertEquals(org.id, decided.organizationId); assertTrue(decided.details!!.contains("\"state\":\"APPROVED\""))
    }

    @Test
    fun `앱을 지우면 그 앱의 승인 결정도 지워지고 조직을 지우면 정책과 결정이 모두 지워진다`() {
        val admin = user(); val member = user(); val org = organization(admin); management.addMember(org, admin, member.loginId!!)
        val a = app(user()); val b = app(user()); consent(member, a); consent(member, b); service.setRestricted(org, true, admin)
        service.decide(org, a.id!!, ApprovalState.APPROVED, admin); service.decide(org, b.id!!, ApprovalState.DENIED, admin); em.flush()
        clientService.deleteAsAdmin(a.id!!); em.flush(); em.clear()
        assertEquals(listOf(b.id), approvals.findAll().map { it.clientRowId })
        management.delete(org); em.flush(); em.clear()
        assertEquals(0, approvals.findAll().size); assertNull(policies.findById(org.id!!).orElse(null))
    }
}
