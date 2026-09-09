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

            // 사용자 요청(2026-09-09)으로 checkout/commit 왕복 하나만으로는 부족하다고 판단해
            // 추가한 시나리오. 실제 svn 클라이언트가 자주 쓰는 표준 작업 전체(추가/수정/복사/
            // 이름변경/삭제/되돌리기/속성/두 워킹카피 간 update/log/cat/export)를 하나의
            // 프로젝트에 순서대로 실행해 SVNKit DAVServlet이 각 작업을 실제로 올바르게
            // 처리하는지 확정 검증한다. 매번 서버를 수동으로 띄워 CLI로 확인하는 대신, 이
            // 테스트가 그 역할을 영구적으로 대신한다.
            it("실제 svn 클라이언트로 추가/수정/복사/이름변경/삭제/되돌리기/속성/update/log/cat/export를 전부 왕복해야 한다") {
                if (!svnAvailable()) return@it

                val password = "fullpass123"
                val salt = "fullsalt"
                val owner = userRepository.findByLoginId("svn-http-full-owner").orElseGet {
                    userRepository.save(
                        User(
                            loginId = "svn-http-full-owner", name = "SVN HTTP풀오너", email = "svn-http-full-owner@yona.io",
                            password = hashPassword(password, salt), passwordSalt = salt
                        )
                    )
                }
                val project = projectRepository.findAll().find { it.name == "svn-http-full-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "svn-http-full-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "SUBVERSION")
                    )
                val managerRole = roleRepository.findById(1L).orElseGet { roleRepository.save(Role(id = 1L, name = "manager", active = true)) }
                if (projectUserRepository.findByProjectIdAndUserId(project.id!!, owner.id!!).isEmpty) {
                    projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))
                }

                val repository = repositoryService.getRepository(project)
                if (!repository.getDirectory().exists()) {
                    repository.create()
                }

                fun svn(dir: File?, vararg args: String): String {
                    val (exit, output) = runSvn(dir, "--username", owner.loginId, "--password", password, *args)
                    withClue("svn ${args.joinToString(" ")}\n$output") { exit shouldBe 0 }
                    return output
                }

                val checkoutUrl = "http://127.0.0.1:$port/svn/${project.owner}/${project.name}"
                val wc1 = Files.createTempDirectory("svn-http-full-wc1-").toFile()
                val wc2 = Files.createTempDirectory("svn-http-full-wc2-").toFile()
                val exportDest = Files.createTempDirectory("svn-http-full-export-").toFile()
                try {
                    svn(null, "checkout", checkoutUrl, wc1.absolutePath)

                    // 1) 디렉터리 생성 + 파일 추가 + 최초 커밋
                    svn(wc1, "mkdir", "dir1")
                    File(wc1, "dir1/file1.txt").writeText("v1")
                    svn(wc1, "add", "dir1/file1.txt")
                    svn(wc1, "commit", "-m", "add dir1/file1.txt")

                    // 2) 파일 수정 + 커밋
                    File(wc1, "dir1/file1.txt").writeText("v2")
                    svn(wc1, "commit", "-m", "edit file1.txt")

                    // 3) 되돌리기(revert) — 커밋 전 로컬 변경을 취소하면 서버에 반영되지 않아야 한다
                    File(wc1, "dir1/file1.txt").writeText("uncommitted garbage")
                    svn(wc1, "revert", "dir1/file1.txt")
                    File(wc1, "dir1/file1.txt").readText() shouldBe "v2"

                    // 4) 복사 + 커밋
                    svn(wc1, "copy", "dir1/file1.txt", "dir1/file1-copy.txt")
                    svn(wc1, "commit", "-m", "copy file1.txt")

                    // 5) 이름변경(rename/move) + 커밋
                    svn(wc1, "move", "dir1/file1-copy.txt", "dir1/file1-renamed.txt")
                    svn(wc1, "commit", "-m", "rename file1-copy.txt")
                    File(wc1, "dir1/file1-copy.txt").exists() shouldBe false
                    File(wc1, "dir1/file1-renamed.txt").exists() shouldBe true

                    // 6) 속성(property) 설정 + 커밋 + 조회
                    svn(wc1, "propset", "custom:label", "hello-prop", "dir1/file1.txt")
                    svn(wc1, "commit", "-m", "set custom property")
                    svn(wc1, "propget", "custom:label", "dir1/file1.txt").trim() shouldBe "hello-prop"

                    // 7) 삭제 + 커밋
                    svn(wc1, "delete", "dir1/file1-renamed.txt")
                    svn(wc1, "commit", "-m", "delete file1-renamed.txt")
                    File(wc1, "dir1/file1-renamed.txt").exists() shouldBe false

                    // 8) 두 번째 워킹카피에서 update로 반영 확인
                    svn(null, "checkout", checkoutUrl, wc2.absolutePath)
                    File(wc1, "dir1/file2.txt").writeText("added for update test")
                    svn(wc1, "add", "dir1/file2.txt")
                    svn(wc1, "commit", "-m", "add file2.txt for update test")
                    File(wc2, "dir1/file2.txt").exists() shouldBe false
                    svn(wc2, "update")
                    File(wc2, "dir1/file2.txt").exists() shouldBe true
                    File(wc2, "dir1/file2.txt").readText() shouldBe "added for update test"

                    // 9) 로그(log -v)에 지금까지의 경로 변경이 실제로 기록됐는지 확인
                    val logOutput = svn(null, "log", "-v", checkoutUrl)
                    withClue(logOutput) {
                        logOutput.contains("dir1/file1.txt") shouldBe true
                        logOutput.contains("dir1/file2.txt") shouldBe true
                    }

                    // 10) cat으로 과거 리비전(r1, 수정 전 v1) 내용을 그대로 읽어올 수 있는지 확인
                    val r1Content = svn(null, "cat", "-r", "1", "$checkoutUrl/dir1/file1.txt")
                    r1Content shouldBe "v1"

                    // 11) export로 워킹카피 메타데이터(.svn) 없이 순수 파일 트리를 받아올 수 있는지 확인
                    exportDest.delete()
                    svn(null, "export", checkoutUrl, exportDest.absolutePath)
                    File(exportDest, "dir1/file1.txt").exists() shouldBe true
                    File(exportDest, "dir1/file1.txt").readText() shouldBe "v2"
                    File(exportDest, ".svn").exists() shouldBe false
                } finally {
                    wc1.deleteRecursively()
                    wc2.deleteRecursively()
                    exportDest.deleteRecursively()
                }
            }
        }
    }
}
