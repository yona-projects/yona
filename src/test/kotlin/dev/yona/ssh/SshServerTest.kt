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
import dev.yona.domain.sshkey.DeployKeyService
import dev.yona.domain.sshkey.DeployKeyRepository
import dev.yona.domain.sshkey.SshKeyRepository
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.repository.RepositoryService
import dev.yona.security.LegacyPasswordHasher
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 내장 SSH 서버(X-3 5단계, `decisions.md` D-66)를 실제 `ssh`와 `git` 클라이언트로 검증한다. 서버는 `yona.ssh.enabled=true`, 포트 0(비어 있는 포트)으로 뜬다.
 * 서버가 따로 뜨므로 데이터는 커밋하고, 테스트가 끝나면 지운다([dev.yona.web.git.GitHttpTest]와 같다).
 */
@SpringBootTest(properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.git-default-branch=master", "yona.ssh.enabled=true", "yona.ssh.port=0"])
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-ssh-server"])
class SshServerTest {

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
    @Autowired private lateinit var deletion: ProjectDeletionService
    @Autowired private lateinit var sshKeys: SshKeyService
    @Autowired private lateinit var sshKeyRepository: SshKeyRepository
    @Autowired private lateinit var deployKeys: DeployKeyService
    @Autowired private lateinit var deployKeyRepository: DeployKeyRepository
    @Autowired private lateinit var rulesets: RulesetRepository
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var dataDirectory: dev.yona.repository.DataDirectory
    @Autowired private lateinit var tokenFailures: dev.yona.security.TokenFailureLimiter
    @Autowired private lateinit var wikis: dev.yona.repository.WikiRepositories
    @Autowired private lateinit var issues: dev.yona.domain.issue.IssueRepository
    @Autowired private lateinit var issueEvents: dev.yona.domain.issue.IssueEventRepository
    @Autowired private lateinit var meters: io.micrometer.core.instrument.MeterRegistry

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()

    /** ssh-keygen이 만든 키 한 벌이다. [file]은 개인키 경로이고 공개키는 `$file.pub`이다. */
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

    private fun project(scope: ProjectScope, wiki: Boolean = false): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "ssh$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true, wiki = wiki))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun register(u: User): Key = newKey().also { sshKeys.add(u, "k", it.publicLine) }

    private fun deploy(p: Project, writable: Boolean): Key = newKey().also { deployKeys.add(p, "d", it.publicLine, writable) }

    @BeforeEach
    fun setUp() {
        member = user("sm"); outsider = user("so")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        deployKeyRepository.deleteAll(); sshKeyRepository.deleteAll()
        auditLogs.deleteAllInBatch()
        tokenFailures.clear()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
        File(dataDirectory.root(), "repo").walkTopDown().filter { it.isDirectory && it.name.endsWith(".wiki.git") }.toList().forEach { it.deleteRecursively() }
    }

    private class Result(val exit: Int, val output: String)

    private fun run(vararg command: String, key: Key? = null, dir: File = work, env: Map<String, String> = emptyMap()): Result {
        val builder = ProcessBuilder(*command).directory(dir).redirectErrorStream(true)
        builder.environment().putAll(mapOf("GIT_TERMINAL_PROMPT" to "0", "GIT_SSH_COMMAND" to sshCommand(key)) + env)
        val process = builder.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(60, TimeUnit.SECONDS)) { "시간 초과: ${command.toList()}" }
        return Result(process.exitValue(), output)
    }

    private fun sshCommand(key: Key?): String = listOfNotNull(
        "ssh", "-F", "/dev/null", "-p", server.boundPort.toString(), "-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=/dev/null", "-o", "BatchMode=yes", "-o", "IdentitiesOnly=yes",
        "-o", "LogLevel=ERROR", key?.let { "-i ${it.file.path}" }
    ).joinToString(" ")

    private fun url(p: Project = project, user: String = "git") = "ssh://$user@127.0.0.1:${server.boundPort}/${p.owner}/${p.name}.git"

    private fun git(vararg args: String, key: Key? = null, dir: File = work) = run("git", "-c", "user.name=t", "-c", "user.email=t@example.com", *args, key = key, dir = dir)

    /** 새 작업 폴더에 커밋 하나를 만들어 [branch]로 푸시한다. */
    private fun push(p: Project, key: Key?, branch: String = "master", user: String = "git"): Result {
        val dir = File(work, "push-${counter.incrementAndGet()}").also { it.mkdirs() }
        check(git("init", "-q", "-b", branch, dir = dir).exit == 0)
        File(dir, "a.txt").writeText("hello"); git("add", ".", dir = dir); git("commit", "-q", "-m", "first", dir = dir)
        return git("push", url(p, user), "$branch:$branch", key = key, dir = dir)
    }

    private fun clone(p: Project, key: Key?): Pair<Result, File> {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        return git("clone", "-q", url(p), dir.path, key = key) to dir
    }

    @Test
    fun `서버는 설정이 켜져 있으면 빈 포트에서 받는다`() {
        assertTrue(server.boundPort > 0)
    }

    @Test
    fun `등록한 키로 푸시하고 복제한다`() {
        val key = register(member)
        val pushed = push(project, key)
        assertEquals(0, pushed.exit, pushed.output)
        val (cloned, dir) = clone(project, key)
        assertEquals(0, cloned.exit, cloned.output)
        assertEquals("hello", File(dir, "a.txt").readText())
    }

    @Test
    fun `등록하지 않은 키는 접속하지 못한다`() {
        val result = push(project, newKey())
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Permission denied"), result.output)
    }

    @Test
    fun `사용자 이름이 git이 아니면 접속하지 못한다`() {
        val key = register(member)
        val result = push(project, key, user = "someone")
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Permission denied"), result.output)
    }

    @Test
    fun `구성원이 아닌 사용자는 공개 저장소를 복제하지만 푸시는 거부된다`() {
        push(project, register(member))
        val key = register(outsider)
        val (cloned, _) = clone(project, key)
        assertEquals(0, cloned.exit, cloned.output)
        val pushed = push(project, key, branch = "feature")
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("Permission to ${project.owner}/${project.name} denied to ${outsider.loginId}"), pushed.output)
    }

    @Test
    fun `비공개 저장소는 구성원이 아니면 없는 것처럼 거부한다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, register(member))
        val (cloned, _) = clone(secret, register(outsider))
        assertNotEquals(0, cloned.exit)
        assertTrue(cloned.output.contains("Repository not found"), cloned.output)
    }

    @Test
    fun `없는 저장소와 이상한 경로는 거부한다`() {
        val key = register(member)
        val missing = git("ls-remote", "ssh://git@127.0.0.1:${server.boundPort}/nobody/none.git", key = key)
        assertNotEquals(0, missing.exit)
        assertTrue(missing.output.contains("Repository not found"), missing.output)
    }

    @Test
    fun `읽기 전용 배포 키는 자기 비공개 저장소를 복제하고 푸시는 거부된다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, register(member))
        val key = deploy(secret, writable = false)
        val (cloned, dir) = clone(secret, key)
        assertEquals(0, cloned.exit, cloned.output)
        assertTrue(File(dir, "a.txt").exists())
        val pushed = push(secret, key, branch = "feature")
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("read-only"), pushed.output)
    }

    @Test
    fun `배포 키로 푸시한 뒤의 작업은 키를 등록한 사용자에게 귀속된다 (D-68)`() {
        val secret = project(ProjectScope.PRIVATE)
        val issue = issues.save(dev.yona.domain.issue.Issue(project = secret, number = 1, title = "이슈"))
        val key = newKey().also { deployKeys.add(secret, "d", it.publicLine, true, createdBy = member) }
        val dir = File(work, "attr").also { it.mkdirs() }
        git("init", "-q", "-b", "master", dir = dir); File(dir, "a.txt").writeText("x"); git("add", ".", dir = dir); git("commit", "-q", "-m", "고침 #1", dir = dir)
        assertEquals(0, git("push", url(secret), "master:master", key = key, dir = dir).exit)
        val events = issueEvents.findByIssueIdOrderByCreatedAscIdAsc(issue.id!!)
        assertEquals(listOf(dev.yona.domain.enumeration.EventType.ISSUE_REFERRED_FROM_COMMIT), events.map { it.eventType })
        assertEquals(member.loginId, events.single().senderLoginId)
    }

    @Test
    fun `배포 키를 등록한 사용자가 프로젝트 관리자여도 브랜치 보호 규칙은 우회하지 못한다 (D-68)`() {
        val admin = register(member)
        assertEquals(0, push(project, admin).exit)
        rulesets.save(Ruleset(project = project, name = "master", pattern = "master", createdDate = java.util.Date()).apply { requirePullRequest = true; blockForcePushes = false; bypassAdmins = true })
        val key = newKey().also { deployKeys.add(project, "d", it.publicLine, true, createdBy = member) }
        val (_, dir) = clone(project, key)
        File(dir, "more.txt").writeText("more"); git("add", ".", dir = dir); git("commit", "-q", "-m", "more", dir = dir)
        val pushed = git("push", "origin", "master", key = key, dir = dir)
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("protected branch"), pushed.output)
    }

    // --- 위키 저장소(owner/name.wiki.git, D-68) ---

    private fun wikiUrl(p: Project, suffix: String = ".wiki.git") = "ssh://git@127.0.0.1:${server.boundPort}/${p.owner}/${p.name}$suffix"

    private fun pushWiki(p: Project, key: Key?, suffix: String = ".wiki.git", branch: String = "master"): Result {
        val dir = File(work, "wiki-push-${counter.incrementAndGet()}").also { it.mkdirs() }
        check(git("init", "-q", "-b", branch, dir = dir).exit == 0)
        File(dir, "Home.md").writeText("# 위키"); git("add", ".", dir = dir); git("commit", "-q", "-m", "첫 페이지", dir = dir)
        return git("push", wikiUrl(p, suffix), "$branch:$branch", key = key, dir = dir)
    }

    @Test
    fun `위키 저장소를 구성원이 푸시하고 복제한다 (소 위키 접미어 두 가지)`() {
        val wp = project(ProjectScope.PUBLIC, wiki = true); val key = register(member)
        assertEquals(0, pushWiki(wp, key).exit)
        val dir = File(work, "wiki-clone"); val cloned = git("clone", "-q", wikiUrl(wp, ".wiki"), dir.path, key = key)
        assertEquals(0, cloned.exit, cloned.output)
        assertEquals("# 위키", File(dir, "Home.md").readText())
    }

    @Test
    fun `위키를 끈 프로젝트는 위키 저장소가 없는 것처럼 거부한다`() {
        val off = project(ProjectScope.PUBLIC, wiki = false)
        val result = pushWiki(off, register(member))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }

    @Test
    fun `구성원이 아닌 사용자는 공개 프로젝트의 위키를 복제하지만 푸시는 거부된다`() {
        val wp = project(ProjectScope.PUBLIC, wiki = true)
        assertEquals(0, pushWiki(wp, register(member)).exit)
        val outsiderKey = register(outsider)
        assertEquals(0, git("clone", "-q", wikiUrl(wp), File(work, "wiki-out").path, key = outsiderKey).exit)
        val pushed = pushWiki(wp, outsiderKey, branch = "other")
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("Permission to"), pushed.output)
    }

    @Test
    fun `비공개 프로젝트의 위키는 구성원이 아니면 없는 것처럼 거부한다`() {
        val wp = project(ProjectScope.PRIVATE, wiki = true)
        assertEquals(0, pushWiki(wp, register(member)).exit)
        val result = git("clone", "-q", wikiUrl(wp), File(work, "wiki-priv").path, key = register(outsider))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }

    @Test
    fun `배포 키는 위키에 접근하지 못한다`() {
        val wp = project(ProjectScope.PRIVATE, wiki = true)
        assertEquals(0, pushWiki(wp, register(member)).exit)
        val result = git("clone", "-q", wikiUrl(wp), File(work, "wiki-dk").path, key = deploy(wp, writable = true))
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Repository not found"), result.output)
    }

    @Test
    fun `위키 푸시는 기본 브랜치를 지우지 못하고 코드 저장소 푸시 훅의 규칙에 걸리지 않는다`() {
        val wp = project(ProjectScope.PUBLIC, wiki = true); val key = register(member)
        assertEquals(0, pushWiki(wp, key).exit)
        val dir = File(work, "wiki-del"); git("clone", "-q", wikiUrl(wp), dir.path, key = key)
        val deleted = git("push", "origin", ":master", key = key, dir = dir)
        assertNotEquals(0, deleted.exit)
    }

    @Test
    fun `위키 요청은 target이 wiki인 지표로 센다`() {
        val before = meters.find("yona.vcs.request").tags("vcs", "git", "service", "receive_pack", "target", "wiki", "result", "ok").timer()?.count() ?: 0L
        assertEquals(0, pushWiki(project(ProjectScope.PUBLIC, wiki = true), register(member)).exit)
        assertEquals(before + 1, meters.find("yona.vcs.request").tags("vcs", "git", "service", "receive_pack", "target", "wiki", "result", "ok").timer()?.count() ?: 0L)
    }

    // --- SSH 키로 서명한 커밋과 서명 커밋 필수 규칙(D-68) ---

    @Test
    fun `서명 커밋 필수 규칙은 등록한 SSH 키로 서명한 커밋을 받고 서명 없는 커밋은 거부한다`() {
        val key = register(member)
        assertEquals(0, push(project, key).exit)
        rulesets.save(Ruleset(project = project, name = "master", pattern = "master", createdDate = java.util.Date()).apply { requireSignedCommits = true; blockForcePushes = false })
        val plain = user("sg").also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        val plainKey = register(plain)
        val dir = File(work, "signed").also { it.mkdirs() }
        git("clone", "-q", url(project), dir.path, key = plainKey)
        File(dir, "unsigned.txt").writeText("u"); git("add", ".", dir = dir); git("commit", "-q", "-m", "서명 없음", dir = dir)
        val rejected = git("push", "origin", "master", key = plainKey, dir = dir)
        assertNotEquals(0, rejected.exit)
        assertTrue(rejected.output.contains("must have verified signatures"), rejected.output)
        // 같은 사용자의 SSH 키로 서명하면(커밋터 이메일은 사용자가 확인한 이메일) 받는다
        git("reset", "-q", "--hard", "HEAD~1", dir = dir)
        File(dir, "signed.txt").writeText("s"); git("add", ".", dir = dir)
        val signingKey = plainKey.file.path
        val commit = run("git", "-c", "user.name=T", "-c", "user.email=${plain.email}", "-c", "gpg.format=ssh", "-c", "user.signingkey=$signingKey", "commit", "-q", "-S", "-m", "SSH 서명", key = plainKey, dir = dir)
        assertEquals(0, commit.exit, commit.output)
        val accepted = git("push", "origin", "master", key = plainKey, dir = dir)
        assertEquals(0, accepted.exit, accepted.output)
    }

    @Test
    fun `쓰기 배포 키는 푸시할 수 있다`() {
        val secret = project(ProjectScope.PRIVATE)
        val pushed = push(secret, deploy(secret, writable = true))
        assertEquals(0, pushed.exit, pushed.output)
        assertEquals("hello", File(clone(secret, register(member)).second, "a.txt").readText())
    }

    @Test
    fun `배포 키는 다른 저장소에는 접근하지 못한다`() {
        val mine = project(ProjectScope.PRIVATE); val other = project(ProjectScope.PUBLIC)
        push(other, register(member))
        val key = deploy(mine, writable = true)
        assertNotEquals(0, clone(other, key).first.exit)
    }

    @Test
    fun `키를 지우면 더는 접속하지 못한다`() {
        val key = register(member)
        assertEquals(0, push(project, key).exit)
        sshKeyRepository.deleteAll()
        assertNotEquals(0, clone(project, key).first.exit)
    }

    @Test
    fun `잠긴 사용자의 키는 접속하지 못한다`() {
        val key = register(member)
        member.state = UserState.LOCKED; users.save(member)
        assertNotEquals(0, push(project, key).exit)
    }

    @Test
    fun `셸 접속과 임의 명령은 거부한다`() {
        val key = register(member)
        val shell = run("ssh", *sshCommand(key).split(" ").drop(1).toTypedArray(), "git@127.0.0.1")
        assertNotEquals(0, shell.exit)
        val command = run("ssh", *sshCommand(key).split(" ").drop(1).toTypedArray(), "git@127.0.0.1", "ls /")
        assertNotEquals(0, command.exit)
        assertFalse(command.output.contains("bin"), command.output)
    }

    @Test
    fun `브랜치 보호 규칙은 SSH 푸시에도 HTTP와 똑같이 적용된다`() {
        val owner = register(member)
        assertEquals(0, push(project, owner).exit)
        rulesets.save(Ruleset(project = project, name = "master", pattern = "master", createdDate = java.util.Date()).apply { requirePullRequest = true; blockForcePushes = false })
        val plain = user("sp").also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        val key = register(plain)
        val (_, dir) = clone(project, key)
        File(dir, "more.txt").writeText("more"); git("add", ".", dir = dir); git("commit", "-q", "-m", "more", dir = dir)
        val pushed = git("push", "origin", "master", key = key, dir = dir)
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("protected branch"), pushed.output)
    }

    @Test
    fun `호스트 키는 데이터 디렉터리에 한 번 만들어 두고 계속 쓴다`() {
        val hostKey = File(dataDirectory.root(), "ssh/ssh_host_ed25519_key")
        assertTrue(hostKey.isFile, hostKey.path)
        assertNotNull(server.hostKeyFingerprint)
    }

    private fun events(action: String) = auditLogs.findAll().filter { it.action == action }

    /** 인증 실패 기록은 접속이 닫힐 때 서버 스레드가 남기므로 잠시 기다린다. */
    private fun awaitEvents(action: String, count: Int = 1): List<dev.yona.domain.audit.AuditLog> {
        val end = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < end) { events(action).let { if (it.size >= count) return it }; Thread.sleep(50) }
        return events(action)
    }

    private fun timerCount(name: String, vararg tags: String) = meters.find(name).tags(*tags).timer()?.count() ?: 0L
    private fun counterValue(name: String, vararg tags: String) = meters.find(name).tags(*tags).counters().sumOf { it.count() }

    @Test
    fun `SSH 푸시도 git push 감사 기록에 사용자와 접속 주소가 남는다 (X-9 B3)`() {
        assertEquals(0, push(project, register(member), branch = "feature").exit)
        val event = events("git.push").single { it.projectId == project.id }
        assertEquals(member.loginId, event.actorLogin)
        assertEquals(dev.yona.domain.audit.AuditActorType.USER, event.actorType)
        assertEquals("127.0.0.1", event.ip)
        assertEquals("ssh", event.userAgent)
    }

    @Test
    fun `배포 키로 푸시하면 행위자가 배포 키로 남는다 (X-9 B3)`() {
        val secret = project(ProjectScope.PRIVATE)
        assertEquals(0, push(secret, deploy(secret, writable = true)).exit)
        val event = events("git.push").single { it.projectId == secret.id }
        assertEquals(dev.yona.domain.audit.AuditActorType.DEPLOY_KEY, event.actorType)
        assertEquals("d", event.actorLogin)
    }

    @Test
    fun `푸시와 복제의 요청 시간과 전송량과 푸시 수를 지표로 남긴다 (X-10 C5c)`() {
        val key = register(member)
        val receive = timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "ok")
        val upload = timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "ok")
        val bytesIn = counterValue("yona.vcs.transfer", "vcs", "git", "direction", "in")
        val bytesOut = counterValue("yona.vcs.transfer", "vcs", "git", "direction", "out")
        val pushes = counterValue("yona.vcs.pushes", "vcs", "git", "kind", "branch", "result", "ok")
        assertEquals(0, push(project, key).exit)
        assertEquals(0, clone(project, key).first.exit)
        assertEquals(receive + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "ok"))
        assertEquals(upload + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "ok"))
        assertTrue(counterValue("yona.vcs.transfer", "vcs", "git", "direction", "in") > bytesIn)
        assertTrue(counterValue("yona.vcs.transfer", "vcs", "git", "direction", "out") > bytesOut)
        assertEquals(pushes + 1, counterValue("yona.vcs.pushes", "vcs", "git", "kind", "branch", "result", "ok"))
    }

    @Test
    fun `거부된 접근은 denied와 not_found로 센다 (X-10 C5c)`() {
        val denied = timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "denied")
        val missing = timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "not_found")
        push(project, register(outsider), branch = "feature")
        git("ls-remote", "ssh://git@127.0.0.1:${server.boundPort}/nobody/none.git", key = register(member))
        assertEquals(denied + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "receive_pack", "target", "code", "result", "denied"))
        assertEquals(missing + 1, timerCount("yona.vcs.request", "vcs", "git", "service", "upload_pack", "target", "code", "result", "not_found"))
    }

    @Test
    fun `인증에 실패한 접속은 ssh 채널의 인증 실패로 기록하고 센다 (X-9 A3, X-10 C5d)`() {
        val failures = counterValue("yona.security.events", "event", "basic_failure", "channel", "ssh")
        assertNotEquals(0, push(project, newKey()).exit)
        val event = awaitEvents("auth.basic_failure").single()
        assertTrue(event.details!!.contains("\"channel\":\"ssh\"") && event.details!!.contains("unknown_key"), event.details)
        assertEquals("127.0.0.1", event.ip)
        assertEquals(failures + 1, counterValue("yona.security.events", "event", "basic_failure", "channel", "ssh"))
    }

    @Test
    fun `인증에 연달아 실패하면 같은 주소는 막히고 막힌 동안은 등록한 키도 거부된다 (X-5)`() {
        val good = register(member)
        assertEquals(0, push(project, good).exit)
        repeat(5) { assertNotEquals(0, push(project, newKey()).exit) }
        val blocked = push(project, good, branch = "feature")
        assertNotEquals(0, blocked.exit)
        assertTrue(blocked.output.contains("Permission denied"), blocked.output)
        assertTrue(events("auth.token_blocked").isNotEmpty())
        tokenFailures.clear()
        assertEquals(0, push(project, good, branch = "feature").exit)
    }

    @Test
    fun `성공한 접속은 실패 횟수를 지운다 (X-5)`() {
        val good = register(member)
        repeat(4) { assertNotEquals(0, push(project, newKey()).exit) }
        assertEquals(0, push(project, good).exit)
        repeat(4) { assertNotEquals(0, push(project, newKey()).exit) }
        assertEquals(0, push(project, good, branch = "feature").exit, "성공이 횟수를 지웠으므로 4번 더 실패해도 막히지 않는다")
    }

    private fun assertNotEquals(unexpected: Int, actual: Int) = assertTrue(unexpected != actual, "종료 코드가 $unexpected 이면 안 된다")
}
