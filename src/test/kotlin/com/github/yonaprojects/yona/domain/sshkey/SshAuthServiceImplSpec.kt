package com.github.yonaprojects.yona.domain.sshkey

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
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

private const val USER_PUBLIC_KEY =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS tester@example.com"
private const val DEPLOY_PUBLIC_KEY =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx tester2@example.com"

// yona-wiki P3-03 Step6 — 리눅스/맥(internal ssh-auth/ssh-shell)과 윈도우(MINA SSHD) 두 경로가
// 공유하는 SshAuthService 판정 로직 검증. 실제 sshd/네트워크와 무관한 순수 서비스 계층 테스트다.
class SshAuthServiceImplSpec @Autowired constructor(
    private val sshAuthService: SshAuthService,
    private val sshKeyService: SshKeyService,
    private val deployKeyService: DeployKeyService,
    private val sshKeyRepository: SshKeyRepository,
    private val deployKeyRepository: DeployKeyRepository,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    init {
        describe("SshAuthServiceImpl") {
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
            }

            it("알 수 없는 공개키는 authenticate가 null을 반환해야 한다") {
                sshAuthService.authenticate(USER_PUBLIC_KEY) shouldBe null
            }

            it("등록된 사용자 SshKey로는 SshKeyPrincipal을 반환하고 사용 기록을 남겨야 한다") {
                val user = userRepository.save(User(loginId = "chulsoo", name = "철수", email = "chulsoo@example.com"))
                sshKeyService.create(user, "노트북", USER_PUBLIC_KEY)

                val principal = sshAuthService.authenticate(USER_PUBLIC_KEY)

                principal shouldNotBe null
                (principal as SshAuthPrincipal.SshKeyPrincipal).user.loginId shouldBe "chulsoo"
                sshKeyRepository.findAll().first().lastUsedAt shouldNotBe null
            }

            it("등록된 Deploy Key로는 DeployKeyPrincipal을 반환해야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "repo-a"))
                deployKeyService.create(project, "CI 키", DEPLOY_PUBLIC_KEY, readOnly = true)

                val principal = sshAuthService.authenticate(DEPLOY_PUBLIC_KEY)

                principal shouldNotBe null
                (principal as SshAuthPrincipal.DeployKeyPrincipal).deployKey.project?.name shouldBe "repo-a"
            }

            it("잘못된 형식의 SSH_ORIGINAL_COMMAND는 거부되어야 한다") {
                val user = userRepository.save(User(loginId = "chulsoo2", name = "철수2", email = "chulsoo2@example.com"))
                val sshKey = sshKeyService.create(user, "노트북", USER_PUBLIC_KEY)
                val principal = SshAuthPrincipal.SshKeyPrincipal(user, sshKey)

                val result = sshAuthService.authorizeGitCommand(principal, "rm -rf /")

                result.allowed shouldBe false
            }

            it("존재하지 않는 저장소로의 명령은 거부되어야 한다") {
                val user = userRepository.save(User(loginId = "chulsoo3", name = "철수3", email = "chulsoo3@example.com"))
                val sshKey = sshKeyService.create(user, "노트북", USER_PUBLIC_KEY)
                val principal = SshAuthPrincipal.SshKeyPrincipal(user, sshKey)

                val result = sshAuthService.authorizeGitCommand(principal, "git-upload-pack 'gildong/no-such-repo.git'")

                result.allowed shouldBe false
            }

            it("PRIVATE 저장소는 멤버인 사용자만 git-upload-pack이 허용되어야 한다") {
                val role = roleRepository.save(Role(id = 1L, name = "manager", active = true))
                val member = userRepository.save(User(loginId = "member1", name = "멤버1", email = "member1@example.com"))
                val outsider = userRepository.save(User(loginId = "outsider1", name = "외부인1", email = "outsider1@example.com"))
                val project = projectRepository.save(
                    Project(name = "private-repo", owner = "gildong", projectScope = ProjectScope.PRIVATE, createdDate = Instant.now())
                )
                projectUserRepository.save(ProjectUser(user = member, project = project, role = role))

                val memberKey = sshKeyService.create(member, "멤버 키", USER_PUBLIC_KEY)
                val outsiderKey = sshKeyService.create(outsider, "외부인 키", DEPLOY_PUBLIC_KEY)

                val memberResult = sshAuthService.authorizeGitCommand(
                    SshAuthPrincipal.SshKeyPrincipal(member, memberKey),
                    "git-upload-pack 'gildong/private-repo.git'"
                )
                val outsiderResult = sshAuthService.authorizeGitCommand(
                    SshAuthPrincipal.SshKeyPrincipal(outsider, outsiderKey),
                    "git-upload-pack 'gildong/private-repo.git'"
                )

                memberResult.allowed shouldBe true
                memberResult.repoDir?.path?.endsWith("gildong/private-repo.git") shouldBe true
                outsiderResult.allowed shouldBe false
            }

            it("PUBLIC 저장소는 게스트가 아닌 비멤버라도 읽기(git-upload-pack)가 허용되어야 한다") {
                val outsider = userRepository.save(User(loginId = "reader1", name = "읽기전용", email = "reader1@example.com"))
                val project = projectRepository.save(
                    Project(name = "public-repo", owner = "gildong", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false, createdDate = Instant.now())
                )
                val readerKey = sshKeyService.create(outsider, "리더 키", USER_PUBLIC_KEY)

                val result = sshAuthService.authorizeGitCommand(
                    SshAuthPrincipal.SshKeyPrincipal(outsider, readerKey),
                    "git-upload-pack 'gildong/public-repo.git'"
                )

                result.allowed shouldBe true
            }

            it("PUBLIC 저장소라도 비멤버는 쓰기(git-receive-pack)가 거부되어야 한다") {
                val outsider = userRepository.save(User(loginId = "writer1", name = "쓰기시도", email = "writer1@example.com"))
                val project = projectRepository.save(
                    Project(name = "public-repo-w", owner = "gildong", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false, createdDate = Instant.now())
                )
                val key = sshKeyService.create(outsider, "쓰기시도 키", USER_PUBLIC_KEY)

                val result = sshAuthService.authorizeGitCommand(
                    SshAuthPrincipal.SshKeyPrincipal(outsider, key),
                    "git-receive-pack 'gildong/public-repo-w.git'"
                )

                result.allowed shouldBe false
            }

            it("게스트 계정은 PUBLIC 저장소 읽기도 거부되어야 한다") {
                val guest = userRepository.save(User(loginId = "guest1", name = "게스트1", email = "guest1@example.com", isGuest = true))
                val project = projectRepository.save(
                    Project(name = "public-repo-g", owner = "gildong", projectScope = ProjectScope.PUBLIC, isCodeAccessibleMemberOnly = false, createdDate = Instant.now())
                )
                val key = sshKeyService.create(guest, "게스트 키", USER_PUBLIC_KEY)

                val result = sshAuthService.authorizeGitCommand(
                    SshAuthPrincipal.SshKeyPrincipal(guest, key),
                    "git-upload-pack 'gildong/public-repo-g.git'"
                )

                result.allowed shouldBe false
            }

            // 보안 리뷰 항목 — Deploy Key가 repository_id 스코프 밖 저장소에는 절대 접근하지 못해야 한다.
            it("Deploy Key는 스코프 밖 저장소로의 접근이 거부되어야 한다") {
                val ownProject = projectRepository.save(Project(owner = "gildong", name = "own-repo"))
                val otherProject = projectRepository.save(Project(owner = "gildong", name = "other-repo"))
                val issued = deployKeyService.create(ownProject, "own 전용 키", DEPLOY_PUBLIC_KEY, readOnly = false)
                val principal = SshAuthPrincipal.DeployKeyPrincipal(issued.deployKey)

                val ownResult = sshAuthService.authorizeGitCommand(principal, "git-upload-pack 'gildong/own-repo.git'")
                val otherResult = sshAuthService.authorizeGitCommand(principal, "git-upload-pack 'gildong/other-repo.git'")

                ownResult.allowed shouldBe true
                otherResult.allowed shouldBe false
            }

            // 보안 리뷰 항목 — read_only 플래그가 실제로 push를 막아야 한다.
            it("read_only Deploy Key는 git-receive-pack이 거부되어야 한다") {
                val project = projectRepository.save(Project(owner = "gildong", name = "readonly-repo"))
                val issued = deployKeyService.create(project, "읽기전용 키", DEPLOY_PUBLIC_KEY, readOnly = true)
                val principal = SshAuthPrincipal.DeployKeyPrincipal(issued.deployKey)

                val readResult = sshAuthService.authorizeGitCommand(principal, "git-upload-pack 'gildong/readonly-repo.git'")
                val writeResult = sshAuthService.authorizeGitCommand(principal, "git-receive-pack 'gildong/readonly-repo.git'")

                readResult.allowed shouldBe true
                writeResult.allowed shouldBe false
            }
        }
    }
}
