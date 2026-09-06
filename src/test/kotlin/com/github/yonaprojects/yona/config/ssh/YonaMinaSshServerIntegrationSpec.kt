package com.github.yonaprojects.yona.config.ssh

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
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
    private val deployKeyRepository: DeployKeyRepository
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

    init {
        describe("Apache MINA SSHD 폴백 — 실제 SSH 클라이언트/git 바이너리 통합테스트") {
            beforeEach {
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
                projectUserRepository.deleteAll()
                projectRepository.deleteAll()
                userRepository.deleteAll()
                roleRepository.deleteAll()
            }

            afterSpec {
                sshKeyRepository.deleteAll()
                deployKeyRepository.deleteAll()
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
        }
    }
}
