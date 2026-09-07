package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.sshkey.SshAuthPrincipal
import com.github.yonaprojects.yona.domain.sshkey.SshAuthService
import com.github.yonaprojects.yona.domain.sshkey.SshKeyRepository
import com.github.yonaprojects.yona.domain.sshkey.SshKeyService
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.BareCommit
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.assertions.withClue
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.SecureRandom
import java.util.Base64

/**
 * yona-wiki P3-18 — 유닉스 도메인 소켓 릴레이(SshRelayServer)가 실제로 동작하는지, 실제 `git`/`hg`
 * 바이너리를 GIT_SSH_COMMAND/ui.ssh 훅으로 이 소켓에 직접 물려 검증한다
 * (YonaMinaSshServerIntegrationSpec과 동일한 "실제 CLI를 테스트 하네스로 관통시킨다" 접근,
 * hg4j의 HgSshWireServerRealHgInteropTest와도 같은 계열).
 *
 * 실제 SSH 세션이 없으므로(이 릴레이는 SSH가 이미 인증한 뒤 forced command가 principal을 이미
 * 확정해 넘겨준다는 전제) 여기서는 ssh-keygen 대신 SshAuthService.encodePrincipal()로 바로
 * "인코딩된 principal" 문자열을 만들어 핸드셰이크에 흘려보낸다 — 실제 forced command(yona-cli,
 * 이번 작업 범위 밖)가 하는 일과 정확히 같다.
 */
