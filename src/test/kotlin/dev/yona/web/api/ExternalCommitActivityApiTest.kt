package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.Email
import dev.yona.domain.user.EmailRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * Magpie 변경 요청 계약의 `list_authored_commits`(로드맵 X-23)를 채우는 조회(`GET .../activity/commits`)를 검증한다. 사람의 이메일(대표 이메일과 인증한 추가 이메일)로 기본 브랜치의
 * 커밋을 최근 순으로 찾는다. 이메일은 찾는 열쇠로만 쓰이고 응답에는 커밋의 작성자 이메일이 담긴다(어댑터가 초안에 쓰지 않는다).
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-commit-activity"])
class ExternalCommitActivityApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var emails: EmailRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter

    private var sequence = (System.nanoTime() % 1_000_000_000L).toInt()

    private fun user(email: String? = null): User {
        val n = ++sequence; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "eca$n", name = "이름eca$n", email = email ?: "eca$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun token(user: User, vararg scopes: String): String = (tokens.create(user, "t", (scopes.toList().ifEmpty { listOf("repo") }).toSet(), null) as TokenCreation.Created).value
    private fun call(builder: MockHttpServletRequestBuilder, token: String?) = mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.also { failures.clear() })

    private class Fixture(val project: Project, val manager: User) { val api get() = "/-_-api/v1/owners/${project.owner}/projects/${project.name}" }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC, vararg commits: TestCommit): Fixture {
        val n = ++sequence
        val project = projects.save(Project(owner = "ecao$n", name = "ecap$n", vcs = "GIT", projectScope = scope, createdDate = Date(1_700_000_000_000L)))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        repositories.createRepository(project)
        if (commits.isNotEmpty()) GitTestSupport.pushCommits(repositories, project, *commits)
        val manager = user().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MANAGER.id).get())) }
        return Fixture(project, manager)
    }

    @Test
    fun `사람의 이메일로 쓴 커밋을 최근 순으로 돌려주고 다른 사람의 커밋은 뺀다`() {
        val person = user("kim@corp.example")
        val f = project(
            ProjectScope.PUBLIC,
            TestCommit("첫 커밋", mapOf("a.txt" to "1".toByteArray()), authorName = "김", authorEmail = "kim@corp.example"),
            TestCommit("남의 커밋", mapOf("b.txt" to "2".toByteArray()), authorName = "이", authorEmail = "lee@corp.example"),
            TestCommit("둘째 커밋\n\nSigned-off-by: 김 <kim@corp.example>", mapOf("c.txt" to "3".toByteArray()), authorName = "김", authorEmail = "KIM@corp.example")
        )
        call(get("${f.api}/activity/commits").param("author", person.loginId!!), token(user())).andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].message").value(org.hamcrest.Matchers.startsWith("둘째 커밋")))
            .andExpect(jsonPath("$.items[0].sha").value(org.hamcrest.Matchers.matchesPattern("[0-9a-f]{40}"))).andExpect(jsonPath("$.items[0].authorName").value("김"))
            .andExpect(jsonPath("$.items[0].authorEmail").value("KIM@corp.example")).andExpect(jsonPath("$.items[0].committedAt").exists()).andExpect(jsonPath("$.items[1].message").value("첫 커밋"))
            .andExpect(jsonPath("$.capped").value(false))
    }

    @Test
    fun `인증한 추가 이메일도 찾는 열쇠이고 인증하지 않은 이메일은 쓰지 않는다`() {
        val person = user("main@corp.example")
        emails.save(Email(user = person, email = "extra@corp.example", valid = true)); emails.save(Email(user = person, email = "pending@corp.example", valid = false))
        val f = project(
            ProjectScope.PUBLIC,
            TestCommit("추가 이메일", mapOf("a.txt" to "1".toByteArray()), authorEmail = "extra@corp.example"),
            TestCommit("인증 전 이메일", mapOf("b.txt" to "2".toByteArray()), authorEmail = "pending@corp.example")
        )
        call(get("${f.api}/activity/commits").param("author", person.loginId!!), token(user())).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].message").value("추가 이메일"))
    }

    @Test
    fun `limit은 1에서 100이고 기본은 30이다`() {
        val person = user("many@corp.example")
        val commits = (1..5).map { TestCommit("커밋 $it", mapOf("f$it.txt" to "$it".toByteArray()), authorEmail = "many@corp.example") }.toTypedArray()
        val f = project(ProjectScope.PUBLIC, *commits)
        val t = token(user())
        call(get("${f.api}/activity/commits").param("author", person.loginId!!).param("limit", "2"), t).andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].message").value("커밋 5"))
        call(get("${f.api}/activity/commits").param("author", person.loginId!!), t).andExpect(jsonPath("$.items.length()").value(5))
        call(get("${f.api}/activity/commits").param("author", person.loginId!!).param("limit", "1000"), t).andExpect(status().isOk)
        call(get("${f.api}/activity/commits").param("author", person.loginId!!).param("limit", "0"), t).andExpect(status().isBadRequest)
    }

    @Test
    fun `author가 없거나 없는 사용자이면 400이고 비공개 프로젝트는 구성원만 읽는다`() {
        val f = project(ProjectScope.PRIVATE, TestCommit("커밋", mapOf("a.txt" to "1".toByteArray())))
        val outsider = user()
        call(get("${f.api}/activity/commits"), token(f.manager)).andExpect(status().isBadRequest)
        call(get("${f.api}/activity/commits").param("author", "nobody-here"), token(f.manager)).andExpect(status().isBadRequest)
        call(get("${f.api}/activity/commits").param("author", f.manager.loginId!!), token(outsider)).andExpect(status().isForbidden)
        call(get("${f.api}/activity/commits").param("author", f.manager.loginId!!), token(f.manager, "read:repo")).andExpect(status().isOk)
        call(get("${f.api}/activity/commits").param("author", f.manager.loginId!!), null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `커밋이 없는 저장소는 빈 목록이다`() {
        val f = project(ProjectScope.PUBLIC)
        call(get("${f.api}/activity/commits").param("author", f.manager.loginId!!), token(f.manager)).andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(0))
    }
}
