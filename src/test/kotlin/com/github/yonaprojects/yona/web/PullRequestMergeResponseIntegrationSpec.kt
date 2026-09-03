package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestCommitRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File
import java.nio.file.Files
import java.time.Instant

// TASK-0424(P3-02 11라운드) — 실서버(H2 프로파일)+실 yona-cli로 "pr merge/close/reopen"
// 골든패스를 실측하다가 발견한 2개 결함을 함께 고정한다.
//
// 1) POST .../pull-requests/{number}/merge(레거시 PullRequestController.mergePullRequest(),
//    /api/v1/... 경로는 PullRequestApiController.merge()가 그대로 위임)가 성공 시 raw
//    PullRequestMergeResult(내부에 raw PullRequest -> contributor: User)를 그대로 반환해
//    User.projectUsers 양방향 연관을 따라가며 password/passwordSalt가 노출됐다(실측: curl로
//    60KB 응답에서 "password" 값 확인, 버그8/"project edit"와 동일한 근본원인).
// 2) 이미 MERGED된 PR을 다시 merge/close/reopen하면 가드가 없어 매번 새 병합 커밋이 쌓이거나
//    (재머지) 이미 병합된 PR이 CLOSED/OPEN을 오갔다(실측: 동일 PR로 `pr merge`를 두 번 호출하니
//    대상 브랜치에 병합 커밋이 중복 2개) — 이제 IllegalArgumentException -> 400으로 거절된다.
class PullRequestMergeResponseIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val pullRequestRepository: PullRequestRepository,
    private val pullRequestCommitRepository: PullRequestCommitRepository,
    private val pullRequestEventRepository: PullRequestEventRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private val ownerName = "pr-merge-resp-owner"
    private val projName = "pr-merge-resp-repo"

    // 순환 직렬화에 빠지면 실측(curl)에서 60KB를 넘겼다.
    private val maxSaneResponseLength = 10_000

    private lateinit var owner: User
    private lateinit var project: Project

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                pullRequestRepository.findByToProject(existing).forEach { pr ->
                    pullRequestCommitRepository.findByPullRequest(pr).forEach { pullRequestCommitRepository.delete(it) }
                    pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pr).forEach { pullRequestEventRepository.delete(it) }
                    pullRequestRepository.delete(pr)
                }
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                try { repositoryService.getRepository(existing).delete() } catch (e: Exception) {}
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }

            owner = userRepository.save(
                User(
                    loginId = ownerName,
                    name = "머지응답소유자",
                    email = "$ownerName@example.com",
                    password = "merge-secret-hash",
                    passwordSalt = "merge-secret-salt"
                )
            )
            project = projectRepository.save(Project(owner = ownerName, name = projName, vcs = "GIT"))
            repositoryService.getRepository(project).create()

            // mergePullRequest()의 checkWritePermission()은 ProjectUser 멤버십(또는 그룹 접근)만
            // 확인한다 — PR contributor라는 사실만으로는 통과하지 못하므로 owner를 명시적으로
            // MANAGER 멤버로 등록한다.
            val role = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            projectUserRepository.save(ProjectUser(user = owner, project = project, role = role))
        }

        afterTest {
            try { repositoryService.getRepository(project).delete() } catch (e: Exception) {}
        }

        fun ownerDetails() = YonaUserDetails(
            id = owner.id!!,
            loginId = ownerName,
            passwordVal = "hashed",
            passwordSalt = "salt",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        fun pushFile(bareDir: File, branch: String, fileName: String, content: String, startPoint: String? = null) {
            val workDir = Files.createTempDirectory("pr-merge-resp-work").toFile()
            try {
                val git = Git.init().setDirectory(workDir).call()
                git.repository.config.setString("remote", "origin", "url", bareDir.absolutePath)
                git.repository.config.save()
                if (startPoint != null) {
                    git.fetch().setRemote("origin")
                        .setRefSpecs(RefSpec("refs/heads/$startPoint:refs/remotes/origin/$startPoint")).call()
                    git.checkout().setCreateBranch(true).setName(branch)
                        .setStartPoint("origin/$startPoint").call()
                }
                File(workDir, fileName).writeText(content)
                git.add().addFilepattern(fileName).call()
                git.commit().setSign(false).setAuthor("tester", "tester@yona.io").setMessage("commit $fileName").call()
                git.push().setRemote("origin").setRefSpecs(RefSpec("HEAD:refs/heads/$branch")).setForce(true).call()
                git.close()
            } finally {
                workDir.deleteRecursively()
            }
        }

        fun setUpMergeablePr(): PullRequest {
            val bareDir = repositoryService.getRepository(project).getDirectory()
            pushFile(bareDir, "master", "base.txt", "base content")
            pushFile(bareDir, "feature", "feature.txt", "feature content", startPoint = "master")
            return pullRequestRepository.save(
                PullRequest(
                    title = "머지 응답 검증 PR",
                    body = "본문",
                    toProject = project,
                    fromProject = project,
                    toBranch = "master",
                    fromBranch = "feature",
                    contributor = owner,
                    created = Instant.now(),
                    state = State.OPEN,
                    number = 1
                )
            )
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/merge") {
            it("순환 직렬화 없이 작은 JSON으로 응답하고 password/passwordSalt를 노출하지 않아야 한다") {
                val pr = setUpMergeablePr()

                val result = mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/merge")
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "password"
                body shouldNotContain "passwordSalt"
                body shouldNotContain "merge-secret-hash"
                body shouldNotContain "projectUsers"
            }

            it("이미 MERGED된 PR을 다시 머지하면 400으로 거절되고 병합 커밋이 중복 생성되지 않아야 한다") {
                val pr = setUpMergeablePr()
                val bareDir = repositoryService.getRepository(project).getDirectory()

                mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/merge")
                        .with(user(ownerDetails()))
                ).andReturn().response.status shouldBe 200

                val refAfterFirstMerge = Git.open(bareDir).use { it.repository.resolve("master")!!.name }

                val secondResult = mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/merge")
                        .with(user(ownerDetails()))
                ).andReturn()

                secondResult.response.status shouldBe 400
                Git.open(bareDir).use { git ->
                    git.repository.resolve("master")!!.name shouldBe refAfterFirstMerge
                }
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/state") {
            it("이미 MERGED된 PR을 close/reopen하려 하면 400으로 거절돼야 한다") {
                val pr = setUpMergeablePr()

                mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/merge")
                        .with(user(ownerDetails()))
                ).andReturn().response.status shouldBe 200

                mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/state")
                        .param("state", "CLOSED")
                        .with(user(ownerDetails()))
                ).andReturn().response.status shouldBe 400

                mockMvc.perform(
                    post("/api/projects/${project.id}/pullrequests/${pr.number}/state")
                        .param("state", "OPEN")
                        .with(user(ownerDetails()))
                ).andReturn().response.status shouldBe 400
            }
        }
    }
}