class SshRelayServerIntegrationSpec @Autowired constructor(
    private val sshRelayServer: SshRelayServer,
    private val sshAuthService: SshAuthService,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val sshKeyService: SshKeyService,
    private val sshKeyRepository: SshKeyRepository,
    private val deployKeyService: DeployKeyService,
    private val deployKeyRepository: DeployKeyRepository,
    private val protectedBranchRepository: ProtectedBranchRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        private val gitBaseDirHolder = Files.createTempDirectory("ssh-relay-it-git-").toFile()
        private val hgBaseDirHolder = Files.createTempDirectory("ssh-relay-it-hg-").toFile()
        private val socketDirHolder = Files.createTempDirectory("ssh-relay-it-sock-").toFile()
        private val socketFile = File(socketDirHolder, "relay.sock")
        private val wrapperScript = File(socketDirHolder, "uds-relay-wrapper.py").apply {
            writeText(UDS_RELAY_WRAPPER_PY)
            setExecutable(true)
        }

        // yona-wiki P3-18 — 이 소켓 릴레이는 "핸드셰이크(인코딩된 principal + SSH_ORIGINAL_COMMAND
        // 두 줄) → 이후 순수 바이트 릴레이"만 하고, 실제 SSH 세션/인증은 전혀 관여하지 않는다.
        // 이 파이썬 스크립트가 미래의 실제 forced command(yona-cli)가 할 일을 그대로 흉내낸다 —
        // git의 GIT_SSH_COMMAND/hg의 ui.ssh 둘 다 "<이 커맨드> [ssh 옵션...] <host> <remote-command>"
        // 형태로 이 스크립트를 실행하므로, 마지막 인자가 항상 원본 커맨드 라인이다(단, git은
        // ssh 변종 자동판별을 위해 먼저 "-G" 옵션이 섞인 프로브 호출을 한 번 더 하므로 그 경우는
        // 그냥 무시하고 종료한다).
        // 주의(2026-09-07 실측으로 재현·수정): 처음에는 sys.stdin.buffer.read(65536)로 stdin을
        // 읽었는데, CPython의 BufferedReader.read(n)은 "인터랙티브가 아닌"(파이프로 연결된)
        // 스트림에서는 EOF를 만나기 전까지 정확히 n바이트를 채우려고 내부적으로 여러 번 raw
        // read를 반복한다 — git/hg는 한 번에 64KB씩 보내지 않고 프로토콜 왕복마다 작은 청크를
        // 보내고 응답을 기다리므로, 이 read(65536) 호출이 영원히 반환되지 않아(다음 64KB가 결코
        // 오지 않음) 전체 세션이 그대로 데드락됐다(실측: 이 스펙의 첫 실행이 2시간 넘게 멈춤).
        // os.read(fd, n)은 raw 시스템 콜 하나만 호출해 "지금 있는 만큼만" 즉시 반환하므로 이
        // 문제가 없다 — 양쪽 스트림 모두 os.read/os.write로 통일한다.
        private const val UDS_RELAY_WRAPPER_PY = """#!/usr/bin/env python3
import os
import socket
import sys
import threading


def main():
    args = sys.argv[1:]
    sock_path = args[0]
    principal = args[1]
    rest = args[2:]
    if "-G" in rest:
        # git's ssh-variant autodetection probe call -- no real command to relay yet.
        sys.exit(0)
    if not rest:
        sys.exit(1)
    remote_command = rest[-1]

    s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    s.connect(sock_path)
    s.sendall((principal + "\n" + remote_command + "\n").encode("utf-8"))

    stdin_fd = sys.stdin.fileno()
    stdout_fd = sys.stdout.fileno()

    def pump_stdin():
        try:
            while True:
                chunk = os.read(stdin_fd, 65536)
                if not chunk:
                    break
                s.sendall(chunk)
        finally:
            try:
                s.shutdown(socket.SHUT_WR)
            except OSError:
                pass

    t = threading.Thread(target=pump_stdin, daemon=True)
    t.start()
    try:
        while True:
            chunk = s.recv(65536)
            if not chunk:
                break
            os.write(stdout_fd, chunk)
    finally:
        try:
            s.close()
        except OSError:
            pass


if __name__ == "__main__":
    main()
"""

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("yona.git.base-dir") { gitBaseDirHolder.absolutePath }
            registry.add("yona.hg.base-dir") { hgBaseDirHolder.absolutePath }
            registry.add("yona.ssh.mina.enabled") { "false" }
            registry.add("yona.ssh.relay.enabled") { "true" }
            registry.add("yona.ssh.relay.socket-path") { socketFile.absolutePath }
        }
    }

    private fun pythonAvailable(): Boolean =
        try {
            ProcessBuilder("python3", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    private fun hgAvailable(): Boolean =
        try {
            ProcessBuilder("hg", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    // fingerprint 충돌을 피하려고 매 호출마다 임의의(그러나 구조적으로 유효한) ed25519류 블롭을
    // 만든다 — 실제로 SSH 인증에 쓰이지 않으므로(핸드셰이크가 principal을 직접 넘김) 진짜
    // 키쌍일 필요가 없다.
    private fun randomPublicKeyLine(comment: String): String {
        val blob = ByteArray(32)
        SecureRandom().nextBytes(blob)
        return "ssh-ed25519 ${Base64.getEncoder().encodeToString(blob)} $comment"
    }

    private fun sshCommandFor(principalEncoded: String): String =
        "python3 ${wrapperScript.absolutePath} ${socketFile.absolutePath} $principalEncoded"

    // 코디네이터 리뷰(2026-09-08) — 실측으로 재현된 실제 사고: 이 스펙의 첫 실행 중 파이썬 relay
    // 래퍼의 stdin 읽기 버그(sys.stdin.buffer.read(65536)가 파이프에서 정확히 64KB를 채우려고
    // 영원히 블로킹, 아래 UDS_RELAY_WRAPPER_PY 주석 참고)로 서버 쪽 "yona-ssh-relay-conn"
    // 스레드가 UploadPack.upload() -> recvWants()에서 무한정 멈췄다(jstack으로 확인, 2시간 넘게
    // 진행 없음). 그 버그 자체는 os.read(fd, n)으로 고쳤지만(래퍼 스크립트만의 문제, 앱 코드
    // 버그 아님), "테스트 하네스 버그가 CI를 몇 시간이고 멈춰 세울 수 있다"는 사실 자체가 별도
    // 리스크라 — 이 헬퍼가 감싸는 모든 프로세스 호출에 하드 타임아웃을 걸어, 앞으로 비슷한 회귀가
    // 생기면 몇 초 안에 실패하도록 만든다(무한 대기 대신 명확한 AssertionError).
    private fun runProcessWithTimeout(builder: ProcessBuilder, timeoutSeconds: Long = 30): Pair<Int, String> {
        builder.redirectErrorStream(true)
        val process = builder.start()
        val output = StringBuilder()
        val readerThread = Thread({
            try {
                process.inputStream.bufferedReader().forEachLine { output.appendLine(it) }
            } catch (ignored: Exception) {
            }
        }, "ssh-relay-it-process-reader")
        readerThread.isDaemon = true
        readerThread.start()

        val finishedInTime = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        if (!finishedInTime) {
            process.destroyForcibly()
            readerThread.join(2000)
            throw AssertionError(
                "프로세스가 ${timeoutSeconds}초 안에 끝나지 않아 강제 종료했다(소켓 릴레이 쪽 행업 " +
                    "회귀를 몇 시간이 아니라 몇 초 안에 잡기 위한 안전장치). 지금까지 출력:\n$output"
            )
        }
        readerThread.join(5000)
        return process.exitValue() to output.toString()
    }

    private fun runGit(vararg args: String, dir: File? = null, sshCommand: String): Pair<Int, String> {
        val builder = ProcessBuilder(*args)
        builder.environment()["GIT_SSH_COMMAND"] = sshCommand
        if (dir != null) builder.directory(dir)
        return runProcessWithTimeout(builder)
    }

    private fun runGitInDir(dir: File, vararg args: String) {
        val (exitCode, output) = runProcessWithTimeout(ProcessBuilder("git", *args).directory(dir))
        withClue(output) { exitCode shouldBe 0 }
    }

    private fun runHgInDir(dir: File, vararg args: String) {
        val (exitCode, output) = runProcessWithTimeout(ProcessBuilder("hg", *args).directory(dir))
        withClue(output) { exitCode shouldBe 0 }
    }

    private fun runHgWithSsh(vararg args: String, dir: File? = null, sshCommand: String): Pair<Int, String> {
        val fullArgs = arrayOf("hg", "--config", "ui.ssh=$sshCommand") + args
        val builder = ProcessBuilder(*fullArgs)
        if (dir != null) builder.directory(dir)
        return runProcessWithTimeout(builder)
    }

    init {
        describe("유닉스 도메인 소켓 릴레이 — 실제 git/hg 바이너리를 GIT_SSH_COMMAND/ui.ssh로 관통") {
            beforeEach {
                protectedBranchRepository.deleteAll()
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            afterSpec {
                protectedBranchRepository.deleteAll()
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
                gitBaseDirHolder.deleteRecursively()
                hgBaseDirHolder.deleteRecursively()
                socketDirHolder.deleteRecursively()
            }

            it("소켓 릴레이가 실제로 리스닝 중이어야 한다") {
                sshRelayServer.isRunning shouldBe true
            }

            it("인가된 사용자의 SshKey principal로 실제 git clone이 소켓 릴레이를 통해 성공해야 한다") {
                if (!pythonAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-owner", name = "릴레이오너", email = "relay-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "relay-public-repo", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# relay", "초기 커밋")

                val reader = userRepository.save(User(loginId = "relay-reader", name = "릴레이리더", email = "relay-reader@example.com"))
                val sshKey = sshKeyService.create(reader, "릴레이 리더 키", randomPublicKeyLine("relay-reader"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(reader, sshKey))

                val cloneUrl = "ssh://relay-reader@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-clone-").toFile()
                try {
                    val (exitCode, output) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommandFor(encoded)
                    )
                    withClue(output) { exitCode shouldBe 0 }
                    File(cloneDest, "README.md").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            // 이 테스트가 이 작업(P3-18)에서 가장 중요한 테스트다 — 소켓 릴레이 경로도
            // HTTPS/기존 MINA SSH 경로와 완전히 동일하게 BranchProtectionPreReceiveHook을 타는지
            // 검증한다. 이 리팩터 전이라면(=소켓 릴레이가 JGit 훅 체이닝을 거치지 않고 그냥
            // 아무 검증 없이 통과시킨다면) 이 테스트는 RED다.
            it("브랜치 보호 정책(require_pull_request)이 소켓 릴레이를 통한 직접 push에도 적용돼야 한다") {
                if (!pythonAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-bp-owner", name = "릴레이브랜치보호오너", email = "relay-bp-owner@example.com"))
                val project = projectRepository.save(Project(name = "relay-bp-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# bp", "초기 커밋")
                protectedBranchRepository.save(
                    ProtectedBranch(project = project, branchPattern = "main", requirePullRequest = true, adminsCanBypass = false)
                )

                val sshKey = sshKeyService.create(owner, "릴레이 브랜치보호오너 키", randomPublicKeyLine("relay-bp-owner"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(owner, sshKey))
                val sshCommand = sshCommandFor(encoded)

                val cloneUrl = "ssh://relay-bp-owner@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-bp-clone-").toFile()
                try {
                    val (cloneExit, cloneOutput) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommand
                    )
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "should-need-a-pr.txt").writeText("direct push must be rejected")
                    runGitInDir(cloneDest, "config", "user.email", "relay-bp-owner@example.com")
                    runGitInDir(cloneDest, "config", "user.name", "relay-bp-owner")
                    runGitInDir(cloneDest, "add", "should-need-a-pr.txt")
                    runGitInDir(cloneDest, "commit", "-m", "direct push should be rejected")

                    val (pushExit, pushOutput) = runGit(
                        "git", "push", cloneUrl, "main", dir = cloneDest, sshCommand = sshCommand
                    )
                    withClue(pushOutput) { (pushExit != 0) shouldBe true }
                    pushOutput.contains("require_pull_request") shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("PRIVATE 저장소는 멤버가 아닌 사용자로는 소켓 릴레이를 통한 clone도 실패해야 한다") {
                if (!pythonAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-priv-owner", name = "릴레이비공개오너", email = "relay-priv-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "relay-private-repo", owner = owner.loginId, projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# priv", "초기 커밋")

                val outsider = userRepository.save(User(loginId = "relay-outsider", name = "릴레이외부인", email = "relay-outsider@example.com"))
                val sshKey = sshKeyService.create(outsider, "릴레이 외부인 키", randomPublicKeyLine("relay-outsider"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(outsider, sshKey))

                val cloneUrl = "ssh://relay-outsider@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-clone-denied-").toFile()
                try {
                    val (exitCode, _) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommandFor(encoded)
                    )
                    (exitCode != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("게스트 계정의 SshKey principal로는 소켓 릴레이를 통한 clone도 실패해야 한다") {
                if (!pythonAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-guest-owner", name = "릴레이게스트오너", email = "relay-guest-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "relay-guest-repo", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# guest", "초기 커밋")

                val guest = userRepository.save(
                    User(loginId = "relay-guest", name = "릴레이게스트", email = "relay-guest@example.com", isGuest = true)
                )
                val sshKey = sshKeyService.create(guest, "릴레이 게스트 키", randomPublicKeyLine("relay-guest"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(guest, sshKey))

                val cloneUrl = "ssh://relay-guest@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-guest-denied-").toFile()
                try {
                    val (exitCode, _) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommandFor(encoded)
                    )
                    (exitCode != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("읽기전용 Deploy Key로는 소켓 릴레이를 통한 clone은 되지만 push는 실패해야 한다") {
                if (!pythonAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-ro-owner", name = "릴레이읽기전용오너", email = "relay-ro-owner@example.com"))
                val project = projectRepository.save(Project(name = "relay-ro-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# ro", "초기 커밋")

                val issued = deployKeyService.create(project, "릴레이 읽기전용 Deploy Key", randomPublicKeyLine("relay-ro"), readOnly = true)
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.DeployKeyPrincipal(issued.deployKey))
                val sshCommand = sshCommandFor(encoded)

                val cloneUrl = "ssh://git@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-ro-clone-").toFile()
                try {
                    val (cloneExit, cloneOutput) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommand
                    )
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "should-not-be-pushed.txt").writeText("this push must be rejected")
                    runGitInDir(cloneDest, "config", "user.email", "readonly@example.com")
                    runGitInDir(cloneDest, "config", "user.name", "readonly")
                    runGitInDir(cloneDest, "add", "should-not-be-pushed.txt")
                    runGitInDir(cloneDest, "commit", "-m", "should be rejected")
                    runGitInDir(cloneDest, "branch", "-M", "main")

                    val (pushExit, _) = runGit(
                        "git", "push", cloneUrl, "main", dir = cloneDest, sshCommand = sshCommand
                    )
                    (pushExit != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("real hg 클라이언트가 소켓 릴레이를 통해 clone/push를 성공적으로 수행해야 한다") {
                if (!pythonAvailable() || !hgAvailable()) return@it

                val owner = userRepository.save(User(loginId = "relay-hg-owner", name = "릴레이Hg오너", email = "relay-hg-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "relay-hg-repo", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "MERCURIAL")
                )
                repositoryService.getRepository(project).create()
                val repoDir = File(hgBaseDirHolder, "${project.owner}/${project.name}")
                File(repoDir, "a.txt").writeText("hello relay")
                runHgInDir(repoDir, "add", "a.txt")
                runHgInDir(
                    repoDir, "--config", "ui.username=relay-hg-owner <relay-hg-owner@example.com>",
                    "commit", "-m", "초기 커밋"
                )

                val sshKey = sshKeyService.create(owner, "릴레이 Hg오너 키", randomPublicKeyLine("relay-hg-owner"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(owner, sshKey))
                val sshCommand = sshCommandFor(encoded)

                // real hg의 -R 경로 파싱과 authorizeHgCommand()의 owner/project 분해 규칙에 맞추려면,
                // real hg가 실제로 보내는 "-R <path>"의 path가 project의 owner/project 두 세그먼트와
                // 정확히 일치해야 한다 — ssh:// URL의 path 부분이 곧 그 -R 인자가 된다(단일 슬래시 =
                // 원격 홈 디렉터리 기준 상대경로, HgSshWireServerRealHgInteropTest와 동일한 규칙).
                val cloneUrl = "ssh://relay-hg-owner@localhost/${project.owner}/${project.name}"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-hg-clone-").toFile()
                try {
                    val (cloneExit, cloneOutput) = runHgWithSsh(
                        "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommand
                    )
                    withClue(cloneOutput) { cloneExit shouldBe 0 }
                    File(cloneDest, "a.txt").exists() shouldBe true

                    File(cloneDest, "b.txt").writeText("pushed via relay")
                    runHgInDir(cloneDest, "add", "b.txt")
                    runHgInDir(
                        cloneDest, "--config", "ui.username=relay-hg-owner <relay-hg-owner@example.com>",
                        "commit", "-m", "pushed commit"
                    )

                    val (pushExit, pushOutput) = runHgWithSsh(
                        "push", cloneUrl, dir = cloneDest, sshCommand = sshCommand
                    )
                    withClue(pushOutput) { pushExit shouldBe 0 }
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("잘못된/알 수 없는 핸드셰이크 명령은 리스너를 죽이지 않고 거부만 해야 한다") {
                val badConnectionSocket = SocketChannel.open(
                    StandardProtocolFamily.UNIX
                )
                // 워치독 — SocketChannel은 setSoTimeout이 없어 read()가 무기한 블로킹될 수 있다.
                // 서버가 응답 없이 멈추는 회귀가 생기면 이 테스트가 몇 시간이고 매달리는 대신
                // 몇 초 안에 채널을 강제로 닫아 read()가 예외로 실패하게 만든다.
                val watchdog = Thread({
                    Thread.sleep(10_000)
                    try {
                        badConnectionSocket.close()
                    } catch (ignored: Exception) {
                    }
                }, "ssh-relay-it-bad-handshake-watchdog")
                watchdog.isDaemon = true
                watchdog.start()

                badConnectionSocket.connect(UnixDomainSocketAddress.of(socketFile.toPath()))
                badConnectionSocket.write(
                    java.nio.ByteBuffer.wrap("not-a-valid-principal\nnot-a-valid-command\n".toByteArray(StandardCharsets.UTF_8))
                )
                val readBuffer = java.nio.ByteBuffer.allocate(4096)
                badConnectionSocket.read(readBuffer)
                badConnectionSocket.close()
                watchdog.interrupt()

                // 리스너가 살아있는지는 그다음 정상적인 연결이 여전히 처리되는지로 확인한다.
                val owner = userRepository.save(User(loginId = "relay-after-bad-owner", name = "릴레이오너2", email = "relay-after-bad-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "relay-after-bad-repo", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# after-bad", "초기 커밋")

                if (!pythonAvailable()) return@it

                val reader = userRepository.save(User(loginId = "relay-after-bad-reader", name = "릴레이리더2", email = "relay-after-bad-reader@example.com"))
                val sshKey = sshKeyService.create(reader, "릴레이 리더2 키", randomPublicKeyLine("relay-after-bad-reader"))
                val encoded = sshAuthService.encodePrincipal(SshAuthPrincipal.SshKeyPrincipal(reader, sshKey))

                val cloneUrl = "ssh://relay-after-bad-reader@localhost/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("ssh-relay-it-after-bad-clone-").toFile()
                try {
                    val (exitCode, output) = runGit(
                        "git", "clone", cloneUrl, cloneDest.absolutePath, sshCommand = sshCommandFor(encoded)
                    )
                    withClue(output) { exitCode shouldBe 0 }
                } finally {
                    cloneDest.deleteRecursively()
                }
            }
        }
    }
}
