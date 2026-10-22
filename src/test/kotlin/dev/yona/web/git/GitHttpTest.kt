package dev.yona.web.git

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
import dev.yona.domain.project.PushedBranchRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.TestDatabase
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.junit.jupiter.api.AfterEach
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * 실제 Git 클라이언트(JGit)로 smart-HTTP 서비스(`info/refs`, `git-upload-pack`, `git-receive-pack`)를 검증한다.
 *
 * 원본: `app/controllers/GitApp.java`, `playRepository/RepositoryService.gitAdvertise`, `gitRpc`, `playRepository/hooks 패키지`.
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["yona.notification-mail-enabled=false"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-git-http"])
class GitHttpTest {

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
    @Autowired private lateinit var pushedBranches: PushedBranchRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var deletion: ProjectDeletionService

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun project(scope: ProjectScope, memberOnly: Boolean = false): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "git$n", vcs = "GIT", projectScope = scope, isCodeAccessibleMemberOnly = memberOnly))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    @BeforeEach
    fun setUp() {
        member = user("gm"); outsider = user("go")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun get(url: String): HttpResponse<ByteArray> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    private fun url(p: Project = project) = "http://localhost:$port/${p.owner}/${p.name}"

    private fun credentials(u: User, password: String = "1234") = UsernamePasswordCredentialsProvider(u.loginId, password)

    /** 작업 디렉터리를 새로 만들어 파일 하나를 커밋하고 [target] 브랜치로 밀어 넣는다. */
    private fun push(p: Project, user: User?, branch: String = "master", password: String = "1234", file: String = "a.txt", ref: String = "refs/heads/$branch") {
        val dir = File(work, "push-${counter.incrementAndGet()}")
        Git.init().setDirectory(dir).setInitialBranch(branch).call().use { git ->
            File(dir, file).writeText("hello")
            git.add().addFilepattern(file).call()
            git.commit().setMessage("first").setAuthor("t", "t@example.com").setCommitter("t", "t@example.com").call()
            val command = git.push().setRemote(url(p)).setRefSpecs(RefSpec("refs/heads/$branch:$ref"))
            user?.let { command.setCredentialsProvider(credentials(it, password)) }
            command.call().forEach { result ->
                result.remoteUpdates.forEach { update ->
                    check(update.status.name == "OK" || update.status.name == "UP_TO_DATE") { "푸시 실패: ${update.status} ${update.message}" }
                }
            }
        }
    }

    private fun clone(p: Project, user: User? = null): File {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        val command = Git.cloneRepository().setURI(url(p)).setDirectory(dir)
        user?.let { command.setCredentialsProvider(credentials(it)) }
        command.call().close()
        return dir
    }

    @Test
    fun `구성원이 푸시하고 공개 프로젝트는 익명으로 복제한다`() {
        push(project, member)
        val dir = clone(project)
        assertEquals("hello", File(dir, "a.txt").readText())
    }

    @Test
    fun `푸시하면 마지막 푸시 시각과 최근 푸시한 브랜치가 남는다`() {
        push(project, member, branch = "feature")
        val saved = projects.findById(project.id!!).get()
        assertNotNull(saved.lastPushedDate)
        assertEquals(listOf("refs/heads/feature"), pushedBranches.findAll().filter { it.project?.id == project.id }.map { it.name })
    }

    @Test
    fun `익명이나 잘못된 비밀번호로는 푸시할 수 없다`() {
        assertFailsWith<TransportException> { push(project, null) }
        assertFailsWith<TransportException> { push(project, member, password = "wrong") }
    }

    @Test
    fun `프로젝트 구성원이 아닌 사용자는 푸시할 수 없다`() {
        val error = assertFailsWith<TransportException> { push(project, outsider) }
        assertTrue(error.message!!.contains("not permitted") || error.message!!.contains("403"), error.message)
    }

    @Test
    fun `예약된 ref에는 푸시할 수 없다`() {
        val error = assertFailsWith<IllegalStateException> { push(project, member, ref = "refs/yobi/test") }
        assertTrue(error.message!!.contains("reserved"), error.message)
    }

    @Test
    fun `비공개 프로젝트는 익명으로 복제할 수 없고 구성원은 복제한다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, member)
        assertFailsWith<TransportException> { clone(secret) }
        assertFailsWith<TransportException> { clone(secret, outsider) }
        assertEquals("hello", File(clone(secret, member), "a.txt").readText())
    }

    @Test
    fun `코드를 구성원에게만 여는 프로젝트는 구성원이 아니면 복제할 수 없다`() {
        val closed = project(ProjectScope.PUBLIC, memberOnly = true)
        push(closed, member)
        assertFailsWith<TransportException> { clone(closed) }
        assertFailsWith<TransportException> { clone(closed, outsider) }
        assertEquals("hello", File(clone(closed, member), "a.txt").readText())
    }

    @Test
    fun `지원하지 않는 서비스와 서비스를 정하지 않은 요청은 거부한다`() {
        assertEquals(403, get("${url()}/info/refs?service=git-foo").statusCode())
        val none = get("${url()}/info/refs")
        assertEquals(403, none.statusCode())
        assertTrue(String(none.body()).contains("getanyfile"))
    }

    @Test
    fun `없는 프로젝트는 찾을 수 없다`() {
        assertEquals(404, get("http://localhost:$port/${member.loginId}/nothing/info/refs?service=git-upload-pack").statusCode())
    }

    @Test
    fun `광고 응답은 서비스별 콘텐츠 유형을 쓴다`() {
        val response = get("${url()}/info/refs?service=git-upload-pack")
        assertEquals(200, response.statusCode())
        assertEquals("application/x-git-upload-pack-advertisement", response.headers().firstValue("Content-Type").get())
        assertTrue(String(response.body()).startsWith("001e# service=git-upload-pack\n"))
    }
}
