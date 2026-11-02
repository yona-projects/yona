package dev.yona.web.lfs

import dev.yona.domain.apitoken.ApiTokenService
import dev.yona.domain.apitoken.TokenCreation
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
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
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Base64

/**
 * LFS 잠금 API(X-8 단계 3, `decisions.md` D-79 2번)를 검증한다. 규격의 `POST /locks`(201, 충돌은 409와 기존 잠금), `GET /locks`(거르기와 쪽 나누기),
 * `POST /locks/verify`(ours, theirs), `POST /locks/{id}/unlock`(강제는 프로젝트 관리자만)이다. 권한은 batch와 같다: 목록은 읽기, 나머지는 쓰기 권한이 필요하다.
 */
@AutoConfigureMockMvc
@Transactional
class LfsLockApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var apiTokens: ApiTokenService

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000
    private val lfsJson = MediaType.parseMediaType("application/vnd.git-lfs+json")

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "la$k-$stamp", name = "la$k", email = "la$k-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(scope: ProjectScope = ProjectScope.PRIVATE): Project = projects.save(Project(owner = "lq$stamp", name = "p${++n}", vcs = "GIT", projectScope = scope, createdDate = java.util.Date()))

    private fun join(p: Project, u: User, role: RoleType) { projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(role.id).get())) }

    private fun basic(user: User, scopes: Set<String> = setOf("repo")): String {
        val token = (apiTokens.create(user, "lock-${++n}", scopes, null) as TokenCreation.Created).value
        return "Basic " + Base64.getEncoder().encodeToString("${user.loginId}:$token".toByteArray())
    }

    private fun base(p: Project) = "/${p.owner}/${p.name}.git/info/lfs"

    private fun lock(p: Project, auth: String?, path: String) =
        post("${base(p)}/locks").contentType(lfsJson).accept(lfsJson).content("""{"path":"$path","ref":{"name":"refs/heads/main"}}""").apply { auth?.let { header(HttpHeaders.AUTHORIZATION, it) } }

    private fun unlock(p: Project, auth: String?, id: String, force: Boolean = false) =
        post("${base(p)}/locks/$id/unlock").contentType(lfsJson).content("""{"force":$force}""").apply { auth?.let { header(HttpHeaders.AUTHORIZATION, it) } }

    private fun idOf(body: String) = Regex("\"id\":\"(\\d+)\"").find(body)!!.groupValues[1]

    @Test
    fun `잠그면 201과 잠금을 돌려주고 같은 경로를 다시 잠그면 409와 기존 잠금이다`() {
        val p = project(); val a = user(); val b = user(); join(p, a, RoleType.MEMBER); join(p, b, RoleType.MEMBER)
        mockMvc.perform(lock(p, basic(a), "assets/logo.psd")).andExpect(status().isCreated).andExpect(content().contentTypeCompatibleWith(lfsJson))
            .andExpect(jsonPath("$.lock.path").value("assets/logo.psd")).andExpect(jsonPath("$.lock.owner.name").value(a.loginId)).andExpect(jsonPath("$.lock.id").isString)
            .andExpect(jsonPath("$.lock.locked_at").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*(Z|[+-]\\d{2}:\\d{2})")))
        mockMvc.perform(lock(p, basic(b), "assets/logo.psd")).andExpect(status().isConflict).andExpect(jsonPath("$.lock.owner.name").value(a.loginId)).andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `권한이 없으면 401이나 403이고 읽기 범위 토큰으로는 잠글 수 없다`() {
        val p = project(); val member = user(); val outsider = user(); join(p, member, RoleType.MEMBER)
        mockMvc.perform(lock(p, null, "a.bin")).andExpect(status().isUnauthorized)
        mockMvc.perform(lock(p, basic(outsider), "a.bin")).andExpect(status().isForbidden)
        mockMvc.perform(lock(p, basic(member, setOf("read:repo")), "a.bin")).andExpect(status().isForbidden)
        mockMvc.perform(post("${base(p)}/locks").contentType(lfsJson).content("""{"path":"/etc/passwd"}""").header(HttpHeaders.AUTHORIZATION, basic(member))).andExpect(status().isUnprocessableContent)
        mockMvc.perform(post("${base(p)}/locks").contentType(lfsJson).content("garbage").header(HttpHeaders.AUTHORIZATION, basic(member))).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `목록은 읽기 권한으로 볼 수 있고 경로로 거르고 쪽을 나눈다`() {
        val p = project(ProjectScope.PUBLIC); val a = user(); join(p, a, RoleType.MEMBER)
        for (name in listOf("a.bin", "b.bin", "c.bin")) mockMvc.perform(lock(p, basic(a), name)).andExpect(status().isCreated)
        mockMvc.perform(get("${base(p)}/locks").param("limit", "2")).andExpect(status().isOk).andExpect(jsonPath("$.locks.length()").value(2)).andExpect(jsonPath("$.next_cursor").isNotEmpty)
        mockMvc.perform(get("${base(p)}/locks").param("path", "b.bin")).andExpect(jsonPath("$.locks.length()").value(1)).andExpect(jsonPath("$.locks[0].path").value("b.bin")).andExpect(jsonPath("$.next_cursor").doesNotExist())
        mockMvc.perform(get("${base(project())}/locks")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `verify는 내 잠금과 남의 잠금을 나눠 주고 쓰기 권한이 필요하다`() {
        val p = project(); val a = user(); val b = user(); join(p, a, RoleType.MEMBER); join(p, b, RoleType.MEMBER)
        mockMvc.perform(lock(p, basic(a), "mine.bin")); mockMvc.perform(lock(p, basic(b), "theirs.bin"))
        mockMvc.perform(post("${base(p)}/locks/verify").contentType(lfsJson).content("""{"ref":{"name":"refs/heads/main"}}""").header(HttpHeaders.AUTHORIZATION, basic(a)))
            .andExpect(status().isOk).andExpect(jsonPath("$.ours[0].path").value("mine.bin")).andExpect(jsonPath("$.theirs[0].path").value("theirs.bin"))
        mockMvc.perform(post("${base(p)}/locks/verify").contentType(lfsJson).content("{}").header(HttpHeaders.AUTHORIZATION, basic(user()))).andExpect(status().isForbidden)
    }

    @Test
    fun `푸는 것은 잠근 사람이고 강제 풀기는 프로젝트 관리자만 한다`() {
        val p = project(); val owner = user(); val member = user(); val manager = user(); join(p, owner, RoleType.MEMBER); join(p, member, RoleType.MEMBER); join(p, manager, RoleType.MANAGER)
        val id = idOf(mockMvc.perform(lock(p, basic(owner), "x.bin")).andReturn().response.contentAsString)
        mockMvc.perform(unlock(p, basic(member), id)).andExpect(status().isForbidden)
        mockMvc.perform(unlock(p, basic(member), id, force = true)).andExpect(status().isForbidden)
        mockMvc.perform(unlock(p, basic(manager), id, force = true)).andExpect(status().isOk).andExpect(jsonPath("$.lock.path").value("x.bin"))
        mockMvc.perform(unlock(p, basic(owner), id)).andExpect(status().isNotFound)
        val id2 = idOf(mockMvc.perform(lock(p, basic(owner), "y.bin")).andReturn().response.contentAsString)
        mockMvc.perform(unlock(p, basic(owner), id2)).andExpect(status().isOk)
    }
}
