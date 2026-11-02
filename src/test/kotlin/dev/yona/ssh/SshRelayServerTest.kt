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
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.TestDatabase
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
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * 시스템 sshd 연동(X-3b, `decisions.md` D-67)을 실제 훅 스크립트와 `git`, `socat`으로 검증한다. sshd는 이 환경에 없으므로 `ssh` 자리에 sshd가 하는 일을 흉내 내는 작은 스크립트를 둔다:
* 훅(`tools/ssh/yona-ssh-auth.sh`)을 부르고, 출력한 `authorized_keys` 줄의 `command="..."`를 `SSH_ORIGINAL_COMMAND`와 `SSH_CLIENT`를 채워 셸로 실행한다. 훅, 내부 인증 주소, 중계 소켓, 인가,
 * 푸시 훅이 모두 실제 코드로 이어진다. 진짜 sshd와의 연결은 별도로 컨테이너에서 확인한다(`architecture/ssh.md`).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["yona.notification-mail-enabled=false", "yona.secret=test-secret", "yona.update.interval=0", "yona.git-default-branch=master", "yona.ssh.relay.enabled=true",
        "yona.ssh.relay.internal-secret=relay-test-secret"]
)
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-ssh-relay"])
open class SshRelayServerTest {

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
    @Autowired private lateinit var relay: SshRelayServer
    @Autowired private lateinit var tokens: SshRelayTokens
    @Autowired private lateinit var access: SshAccess
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
    @Autowired private lateinit var auditLogs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var meters: io.micrometer.core.instrument.MeterRegistry

    @TempDir lateinit var work: File

    private lateinit var member: User
    private lateinit var outsider: User
    private lateinit var project: Project
    private val created = mutableListOf<User>()

    private class Key(val file: File) {
        val publicLine: String get() = File(file.path + ".pub").readText().trim()
        val type: String get() = publicLine.split(" ")[0]
        val blob: String get() = publicLine.split(" ")[1]
    }

    private fun newKey(): Key {
        val file = File(work, "key-${counter.incrementAndGet()}")
        check(ProcessBuilder("ssh-keygen", "-q", "-t", "ed25519", "-N", "", "-C", "test", "-f", file.path).redirectErrorStream(true).start().also { it.inputStream.readBytes() }.waitFor() == 0)
        return Key(file)
    }

