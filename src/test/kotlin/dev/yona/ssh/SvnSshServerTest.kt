package dev.yona.ssh

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
import dev.yona.domain.sshkey.DeployKeyRepository
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyRepository
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.repository.svn.SvnRepositories
import dev.yona.security.LegacyPasswordHasher
import dev.yona.ssh.svn.RaSvnTestServer
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 내장 SSH 서버의 Subversion 접근(`svn+ssh://`)을 실제 `ssh`와 `svn` 클라이언트로 검증한다. 사용자별 권한은 Git SSH와 같은 규칙([SshAccess])이다.
 * 클라이언트는 `svnserve -t`를 실행하고 저장소는 그 뒤 프로토콜의 첫 메시지로 알리므로, 서버가 그 메시지를 읽은 뒤에 인가한다.
 */
@SpringBootTest(properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.ssh.enabled=true", "yona.ssh.port=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-svn-ssh-server"])
class SvnSshServerTest {

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

    @Autowired private lateinit var server: SshServerLifecycle
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var svnRepositories: SvnRepositories
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var sshKeyRepository: SshKeyRepository
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var deployKeyRepository: DeployKeyRepository
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project

    private class Key(val file: File)

    private fun newKey(): Key {
        val file = File(work, "key-${counter.incrementAndGet()}")
        check(ProcessBuilder("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "test", "-f", file.path).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0)
        return Key(file)
    }

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    /** Subversion 프로젝트를 만들고 `/trunk/a.txt`를 커밋해 둔다(파일 접근이라 서버를 거치지 않는다). */
    private fun project(scope: ProjectScope): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "svn$n", vcs = "Subversion", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        seed(p)
        return p
    }

    private fun seed(p: Project) {
        val directory = svnRepositories.directoryOf(p.owner!!, p.name!!)
        val wc = File(work, "seed-${counter.incrementAndGet()}")
        fun svn(vararg a: String) = check(ProcessBuilder("svn", *a).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0) { a.toList().toString() }
        svn("checkout", "-q", "file://${directory.absolutePath}", wc.path)
        File(wc, "trunk").mkdirs(); File(wc, "trunk/a.txt").writeText("hello\n")
        svn("add", "-q", "${wc.path}/trunk"); svn("commit", "-q", "-m", "seed", wc.path)
    }

    private fun register(u: User): Key = newKey().also { sshKeys.add(u, "k", it.publicLine) }

    private fun deploy(p: Project, writable: Boolean): Key = newKey().also { deployKeys.add(p, "d", it.publicLine, writable) }

    private val File.publicLine get() = File("$path.pub").readText().trim()

    private val Key.publicLine get() = file.publicLine

    @BeforeEach
    fun setUp() {
        RaSvnTestServer.assumeSubversionInstalled()
        member = user("sm"); outsider = user("so")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        deployKeyRepository.deleteAll(); sshKeyRepository.deleteAll()
        auditLogs.deleteAllInBatch()
        tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
    }

    private class Result(val exit: Int, val output: String)

    private fun svn(vararg args: String, key: Key?): Result {
        val ssh = listOfNotNull(
            "ssh", "-F", "/dev/null", "-p", server.boundPort.toString(), "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "LogLevel=ERROR", key?.let { "-i ${it.file.path}" }
        ).joinToString(" ")
        val builder = ProcessBuilder(listOf("svn", "--non-interactive", "--config-dir", File(work, "svn-config").path) + args).directory(work).redirectErrorStream(true)
        builder.environment().putAll(mapOf("SVN_SSH" to ssh, "LC_ALL" to "C.UTF-8"))
        val process = builder.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과: ${args.toList()}" }
        return Result(process.exitValue(), output)
    }

    /** `svn`은 사용자 지정 `SVN_SSH`에는 주소의 포트를 `-p`로 넘기지 않으므로 포트는 ssh 명령에 주고 주소에는 쓰지 않는다. */
    private fun url(p: Project = project, path: String = "", user: String = "git") = "svn+ssh://$user@127.0.0.1/${p.owner}/${p.name}$path"

    @Test
    fun `등록한 키로 목록과 내용을 읽는다`() {
        val key = register(member)
        val listed = svn("ls", url(path = "/trunk"), key = key)
        assertEquals(0, listed.exit, listed.output)
        assertEquals("a.txt\n", listed.output)
        val cat = svn("cat", url(path = "/trunk/a.txt"), key = key)
        assertEquals(0, cat.exit, cat.output)
        assertEquals("hello\n", cat.output)
    }

    @Test
    fun `정보와 로그를 읽는다`() {
        val key = register(member)
        val info = svn("info", url(path = "/trunk/a.txt"), key = key)
        assertEquals(0, info.exit, info.output)
        assertTrue(info.output.contains("Node Kind: file"), info.output)
        val log = svn("log", url(), key = key)
        assertEquals(0, log.exit, log.output)
        assertTrue(log.output.contains("seed"), log.output)
    }

    @Test
    fun `등록하지 않은 키는 접속하지 못한다`() {
        val result = svn("ls", url(path = "/trunk"), key = newKey())
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Permission denied") || result.output.contains("Connection closed"), result.output)
    }

    @Test
    fun `비공개 저장소는 구성원이 아니면 없는 것처럼 거부한다`() {
        val secret = project(ProjectScope.PRIVATE)
        assertEquals(0, svn("ls", url(secret, "/trunk"), key = register(member)).exit)
        val result = svn("ls", url(secret, "/trunk"), key = register(outsider))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }

    @Test
    fun `공개 저장소는 등록한 다른 사용자도 읽는다`() {
        val result = svn("cat", url(path = "/trunk/a.txt"), key = register(outsider))
        assertEquals(0, result.exit, result.output)
        assertEquals("hello\n", result.output)
    }

    @Test
    fun `없는 저장소는 거부한다`() {
        val result = svn("ls", "svn+ssh://git@127.0.0.1/nobody/none", key = register(member))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }

    @Test
    fun `읽기 전용 배포 키는 자기 비공개 저장소를 읽는다`() {
        val secret = project(ProjectScope.PRIVATE)
        val result = svn("cat", url(secret, "/trunk/a.txt"), key = deploy(secret, writable = false))
        assertEquals(0, result.exit, result.output)
        assertEquals("hello\n", result.output)
    }


    /** 작업 복사본을 SSH로 받아 파일을 고치고 커밋한다. */
    private fun commitVia(p: Project, key: Key, message: String = "ssh 커밋", user: String = "git"): Pair<Result, File> {
        val wc = File(work, "wc-${counter.incrementAndGet()}")
        val checkout = svn("checkout", "-q", url(p, "/trunk", user), wc.path, key = key)
        check(checkout.exit == 0) { "체크아웃 실패: ${checkout.output}" }
        File(wc, "new-${counter.get()}.txt").writeText("ssh로 커밋\n")
        File(wc, "a.txt").appendText("추가\n")
        check(svn("add", "-q", "--force", wc.path, key = key).exit == 0)
        return svn("commit", "-q", "-m", message, wc.path, key = key) to wc
    }

    @Test
    fun `쓸 수 있는 사용자는 커밋하고 작성자가 로그인 아이디로 기록된다`() {
        val (result, _) = commitVia(project, register(member))
        assertEquals(0, result.exit, result.output)
        val log = svn("log", "-l", "1", url(), key = register(member))
        assertTrue(log.output.contains(member.loginId!!) && log.output.contains("ssh 커밋"), log.output)
        assertEquals("hello\n추가\n", svn("cat", url(path = "/trunk/a.txt"), key = register(outsider)).output)
    }

    @Test
    fun `읽기만 되는 사용자는 커밋이 거절되고 저장소는 그대로이다`() {
        val (result, _) = commitVia(project, register(outsider))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Authorization failed"), result.output)
        assertEquals("hello\n", svn("cat", url(path = "/trunk/a.txt"), key = register(member)).output)
    }

    @Test
    fun `읽기 전용 배포 키는 커밋하지 못하고 쓰기 배포 키는 커밋한다`() {
        val secret = project(ProjectScope.PRIVATE)
        val (denied, _) = commitVia(secret, deploy(secret, writable = false))
        assertNotEquals(0, denied.exit)
        assertTrue(denied.output.contains("Authorization failed"), denied.output)
        val (allowed, _) = commitVia(secret, deploy(secret, writable = true))
        assertEquals(0, allowed.exit, allowed.output)
        val log = svn("log", "-l", "1", url(secret), key = register(member))
        assertTrue(log.output.contains("deploy-key-"), "배포 키의 커밋은 deploy-key-<번호>로 기록된다: ${log.output}")
    }

    @Test
    fun `배포 키는 다른 저장소를 읽지 못한다`() {
        val secret = project(ProjectScope.PRIVATE)
        val other = project(ProjectScope.PRIVATE)
        val result = svn("cat", url(other, "/trunk/a.txt"), key = deploy(secret, writable = false))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }
}
