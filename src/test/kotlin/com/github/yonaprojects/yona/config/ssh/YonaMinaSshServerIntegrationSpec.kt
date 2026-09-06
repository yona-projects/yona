package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranch
import com.github.yonaprojects.yona.domain.branchprotection.ProtectedBranchRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyRepository
import com.github.yonaprojects.yona.domain.gpgkey.GpgKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.sshkey.SshKeyRepository
import com.github.yonaprojects.yona.domain.sshkey.SshKeyService
import com.github.yonaprojects.yona.domain.user.EmailRepository
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
import java.nio.file.Files

/**
 * yona-wiki P3-03 Step5/Step6 — 윈도우 SSH 폴백(Apache MINA SSHD, 이 애플리케이션이 직접 띄우는
 * 별도 포트의 임베디드 서버 — 시스템 sshd/포트 22와 무관, 호스트 시스템을 전혀 건드리지 않음)이
 * 실제로 동작하는지 실제 SSH 클라이언트(시스템 `ssh`)와 실제 `git` 바이너리로 검증한다. 사람이
 * 수동으로 눌러보는 것의 자동화된 동등 검증(GitSmartHttpProtocolIntegrationSpec과 동일한 접근).
 *
 * yona.ssh.mina.enabled=true로 강제해(운영에서는 "auto"가 기본값 — 윈도우에서만 자동 활성화)
 * 리눅스 CI에서도 이 경로를 실제로 검증한다.
 */