    private fun user(prefix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val n = counter.incrementAndGet()
        return users.save(User(loginId = "$prefix$n", name = "$prefix$n", email = "$prefix$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { created += it }
    }

    private fun project(scope: ProjectScope): Project {
        val n = counter.incrementAndGet()
        val p = projects.save(Project(owner = member.loginId, name = "rel$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        projectUsers.save(ProjectUser(user = member, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        repositories.createRepository(p)
        return p
    }

    private fun register(u: User): Key = newKey().also { sshKeys.add(u, "k", it.publicLine) }

    private fun deploy(p: Project, writable: Boolean): Key = newKey().also { deployKeys.add(p, "d", it.publicLine, writable) }

    @BeforeEach
    fun setUp() {
        member = user("rm"); outsider = user("ro")
        project = project(ProjectScope.PUBLIC)
    }

    @AfterEach
    fun tearDown() {
        deployKeyRepository.deleteAll(); sshKeyRepository.deleteAll()
        auditLogs.deleteAllInBatch()
        projects.findAll().filter { it.owner == member.loginId }.forEach { deletion.delete(it) }
        users.deleteAll(created)
    }

    // --- sshd를 흉내 내는 `ssh` ---

    /** 훅 명령이다. 셸 스크립트(기본) 또는 Go 실행 파일의 `auth` 하위 명령이다(하위 클래스가 바꾼다, [SshRelayGoHelperTest]). 공백이 없는 경로여야 한다. */
    protected open fun hook(): String = File("tools/ssh/yona-ssh-auth.sh").absolutePath

    /** sshd가 하는 일을 흉내 낸다: 훅을 불러 authorized_keys 줄을 받고, 없으면 `Permission denied`, 있으면 `command="..."`를 셸로 실행한다. */
    private fun fakeSsh(): File = File(work, "fake-ssh").also {
        it.writeText(
            """
            #!/usr/bin/env bash
            # 사용법: fake-ssh <호스트> <명령>  (git이 부르는 모양). 사용할 키는 FAKE_KEY_TYPE, FAKE_KEY_BLOB 환경 변수이다
            command_line="${'$'}{@: -1}"
            line="${'$'}(${hook()} "${'$'}FAKE_KEY_TYPE" "${'$'}FAKE_KEY_BLOB")" || exit 255
            if [ -z "${'$'}line" ]; then echo "git@host: Permission denied (publickey)." >&2; exit 255; fi
            value="${'$'}(printf '%s' "${'$'}line" | sed -E 's/^command="(.*)",no-port-forwarding.*${'$'}/\1/' | sed 's/\\"/"/g')"
            export SSH_ORIGINAL_COMMAND="${'$'}command_line" SSH_CLIENT="127.0.0.1 50000 22"
            exec bash -c "${'$'}value"
            """.trimIndent() + "\n"
        )
        it.setExecutable(true)
    }

    private class Result(val exit: Int, val output: String)

    private fun run(vararg command: String, key: Key? = null, dir: File = work): Result {
        val builder = ProcessBuilder(*command).directory(dir).redirectErrorStream(true)
        builder.environment().putAll(
            mapOf(
                "GIT_TERMINAL_PROMPT" to "0", "GIT_SSH" to fakeSsh().path, "GIT_SSH_VARIANT" to "simple",
                "YONA_INTERNAL_URL" to "http://127.0.0.1:$port", "YONA_SSH_SECRET_FILE" to secretFile().path, "YONA_SSH_RELAY_SOCKET" to relay.socketPath,
                "FAKE_KEY_TYPE" to (key?.type ?: "ssh-ed25519"), "FAKE_KEY_BLOB" to (key?.blob ?: "AAAA")
            )
        )
        // 출력을 파일로 받아 시간 초과를 정확히 센다(고아가 된 자식이 출력 파이프를 쥐고 있어도 읽기가 멈추지 않는다)
        val log = File(work, "out-${counter.incrementAndGet()}.log")
        builder.redirectOutput(log)
        val process = builder.start()
        process.outputStream.close()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) process.destroyForcibly()
        check(finished) { "시간 초과: ${command.toList()}\n${log.readText()}" }
        return Result(process.exitValue(), log.readText())
    }

    private fun secretFile(): File = File(work, "secret").also { it.writeText("relay-test-secret") }

    private fun url(p: Project = project) = "ssh://git@host/${p.owner}/${p.name}.git"

    private fun git(vararg args: String, key: Key? = null, dir: File = work) = run("git", "-c", "user.name=t", "-c", "user.email=t@example.com", *args, key = key, dir = dir)

    private fun push(p: Project, key: Key?, branch: String = "master"): Result {
        val dir = File(work, "push-${counter.incrementAndGet()}").also { it.mkdirs() }
        check(git("init", "-q", "-b", branch, dir = dir).exit == 0)
        File(dir, "a.txt").writeText("hello"); git("add", ".", dir = dir); git("commit", "-q", "-m", "first", dir = dir)
        return git("push", url(p), "$branch:$branch", key = key, dir = dir)
    }

    private fun clone(p: Project, key: Key?): Pair<Result, File> {
        val dir = File(work, "clone-${counter.incrementAndGet()}")
        return git("clone", "-q", url(p), dir.path, key = key) to dir
    }

    // --- 중계 소켓을 직접 쓰는 시험 ---

    private fun talk(vararg lines: String): String {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(relay.socketPath))
            val out = Channels.newOutputStream(channel)
            lines.forEach { out.write((it + "\n").toByteArray()) }
            out.flush()
            channel.shutdownOutput() // 더 보낼 것이 없다고 알려야 서버가 모자란 핸드셰이크를 알아차린다
            return Channels.newInputStream(channel).readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun tokenOf(u: User): String = tokens.issue(access.authenticate(sshKeys.list(u).first().fingerprint!!)!!)

    @Test
    fun `서버는 설정한 소켓 경로에서 받고 소켓 파일은 소유자와 그룹만 읽고 쓴다`() {
        assertTrue(relay.isRunning())
        val permissions = java.nio.file.Files.getPosixFilePermissions(File(relay.socketPath).toPath())
        assertEquals(setOf(java.nio.file.attribute.PosixFilePermission.OWNER_READ, java.nio.file.attribute.PosixFilePermission.OWNER_WRITE, java.nio.file.attribute.PosixFilePermission.GROUP_READ, java.nio.file.attribute.PosixFilePermission.GROUP_WRITE), permissions)
    }

    @Test
    fun `유효하지 않은 인증 표는 git 오류 줄로 거부한다`() {
        val reply = talk("v1.user.1.4102444800.badsignature", "127.0.0.1", "git-upload-pack '/${project.owner}/${project.name}.git'")
        assertTrue(reply.contains("ERR ") && reply.contains("Invalid credentials"), reply)
    }

    @Test
    fun `핸드셰이크가 모자라거나 알 수 없는 명령은 거부한다`() {
        register(member)
        assertTrue(talk(tokenOf(member), "127.0.0.1", "ls /").contains("Invalid command"))
        assertTrue(talk(tokenOf(member)).contains("Invalid handshake"), "명령 줄이 없으면 핸드셰이크 오류이다")
        assertEquals("", talk(), "아무것도 보내지 않고 닫으면 답이 없다")
    }

    // --- 실제 훅 스크립트와 git ---

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
    fun `등록하지 않은 키는 훅이 거부해 Permission denied이다`() {
        val result = push(project, newKey())
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Permission denied"), result.output)
    }

    @Test
    fun `구성원이 아닌 사용자는 공개 저장소를 복제하지만 푸시는 거부된다`() {
        push(project, register(member))
        val key = register(outsider)
        assertEquals(0, clone(project, key).first.exit)
        val pushed = push(project, key, branch = "feature")
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("Permission to ${project.owner}/${project.name} denied to ${outsider.loginId}"), pushed.output)
    }

    @Test
    fun `비공개 저장소는 구성원이 아니면 git 오류 메시지로 없는 것처럼 거부한다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, register(member))
        val (cloned, _) = clone(secret, register(outsider))
        assertNotEquals(0, cloned.exit)
        assertTrue(cloned.output.contains("Repository not found."), cloned.output)
    }

    @Test
    fun `읽기 전용 배포 키는 복제만 되고 쓰기 배포 키는 푸시도 된다`() {
        val secret = project(ProjectScope.PRIVATE)
        push(secret, register(member))
        val readOnly = deploy(secret, writable = false)
        assertEquals(0, clone(secret, readOnly).first.exit)
        val denied = push(secret, readOnly, branch = "feature")
        assertNotEquals(0, denied.exit)
        assertTrue(denied.output.contains("read-only"), denied.output)
        assertEquals(0, push(secret, deploy(secret, writable = true), branch = "deployed").exit)
    }

    @Test
    fun `키를 지우면 훅이 더는 인증하지 않는다`() {
        val key = register(member)
        assertEquals(0, push(project, key).exit)
        sshKeyRepository.deleteAll()
        val result = clone(project, key).first
        assertNotEquals(0, result.exit)
        assertTrue(result.output.contains("Permission denied"), result.output)
    }

    @Test
    fun `원본 명령이 없는 접속은 셸을 주지 않는다`() {
        val key = register(member)
        val ssh = fakeSsh()
        val builder = ProcessBuilder(ssh.path, "host", "").redirectErrorStream(true)
        builder.environment().putAll(mapOf("YONA_INTERNAL_URL" to "http://127.0.0.1:$port", "YONA_SSH_SECRET_FILE" to secretFile().path, "YONA_SSH_RELAY_SOCKET" to relay.socketPath, "FAKE_KEY_TYPE" to key.type, "FAKE_KEY_BLOB" to key.blob))
        val process = builder.start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        assertTrue(process.waitFor(30, TimeUnit.SECONDS))
        assertNotEquals(0, process.exitValue())
        assertTrue(output.contains("Interactive shell access is not provided"), output)
    }

    @Test
    fun `브랜치 보호 규칙은 중계 푸시에도 똑같이 적용된다`() {
        val owner = register(member)
        assertEquals(0, push(project, owner).exit)
        val rules = dev.yona.domain.project.Ruleset(project = project, name = "master", pattern = "master", createdDate = java.util.Date()).apply { requirePullRequest = true; blockForcePushes = false }
        rulesetRepository.save(rules)
        val plain = user("rp").also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        val key = register(plain)
        val (_, dir) = clone(project, key)
        File(dir, "more.txt").writeText("more"); git("add", ".", dir = dir); git("commit", "-q", "-m", "more", dir = dir)
        val pushed = git("push", "origin", "master", key = key, dir = dir)
        assertNotEquals(0, pushed.exit)
        assertTrue(pushed.output.contains("protected branch"), pushed.output)
    }

    @Autowired private lateinit var rulesetRepository: dev.yona.domain.project.RulesetRepository

    @Test
    fun `중계 푸시도 git push 감사 기록에 사용자와 접속 주소가 남고 지표가 센다`() {
        val before = meters.find("yona.vcs.request").tags("vcs", "git", "service", "receive_pack", "target", "code", "result", "ok").timer()?.count() ?: 0L
        assertEquals(0, push(project, register(member), branch = "feature").exit)
        val event = auditLogs.findAll().single { it.action == "git.push" && it.projectId == project.id }
        assertEquals(member.loginId, event.actorLogin)
        assertEquals("127.0.0.1", event.ip, "강제 명령이 넘긴 클라이언트 주소(SSH_CLIENT)가 남는다")
        assertEquals("ssh", event.userAgent)
        assertEquals(before + 1, meters.find("yona.vcs.request").tags("vcs", "git", "service", "receive_pack", "target", "code", "result", "ok").timer()?.count() ?: 0L)
    }
}
