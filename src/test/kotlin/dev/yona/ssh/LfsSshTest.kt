package dev.yona.ssh

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.lfs.LfsTokens
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SSH의 `git-lfs-authenticate`(X-8 단계 4, `decisions.md` D-79 8번)를 검증한다. 명령을 읽고, 서버가 HTTP 주소와 짧은 수명의 서명 토큰을 알려 주며,
 * 그 토큰이 LFS 경로에서만 그 프로젝트와 그 동작(내려받기나 올리기)에 한해 통한다. 객체 전송은 HTTP로 한다.
 */
@AutoConfigureMockMvc
@Transactional
class LfsSshTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var handler: LfsSshHandler
    @Autowired private lateinit var tokens: LfsTokens
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: dev.yona.domain.project.ProjectUserRepository
    @Autowired private lateinit var roles: dev.yona.domain.user.RoleRepository

    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000
    private val lfsJson = MediaType.parseMediaType("application/vnd.git-lfs+json")

    private fun user() = users.save(User(loginId = "ls${++n}-$stamp", name = "ls$n", email = "ls$n-$stamp@example.com", state = UserState.ACTIVE))
    private fun project(scope: ProjectScope = ProjectScope.PRIVATE) = projects.save(Project(owner = "lssh$stamp", name = "p${++n}", vcs = "GIT", projectScope = scope, createdDate = java.util.Date()))

    @Test
    fun `명령을 읽는다 - 경로 모양과 동작`() {
        val download = SshRepositoryRequest("GIT", "owner", "repo", write = false, service = "lfs_authenticate")
        val upload = SshRepositoryRequest("GIT", "owner", "repo", write = true, service = "lfs_authenticate")
        for (path in listOf("'owner/repo.git'", "'/owner/repo'", "'/owner/repo.git/'", "/owner/repo.git", "\"/owner/repo.git\"")) {
            assertEquals(download, handler.parse("git-lfs-authenticate $path download"), path); assertEquals(upload, handler.parse("git-lfs-authenticate $path upload"), path)
        }
    }

    @Test
    fun `지원하지 않는 명령과 위험한 경로는 null이다`() {
        for (bad in listOf("", "git-lfs-authenticate", "git-lfs-authenticate '/owner/repo.git'", "git-lfs-authenticate '/owner/repo.git' delete", "git-lfs-authenticate '/owner/repo.git' download extra",
            "git-lfs-authenticate '/owner' download", "git-lfs-authenticate '/a/b/c.git' upload", "git-lfs-authenticate '/../repo.git' download", "git-lfs-authenticate '/owner/re po.git' download",
            "git-lfs-authenticate '/owner/repo;ls.git' download", "git-lfs-transfer '/owner/repo.git' download", "git-upload-pack '/owner/repo.git'")) assertNull(handler.parse(bad), bad)
    }

    @Test
    fun `토큰은 사용자와 프로젝트와 동작과 만료에 묶인다`() {
        val u = user(); val p = project(); val now = Instant.parse("2026-11-02T10:00:00Z")
        val token = tokens.issue(u, p, write = false, now = now)
        assertTrue(token.startsWith("yona_lfs_"))
        val claims = assertNotNull(tokens.verify(token, now = now.plusSeconds(60)))
        assertEquals(u.id, claims.userId); assertEquals(p.id, claims.projectId); assertEquals(false, claims.write)
        assertNull(tokens.verify(token, now = now.plusSeconds(LfsTokens.TTL_SECONDS + 1)), "만료")
        assertNull(tokens.verify(token.dropLast(2) + "xx", now = now), "서명 변조")
        assertNull(tokens.verify("yona_lfs_" + "A".repeat(20), now = now)); assertNull(tokens.verify("garbage", now = now))
        assertEquals(true, tokens.verify(tokens.issue(u, p, write = true, now = now), now = now)!!.write)
    }

    private fun authenticate(grant: SshAuthorization.Allowed, command: String): Pair<Int, String> {
        val request = handler.parse(command)!!; val output = ByteArrayOutputStream()
        val exit = handler.execute(grant, request, command, SshStreams(ByteArrayInputStream(ByteArray(0)), output, ByteArrayOutputStream()))
        return exit to output.toString(Charsets.UTF_8)
    }

    @Test
    fun `서버는 HTTP 주소와 인증 헤더와 만료를 JSON으로 알려 준다`() {
        val u = user(); val p = project()
        val (exit, out) = authenticate(SshAuthorization.Allowed(p, u), "git-lfs-authenticate '/${p.owner}/${p.name}.git' download")
        assertEquals(0, exit)
        val json = JsonMapper.builder().build().readTree(out)
        assertTrue(json.get("href").asString().endsWith("/${p.owner}/${p.name}.git/info/lfs"), out)
        assertTrue(json.get("header").get("Authorization").asString().startsWith("Bearer yona_lfs_")); assertEquals(LfsTokens.TTL_SECONDS, json.get("expires_in").asLong())
    }

    private fun bearerOf(grant: SshAuthorization.Allowed, command: String) = JsonMapper.builder().build().readTree(authenticate(grant, command).second).get("header").get("Authorization").asString()

    private fun batch(p: Project, operation: String, auth: String, project: Project = p) =
        post("/${project.owner}/${project.name}.git/info/lfs/objects/batch").contentType(lfsJson).accept(lfsJson).header(HttpHeaders.AUTHORIZATION, auth)
            .content("""{"operation":"$operation","objects":[{"oid":"${"a".repeat(64)}","size":1}]}""")

    @Test
    fun `내려받기 토큰으로 내려받기 batch가 통하고 올리기는 403이다`() {
        val u = user(); val p = project(ProjectScope.PUBLIC); val auth = bearerOf(SshAuthorization.Allowed(p, u), "git-lfs-authenticate '/${p.owner}/${p.name}.git' download")
        mockMvc.perform(batch(p, "download", auth)).andExpect(status().isOk)
        mockMvc.perform(batch(p, "upload", auth)).andExpect(status().isForbidden)
    }

    @Test
    fun `올리기 토큰은 두 동작 모두 통하지만 다른 프로젝트에는 통하지 않는다`() {
        val u = user(); val p = project(ProjectScope.PUBLIC); val other = project(ProjectScope.PUBLIC)
        projectUsers.save(dev.yona.domain.project.ProjectUser(user = u, project = p, role = roles.findById(dev.yona.domain.enumeration.RoleType.MEMBER.id).get())); val auth = bearerOf(SshAuthorization.Allowed(p, u), "git-lfs-authenticate '/${p.owner}/${p.name}.git' upload")
        mockMvc.perform(batch(p, "upload", auth)).andExpect(status().isOk); mockMvc.perform(batch(p, "download", auth)).andExpect(status().isOk)
        mockMvc.perform(batch(other, "download", auth, other)).andExpect(status().isForbidden)
    }

    @Test
    fun `만료되었거나 위조한 토큰은 401이고 프로젝트 권한이 없어진 사용자는 쓸 수 없다`() {
        val u = user(); val p = project()
        val expired = "Bearer " + tokens.issue(u, p, write = false, now = Instant.now().minusSeconds(LfsTokens.TTL_SECONDS + 60))
        mockMvc.perform(batch(p, "download", expired)).andExpect(status().isUnauthorized)
        mockMvc.perform(batch(p, "download", "Bearer yona_lfs_forged.sig")).andExpect(status().isUnauthorized)
        // 비공개 프로젝트의 구성원이 아닌 사용자의 토큰은 토큰이 유효해도 프로젝트 권한 검사에서 막힌다
        mockMvc.perform(batch(p, "download", "Bearer " + tokens.issue(u, p, write = false))).andExpect(status().isForbidden)
    }
}
