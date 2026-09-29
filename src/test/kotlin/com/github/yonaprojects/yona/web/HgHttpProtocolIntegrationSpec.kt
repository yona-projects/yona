package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
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
import java.util.Base64

// yona-wiki P3-12(Mercurial 지원) 2라운드 — HgController(HgAuthorizationFilter + hg4j
// HgHttpWireServer)가 실제 `hg` 바이너리로 clone/push까지 되는지 end-to-end로 검증한다.
// GitSmartHttpProtocolIntegrationSpec과 동일한 이유로 webEnvironment=RANDOM_PORT의 실제 임베디드
// 톰캣이 필요하다(MockMvc는 이 컨트롤러 앞의 실제 서블릿 파이프라인/보안 필터 체인을 그대로 태우지
// 않아 이 종단간 경로를 검증할 수 없다).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HgHttpProtocolIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
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
        private val hgBaseDirHolder = Files.createTempDirectory("hg-http-it-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideHgBaseDir(registry: DynamicPropertyRegistry) {
            registry.add("yona.hg.base-dir") { hgBaseDirHolder.absolutePath }
        }

        private fun hgAvailable(): Boolean =
            try {
                ProcessBuilder("hg", "--version").start().waitFor() == 0
            } catch (e: Exception) {
                false
            }
    }

    private fun runHg(dir: File?, vararg args: String): Pair<Int, String> {
        val builder = ProcessBuilder("hg", *args).redirectErrorStream(true)
        if (dir != null) builder.directory(dir)
        val process = builder.start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return exitCode to output
    }

    init {
        describe("실제 hg clone/push 커맨드로 검증하는 HTTP wire protocol (P3-12 2라운드)") {
            it("PUBLIC Mercurial 프로젝트를 실제 hg clone 바이너리로 clone하면 성공하고 커밋된 파일이 있어야 한다") {
                if (!hgAvailable()) return@it

                val owner = userRepository.findByLoginId("hg-http-owner").orElseGet {
                    userRepository.save(User(loginId = "hg-http-owner", name = "Hg HTTP오너", email = "hg-http-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "hg-http-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "hg-http-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "MERCURIAL")
                    )

                val repoDir = File(hgBaseDirHolder, "${project.owner}/${project.name}")
                if (!repoDir.exists()) {
                    repositoryService.getRepository(project).create()
                    File(repoDir, "README.md").writeText("# hg-http-proj")
                    runHg(repoDir, "add", "README.md")
                    val (commitExit, commitOutput) = runHg(
                        repoDir, "--config", "ui.username=hg-http-owner <hg-http-owner@yona.io>",
                        "commit", "-m", "초기 커밋"
                    )
                    withClue(commitOutput) { commitExit shouldBe 0 }
                }

                val cloneUrl = "http://127.0.0.1:$port/hg/${project.owner}/${project.name}"
                val cloneDest = Files.createTempDirectory("hg-http-clone-").toFile()
                try {
                    val (exitCode, output) = runHg(null, "clone", cloneUrl, cloneDest.absolutePath)
                    withClue(output) { exitCode shouldBe 0 }
                    File(cloneDest, "README.md").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            it("멤버가 Basic 인증으로 실제 hg push 바이너리를 통해 push하면 성공하고 서버에 반영돼야 한다") {
                if (!hgAvailable()) return@it

                val pushPassword = "pass123"
                val salt = "saltsalt"
                val owner = userRepository.findByLoginId("hg-http-push-owner").orElseGet {
                    userRepository.save(
                        User(
                            loginId = "hg-http-push-owner", name = "Hg HTTP push오너", email = "hg-http-push-owner@yona.io",
                            password = hashPassword(pushPassword, salt), passwordSalt = salt
                        )
                    )
                }
                val project = projectRepository.findAll().find { it.name == "hg-http-push-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "hg-http-push-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "MERCURIAL")
                    )
                // push(쓰기) 권한을 위해 owner를 manager로 명시적 멤버 등록한다 — HgAuthorizationFilter가
                // RepoAccessPolicy.isMember()로 요구하는 것과 동일한 패턴(GitSmartHttpProtocolIntegrationSpec 참고).
                val managerRole = roleRepository.findById(1L).orElseGet { roleRepository.save(Role(id = 1L, name = "manager", active = true)) }
                if (projectUserRepository.findByProjectIdAndUserId(project.id!!, owner.id!!).isEmpty) {
                    projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))
                }

                val repoDir = File(hgBaseDirHolder, "${project.owner}/${project.name}")
                if (!repoDir.exists()) {
                    repositoryService.getRepository(project).create()
                    File(repoDir, "README.md").writeText("# hg-http-push-proj")
                    runHg(repoDir, "add", "README.md")
                    val (commitExit, commitOutput) = runHg(
                        repoDir, "--config", "ui.username=hg-http-push-owner <hg-http-push-owner@yona.io>",
                        "commit", "-m", "초기 커밋"
                    )
                    withClue(commitOutput) { commitExit shouldBe 0 }
                }

                // real hg 바이너리의 HTTP Basic 인증은 URL에 담긴 계정 비밀번호로만 동작한다(git과
                // 동일한 확립된 사실 — GitSmartHttpProtocolIntegrationSpec 참고).
                val authedUrl = "http://${owner.loginId}:$pushPassword@127.0.0.1:$port/hg/${project.owner}/${project.name}"
                val plainUrl = "http://127.0.0.1:$port/hg/${project.owner}/${project.name}"
                val cloneDest = Files.createTempDirectory("hg-http-push-clone-").toFile()
                val verifyDest = Files.createTempDirectory("hg-http-push-verify-").toFile()
                try {
                    val (cloneExit, cloneOutput) = runHg(null, "clone", authedUrl, cloneDest.absolutePath)
                    withClue(cloneOutput) { cloneExit shouldBe 0 }

                    File(cloneDest, "pushed.txt").writeText("pushed via real hg HTTP push")
                    runHg(cloneDest, "add", "pushed.txt")
                    val (commitExit, commitOutput) = runHg(
                        cloneDest, "--config", "ui.username=hg-http-push-owner <hg-http-push-owner@yona.io>",
                        "commit", "-m", "pushed commit"
                    )
                    withClue(commitOutput) { commitExit shouldBe 0 }

                    val (pushExit, pushOutput) = runHg(cloneDest, "push", authedUrl)
                    withClue(pushOutput) { pushExit shouldBe 0 }

                    // push가 응답만 성공하고 실제로는 반영이 안 된 게 아닌지, 별도 clone으로 재검증한다.
                    val (verifyExit, verifyOutput) = runHg(null, "clone", plainUrl, verifyDest.absolutePath)
                    withClue(verifyOutput) { verifyExit shouldBe 0 }
                    File(verifyDest, "pushed.txt").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                    verifyDest.deleteRecursively()
                }
            }
        }
    }
}
