package dev.yona.mcp

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthAuthorization
import dev.yona.domain.oauth2server.OAuthAuthorizationRepository
import dev.yona.domain.oauth2server.OAuthClientService
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Date
import java.util.UUID

/**
 * MCP 도구 시험이 함께 쓰는 도우미이다(X-6). 사용자, 프로젝트, OAuth 토큰을 만들고 `/mcp`에 도구 호출을 보낸다.
 */
@Component
class McpToolsSupport(
    private val users: UserRepository, private val projects: ProjectRepository, private val projectUsers: ProjectUserRepository, private val menuSettings: ProjectMenuSettingRepository,
    private val roles: RoleRepository, private val issues: IssueRepository, private val clients: OAuthClientService, private val authorizations: OAuthAuthorizationRepository
) {
    private val mapper = JsonMapper.builder().build()
    private var n = 0

    class Fixture(val project: Project, val manager: User, val member: User)

    fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "mt$k-${System.nanoTime() % 100000}", name = "mt$k", email = "mt$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    fun project(scope: ProjectScope = ProjectScope.PUBLIC): Fixture {
        val k = ++n
        val project = projects.save(Project(owner = "mto$k", name = "mtp$k", vcs = "GIT", projectScope = scope, overview = "개요 $k", createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = true))
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        val member = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        return Fixture(project, manager, member)
    }

    fun issue(f: Fixture, title: String, author: User): Issue {
        val number = (issues.findAll().filter { it.project?.id == f.project.id }.maxOfOrNull { it.number ?: 0 } ?: 0) + 1
        return issues.save(
            Issue(
                project = f.project, number = number, title = title, body = "$title 본문", state = dev.yona.domain.enumeration.State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
    }

    /** 사용자 [user]의 [scopes] 범위를 가진 OAuth 액세스 토큰 값을 돌려준다. */
    fun token(user: User, scopes: String): String {
        val client = (clients.create(user, "MCP 앱 ${++n}", "", "", "https://app.example.com/cb", false, setOf("read:user", "read:repo", "repo")) as ClientCreation.Created).client
        val value = "yona_oat_" + UUID.randomUUID().toString().replace("-", "")
        authorizations.save(
            OAuthAuthorization(
                id = UUID.randomUUID().toString(), clientRowId = client.id, userId = user.id, grantType = "authorization_code", authorizedScopes = scopes, attributes = "{}",
                accessTokenHash = TokenHasher.sha256Hex(value), accessTokenIssuedDate = Date(), accessTokenExpiresDate = Date.from(Instant.now().plusSeconds(3600)), accessTokenScopes = scopes,
                accessTokenMetadata = "{\"metadata.token.invalidated\":false}", createdDate = Date()
            )
        )
        return value
    }

    /** 도구 호출 결과이다. [error]가 참이면 도구가 오류를 알렸다. [text]는 결과의 첫 글 조각이고 [json]은 그것을 JSON으로 읽은 것이다(JSON이 아니면 `null`). */
    class ToolResult(val error: Boolean, val text: String) {
        val json: JsonNode? by lazy { runCatching { JsonMapper.builder().build().readTree(text) }.getOrNull() }
    }

    fun call(mockMvc: MockMvc, token: String, tool: String, arguments: Map<String, Any?> = emptyMap()): ToolResult {
        val body = mapper.writeValueAsString(mapOf("jsonrpc" to "2.0", "id" to 1, "method" to "tools/call", "params" to mapOf("name" to tool, "arguments" to arguments)))
        val response = mockMvc.perform(
            post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer $token").content(body)
        ).andReturn().response
        val root = mapper.readTree(response.contentAsString)
        val error = root.get("error")
        if (error != null) return ToolResult(true, error.get("message")?.asString().orEmpty())
        val result = root.get("result")
        return ToolResult(result.get("isError")?.asBoolean(false) == true, result.get("content")?.get(0)?.get("text")?.asString().orEmpty())
    }

    /** 등록된 도구 이름 집합이다. */
    fun toolNames(mockMvc: MockMvc, token: String): Set<String> {
        val response = mockMvc.perform(
            post("/mcp").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).header("Authorization", "Bearer $token")
                .content("""{"jsonrpc":"2.0","id":2,"method":"tools/list"}""")
        ).andReturn().response
        return mapper.readTree(response.contentAsString).get("result").get("tools").toList().map { node -> node.get("name").asString() }.toSet()
    }
}
