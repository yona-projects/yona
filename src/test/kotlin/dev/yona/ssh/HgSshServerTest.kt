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
import dev.yona.domain.project.Ruleset
import dev.yona.domain.project.RulesetRepository
import dev.yona.domain.sshkey.DeployKeyRepository
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.SshKeyRepository
import dev.yona.domain.sshkey.SshKeyService
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 내장 SSH 서버로 실제 `hg` 클라이언트의 복제, 가져오기, 푸시를 검증한다(X-7 6d). `hg`는 `ssh://git@호스트:포트/소유자/이름`으로 원격에서
 * `hg -R 소유자/이름 serve --stdio`를 실행한다([dev.yona.ssh.SshServerTest]와 같은 방식으로 서버를 띄운다).
 */
@SpringBootTest(properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.ssh.enabled=true", "yona.ssh.port=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-hg-ssh"])
class HgSshServerTest {

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
    @Autowired private lateinit var hgRepositories: HgRepositories
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var sshKeyRepository: SshKeyRepository
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var deployKeyRepository: DeployKeyRepository
    @Autowired private lateinit var rulesets: RulesetRepository
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()

    private class Key(val file: File) {
        val publicLine: String get() = File(file.path + ".pub").readText().trim()
    }

    private fun newKey(): Key {
        val file = File(work, "key-${counter.incrementAndGet()}")
        val exit = ProcessBuilder("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "test", "-f", file.path).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor()
        check(exit == 0) { "ssh-keygen 실패" }
        return Key(file)
    }

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun project(scope: ProjectScope): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "hs$n", vcs = "Mercurial", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun register(u: User): Key = newKey().also { sshKeys.add(u, "k", it.publicLine) }

    private fun deploy(p: Project, writable: Boolean): Key = newKey().also { deployKeys.add(p, "d", it.publicLine, writable) }

    private fun serverDir(p: Project = project) = hgRepositories.directoryOf(p.owner!!, p.name!!)

    @BeforeEach
    fun setUp() {
        assumeTrue(HgTestSupport.available, "hg가 설치되어 있어야 한다")
        member = user("hm"); outsider = user("ho")
        project = project(ProjectScope.PUBLIC)
        HgTestSupport.commit(serverDir(), "서버의 첫 커밋", mapOf("a.txt" to "hello\n"))
    }

    @AfterEach
    fun tearDown() {
        deployKeyRepository.deleteAll(); sshKeyRepository.deleteAll()
        auditLogs.deleteAllInBatch()
        tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    private fun sshCommand(key: Key?): String = listOfNotNull(
        "ssh", "-F", "/dev/null", "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes", "-o", "LogLevel=ERROR",
        key?.let { "-i ${it.file.path}" }
    ).joinToString(" ")

    private fun url(p: Project = project, user: String = "git") = "ssh://$user@127.0.0.1:${server.boundPort}/${p.owner}/${p.name}"

    /** `hg`를 [dir]에서 [key]로 접속하도록 실행한다. */
    private fun hg(dir: File, key: Key?, vararg args: String): Pair<Int, String> {
        val builder = ProcessBuilder(listOf("hg", "--config", "ui.interactive=off", "--config", "ui.ssh=${sshCommand(key)}") + args.toList()).directory(dir).redirectErrorStream(true)
        builder.environment()["HGPLAIN"] = "1"; builder.environment()["HGUSER"] = "Tester <tester@example.com>"
        builder.redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
        val process = builder.start()
        val out = String(process.inputStream.readBytes())
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과: $args" }
        return process.exitValue() to out
    }

    private fun clone(p: Project, key: Key?): Pair<File, Pair<Int, String>> {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        return dir to hg(work, key, "clone", url(p), dir.path)
    }

    /** 복제한 폴더에 커밋 하나를 얹는다. */
    private fun commitMore(dir: File, message: String = "클라이언트 커밋") {
        File(dir, "more-${counter.incrementAndGet()}.txt").writeText("more\n")
        assertEquals(0, hg(dir, null, "add", ".").first)
        assertEquals(0, hg(dir, null, "commit", "-m", message, "-d", "2026-11-04 10:00 +0900").first)
    }

    private fun serverLog() = HgTestSupport.hg(serverDir(), "log", "--template", "{desc|firstline}\\n")

    @Test
    fun `등록한 키로 복제하고 가져오고 푸시한다`() {
        val key = register(member)
        val (dir, cloned) = clone(project, key)
        assertEquals(0, cloned.first, cloned.second)
        assertEquals("hello\n", File(dir, "a.txt").readText())
        HgTestSupport.commit(serverDir(), "서버의 둘째 커밋", mapOf("b.txt" to "b\n"), "2026-11-02 10:00 +0900")
        val pulled = hg(dir, key, "pull", "-u")
        assertEquals(0, pulled.first, pulled.second)
        assertEquals("b\n", File(dir, "b.txt").readText())
        commitMore(dir)
        val pushed = hg(dir, key, "push")
        assertEquals(0, pushed.first, pushed.second)
        assertTrue("클라이언트 커밋" in serverLog(), serverLog())
        assertTrue(HgTestSupport.verifies(serverDir()))
    }

    @Test
    fun `등록하지 않은 키는 접속하지 못한다`() {
        val (_, result) = clone(project, newKey())
        assertNotEquals(0, result.first)
        assertTrue(result.second.contains("Permission denied") || result.second.contains("cannot connect"), result.second)
    }

    @Test
    fun `읽기만 되는 사용자와 읽기 전용 배포 키는 복제하지만 푸시하지 못한다`() {
        val readerKey = register(outsider)
        val (dir, cloned) = clone(project, readerKey)
        assertEquals(0, cloned.first, cloned.second)
        commitMore(dir, "막힐 커밋")
        assertNotEquals(0, hg(dir, readerKey, "push").first)
        val readOnly = deploy(project, writable = false)
        val (dir2, cloned2) = clone(project, readOnly)
        assertEquals(0, cloned2.first, cloned2.second)
        commitMore(dir2, "막힐 커밋 2")
        assertNotEquals(0, hg(dir2, readOnly, "push").first)
        assertFalse("막힐 커밋" in serverLog(), serverLog())
    }

    @Test
    fun `쓰기 배포 키로는 푸시한다`() {
        val key = deploy(project, writable = true)
        val (dir, cloned) = clone(project, key)
        assertEquals(0, cloned.first, cloned.second)
        commitMore(dir)
        assertEquals(0, hg(dir, key, "push").first)
        assertTrue("클라이언트 커밋" in serverLog())
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아닌 사용자에게 없는 것처럼 보인다`() {
        val closed = project(ProjectScope.PRIVATE)
        val (dir, result) = clone(closed, register(outsider))
        // 실제 hg는 거절 사유를 읽지 못하고 빠르게 실패한다(hg4j `rejectConnection`의 알려진 한계). 멈추지 않고 실패하며 복제되지 않는 것만 확인한다
        assertNotEquals(0, result.first)
        assertFalse(File(dir, "a.txt").exists())
    }

    @Test
    fun `보호된 이름표로는 SSH로도 직접 푸시하지 못한다`() {
        rulesets.save(Ruleset(project = project, name = "main", pattern = "main", requirePullRequest = true, createdDate = java.util.Date()))
        HgTestSupport.hg(serverDir(), "bookmark", "main")
        val key = register(member)
        val (dir, cloned) = clone(project, key)
        assertEquals(0, cloned.first, cloned.second)
        assertEquals(0, hg(dir, null, "update", "main").first)
        commitMore(dir, "보호된 커밋")
        val pushed = hg(dir, key, "push", "-B", "main")
        assertNotEquals(0, pushed.first, pushed.second)
        assertFalse("보호된 커밋" in serverLog(), serverLog())
    }

    @Test
    fun `Git 프로젝트에 hg로 접속하면 거절한다`() {
        val git = projects.save(Project(owner = member.loginId, name = "gitone", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val (_, result) = clone(git, register(member))
        assertNotEquals(0, result.first)
        projects.delete(git)
    }
}
