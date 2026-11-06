package dev.yona.web.hg

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectDeletionService
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.hg.HgRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.HgTestSupport
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 실제 Mercurial 클라이언트(`hg`)로 HTTP 서빙(`?cmd=` 와이어 프로토콜)을 검증한다: 복제, 가져오기, 푸시와 접근 권한(X-7 6단계).
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-http"])
class HgHttpTest {

    companion object {
        private val counter = AtomicInteger()

        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            TestDatabase.current.register(registry)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.datasource.hikari.maximum-pool-size") { "3" }
            registry.add("spring.datasource.hikari.minimum-idle") { "0" }
        }
    }

    @LocalServerPort private var port: Int = 0
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var apiTokenService: dev.yona.domain.apitoken.ApiTokenService
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()
    private val tokens = mutableMapOf<Long, String>()

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun tokenOf(u: User): String = tokens.getOrPut(requireNotNull(u.id)) { (apiTokenService.create(u, "hg", setOf("repo"), null) as dev.yona.domain.apitoken.TokenCreation.Created).value }

    private fun project(scope: ProjectScope, vcs: String = "Mercurial"): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "hg$n", vcs = vcs, projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun serverDir(p: Project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        member = user("hm"); outsider = user("ho")
        project = project(ProjectScope.PUBLIC)
        HgTestSupport.commit(serverDir(project), "서버의 첫 커밋", mapOf("a.txt" to "hello\n"))
    }

    @AfterEach
    fun tearDown() {
        // 토큰은 사용자를 참조하므로 먼저 지운다
        apiTokenRepository.deleteAll(); tokens.clear(); tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun url(p: Project = project, user: User? = null) = "http://" + (user?.let { "${it.loginId}:${tokenOf(it)}@" } ?: "") + "localhost:$port/${p.owner}/${p.name}"

    /** `hg`를 [dir]에서 실행하고 종료 코드와 출력을 돌려준다. 묻는 일은 모두 꺼서 인증이 필요하면 실패한다. */
    private fun hg(dir: File, vararg args: String): Pair<Int, String> {
        val builder = ProcessBuilder(listOf("hg", "--config", "ui.interactive=off") + args.toList()).directory(dir).redirectErrorStream(true)
        builder.environment()["HGPLAIN"] = "1"; builder.environment()["HGUSER"] = "Tester <tester@example.com>"
        builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        val process = builder.start()
        val out = String(process.inputStream.readBytes())
        return process.waitFor() to out
    }

    private fun clone(p: Project, user: User? = null): Pair<File, Pair<Int, String>> {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        return dir to hg(work, "clone", url(p, user), dir.path)
    }

    private fun raw(path: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    @Test
    fun `공개 프로젝트는 익명으로 복제한다`() {
        val (dir, result) = clone(project)
        assertEquals(0, result.first, result.second)
        assertEquals("hello\n", File(dir, "a.txt").readText())
    }

    @Test
    fun `서버에 새 커밋이 생기면 pull로 가져온다`() {
        val (dir, _) = clone(project)
        HgTestSupport.commit(serverDir(project), "둘째 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900")
        val pulled = hg(dir, "pull", "-u")
        assertEquals(0, pulled.first, pulled.second)
        assertEquals("b\n", File(dir, "b.txt").readText())
    }

    @Test
    fun `비공개 프로젝트는 익명이 복제하지 못하고 토큰을 가진 구성원은 한다`() {
        val closed = project(ProjectScope.PRIVATE)
        HgTestSupport.commit(serverDir(closed), "비공개 커밋", mapOf("secret.txt" to "s\n"))
        val anonymous = clone(closed)
        assertFalse(anonymous.second.first == 0, "익명 복제가 성공했다: ${anonymous.second.second}")
        assertTrue(anonymous.second.second.contains("authorization", ignoreCase = true) || anonymous.second.second.contains("401"), anonymous.second.second)
        val (dir, result) = clone(closed, member)
        assertEquals(0, result.first, result.second)
        assertEquals("s\n", File(dir, "secret.txt").readText())
    }

    @Test
    fun `구성원은 푸시하고 서버 저장소에 체인지셋이 들어온다`() {
        val (dir, _) = clone(project, member)
        File(dir, "c.txt").writeText("from client\n")
        assertEquals(0, hg(dir, "add", "c.txt").first)
        assertEquals(0, hg(dir, "commit", "-m", "클라이언트 커밋", "-d", "2026-11-03 10:00 +0900").first)
        val pushed = hg(dir, "push", url(project, member))
        assertEquals(0, pushed.first, pushed.second)
        val log = HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n")
        assertTrue("클라이언트 커밋" in log, log)
        assertTrue(HgTestSupport.verifies(serverDir(project)))
    }

    @Test
    fun `익명이나 잘못된 비밀번호와 구성원이 아닌 사용자는 푸시하지 못한다`() {
        val (dir, _) = clone(project)
        File(dir, "d.txt").writeText("x\n"); hg(dir, "add", "d.txt"); hg(dir, "commit", "-m", "막힐 커밋")
        assertFalse(hg(dir, "push", url(project)).first == 0, "익명 푸시가 성공했다")
        assertFalse(hg(dir, "push", "http://${member.loginId}:wrong@localhost:$port/${project.owner}/${project.name}").first == 0, "잘못된 비밀번호 푸시가 성공했다")
        assertFalse(hg(dir, "push", url(project, outsider)).first == 0, "구성원이 아닌 사용자의 푸시가 성공했다")
        val log = HgTestSupport.hg(serverDir(project), "log", "--template", "{desc|firstline}\\n")
        assertFalse("막힐 커밋" in log, log)
    }

    @Test
    fun `Mercurial이 아닌 프로젝트와 모르는 명령은 거절한다`() {
        val git = project(ProjectScope.PUBLIC, vcs = "GIT")
        assertEquals(404, raw("/${git.owner}/${git.name}?cmd=capabilities").statusCode())
        assertEquals(400, raw("/${project.owner}/${project.name}?cmd=nosuchcommand").statusCode())
        assertEquals(404, raw("/${member.loginId}/nosuchproject?cmd=capabilities").statusCode())
    }

    @Test
    fun `기능 목록은 와이어 프로토콜로 답한다`() {
        val body = String(raw("/${project.owner}/${project.name}?cmd=capabilities").body())
        assertTrue("batch" in body && "unbundle" in body, body)
    }
}
