package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.assertions.withClue
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

private const val TEST_DEPLOY_PUBLIC_KEY =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS svn-http-it@example.com"

// P3-34 — Git/Hg는 GitSmartHttpProtocolIntegrationSpec/HgHttpProtocolIntegrationSpec에서
// webEnvironment=RANDOM_PORT의 실제 임베디드 톰캣 + 실제 CLI 바이너리로 clone/push를 확정
// 검증하지만, SVN은 여태 SvnControllerOptionsIntegrationSpec(OPTIONS Allow 헤더 간접 추론)과
// DeployKeySvnAuthorizationIntegrationSpec(MockMvc, 실제 포트 없음)뿐이라 실제 `svn checkout`/
// `svn commit`이 SvnController가 위임하는 DAVServlet(SVNKit의 WebDAV/DeltaV 구현)을 실제로
// 왕복하는지 확정 검증된 적이 없었다. Git/Hg와 동일한 패턴으로 실서버+실 svn 클라이언트
// checkout/add/commit round-trip을 검증하고, 별도 checkout으로 서버에 실제 반영됐는지까지
// 재확인한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SvnHttpProtocolIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val deployKeyService: DeployKeyService
) : AbstractIntegrationTest() {

    private fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.reset()
        digest.update(salt.toByteArray(StandardCharsets.UTF_8))
        var hashed = digest.digest(password.toByteArray(StandardCharsets.UTF_8))
        for (i in 1 until 1024) {
            digest.reset()
            hashed = digest.digest(hashed)
        }
        return Base64.getEncoder().encodeToString(hashed)
    }

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    companion object {
        private val svnBaseDirHolder = Files.createTempDirectory("svn-http-it-").toFile()

        // 실제 svn CLI가 로컬 사용자의 ~/.subversion 설정/인증 캐시를 건드리지 않도록(공유
        // 개발 환경에서 다른 세션이 svn을 쓰고 있을 수 있음) 이 스펙 전용 config-dir을 쓴다.
        private val svnConfigDirHolder = Files.createTempDirectory("svn-http-it-config-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideSvnBaseDir(registry: DynamicPropertyRegistry) {
            registry.add("yona.svn.base-dir") { svnBaseDirHolder.absolutePath }
        }

        private fun svnAvailable(): Boolean =
            try {
                ProcessBuilder("svn", "--version").start().waitFor() == 0
            } catch (e: Exception) {
                false
            }
    }

    private fun runSvn(dir: File?, vararg args: String): Pair<Int, String> {
        val fullArgs = mutableListOf("svn", "--non-interactive", "--config-dir", svnConfigDirHolder.absolutePath)
        fullArgs.addAll(args)
        val builder = ProcessBuilder(fullArgs).redirectErrorStream(true)
        if (dir != null) builder.directory(dir)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    init {
        describe("실제 svn checkout/commit 커맨드로 검증하는 WebDAV/DeltaV 프로토콜 (P3-34)") {
            it("PUBLIC SVN 프로젝트를 실제 svn checkout 바이너리로 checkout하면 성공해야 한다") {
                if (!svnAvailable()) return@it

                val owner = userRepository.findByLoginId("svn-http-owner").orElseGet {
                    userRepository.save(User(loginId = "svn-http-owner", name = "SVN HTTP오너", email = "svn-http-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "svn-http-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "svn-http-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                    )
                val repository = repositoryService.getRepository(project)
                if (!repository.getDirectory().exists()) {
                    repository.create()
                }

                val checkoutUrl = "http://127.0.0.1:$port/svn/${project.owner}/${project.name}"
                val checkoutDest = Files.createTempDirectory("svn-http-checkout-").toFile()
                try {
                    val (exitCode, output) = runSvn(null, "checkout", checkoutUrl, checkoutDest.absolutePath)
                    withClue(output) { exitCode shouldBe 0 }
                    File(checkoutDest, ".svn").exists() shouldBe true
                } finally {
                    checkoutDest.deleteRecursively()
                }
            }

            it("멤버가 Basic 인증으로 실제 svn commit 바이너리를 통해 커밋하면 성공하고 서버에 실제 반영돼야 한다") {
                if (!svnAvailable()) return@it

                val commitPassword = "pass123"
                val salt = "saltsalt"
                val owner = userRepository.findByLoginId("svn-http-commit-owner").orElseGet {
                    userRepository.save(
                        User(
                            loginId = "svn-http-commit-owner", name = "SVN HTTP커밋오너", email = "svn-http-commit-owner@yona.io",
                            password = hashPassword(commitPassword, salt), passwordSalt = salt
                        )
                    )
                }
                val project = projectRepository.findAll().find { it.name == "svn-http-commit-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "svn-http-commit-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                    )
                // 쓰기 권한을 위해 owner를 manager로 명시적 멤버 등록한다 — SvnAuthorizationFilter가
                // RepoAccessPolicy.isMember()로 요구하는 것과 동일한 패턴(Git/Hg 스펙과 동일).
                val managerRole = roleRepository.findById(1L).orElseGet { roleRepository.save(Role(id = 1L, name = "manager", active = true)) }
                if (projectUserRepository.findByProjectIdAndUserId(project.id!!, owner.id!!).isEmpty) {
                    projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))
                }

                val repository = repositoryService.getRepository(project)
                if (!repository.getDirectory().exists()) {
                    repository.create()
                }

                val checkoutUrl = "http://127.0.0.1:$port/svn/${project.owner}/${project.name}"
                val checkoutDest = Files.createTempDirectory("svn-http-commit-checkout-").toFile()
                val verifyDest = Files.createTempDirectory("svn-http-commit-verify-").toFile()
                try {
                    val (checkoutExit, checkoutOutput) = runSvn(
                        null, "checkout", "--username", owner.loginId, "--password", commitPassword,
                        checkoutUrl, checkoutDest.absolutePath
                    )
                    withClue(checkoutOutput) { checkoutExit shouldBe 0 }

                    File(checkoutDest, "committed.txt").writeText("committed via real svn HTTP commit")
                    val (addExit, addOutput) = runSvn(checkoutDest, "add", "committed.txt")
                    withClue(addOutput) { addExit shouldBe 0 }

                    val (commitExit, commitOutput) = runSvn(
                        checkoutDest, "commit", "-m", "committed via real svn binary",
                        "--username", owner.loginId, "--password", commitPassword
                    )
                    // svn 클라이언트의 커밋 완료 메시지는 로케일에 따라 문구가 달라지므로("Committed
                    // revision 1." / "커밋된 리비전 1." 등) 문자열로 단정하지 않고, exitCode와
                    // 아래의 별도 checkout 재검증으로 실제 반영 여부를 확정한다.
                    withClue(commitOutput) { commitExit shouldBe 0 }

                    // 커밋이 응답만 성공하고 실제로는 서버에 반영이 안 된 게 아닌지, 별도 checkout으로 재검증한다.
                    val (verifyExit, verifyOutput) = runSvn(
                        null, "checkout", "--username", owner.loginId, "--password", commitPassword,
                        checkoutUrl, verifyDest.absolutePath
                    )
                    withClue(verifyOutput) { verifyExit shouldBe 0 }
                    File(verifyDest, "committed.txt").exists() shouldBe true
                } finally {
                    checkoutDest.deleteRecursively()
                    verifyDest.deleteRecursively()
                }
            }

            // DeployKeySvnAuthorizationIntegrationSpec의 MockMvc 검증(HTTP 상태 코드만 확인)을
            // 대체하는 게 아니라, 그 위에 실제 svn 클라이언트 + 실제 서버로 왕복까지 되는지 보강한다.
            it("Deploy Key 인증으로 실제 svn checkout/commit 바이너리를 통해 읽기/쓰기를 왕복할 수 있어야 한다") {
                if (!svnAvailable()) return@it

                val project = projectRepository.findAll().find { it.name == "svn-http-dk-proj" && it.owner == "svn-http-dk-owner" }
                    ?: projectRepository.save(
                        Project(
                            name = "svn-http-dk-proj", owner = "svn-http-dk-owner",
                            projectScope = ProjectScope.PRIVATE, vcs = "SUBVERSION", createdDate = Instant.now()
                        )
                    )
                val repository = repositoryService.getRepository(project)
                if (!repository.getDirectory().exists()) {
                    repository.create()
                }

                val issued = deployKeyService.create(project, "svn http e2e 쓰기 키", TEST_DEPLOY_PUBLIC_KEY, readOnly = false)

                val checkoutUrl = "http://127.0.0.1:$port/svn/${project.owner}/${project.name}"
                val checkoutDest = Files.createTempDirectory("svn-http-dk-checkout-").toFile()
                val verifyDest = Files.createTempDirectory("svn-http-dk-verify-").toFile()
                try {
                    val (checkoutExit, checkoutOutput) = runSvn(
                        null, "checkout", "--username", "x-access-deploykey", "--password", issued.rawHttpsToken,
                        checkoutUrl, checkoutDest.absolutePath
                    )
                    withClue(checkoutOutput) { checkoutExit shouldBe 0 }

                    File(checkoutDest, "deploykey.txt").writeText("committed via deploy key over real svn HTTP")
                    val (addExit, addOutput) = runSvn(checkoutDest, "add", "deploykey.txt")
                    withClue(addOutput) { addExit shouldBe 0 }

                    val (commitExit, commitOutput) = runSvn(
                        checkoutDest, "commit", "-m", "committed via deploy key",
                        "--username", "x-access-deploykey", "--password", issued.rawHttpsToken
                    )
                    withClue(commitOutput) { commitExit shouldBe 0 }

                    val (verifyExit, verifyOutput) = runSvn(
                        null, "checkout", "--username", "x-access-deploykey", "--password", issued.rawHttpsToken,
                        checkoutUrl, verifyDest.absolutePath
                    )
                    withClue(verifyOutput) { verifyExit shouldBe 0 }
                    File(verifyDest, "deploykey.txt").exists() shouldBe true
                } finally {
                    checkoutDest.deleteRecursively()
                    verifyDest.deleteRecursively()
                }
            }
        }
    }
}