class YonaMinaSshServerIntegrationSpec @Autowired constructor(
    private val yonaMinaSshServer: YonaMinaSshServer,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService,
    private val sshKeyService: SshKeyService,
    private val sshKeyRepository: SshKeyRepository,
    private val deployKeyService: DeployKeyService,
    private val deployKeyRepository: DeployKeyRepository,
    private val protectedBranchRepository: ProtectedBranchRepository,
    // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — require_signed_commits가 SSH 직접 push에도
    // 실제로 적용되는지 실제 gpg/git 바이너리로 검증하는 데 필요.
    private val gpgKeyService: GpgKeyService,
    private val gpgKeyRepository: GpgKeyRepository,
    private val emailRepository: EmailRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        private val gitBaseDirHolder = Files.createTempDirectory("mina-ssh-it-git-").toFile()
        private val hostKeyHolder = Files.createTempDirectory("mina-ssh-it-hostkey-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("yona.git.base-dir") { gitBaseDirHolder.absolutePath }
            registry.add("yona.ssh.mina.enabled") { "true" }
            registry.add("yona.ssh.mina.port") { "0" }
            registry.add("yona.ssh.mina.host-key-path") { File(hostKeyHolder, "host_key").absolutePath }
        }
    }

    // ssh-keygen으로 실제 ed25519 키쌍을 생성한다(사전에 커밋된 고정 키 파일을 쓰지 않고 테스트마다
    // 새로 생성 — 개인키 파일 권한(600)도 ssh-keygen이 알아서 맞춰준다).
    private fun generateKeyPair(name: String): Pair<File, String> {
        val dir = Files.createTempDirectory("mina-ssh-it-key-$name-").toFile()
        val keyFile = File(dir, "id_ed25519")
        val process = ProcessBuilder("ssh-keygen", "-t", "ed25519", "-N", "", "-f", keyFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        withClue(output) { process.waitFor() shouldBe 0 }
        val publicKey = File(dir, "id_ed25519.pub").readText().trim()
        return keyFile to publicKey
    }

    private fun runGit(vararg args: String, dir: File? = null, privateKeyFile: File): Pair<Int, String> {
        val sshCommand = "ssh -i ${privateKeyFile.absolutePath} -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes"
        val builder = ProcessBuilder(*args)
            .redirectErrorStream(true)
        builder.environment()["GIT_SSH_COMMAND"] = sshCommand
        if (dir != null) builder.directory(dir)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    // yona-wiki P3-03/P3-04 연결 작업 — GpgSignatureVerifierSpec과 동일한 방식(실제 gpg 바이너리로
    // 진짜 ed25519 키쌍을 생성)으로, require_signed_commits가 실제 SSH push 경로에도 적용되는지
    // 검증한다. 순수 mock으로는 RevWalk/RevCommit이 실제 git 객체 저장소를 필요로 해 의미있게
    // 테스트할 수 없다.
    private fun gpgAvailable(): Boolean =
        try {
            ProcessBuilder("gpg", "--version").start().waitFor() == 0
        } catch (e: Exception) {
            false
        }

    private data class GeneratedGpgKey(val gnupgHome: File, val keyId: String, val armoredPublicKey: String, val email: String)

    private fun generateGpgKey(emailLocalPart: String): GeneratedGpgKey {
        val gnupgHome = Files.createTempDirectory("mina-ssh-it-gpg-home-").toFile()
        val email = "$emailLocalPart@example.com"

        val batchFile = File(gnupgHome, "gen-key.batch")
        batchFile.writeText(
            """
            %no-protection
            Key-Type: EDDSA
            Key-Curve: ed25519
            Subkey-Type: EDDSA
            Subkey-Curve: ed25519
            Name-Real: Mina SSH Test Committer
            Name-Email: $email
            Expire-Date: 0
            %commit
            """.trimIndent()
        )

        fun run(vararg cmd: String): String {
            val process = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .also { it.environment()["GNUPGHOME"] = gnupgHome.absolutePath }
                .start()
            val output = process.inputStream.bufferedReader().readText()
            withClue(output) { process.waitFor() shouldBe 0 }
            return output
        }

        run("gpg", "--batch", "--generate-key", batchFile.absolutePath)
        val listing = run("gpg", "--list-secret-keys", "--keyid-format=long", "--with-colons")
        val keyId = listing.lineSequence().first { it.startsWith("sec:") }.split(":")[4]

        val exportProcess = ProcessBuilder("gpg", "--armor", "--export", keyId)
            .also { it.environment()["GNUPGHOME"] = gnupgHome.absolutePath }
            .start()
        val armoredPublicKey = exportProcess.inputStream.bufferedReader().readText()
        withClue(armoredPublicKey) { exportProcess.waitFor() shouldBe 0 }

        return GeneratedGpgKey(gnupgHome, keyId, armoredPublicKey, email)
    }

    private fun runGitInDir(dir: File, env: Map<String, String> = emptyMap(), vararg args: String) {
        val process = ProcessBuilder("git", *args)
            .directory(dir)
            .redirectErrorStream(true)
            .also { pb -> env.forEach { (k, v) -> pb.environment()[k] = v } }
            .start()
        val output = process.inputStream.bufferedReader().readText()
        withClue(output) { process.waitFor() shouldBe 0 }
    }

    init {
        describe("Apache MINA SSHD 폴백 — 실제 SSH 클라이언트/git 바이너리 통합테스트") {
            beforeEach {
                protectedBranchRepository.deleteAll()
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            afterSpec {
                protectedBranchRepository.deleteAll()
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
                gpgKeyRepository.deleteAll()
                emailRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
                gitBaseDirHolder.deleteRecursively()
                hostKeyHolder.deleteRecursively()
            }

            it("임베디드 SSH 서버가 실제로 포트를 바인딩해야 한다") {
                yonaMinaSshServer.isEnabled shouldBe true
                (yonaMinaSshServer.boundPort > 0) shouldBe true
            }

            it("사용자 SshKey로 PUBLIC 저장소를 실제 git clone(ssh://)으로 clone할 수 있어야 한다") {
                val owner = userRepository.save(User(loginId = "mina-owner", name = "미나오너", email = "mina-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "mina-public-repo", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# mina-public-repo", "초기 커밋")

                val reader = userRepository.save(User(loginId = "mina-reader", name = "미나리더", email = "mina-reader@example.com"))
                val (privateKeyFile, publicKey) = generateKeyPair("reader")
                sshKeyService.create(reader, "리더 키", publicKey)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://mina-reader@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-clone-").toFile()

                try {
                    val (exitCode, output) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(output) { exitCode shouldBe 0 }
                    File(cloneDest, "README.md").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("PRIVATE 저장소는 멤버가 아닌 사용자의 SshKey로 clone 시도 시 실패해야 한다") {
                val owner = userRepository.save(User(loginId = "mina-priv-owner", name = "미나비공개오너", email = "mina-priv-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "mina-private-repo", owner = owner.loginId, projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# mina-private-repo", "초기 커밋")

                val outsider = userRepository.save(User(loginId = "mina-outsider", name = "미나외부인", email = "mina-outsider@example.com"))
                val (privateKeyFile, publicKey) = generateKeyPair("outsider")
                sshKeyService.create(outsider, "외부인 키", publicKey)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://mina-outsider@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-clone-denied-").toFile()

                try {
                    val (exitCode, _) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    (exitCode != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("스코프 내 Deploy Key(쓰기 허용)로는 실제 clone과 push가 모두 성공해야 한다") {
                val owner = userRepository.save(User(loginId = "mina-dk-owner", name = "미나DK오너", email = "mina-dk-owner@example.com"))
                val project = projectRepository.save(
                    Project(name = "mina-dk-repo", owner = owner.loginId, projectScope = ProjectScope.PRIVATE, vcs = "GIT")
                )
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# mina-dk-repo", "초기 커밋")

                val (privateKeyFile, publicKey) = generateKeyPair("deploykey-write")
                deployKeyService.create(project, "쓰기 허용 Deploy Key", publicKey, readOnly = false)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://git@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-dk-clone-").toFile()
                val verifyDest = Files.createTempDirectory("mina-ssh-it-dk-verify-").toFile()

                try {
                    val (cloneExit, cloneOutput) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "pushed-by-deploykey.txt").writeText("hello from deploy key")
                    fun run(vararg cmd: String): String {
                        val (exit, out) = runGit(*cmd, dir = cloneDest, privateKeyFile = privateKeyFile)
                        withClue(out) { exit shouldBe 0 }
                        return out
                    }
                    run("git", "config", "user.email", "deploykey@example.com")
                    run("git", "config", "user.name", "deploykey")
                    run("git", "add", "pushed-by-deploykey.txt")
                    run("git", "commit", "-m", "push via deploy key over SSH")
                    run("git", "branch", "-M", "main")
                    run("git", "push", cloneUrl, "main")

                    val (verifyExit, verifyOutput) = runGit("git", "clone", cloneUrl, verifyDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(verifyOutput) { verifyExit shouldBe 0 }
                    File(verifyDest, "pushed-by-deploykey.txt").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                    verifyDest.deleteRecursively()
                }
            }

            // 보안 리뷰 항목 — Deploy Key가 repository_id 스코프 밖 저장소에는 절대 접근하지 못해야 한다.
            it("다른 프로젝트에 스코프된 Deploy Key로는 실제 SSH clone도 실패해야 한다") {
                val owner = userRepository.save(User(loginId = "mina-scope-owner", name = "미나스코프오너", email = "mina-scope-owner@example.com"))
                val ownProject = projectRepository.save(Project(name = "mina-own-repo", owner = owner.loginId, vcs = "GIT"))
                val otherProject = projectRepository.save(Project(name = "mina-other-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(ownProject).create()
                repositoryService.getRepository(otherProject).create()
                BareCommit(ownProject, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# own", "초기 커밋")
                BareCommit(otherProject, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# other", "초기 커밋")

                val (privateKeyFile, publicKey) = generateKeyPair("deploykey-scope")
                deployKeyService.create(ownProject, "own 전용 Deploy Key", publicKey, readOnly = false)

                val port = yonaMinaSshServer.boundPort
                val otherCloneUrl = "ssh://git@127.0.0.1:$port/${otherProject.owner}/${otherProject.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-scope-denied-").toFile()

                try {
                    val (exitCode, _) = runGit("git", "clone", otherCloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    (exitCode != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            // 보안 리뷰 항목 — read_only 플래그가 실제로 push를 막아야 한다.
            it("read_only Deploy Key는 실제 clone은 되지만 push는 실패해야 한다") {
                val owner = userRepository.save(User(loginId = "mina-ro-owner", name = "미나읽기전용오너", email = "mina-ro-owner@example.com"))
                val project = projectRepository.save(Project(name = "mina-ro-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# ro", "초기 커밋")

                val (privateKeyFile, publicKey) = generateKeyPair("deploykey-readonly")
                deployKeyService.create(project, "읽기전용 Deploy Key", publicKey, readOnly = true)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://git@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-ro-clone-").toFile()

                try {
                    val (cloneExit, cloneOutput) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "should-not-be-pushed.txt").writeText("this push must be rejected")
                    fun run(vararg cmd: String) {
                        val (exit, out) = runGit(*cmd, dir = cloneDest, privateKeyFile = privateKeyFile)
                        withClue(out) { exit shouldBe 0 }
                    }
                    run("git", "config", "user.email", "readonly@example.com")
                    run("git", "config", "user.name", "readonly")
                    run("git", "add", "should-not-be-pushed.txt")
                    run("git", "commit", "-m", "should be rejected")
                    run("git", "branch", "-M", "main")

                    val (pushExit, _) = runGit("git", "push", cloneUrl, "main", dir = cloneDest, privateKeyFile = privateKeyFile)
                    (pushExit != 0) shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            // 코디네이터 push 전 리뷰(2026-09-07) — HTTPS 경로(GitServletConfig)는
            // RejectPushToReservedRefsPreReceiveHook과 BranchProtectionPreReceiveHook을 함께
            // 체이닝하는데, 이 SSH 경로(YonaSshGitCommand)는 처음 구현 시 전자만 걸고 후자를
            // 빠뜨려서 P3-04(브랜치 보호) 정책 전체가 SSH를 통하면 우회되는 실제 보안 결함이
            // 있었다 — 이 테스트는 그 결함을 고정하는 회귀 가드다.
            it("브랜치 보호 정책(require_pull_request)이 SSH 직접 push에도 적용돼야 한다(HTTPS와 동일)") {
                val owner = userRepository.save(User(loginId = "mina-bp-owner", name = "미나브랜치보호오너", email = "mina-bp-owner@example.com"))
                val project = projectRepository.save(Project(name = "mina-bp-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# bp", "초기 커밋")
                protectedBranchRepository.save(
                    ProtectedBranch(project = project, branchPattern = "main", requirePullRequest = true, adminsCanBypass = false)
                )

                val (privateKeyFile, publicKey) = generateKeyPair("bp-owner")
                sshKeyService.create(owner, "브랜치보호오너 키", publicKey)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://mina-bp-owner@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-bp-clone-").toFile()

                try {
                    val (cloneExit, cloneOutput) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "should-need-a-pr.txt").writeText("direct push must be rejected")
                    fun run(vararg cmd: String) {
                        val (exit, out) = runGit(*cmd, dir = cloneDest, privateKeyFile = privateKeyFile)
                        withClue(out) { exit shouldBe 0 }
                    }
                    run("git", "config", "user.email", "mina-bp-owner@example.com")
                    run("git", "config", "user.name", "mina-bp-owner")
                    run("git", "add", "should-need-a-pr.txt")
                    run("git", "commit", "-m", "direct push should be rejected")

                    val (pushExit, pushOutput) = runGit("git", "push", cloneUrl, "main", dir = cloneDest, privateKeyFile = privateKeyFile)
                    withClue(pushOutput) { (pushExit != 0) shouldBe true }
                    pushOutput.contains("require_pull_request") shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            // yona-wiki P3-03/P3-04 연결 작업(2026-09-07) — P3-03 4부 완료 로그에 "후속 과제"로
            // 명시적으로 남겨뒀던 갭. require_signed_commits가 이제 실제로 BranchProtectionPreReceiveHook에
            // 연결되었는지, GpgSignatureVerifier가 UNSIGNED로 판정하는 커밋의 직접 push를 실제로
            // 거부하는지 검증한다(gpg 없이도 재현 가능 — 서명 자체를 하지 않으므로).
            it("require_signed_commits가 켜진 브랜치는 서명되지 않은 커밋의 SSH 직접 push를 거부해야 한다") {
                val owner = userRepository.save(User(loginId = "mina-sig-owner", name = "미나서명오너", email = "mina-sig-owner@example.com"))
                val project = projectRepository.save(Project(name = "mina-sig-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# sig", "초기 커밋")
                protectedBranchRepository.save(
                    ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true, adminsCanBypass = false)
                )

                val (privateKeyFile, publicKey) = generateKeyPair("sig-owner")
                sshKeyService.create(owner, "서명오너 키", publicKey)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://mina-sig-owner@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-sig-clone-").toFile()

                try {
                    val (cloneExit, cloneOutput) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "unsigned.txt").writeText("this commit is not signed")
                    fun run(vararg cmd: String) {
                        val (exit, out) = runGit(*cmd, dir = cloneDest, privateKeyFile = privateKeyFile)
                        withClue(out) { exit shouldBe 0 }
                    }
                    run("git", "config", "user.email", "mina-sig-owner@example.com")
                    run("git", "config", "user.name", "mina-sig-owner")
                    run("git", "add", "unsigned.txt")
                    run("git", "commit", "-m", "unsigned commit must be rejected")

                    val (pushExit, pushOutput) = runGit("git", "push", cloneUrl, "main", dir = cloneDest, privateKeyFile = privateKeyFile)
                    withClue(pushOutput) {
                        (pushExit != 0) shouldBe true
                        pushOutput.contains("require_signed_commits") shouldBe true
                    }
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("require_signed_commits가 켜져 있어도 실제로 서명되고 검증되는 커밋의 SSH push는 성공해야 한다") {
                if (!gpgAvailable()) return@it

                val generatedKey = generateGpgKey("mina-sig-ok")
                // author 이메일이 GPG 키의 (계정 소유로 인증된) UID 이메일과 일치해야 VERIFIED로
                // 판정된다. GpgKeyServiceImpl.ownedVerifiedEmailsOf()는 user.email(주 이메일
                // 필드)을 우선 신뢰하므로, GpgSignatureVerifierSpec과 동일하게 User 생성 시점에
                // 곧바로 이 이메일을 주 이메일로 지정한다 — Email(연관 엔티티)을 별도로 만들어
                // user.emails(지연 로딩 컬렉션)에 의존하면 트랜잭션 경계 밖에서 지연 로딩 예외가
                // 날 수 있다(실측: user.emails 경로로 시도했다가 실패해 이 방식으로 바꿨다).
                val owner = userRepository.save(User(loginId = "mina-sig-ok-owner", name = "미나서명성공오너", email = generatedKey.email))
                val project = projectRepository.save(Project(name = "mina-sig-ok-repo", owner = owner.loginId, vcs = "GIT"))
                repositoryService.getRepository(project).create()
                BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile("README.md", "# sig-ok", "초기 커밋")
                protectedBranchRepository.save(
                    ProtectedBranch(project = project, branchPattern = "main", requireSignedCommits = true, adminsCanBypass = false)
                )

                gpgKeyService.create(owner, generatedKey.armoredPublicKey)

                val (privateKeyFile, publicKey) = generateKeyPair("sig-ok-owner")
                sshKeyService.create(owner, "서명성공오너 키", publicKey)

                val port = yonaMinaSshServer.boundPort
                val cloneUrl = "ssh://mina-sig-ok-owner@127.0.0.1:$port/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("mina-ssh-it-sig-ok-clone-").toFile()

                try {
                    val (cloneExit, cloneOutput) = runGit("git", "clone", cloneUrl, cloneDest.absolutePath, privateKeyFile = privateKeyFile)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "signed.txt").writeText("this commit is signed")
                    runGitInDir(cloneDest, args = arrayOf("config", "user.email", generatedKey.email))
                    runGitInDir(cloneDest, args = arrayOf("config", "user.name", "mina-sig-ok-owner"))
                    runGitInDir(cloneDest, args = arrayOf("config", "user.signingkey", generatedKey.keyId))
                    runGitInDir(cloneDest, args = arrayOf("config", "gpg.program", "gpg"))
                    runGitInDir(cloneDest, args = arrayOf("add", "signed.txt"))
                    runGitInDir(
                        cloneDest,
                        env = mapOf("GNUPGHOME" to generatedKey.gnupgHome.absolutePath),
                        args = arrayOf("commit", "-S", "-m", "signed commit should be accepted")
                    )

                    val (pushExit, pushOutput) = runGit("git", "push", cloneUrl, "main", dir = cloneDest, privateKeyFile = privateKeyFile)
                    withClue(pushOutput) { pushExit shouldBe 0 }
                } finally {
                    cloneDest.deleteRecursively()
                    generatedKey.gnupgHome.deleteRecursively()
                }
            }
        }
    }
}
