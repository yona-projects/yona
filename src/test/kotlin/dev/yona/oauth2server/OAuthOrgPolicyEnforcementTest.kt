package dev.yona.oauth2server

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ApprovalState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.ClientOwner
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.oauth2server.OAuthConsent
import dev.yona.domain.oauth2server.OAuthConsentRepository
import dev.yona.domain.oauth2server.OAuthOrgPolicyService
import dev.yona.domain.project.Organization
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 조직의 OAuth 앱 정책이 외부 API에서 지켜지는지 검증한다(X-39, `decisions.md` D-78). 제한한 조직에서 승인하지 않은 앱의 토큰은 그 조직의 비공개 프로젝트와 조직 정보에 닿지 못하고,
 * 공개 프로젝트는 읽을 수 있으며(쓰기는 막힌다), 목록에서도 빠진다. 승인한 앱과 조직이 소유한 앱, 제한이 없는 조직, 개인용 토큰은 영향을 받지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-oauth-org-policy"])
class OAuthOrgPolicyEnforcementTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var policy: OAuthOrgPolicyService
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService
    @Autowired private lateinit var management: dev.yona.domain.project.OrganizationManagementService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var apiTokens: ApiTokenService
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pe$k-$stamp", name = "pe$k", email = "pe$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    /** 조직, 관리자, 구성원(두 프로젝트의 구성원이다), 공개와 비공개 프로젝트이다. */
    private inner class World {
        val admin = user(); val member = user()
        val org: Organization = organizationService.create("pe$stamp${++n}", null, admin).also { management.addMember(it, admin, member.loginId!!) }
        val open = project("open", ProjectScope.PUBLIC); val secret = project("secret", ProjectScope.PRIVATE)

        private fun project(name: String, scope: ProjectScope): Project {
            val p = projects.save(Project(owner = org.name!!, name = name, vcs = "GIT", projectScope = scope, organization = org, createdDate = Date()))
            menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true))
            projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MEMBER.id).get()))
            return p
        }
    }

    private fun app(owner: User, name: String = "외부 앱") = (clientService.create(owner, name, "", "", "https://a.example/cb", false, setOf("read:user", "read:org", "repo")) as ClientCreation.Created).client

    private fun token(user: User, client: OAuthClient, scopes: String = "read:user,read:org,repo"): String {
        val value = "yona_oat_" + UUID.randomUUID().toString().replace("-", "")
        authorizations.save(
            OAuthAuthorization(
                id = UUID.randomUUID().toString(), clientRowId = client.id, userId = user.id, grantType = "authorization_code", authorizedScopes = scopes, attributes = "{}",
                accessTokenHash = TokenHasher.sha256Hex(value), accessTokenIssuedDate = Date(), accessTokenExpiresDate = Date.from(Instant.now().plusSeconds(3600)), accessTokenScopes = scopes,
                accessTokenMetadata = "{\"metadata.token.invalidated\":false}", createdDate = Date()
            )
        )
        consents.save(OAuthConsent(clientRowId = client.id, userId = user.id, scopes = scopes, createdDate = Date()))
        return value
    }

    private fun bearer(token: String) = "Bearer $token"

    private fun names(path: String, token: String, key: String = "projects"): Set<String> {
        val body = mockMvc.perform(get(path).header("Authorization", bearer(token))).andExpect(status().isOk).andReturn().response.contentAsString
        return JsonMapper.builder().build().readTree(body).get(key).toList().map { it.get("name").asString() }.toSet()
    }

    @Test
    fun `제한하지 않은 조직은 모든 앱을 허용한다`() {
        val w = World(); val t = token(w.member, app(user()))
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/secret").header("Authorization", bearer(t))).andExpect(status().isOk)
        assertEquals(setOf("open", "secret"), names("/-_-api/v1/owners/${w.org.name}/projects", t))
    }

    @Test
    fun `제한한 조직에서 승인하지 않은 앱은 비공개 프로젝트와 조직 정보에 닿지 못하고 이유를 알려 준다`() {
        val w = World(); val t = token(w.member, app(user())); policy.setRestricted(w.org, true, w.admin)
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/secret").header("Authorization", bearer(t))).andExpect(status().isForbidden)
            .andExpect(jsonPath("$.message").value("this organization restricts access by OAuth apps it has not approved"))
        mockMvc.perform(get("/-_-api/v1/orgs/${w.org.name}").header("Authorization", bearer(t))).andExpect(status().isForbidden)
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/open").header("Authorization", bearer(t))).andExpect(status().isOk)
    }

    @Test
    fun `공개 프로젝트는 읽을 수 있지만 쓰기는 막힌다`() {
        val w = World(); val t = token(w.member, app(user())); policy.setRestricted(w.org, true, w.admin)
        mockMvc.perform(
            post("/-_-api/v1/owners/${w.org.name}/projects/open/issues").header("Authorization", bearer(t)).contentType(MediaType.APPLICATION_JSON)
                .content("""{"issues":[{"title":"막혀야 한다","body":"x"}]}""")
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `목록에서도 승인하지 않은 앱에는 비공개 프로젝트와 조직이 빠진다`() {
        val w = World(); val t = token(w.member, app(user())); policy.setRestricted(w.org, true, w.admin)
        assertEquals(setOf("open"), names("/-_-api/v1/owners/${w.org.name}/projects", t)); assertEquals(setOf("open"), names("/-_-api/v1/user/projects", t))
        assertEquals(emptySet(), names("/-_-api/v1/user/organizations", t, "result"))
    }

    @Test
    fun `승인한 앱과 조직이 소유한 앱은 제한한 조직에서도 쓸 수 있다`() {
        val w = World(); val approved = app(user(), "승인할 앱"); val approvedToken = token(w.member, approved)
        val own = (clientService.createFor(ClientOwner.of(w.org), w.admin, "조직 앱", "", "", "https://a.example/cb", false, setOf("read:user", "read:org", "repo")) as ClientCreation.Created).client
        val ownToken = token(w.member, own); policy.setRestricted(w.org, true, w.admin)
        assertEquals(setOf("open"), names("/-_-api/v1/owners/${w.org.name}/projects", token(w.member, app(user()))))
        policy.decide(w.org, approved.id!!, ApprovalState.APPROVED, w.admin)
        for (t in listOf(approvedToken, ownToken)) {
            assertEquals(setOf("open", "secret"), names("/-_-api/v1/owners/${w.org.name}/projects", t))
            mockMvc.perform(get("/-_-api/v1/orgs/${w.org.name}").header("Authorization", bearer(t))).andExpect(status().isOk)
        }
        policy.decide(w.org, approved.id!!, ApprovalState.DENIED, w.admin)
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/secret").header("Authorization", bearer(approvedToken))).andExpect(status().isForbidden)
    }

    @Test
    fun `개인용 토큰은 정책의 영향을 받지 않는다`() {
        val w = World(); policy.setRestricted(w.org, true, w.admin)
        val pat = (apiTokens.create(w.member, "개인", setOf("repo", "read:org"), null) as TokenCreation.Created).value
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/secret").header("Authorization", bearer(pat))).andExpect(status().isOk)
    }

    @Test
    fun `거부한 요청은 조직에 속한 감사 기록으로 남는다`() {
        val w = World(); val client = app(user()); val t = token(w.member, client); policy.setRestricted(w.org, true, w.admin)
        mockMvc.perform(get("/-_-api/v1/owners/${w.org.name}/projects/secret").header("Authorization", bearer(t))).andExpect(status().isForbidden)
        val orgId = w.org.id; val orgName = w.org.name!!; val memberId = w.member.id
        // 시험 안에서 이미 감사 기록을 쌓았으면 경계 이벤트는 트랜잭션이 끝난 뒤에 쓰이므로, 시험 트랜잭션을 되돌려 끝내고 새로 연다
        TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start()
        val event = logs.findAll().single { it.action == "oauth.org_policy_denied" && it.details!!.contains("/owners/$orgName/") }
        assertEquals(orgId, event.organizationId); assertEquals(memberId, event.actorId); assertTrue(event.details!!.contains(client.clientId!!), event.details)
    }
}
