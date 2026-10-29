package dev.yona.web.api

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
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
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 외부 프로젝트 API의 라벨, 마일스톤, 내보내기, 구독자 엔드포인트가 토큰 인증과 범위와 프로젝트 권한을 올바르게 확인하는지 하나씩 점검한다
 * (X-5, `decisions.md` D-36). 각 엔드포인트를 같은 조건표로 호출해 기대 응답을 맞춰 본다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-external-project-auth"])
class ExternalProjectApiAuthTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var tokens: ApiTokenService
    @Autowired private lateinit var failures: dev.yona.security.TokenFailureLimiter

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE): User {
        val n = ++sequence; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "pa$n", name = "pa$n", email = "pa$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(scope: ProjectScope): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "pao$n", name = "pap$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 1, lastPostingNumber = 0, overview = "개요", createdDate = Date()))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun join(p: Project, u: User, role: RoleType) { projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(role.id).get())) }

    private fun token(u: User, vararg scopes: String, expires: Date? = null) = (tokens.create(u, "t", scopes.toSet(), expires) as TokenCreation.Created).value

    /** 점검 대상 엔드포인트 하나이다. [allowedRole]은 `repo` 범위 토큰이 통과하려면 필요한 프로젝트 역할이다. */
    private class Endpoint(val name: String, val request: (Project) -> MockHttpServletRequestBuilder, val minimumRole: RoleType, val readOnlyPublic: Boolean)

    private val endpoints = listOf(
        Endpoint("라벨 만들기", { p -> post("/-_-api/v1/owners/${p.owner}/projects/${p.name}/labels").content("""{"labels":[{"labelName":"a","labelColor":"#fff","category":"c"}]}""") }, RoleType.MEMBER, false),
        Endpoint("마일스톤 만들기", { p -> post("/-_-api/v1/owners/${p.owner}/projects/${p.name}/milestones").content("""{"milestones":[{"title":"m"}]}""") }, RoleType.MEMBER, false),
        Endpoint("내보내기", { p -> get("/-_-api/v1/owners/${p.owner}/projects/${p.name}/exports") }, RoleType.MANAGER, false),
        Endpoint("구독자", { p -> get("/-_-api/v1/owners/${p.owner}/projects/${p.name}/posts/1/watchers").param("type", "issues") }, RoleType.MEMBER, true)
    )

    /** 점검 호출이 같은 주소에서 실패를 쌓아 차단(429)에 걸리지 않게 호출마다 실패 기록을 비운다. 차단 자체는 `ApiNamespaceAuthTest`가 검증한다. */
    private fun call(e: Endpoint, p: Project, authorization: String?, session: org.springframework.mock.web.MockHttpSession? = null): Int =
        failures.clear().let { mockMvc.perform(e.request(p).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { authorization?.let { header("Authorization", "token $it") }; session?.let { session(it) } })
            .andReturn().response.status }

    private fun seed(p: Project, owner: User) {
        issues.save(Issue(project = p, number = 1, title = "t", body = "b", state = dev.yona.domain.enumeration.State.OPEN, authorId = owner.id, authorLoginId = owner.loginId, authorName = owner.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    @Test
    fun `토큰이 없거나 틀리거나 세션뿐이면 401이다`() {
        val owner = user(); val p = project(ProjectScope.PUBLIC); join(p, owner, RoleType.MANAGER); seed(p, owner)
        val session = org.springframework.mock.web.MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", owner.loginId!!).param("password", "1234")) }
        endpoints.forEach { e ->
            assertEquals(401, call(e, p, null), "${e.name}: 토큰 없음")
            assertEquals(401, call(e, p, "yona_pat_" + "x".repeat(40)), "${e.name}: 틀린 토큰")
            assertEquals(401, call(e, p, null, session), "${e.name}: 세션 로그인만")
        }
    }

    @Test
    fun `프로젝트 역할이 충분한 repo 토큰은 통과한다`() {
        val owner = user(); val p = project(ProjectScope.PRIVATE); join(p, owner, RoleType.MANAGER); seed(p, owner)
        endpoints.forEach { e ->
            val status = call(e, p, token(owner, "repo"))
            assertEquals(true, status in 200..201, "${e.name}: 관리자 + repo => $status")
        }
    }

    @Test
    fun `프로젝트 역할이 모자라면 403이다`() {
        val owner = user(); val member = user(); val outsider = user(); val p = project(ProjectScope.PRIVATE); join(p, owner, RoleType.MANAGER); join(p, member, RoleType.MEMBER); seed(p, owner)
        endpoints.forEach { e ->
            assertEquals(403, call(e, p, token(outsider, "repo")), "${e.name}: 비공개 + 비구성원")
            if (e.minimumRole == RoleType.MANAGER) assertEquals(403, call(e, p, token(member, "repo")), "${e.name}: 구성원은 관리자 권한이 필요한 일을 못 한다")
        }
    }

    @Test
    fun `공개 프로젝트의 비구성원은 읽기만 되고 쓰기는 403이다`() {
        val owner = user(); val outsider = user(); val p = project(ProjectScope.PUBLIC); join(p, owner, RoleType.MANAGER); seed(p, owner)
        endpoints.forEach { e ->
            val status = call(e, p, token(outsider, "repo"))
            if (e.readOnlyPublic) assertEquals(200, status, "${e.name}: 공개 읽기") else assertEquals(403, status, "${e.name}: 공개 프로젝트라도 쓰기와 내보내기는 구성원만 => $status")
        }
    }

    @Test
    fun `범위가 모자라거나 public repo 토큰이 비공개 프로젝트에 쓰이면 403이다`() {
        val owner = user(); val priv = project(ProjectScope.PRIVATE); val pub = project(ProjectScope.PUBLIC)
        join(priv, owner, RoleType.MANAGER); join(pub, owner, RoleType.MANAGER); seed(priv, owner); seed(pub, owner)
        endpoints.forEach { e ->
            assertEquals(403, call(e, priv, token(owner, "user")), "${e.name}: 저장소 범위가 없다")
            assertEquals(403, call(e, priv, token(owner, "public_repo")), "${e.name}: public_repo는 비공개 프로젝트에 못 쓴다")
            val status = call(e, pub, token(owner, "public_repo"))
            assertEquals(true, status in 200..201, "${e.name}: public_repo는 공개 프로젝트에 쓸 수 있다 => $status")
        }
    }

    @Test
    fun `만료되었거나 잠긴 사용자의 토큰은 401이다`() {
        val owner = user(); val p = project(ProjectScope.PUBLIC); join(p, owner, RoleType.MANAGER); seed(p, owner)
        val locked = user(); join(p, locked, RoleType.MANAGER)
        val lockedToken = token(locked, "repo"); locked.state = UserState.LOCKED; users.save(locked)
        val expiring = token(owner, "repo", expires = Date(System.currentTimeMillis() + 60_000))
        tokens.list(owner).forEach { it.expiresDate = Date(System.currentTimeMillis() - 1000); }
        endpoints.forEach { e ->
            assertEquals(401, call(e, p, lockedToken), "${e.name}: 잠긴 사용자")
            assertEquals(401, call(e, p, expiring), "${e.name}: 만료")
        }
    }

    @Test
    fun `없는 프로젝트는 존재를 숨기려고 403이다 (v1_6 AbstractProjectCheckAction의 error_forbidden_or_notfound)`() {
        val owner = user(); val ghost = Project(owner = "nobody", name = "nothing", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
        endpoints.forEach { e -> assertEquals(403, call(e, ghost, token(owner, "repo")), "${e.name}: 없는 프로젝트") }
    }
}
