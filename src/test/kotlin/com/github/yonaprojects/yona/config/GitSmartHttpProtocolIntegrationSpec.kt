package com.github.yonaprojects.yona.config

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
import com.github.yonaprojects.yona.domain.vcs.BareCommit
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
import java.nio.file.Files
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

// TASK-0416 회귀 방지: yona-cli의 골든패스를 실제 `git clone` 커맨드로 재현하다가 발견한 버그.
//
// 근본원인: GitServletConfig가 만드는 디스패처 서블릿은 JGit의 GitServlet(내부 GitFilter)을
// 컨테이너에 등록하지 않고 이 디스패처의 service()에서 gitServlet.service(req,res)를 수동으로
// 호출만 해왔다. 그런데 GitServlet(MetaServlet 상속)은 init(ServletConfig) 시점에 비로소
// GitFilter가 upload-pack/receive-pack/info-refs 등 URL 파이프라인(bindings)을 등록한다.
// init()이 한 번도 호출되지 않으면 파이프라인이 텅 빈 채로 남아, 모든 요청이 첫 매치 실패로
// 기본 체인(chain.doFilter)에 떨어져 예외 없이 조용히 404를 반환한다 — RepositoryResolver까지
// 도달하지도 못했다. mockk 기반 GitServletConfigSpec은 servlet.service() 호출을 통째로
// try/catch로 감싸고 결과를 검증하지 않아 이 문제를 놓쳤었다.
//
// 이 스펙은 webEnvironment=RANDOM_PORT로 실제 임베디드 톰캣을 띄워 ServletRegistrationBean이
// 정상적인 서블릿 생명주기(init 포함)를 타게 하고, 실제 `git` 바이너리로 스마트 HTTP 프로토콜
// clone까지 재현해 회귀를 고정한다(MockMvc는 DispatcherServlet만 태우고 이 raw 서블릿을
// 우회하므로 이 버그를 검증할 수 없다).
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitSmartHttpProtocolIntegrationSpec @Autowired constructor(
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val repositoryService: RepositoryService,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
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
        private val gitBaseDirHolder = Files.createTempDirectory("git-smart-http-it-").toFile()

        @JvmStatic
        @DynamicPropertySource
        fun overrideGitBaseDir(registry: DynamicPropertyRegistry) {
            registry.add("yona.git.base-dir") { gitBaseDirHolder.absolutePath }
        }
    }

    init {
        describe("실제 git clone 커맨드로 검증하는 스마트 HTTP 프로토콜 (TASK-0416)") {
            it("PUBLIC 프로젝트를 실제 git clone 바이너리로 clone하면 성공하고 커밋된 파일이 있어야 한다") {
                val owner = userRepository.findByLoginId("git-proto-owner").orElseGet {
                    userRepository.save(User(loginId = "git-proto-owner", name = "깃프로토콜오너", email = "git-proto-owner@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "git-proto-proj" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "git-proto-proj", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                    )

                val gitDir = File(gitBaseDirHolder, "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                    BareCommit(project, owner, gitBaseDirHolder.absolutePath).commitTextFile(
                        "README.md", "# git-proto-proj", "초기 커밋"
                    )
                }

                val cloneUrl = "http://127.0.0.1:$port/git/${project.owner}/${project.name}.git"
                val cloneDest = Files.createTempDirectory("git-smart-http-clone-").toFile()

                try {
                    val process = ProcessBuilder("git", "clone", cloneUrl, cloneDest.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    val exitCode = process.waitFor()

                    withClue(output) { exitCode shouldBe 0 }
                    File(cloneDest, "README.md").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                }
            }

            // yona-wiki P3-02 12라운드(2026-09-01) 회귀 방지 — 실서버+실 CLI로 PR merge 충돌 경로를
            // 실측하려다 발견한 버그. GitServletConfig.gitServletRegistrationBean()의
            // setRepositoryResolver { _, name -> File(gitBaseDir, name) ... }가 JGit이 URL에서
            // 파싱해 넘겨주는 name을 그대로(가공 없이) 파일 경로로 써서, 클라이언트가 ".git" 접미어
            // 없이 clone/push URL을 쓰면(GitHub 등에서 흔한 방식, 예: "git clone
            // http://host/git/owner/repo") 실제 bare 저장소 디렉터리(항상 "owner/name.git"로
            // 생성됨 — GitServiceImpl.createRepository() 등 이 코드베이스 전역의 관례)가 아닌
            // 존재하지 않는 "owner/repo" 경로로 잘못 resolve된다. clone(읽기)은 존재하지 않는
            // 저장소를 빈 저장소처럼 조용히(에러 없이) 취급해 성공한 것처럼 보이지만, push(쓰기)는
            // ObjectDirectoryPackParser.parse()가 그 존재하지 않는 objects 디렉터리에 임시
            // 팩 파일을 만들려다 IOException(ENOENT)을 던져 "unpacker error"로 거절된다(실측
            // 재현: 실서버+실 git 바이너리로 100% 재현 확인).
            it("clone/push URL에 \".git\" 접미어가 없어도 실제 git push 바이너리로 push가 성공해야 한다") {
                val pushPassword = "pass123"
                val salt = "saltsalt"
                val owner = userRepository.findByLoginId("git-proto-owner-nosuffix").orElseGet {
                    userRepository.save(
                        User(
                            loginId = "git-proto-owner-nosuffix", name = "깃프로토콜오너2", email = "git-proto-owner-nosuffix@yona.io",
                            password = hashPassword(pushPassword, salt), passwordSalt = salt
                        )
                    )
                }
                val project = projectRepository.findAll().find { it.name == "git-proto-proj-nosuffix" && it.owner == owner.loginId }
                    ?: projectRepository.save(
                        Project(name = "git-proto-proj-nosuffix", owner = owner.loginId, projectScope = ProjectScope.PUBLIC, vcs = "GIT")
                    )
                // push(쓰기) 권한을 위해 owner를 manager로 명시적 멤버 등록한다(GitAuthorizationFilter가
                // 요구하는 것과 동일한 패턴, GitAuthorizationFilterIntegrationSpec 참고).
                val managerRole = roleRepository.findById(1L).orElseGet { roleRepository.save(Role(id = 1L, name = "manager", active = true)) }
                if (projectUserRepository.findByProjectIdAndUserId(project.id!!, owner.id!!).isEmpty) {
                    projectUserRepository.save(ProjectUser(user = owner, project = project, role = managerRole))
                }

                val gitDir = File(gitBaseDirHolder, "${project.owner}/${project.name}.git")
                if (!gitDir.exists()) {
                    repositoryService.getRepository(project).create()
                }

                // ".git" 접미어를 의도적으로 뺀 URL — GitHub 등에서 흔히 쓰이는 축약 표기다.
                // git push의 HTTP Basic Auth는 API 토큰이 아니라 실제 계정 비밀번호로만 동작한다
                // (yona-wiki 11라운드 로그와 동일한 확립된 사실).
                val noSuffixUrl = "http://${owner.loginId}:$pushPassword@127.0.0.1:$port/git/${project.owner}/${project.name}"
                val cloneDest = Files.createTempDirectory("git-smart-http-clone-nosuffix-").toFile()
                val verifyDest = Files.createTempDirectory("git-smart-http-verify-nosuffix-").toFile()

                try {
                    val cloneProcess = ProcessBuilder("git", "clone", noSuffixUrl, cloneDest.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val cloneOutput = cloneProcess.inputStream.bufferedReader().readText()
                    withClue(cloneOutput) { cloneProcess.waitFor() shouldBe 0 }

                    File(cloneDest, "committed.txt").writeText("committed via push without .git suffix")
                    fun run(vararg cmd: String): String {
                        val p = ProcessBuilder(*cmd).directory(cloneDest).redirectErrorStream(true).start()
                        val out = p.inputStream.bufferedReader().readText()
                        withClue(out) { p.waitFor() shouldBe 0 }
                        return out
                    }
                    run("git", "config", "user.email", "nosuffix@yona.io")
                    run("git", "config", "user.name", "nosuffix")
                    run("git", "add", "committed.txt")
                    run("git", "commit", "-m", "push without .git suffix")
                    run("git", "branch", "-M", "main")

                    val pushProcess = ProcessBuilder("git", "push", noSuffixUrl, "main")
                        .directory(cloneDest)
                        .redirectErrorStream(true)
                        .start()
                    val pushOutput = pushProcess.inputStream.bufferedReader().readText()
                    withClue(pushOutput) { pushProcess.waitFor() shouldBe 0 }

                    // push가 응답 코드만 성공하고 실제로는 엉뚱한 경로에 쓴 게 아닌지, 저장소가
                    // 실제로 그 커밋을 받았는지까지 별도 clone으로 검증한다.
                    val verifyProcess = ProcessBuilder("git", "clone", "http://127.0.0.1:$port/git/${project.owner}/${project.name}.git", verifyDest.absolutePath)
                        .redirectErrorStream(true)
                        .start()
                    val verifyOutput = verifyProcess.inputStream.bufferedReader().readText()
                    withClue(verifyOutput) { verifyProcess.waitFor() shouldBe 0 }
                    File(verifyDest, "committed.txt").exists() shouldBe true
                } finally {
                    cloneDest.deleteRecursively()
                    verifyDest.deleteRecursively()
                }
            }
        }
    }
}
