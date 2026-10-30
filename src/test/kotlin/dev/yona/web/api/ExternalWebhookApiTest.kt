package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.project.Webhook
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.webhook.WebhookRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 웹훅 REST(로드맵 X-30b, `decisions.md` D-49)를 검증한다. 웹 화면(`/{o}/{p}/webhooks`)에 있는 목록, 만들기, 삭제만 있고 수정은 v1.6에 없다.
 * 범위는 읽기 `read:repo_hook`, 만들기 `write:repo_hook`, 삭제 `admin:repo_hook`이고 `repo`가 셋을 포함한다. 판정은 웹 화면과 같아 프로젝트 관리자만 쓴다.
 * 비밀 값(`secret`)은 돌려주지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalWebhookApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var webhooks: WebhookRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(token: String? = null, scopes: Collection<String>? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "wh$n", name = "wh$n", email = "wh$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let {
            tokens[saved.id!!] = "wt$n$it"
            if (scopes == null) dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!))
            else dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!), scopes)
        }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "who$n", name = "whp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun joined(project: Project, role: RoleType, scopes: Collection<String>? = null): String {
        val user = newUser("a", scopes); projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return tokenOf(user)
    }

    private fun hook(project: Project, url: String = "https://example.com/hook", secret: String = "s3cret", type: WebhookType = WebhookType.SIMPLE, gitPush: Boolean = false): Webhook =
        webhooks.save(Webhook(project = project, payloadUrl = url, secret = secret, gitPush = gitPush, webhookType = type, createdAt = Date()))

    private fun api(project: Project, path: String = "hooks") = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 목록 ---

    @Test
    fun `웹훅 목록은 프로젝트 관리자만 보고 비밀 값은 돌려주지 않는다`() {
        val project = newProject(); val manager = joined(project, RoleType.MANAGER); val member = joined(project, RoleType.MEMBER); val other = newProject(); hook(other, "https://other.example.com")
        val a = hook(project, "https://a.example.com", "abc", WebhookType.DETAIL_SLACK, true); hook(project, "https://b.example.com", "")
        json(get(api(project)), manager, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[0].id").value(a.id)).andExpect(jsonPath("$.result[0].payloadUrl").value("https://a.example.com")).andExpect(jsonPath("$.result[0].webhookType").value("DETAIL_SLACK"))
            .andExpect(jsonPath("$.result[0].gitPush").value(true)).andExpect(jsonPath("$.result[0].hasSecret").value(true)).andExpect(jsonPath("$.result[0].secret").doesNotExist()).andExpect(jsonPath("$.result[1].hasSecret").value(false))
        json(get(api(project)), member, null).andExpect(status().isForbidden)
        json(get(api(project)), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(get(api(project)), null, null).andExpect(status().isUnauthorized)
        json(get(api(newProject(), "hooks")), manager, null).andExpect(status().isForbidden)
    }

    // --- 만들기 ---

    @Test
    fun `웹훅을 만들면 기본값을 채우고 JSON 형식은 푸시 이벤트를 켠다`() {
        val project = newProject(); val manager = joined(project, RoleType.MANAGER)
        json(post(api(project)), manager, """{"payloadUrl":"https://example.com/a"}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.webhookType").value("SIMPLE")).andExpect(jsonPath("$.result.gitPush").value(false)).andExpect(jsonPath("$.result.hasSecret").value(false)).andExpect(jsonPath("$.result.secret").doesNotExist())
        json(post(api(project)), manager, """{"payloadUrl":"https://example.com/b","secret":"k","webhookType":"DETAIL_HANGOUT_CHAT","gitPush":true}""").andExpect(status().isCreated)
            .andExpect(jsonPath("$.result.webhookType").value("DETAIL_HANGOUT_CHAT")).andExpect(jsonPath("$.result.gitPush").value(true)).andExpect(jsonPath("$.result.hasSecret").value(true))
        json(post(api(project)), manager, """{"payloadUrl":"https://example.com/c","webhookType":"JSON"}""").andExpect(status().isCreated).andExpect(jsonPath("$.result.gitPush").value(true))
        json(post(api(project)), manager, """{"payloadUrl":"https://example.com/d","webhookType":"JSON","gitPush":false}""").andExpect(jsonPath("$.result.gitPush").value(false))
        entityManager.flush(); entityManager.clear()
        val saved = webhooks.findByProjectId(project.id!!)
        assertEquals(4, saved.size)
        assertEquals("k", saved.single { it.payloadUrl == "https://example.com/b" }.secret)
        assertEquals("", saved.single { it.payloadUrl == "https://example.com/a" }.secret)
    }

    @Test
    fun `웹훅 만들기의 오류와 권한`() {
        val project = newProject(); val manager = joined(project, RoleType.MANAGER); val member = joined(project, RoleType.MEMBER)
        json(post(api(project)), manager, """{}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, """{"payloadUrl":""}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, """{"payloadUrl":"https://e.com/${"x".repeat(2000)}"}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, """{"payloadUrl":"https://e.com","secret":"${"s".repeat(251)}"}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, """{"payloadUrl":"https://e.com","webhookType":"FAX"}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, """{"payloadUrl":"https://e.com","gitPush":"yes"}""").andExpect(status().isBadRequest)
        json(post(api(project)), manager, null).andExpect(status().isBadRequest)
        json(post(api(project)), member, """{"payloadUrl":"https://e.com"}""").andExpect(status().isForbidden)
        json(post(api(project)), tokenOf(newUser("x")), """{"payloadUrl":"https://e.com"}""").andExpect(status().isForbidden)
        json(post(api(project)), null, """{"payloadUrl":"https://e.com"}""").andExpect(status().isUnauthorized)
        assertEquals(0, webhooks.findByProjectId(project.id!!).size)
    }

    // --- 지우기 ---

    @Test
    fun `웹훅 지우기는 관리자만 하고 다른 프로젝트의 웹훅은 찾지 못한다`() {
        val project = newProject(); val other = newProject(); val manager = joined(project, RoleType.MANAGER); val member = joined(project, RoleType.MEMBER)
        val mine = hook(project); val foreign = hook(other)
        json(delete(api(project, "hooks/${mine.id}")), member, null).andExpect(status().isForbidden)
        json(delete(api(project, "hooks/${foreign.id}")), manager, null).andExpect(status().isNotFound)
        json(delete(api(project, "hooks/${mine.id}")), manager, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(webhooks.findById(mine.id!!).orElse(null)); assertEquals(1, webhooks.findByProjectId(other.id!!).size)
        json(delete(api(project, "hooks/${mine.id}")), manager, null).andExpect(status().isNotFound)
        json(delete(api(project, "hooks/${foreign.id}")), null, null).andExpect(status().isUnauthorized)
    }

    // --- 범위 ---

    @Test
    fun `토큰의 범위에 따라 읽기와 만들기와 삭제를 나눈다`() {
        val project = newProject(); val h = hook(project)
        val read = joined(project, RoleType.MANAGER, listOf("read:repo_hook")); val write = joined(project, RoleType.MANAGER, listOf("write:repo_hook"))
        val admin = joined(project, RoleType.MANAGER, listOf("admin:repo_hook")); val repo = joined(project, RoleType.MANAGER, listOf("repo")); val publicRepo = joined(project, RoleType.MANAGER, listOf("public_repo"))
        json(get(api(project)), read, null).andExpect(status().isOk)
        json(post(api(project)), read, """{"payloadUrl":"https://e.com/r"}""").andExpect(status().isForbidden)
        json(delete(api(project, "hooks/${h.id}")), read, null).andExpect(status().isForbidden)
        json(get(api(project)), write, null).andExpect(status().isOk)
        json(post(api(project)), write, """{"payloadUrl":"https://e.com/w"}""").andExpect(status().isCreated)
        json(delete(api(project, "hooks/${h.id}")), write, null).andExpect(status().isForbidden)
        json(delete(api(project, "hooks/${h.id}")), admin, null).andExpect(status().isNoContent)
        json(post(api(project)), admin, """{"payloadUrl":"https://e.com/ad"}""").andExpect(status().isCreated)
        json(get(api(project)), repo, null).andExpect(status().isOk)
        json(post(api(project)), repo, """{"payloadUrl":"https://e.com/repo"}""").andExpect(status().isCreated)
        json(get(api(project)), publicRepo, null).andExpect(status().isForbidden)
    }
}
