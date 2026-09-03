package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.eclipse.jgit.api.Git
import org.jsoup.Jsoup
import org.eclipse.jgit.transport.RefSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.io.File
import java.nio.file.Files
import java.util.UUID

// legacy git/partial_merge_result.scala.html + PullRequestApp.mergeResult() 대응 (#178, TASK-0257).
// PullRequestViewController#mergeResult()가 실제 물리 git 저장소를 대상으로 JGit 병합을 시도해
// PullRequestService.previewMerge()를 호출하고, 그 결과가 실제 Thymeleaf 렌더링(HTTP 응답 HTML)에
// 커밋 목록/충돌 여부로 나타나는지까지 end-to-end로 검증한다(단위 mockk 테스트로는 실제 JGit 병합
// 로직과 프래그먼트 렌더링을 함께 검증할 수 없다).
@Transactional
class PullRequestMergeResultTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val repositoryService: RepositoryService
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("PR 생성/수정 화면의 mergeResult AJAX 프리뷰 렌더링") {
            // 이 스펙은 이름이 고유한(mr- 접두) 프로젝트/유저만 만들고, 클래스에 붙은 @Transactional이
            // 각 테스트 종료 시 롤백을 보장하므로 다른 스펙의 데이터를 건드리는 전역 deleteAll()은
            // 쓰지 않는다(공유 테스트 DB에서 무관한 프로젝트를 지우다 FK 위반이 날 수 있음).

            fun createCommit(bareRepoDir: File, branch: String, filePath: String, content: String, commitMsg: String) {
                val tempWorkingDir = Files.createTempDirectory("yona-test-mr").toFile()
                try {
                    val git = Git.init().setDirectory(tempWorkingDir).call()
                    val config = git.repository.config
                    config.setString("remote", "origin", "url", bareRepoDir.absolutePath)
                    config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
                    config.save()

                    try {
                        git.fetch().setRemote("origin").call()
                        val ref = git.repository.resolve("refs/remotes/origin/$branch")
                        if (ref != null) {
                            git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/$branch").call()
                        } else {
                            val originMaster = git.repository.resolve("refs/remotes/origin/master")
                            if (originMaster != null) {
                                git.checkout().setCreateBranch(true).setName(branch).setStartPoint("origin/master").call()
                            }
                        }
                    } catch (e: Exception) {
                        // 빈 저장소인 경우 checkout 생략
                    }

                    val file = File(tempWorkingDir, filePath)
                    file.parentFile.mkdirs()
                    file.writeText(content)

                    git.add().addFilepattern(filePath).call()
                    git.commit().setSign(false).setAuthor("tester", "tester@yona.io").setMessage(commitMsg).call()
                    git.push().setRemote("origin").setRefSpecs(RefSpec("HEAD:refs/heads/$branch")).setForce(true).call()

                    git.repository.close()
                    git.close()
                } finally {
                    tempWorkingDir.deleteRecursively()
                }
            }

            fun setUpProjectWithMember(uniqueSuffix: String): Pair<Project, User> {
                val owner = userRepository.save(User(loginId = "mr-owner-$uniqueSuffix", name = "MR소유자", email = "mr-owner-$uniqueSuffix@yona.io"))
                val project = projectRepository.save(
                    Project(name = "mr-repo-$uniqueSuffix", owner = "mr-org-$uniqueSuffix", vcs = "GIT", projectScope = ProjectScope.PUBLIC)
                )
                projectUserRepository.save(ProjectUser(user = owner, project = project, role = Role(id = RoleType.MANAGER.roleType)))
                repositoryService.getRepository(project).create()
                return project to owner
            }

            fun authOf(user: User) = user(
                YonaUserDetails(
                    id = user.id ?: 0L,
                    loginId = user.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("충돌 없는 두 브랜치를 지정하면 커밋 목록이 실제로 렌더링되어야 한다") {
                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                val (project, owner) = setUpProjectWithMember(uniqueSuffix)
                val bareDir = repositoryService.getRepository(project).getDirectory()

                createCommit(bareDir, "master", "base.txt", "base content", "Initial base commit")
                createCommit(bareDir, "feature-safe", "feature.txt", "feature content", "Safe feature commit")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/mergeResult")
                        .param("fromBranch", "refs/heads/feature-safe")
                        .param("toBranch", "refs/heads/master")
                        .with(authOf(owner))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "id=\"mergeResult\""
                body shouldContain "data-commits=\"1\""
                body shouldContain "Safe feature commit"
                body shouldNotContain "data-conflict=\"true\""
            }

            it("충돌하는 두 브랜치를 지정하면 data-conflict=true로 렌더링되어야 한다") {
                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                val (project, owner) = setUpProjectWithMember(uniqueSuffix)
                val bareDir = repositoryService.getRepository(project).getDirectory()

                createCommit(bareDir, "master", "conflict.txt", "base\ncommon line", "Initial base commit")
                // feature-conflict를 공통 조상(master가 아직 갈라지기 전) 시점에서 분기시켜야 실제
                // 3-way 충돌이 재현된다 - master를 먼저 앞으로 진행시키면 feature-conflict가 그 위에서
                // 시작해(createCommit의 fallback이 항상 "현재" origin/master 팁에서 분기) 단순
                // fast-forward가 되어버린다.
                createCommit(bareDir, "feature-conflict", "conflict.txt", "base\nsource edit", "Source edits conflict file")
                createCommit(bareDir, "master", "conflict.txt", "base\ntarget edit", "Target edits conflict file")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/mergeResult")
                        .param("fromBranch", "refs/heads/feature-conflict")
                        .param("toBranch", "refs/heads/master")
                        .with(authOf(owner))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "data-conflict=\"true\""
            }

            it("변경 사항이 없으면 안내 메시지를 렌더링해야 한다") {
                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                val (project, owner) = setUpProjectWithMember(uniqueSuffix)
                val bareDir = repositoryService.getRepository(project).getDirectory()

                createCommit(bareDir, "master", "only.txt", "only content", "Only commit")

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/mergeResult")
                        .param("fromBranch", "refs/heads/master")
                        .param("toBranch", "refs/heads/master")
                        .with(authOf(owner))
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "data-commits=\"0\""
            }

            it("프로젝트 멤버가 아닌 로그인 사용자는 403을 받아야 한다") {
                val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                val (project, _) = setUpProjectWithMember(uniqueSuffix)
                val outsider = userRepository.save(User(loginId = "mr-outsider-$uniqueSuffix", name = "비멤버", email = "mr-outsider-$uniqueSuffix@yona.io"))

                mockMvc.perform(
                    get("/${project.owner}/${project.name}/pull/mergeResult")
                        .param("fromBranch", "refs/heads/master")
                        .param("toBranch", "refs/heads/master")
                        .with(authOf(outsider))
                ).andExpect(view().name("error/forbidden"))
            }

            // yona Project.getAssociationProjects()/PullRequestApp.getSelectedProject() 대응
            // (그룹11 #168, TASK-0263) — fork인 프로젝트에서 PR 생성 화면을 열면 원본 프로젝트가
            // toProject 기본값이 되고, fromProjectId/toProjectId 쿼리로 서로 다른 fork 저장소 간
            // 커밋을 실제로 병합 미리보기할 수 있어야 한다.
            describe("fork 프로젝트 간(cross-fork) PR — Project.associationProjects") {
                it("fork 프로젝트에서 PR 생성 화면을 열면 원본이 toProject 기본값이고, association 목록에 둘 다 포함되어야 한다") {
                    val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                    val (originProject, owner) = setUpProjectWithMember(uniqueSuffix)
                    createCommit(repositoryService.getRepository(originProject).getDirectory(), "master", "base.txt", "base", "origin base commit")

                    val forkProject = projectRepository.save(
                        Project(
                            name = "mr-fork-$uniqueSuffix", owner = "mr-fork-org-$uniqueSuffix", vcs = "GIT",
                            projectScope = ProjectScope.PUBLIC, originalProject = originProject
                        )
                    )
                    projectUserRepository.save(ProjectUser(user = owner, project = forkProject, role = Role(id = RoleType.MANAGER.roleType)))
                    repositoryService.getRepository(forkProject).create()
                    createCommit(repositoryService.getRepository(forkProject).getDirectory(), "master", "base.txt", "base", "fork base commit")

                    val body = mockMvc.perform(
                        get("/${forkProject.owner}/${forkProject.name}/pull/new").with(authOf(owner))
                    ).andExpect(status().isOk).andReturn().response.contentAsString

                    body shouldContain "value=\"${forkProject.id}\""
                    body shouldContain "value=\"${originProject.id}\""
                    // toProjectId select에서 origin이 selected 상태여야 한다(기본값 전환)
                    val doc = Jsoup.parse(body)
                    doc.select("#toProjectId option[value='${originProject.id}']").hasAttr("selected") shouldBe true
                }

                it("fromProjectId/toProjectId를 서로 다른 fork로 지정하면 그 두 저장소 간 커밋 미리보기가 실제로 계산되어야 한다") {
                    val uniqueSuffix = System.currentTimeMillis().toString() + "-" + UUID.randomUUID().toString().take(6)
                    val (originProject, owner) = setUpProjectWithMember(uniqueSuffix)
                    createCommit(repositoryService.getRepository(originProject).getDirectory(), "master", "base.txt", "base", "origin base commit")

                    val forkProject = projectRepository.save(
                        Project(
                            name = "mr-fork2-$uniqueSuffix", owner = "mr-fork2-org-$uniqueSuffix", vcs = "GIT",
                            projectScope = ProjectScope.PUBLIC, originalProject = originProject
                        )
                    )
                    projectUserRepository.save(ProjectUser(user = owner, project = forkProject, role = Role(id = RoleType.MANAGER.roleType)))
                    repositoryService.getRepository(forkProject).create()
                    createCommit(repositoryService.getRepository(forkProject).getDirectory(), "master", "base.txt", "base", "fork base commit")
                    createCommit(repositoryService.getRepository(forkProject).getDirectory(), "feature", "feature.txt", "feature", "fork feature commit")

                    val body = mockMvc.perform(
                        get("/${forkProject.owner}/${forkProject.name}/pull/mergeResult")
                            .param("fromProjectId", forkProject.id.toString())
                            .param("toProjectId", originProject.id.toString())
                            .param("fromBranch", "refs/heads/feature")
                            .param("toBranch", "refs/heads/master")
                            .with(authOf(owner))
                    ).andExpect(status().isOk).andReturn().response.contentAsString

                    // fork 저장소가 origin에서 실제로 clone된 게 아니라 이 테스트에서 독립적으로 만든
                    // 별도 bare repo라 fork의 커밋 2개(base+feature) 전부가 origin에는 없는 커밋으로
                    // 계산된다 — 서로 다른 물리 저장소 간 diff가 실제로 계산됐음을 확인하는 게 핵심.
                    body shouldContain "id=\"mergeResult\""
                    body shouldContain "data-commits=\"2\""
                    body shouldContain "fork feature commit"
                    body shouldContain "fork base commit"
                }
            }
        }
    }
}
