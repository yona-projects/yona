package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.AttachLabelResult
import com.github.yonaprojects.yona.domain.project.Label
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.PushedBranch
import com.github.yonaprojects.yona.domain.vcs.PushedBranchRepository
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.TitleHeadService
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.project.TitleHead
import org.springframework.test.web.servlet.result.MockMvcResultHandlers
import com.github.yonaprojects.yona.domain.user.UserState
import org.springframework.data.domain.PageImpl
import com.github.yonaprojects.yona.domain.project.ProjectTransfer

class ProjectControllerSpec : DescribeSpec({
    val projectService = mockk<ProjectService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val pushedBranchRepository = mockk<PushedBranchRepository>()
    val titleHeadService = mockk<TitleHeadService>()
    val issueLabelRepository = mockk<IssueLabelRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val userRepositoryForAccessControl = mockk<UserRepository>()
    val organizationRepositoryForAccessControl = mockk<OrganizationRepository>()
    val issueRepositoryForAccessControl = mockk<IssueRepository>()
    val postingRepositoryForAccessControl = mockk<PostingRepository>()
    val reviewCommentRepositoryForAccessControl = mockk<ReviewCommentRepository>()
    val commitCommentRepositoryForAccessControl = mockk<CommitCommentRepository>()
    val milestoneRepositoryForAccessControl = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepositoryForAccessControl, organizationRepositoryForAccessControl,
        issueRepositoryForAccessControl, postingRepositoryForAccessControl,
        reviewCommentRepositoryForAccessControl, commitCommentRepositoryForAccessControl,
        milestoneRepositoryForAccessControl
    )

    val projectController = ProjectController(
        projectService,
        projectRepository,
        projectUserRepository,
        userRepository,
        pushedBranchRepository,
        accessControl,
        titleHeadService,
        issueLabelRepository
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(projectController).build()

    beforeTest {
        clearMocks(projectService, projectRepository, projectUserRepository, userRepository, pushedBranchRepository, titleHeadService, issueLabelRepository)
    }

    describe("ProjectController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", owner = "owner", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerRole = Role(id = RoleType.MANAGER.roleType)
        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = managerRole)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")

        describe("PUT /api/projects/{projectId}") {
            it("MANAGER 권한이 있다면 200 OK를 반환하고 설정을 갱신해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.updateProject(1L, any()) } returns project

                val jsonContent = """
                    {
                        "overview": "새로운 설명",
                        "projectScope": "PUBLIC",
                        "isCodeAccessibleMemberOnly": false,
                        "isUsingReviewerCount": false,
                        "defaultReviewerCount": 2,
                        "defaultBranch": "refs/heads/master",
                        "isCodeEnabled": true,
                        "isIssueEnabled": true,
                        "isPullRequestEnabled": true,
                        "isReviewEnabled": true,
                        "isMilestoneEnabled": true,
                        "isBoardEnabled": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify { projectService.updateProject(1L, any()) }
            }

            it("name 필드를 포함해 요청하면 UpdateProjectParam.name으로 그대로 전달해야 한다 (P1-144, UI는 나중에 붙일 예정이라 API만 우선 이식)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every {
                    projectService.updateProject(1L, match { it.name == "new-project-name" })
                } returns project

                val jsonContent = """
                    {
                        "name": "new-project-name",
                        "overview": "새로운 설명",
                        "projectScope": "PUBLIC",
                        "isCodeAccessibleMemberOnly": false,
                        "isUsingReviewerCount": false,
                        "defaultReviewerCount": 2,
                        "defaultBranch": "refs/heads/master",
                        "isCodeEnabled": true,
                        "isIssueEnabled": true,
                        "isPullRequestEnabled": true,
                        "isReviewEnabled": true,
                        "isMilestoneEnabled": true,
                        "isBoardEnabled": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify { projectService.updateProject(1L, match { it.name == "new-project-name" }) }
            }

            it("서비스가 이름 중복 예외를 던지면 400 Bad Request로 응답해야 한다 (P1-144)") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every {
                    projectService.updateProject(1L, match { it.name == "taken-name" })
                } throws IllegalArgumentException("이미 사용 중인 프로젝트 이름입니다.")

                val jsonContent = """
                    {
                        "name": "taken-name",
                        "overview": "설명",
                        "projectScope": "PUBLIC",
                        "isCodeAccessibleMemberOnly": false,
                        "isUsingReviewerCount": false,
                        "defaultReviewerCount": 1,
                        "isCodeEnabled": true,
                        "isIssueEnabled": true,
                        "isPullRequestEnabled": true,
                        "isReviewEnabled": true,
                        "isMilestoneEnabled": true,
                        "isBoardEnabled": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }

            it("MANAGER 권한이 없다면 403 Forbidden을 반환해야 한다") {
                val memberRole = Role(id = RoleType.MEMBER.roleType)
                val memberProjectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)

                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(memberProjectUser)

                val jsonContent = """
                    {
                        "overview": "새로운 설명",
                        "projectScope": "PUBLIC"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }
        }

        describe("DELETE /api/projects/{projectId}") {
            it("소유자(owner) 본인이라면 200 OK를 반환하고 프로젝트를 제거해야 한다") {
                val ownerUser = User(id = 20L, loginId = "owner", name = "소유자")
                val ownerAuth = UsernamePasswordAuthenticationToken("owner", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("owner") } returns Optional.of(ownerUser)
                every { projectService.deleteProject(1L) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1")
                        .principal(ownerAuth)
                )
                    .andExpect(status().isOk)

                verify { projectService.deleteProject(1L) }
            }
        }

        describe("GET/POST/DELETE /api/{owner}/{projectName}/labels (P1-13)") {
            val publicProject = Project(id = 30L, name = "pub", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val privateProject = Project(id = 31L, name = "priv", owner = "owner", projectScope = ProjectScope.PRIVATE)
            val memberRole = Role(id = RoleType.MEMBER.roleType)
            val memberProjectUser = ProjectUser(id = 200L, user = user, project = privateProject, role = memberRole)

            it("공개 프로젝트는 비회원도 라벨 목록을 조회할 수 있어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "pub") } returns Optional.of(publicProject)
                every { projectService.getProjectLabels(30L) } returns setOf(Label(id = 1L, category = "os", name = "linux"))

                mockMvc.perform(get("/api/owner/pub/labels"))
                    .andExpect(status().isOk)
            }

            it("라벨 목록 조회 시 프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(get("/api/owner/unknown/labels"))
                    .andExpect(status().isNotFound)
            }

            it("라벨 붙이기 시 프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(post("/api/owner/unknown/labels").param("name", "linux").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // legacy controllers/api/ProjectApi.java newLabel() 경로 별칭 (P2-59)
            it("legacy 경로 /-_-api/v1/owners/{owner}/projects/{projectName}/labels로도 동일하게 동작해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(
                    post("/-_-api/v1/owners/owner/projects/unknown/labels").param("name", "linux").principal(userAuth)
                ).andExpect(status().isNotFound)
            }

            it("라벨 붙이기 시 인증되지 않으면 401을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)

                mockMvc.perform(post("/api/owner/priv/labels").param("name", "linux"))
                    .andExpect(status().isUnauthorized)
            }

            it("이미 존재하는 라벨에 새로 붙는 경우(isCreated=false, isAttached=true) 200 OK와 라벨을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns true
                every { projectService.attachLabel(31L, "os", "linux") } returns
                    AttachLabelResult(Label(id = 1L, category = "os", name = "linux"), isCreated = false, isAttached = true)

                mockMvc.perform(
                    post("/api/owner/priv/labels")
                        .param("category", "os")
                        .param("name", "linux")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("라벨 떼기 시 프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(delete("/api/owner/unknown/labels/1").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("라벨 떼기 시 인증되지 않으면 401을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)

                mockMvc.perform(delete("/api/owner/priv/labels/1"))
                    .andExpect(status().isUnauthorized)
            }

            it("비공개 프로젝트는 비회원이 조회하면 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)

                mockMvc.perform(get("/api/owner/priv/labels"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트 멤버(MEMBER 권한도 포함)는 라벨을 붙일 수 있어야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns true
                every { projectService.attachLabel(31L, "os", "linux") } returns
                    AttachLabelResult(Label(id = 1L, category = "os", name = "linux"), isCreated = true, isAttached = true)

                mockMvc.perform(
                    post("/api/owner/priv/labels")
                        .param("category", "os")
                        .param("name", "linux")
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("프로젝트 멤버가 아니면 라벨 붙이기가 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns false

                mockMvc.perform(
                    post("/api/owner/priv/labels")
                        .param("name", "linux")
                        .principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("이미 붙어있는 라벨을 다시 붙이면 204 No Content를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns true
                every { projectService.attachLabel(31L, null, "linux") } returns
                    AttachLabelResult(Label(id = 1L, category = "Label", name = "linux"), isCreated = false, isAttached = false)

                mockMvc.perform(
                    post("/api/owner/priv/labels")
                        .param("name", "linux")
                        .principal(userAuth)
                )
                    .andExpect(status().isNoContent)
            }

            it("멤버는 라벨을 뗄 수 있어야 하고 204를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns true
                every { projectService.detachLabel(31L, 1L) } returns true

                mockMvc.perform(
                    delete("/api/owner/priv/labels/1")
                        .principal(userAuth)
                )
                    .andExpect(status().isNoContent)
            }

            it("존재하지 않는 라벨을 떼려고 하면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "priv") } returns Optional.of(privateProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(31L, 10L) } returns true
                every { projectService.detachLabel(31L, 999L) } returns false

                mockMvc.perform(
                    delete("/api/owner/priv/labels/999")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }
        }

        // yona ProjectApi.titleHeads()/getherTitleHeads()/getherProjectLabels() 대응 (P1-103).
        describe("GET /api/{owner}/{projectName}/titleHeads") {
            val titleHeadProject = Project(id = 50L, name = "th", owner = "owner", projectScope = ProjectScope.PUBLIC)
            val category = IssueLabelCategory(id = 1L, name = "type", isExclusive = false, project = titleHeadProject)
            val label = IssueLabel(id = 9L, category = category, color = "#ff0000", name = "bug", project = titleHeadProject)

            it("공개 프로젝트는 비회원도 조회 가능하고, 머리말과 라벨을 합쳐서 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "th") } returns Optional.of(titleHeadProject)
                every { titleHeadService.search(titleHeadProject, "bu") } returns listOf(
                    TitleHead(id = 1L, project = titleHeadProject, headKeyword = "Bug", frequency = 3)
                )
                every { issueLabelRepository.findByProject(titleHeadProject) } returns listOf(label)

                mockMvc.perform(get("/api/owner/th/titleHeads").param("query", "bu"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result[0].name").value("Bug"))
                    .andExpect(jsonPath("$.result[0].frequency").value(3))
                    .andExpect(jsonPath("$.result[0].searchText").value("Bug"))
                    .andExpect(jsonPath("$.result[1].name").value("bug"))
                    .andExpect(jsonPath("$.result[1].category").value("type"))
                    .andExpect(jsonPath("$.result[1].labelColor").value("#ff0000"))
                    .andExpect(jsonPath("$.result[1].searchText").value("bug/type"))
            }

            it("query 파라미터가 없으면 빈 문자열로 전체 조회해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "th") } returns Optional.of(titleHeadProject)
                every { titleHeadService.search(titleHeadProject, "") } returns emptyList()
                every { issueLabelRepository.findByProject(titleHeadProject) } returns emptyList()

                mockMvc.perform(get("/api/owner/th/titleHeads"))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.result").isArray)
            }

            it("비공개 프로젝트는 비회원이 조회하면 403을 반환해야 한다") {
                val privateTitleHeadProject = Project(id = 51L, name = "th-priv", owner = "owner", projectScope = ProjectScope.PRIVATE)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "th-priv") } returns Optional.of(privateTitleHeadProject)

                mockMvc.perform(get("/api/owner/th-priv/titleHeads"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(get("/api/owner/unknown/titleHeads"))
                    .andExpect(status().isNotFound)
            }
        }

        describe("GET/DELETE /api/{owner}/{projectName}/pushedBranches (P1-15/24)") {
            val branchProject = Project(id = 40L, name = "bp", owner = "owner", projectScope = ProjectScope.PRIVATE)

            it("비회원은 최근 push된 브랜치 목록 조회 시 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)

                mockMvc.perform(get("/api/owner/bp/pushedBranches"))
                    .andExpect(status().isForbidden)
            }

            it("프로젝트 멤버는 최근 push된 브랜치 목록을 조회할 수 있어야 한다") {
                val branch = PushedBranch(id = 1L, name = "feature/x", pushedDate = Instant.now(), project = branchProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(40L, 10L) } returns true
                user.projectUsers.add(ProjectUser(id = 400L, user = user, project = branchProject, role = Role(id = RoleType.MEMBER.roleType)))
                every { pushedBranchRepository.findByProjectAndPushedDateAfter(branchProject, any()) } returns listOf(branch)

                mockMvc.perform(
                    get("/api/owner/bp/pushedBranches").principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].name").value("feature/x"))
            }

            it("프로젝트 멤버는 push된 브랜치 기록을 삭제할 수 있어야 한다") {
                val branch = PushedBranch(id = 2L, name = "feature/y", project = branchProject)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(40L, 10L) } returns true
                every { pushedBranchRepository.findById(2L) } returns Optional.of(branch)
                every { pushedBranchRepository.delete(branch) } returns Unit

                mockMvc.perform(
                    delete("/api/owner/bp/pushedBranches/2").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify { pushedBranchRepository.delete(branch) }
            }

            it("존재하지 않는 id를 삭제해도 yona와 동일하게 200 OK를 반환해야 한다(404 아님)") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(40L, 10L) } returns true
                every { pushedBranchRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    delete("/api/owner/bp/pushedBranches/999").principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 0) { pushedBranchRepository.delete(any()) }
            }

            it("프로젝트 멤버가 아니면 삭제 시 403을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(40L, 10L) } returns false

                mockMvc.perform(
                    delete("/api/owner/bp/pushedBranches/2").principal(userAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("조회 시 프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(get("/api/owner/unknown/pushedBranches"))
                    .andExpect(status().isNotFound)
            }

            it("삭제 시 프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()

                mockMvc.perform(delete("/api/owner/unknown/pushedBranches/2").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("삭제 시 인증되지 않으면 401을 반환해야 한다") {
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "bp") } returns Optional.of(branchProject)

                mockMvc.perform(delete("/api/owner/bp/pushedBranches/2"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("GET /api/projects/search") {
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                mockMvc.perform(get("/api/projects/search"))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("siteManager는 모든 프로젝트를 검색할 수 있어야 한다") {
                val adminUser = User(id = 30L, loginId = "admin", name = "Admin", state = UserState.SITE_ADMIN)
                every { userRepository.findByLoginId("admin") } returns Optional.of(adminUser)
                val adminAuth = UsernamePasswordAuthenticationToken("admin", "password")
                val page = PageImpl(listOf(project))
                every { projectRepository.findProjectsForAdmin(any(), any()) } returns page

                mockMvc.perform(get("/api/projects/search").principal(adminAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("owner/TestProject"))
            }
            it("일반 유저는 자신이 허용된 프로젝트 아이디가 있으면 그걸로 검색해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findAllowedProjectIdsForUser(10L) } returns listOf(1L)
                val page = PageImpl(listOf(project))
                every { projectRepository.searchProjects(listOf(1L), any(), any()) } returns page

                mockMvc.perform(get("/api/projects/search").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("owner/TestProject"))
            }
            it("허용된 프로젝트가 없고 공개 프로젝트가 없으면 빈 리스트를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findAllowedProjectIdsForUser(10L) } returns emptyList()
                every { projectRepository.findPublicProjectIds() } returns emptyList()

                mockMvc.perform(get("/api/projects/search").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$").isEmpty)
            }
            it("허용된 프로젝트가 없고 공개 프로젝트가 있으면 공개 프로젝트 내에서 검색해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findAllowedProjectIdsForUser(10L) } returns emptyList()
                every { projectRepository.findPublicProjectIds() } returns listOf(1L)
                val page = PageImpl(listOf(project))
                every { projectRepository.searchProjects(listOf(1L), any(), any()) } returns page

                mockMvc.perform(get("/api/projects/search").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0]").value("owner/TestProject"))
            }
        }

        describe("PUT /api/projects/{projectId} 추가 예외 처리") {
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                mockMvc.perform(put("/api/projects/1").contentType(MediaType.APPLICATION_JSON).content("""{"overview":"test","projectScope":"PUBLIC"}"""))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("서비스가 IllegalStateException을 던지면 500을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.updateProject(1L, any()) } throws IllegalStateException("Internal Error")

                val jsonContent = """{"overview": "새로운 설명", "projectScope": "PUBLIC"}"""
                mockMvc.perform(put("/api/projects/1").contentType(MediaType.APPLICATION_JSON).content(jsonContent).principal(userAuth))
                    .andExpect(status().isInternalServerError)
            }
        }

        describe("DELETE /api/projects/{projectId} 추가 예외 처리") {
            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()
                mockMvc.perform(delete("/api/projects/999"))
                    .andExpect(status().isNotFound)
            }
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                mockMvc.perform(delete("/api/projects/1"))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("owner가 아니지만 MANAGER인 경우 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user) // testuser != owner
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser) // MANAGER
                every { projectService.deleteProject(1L) } returns Unit

                mockMvc.perform(delete("/api/projects/1").principal(userAuth))
                    .andExpect(status().isOk)
            }
            it("owner도 아니고 MANAGER도 아니면 403을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/1").principal(userAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /api/{owner}/{projectName}/transfer") {
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                mockMvc.perform(post("/api/owner/TestProject/transfer").param("destination", "dest"))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()
                mockMvc.perform(post("/api/owner/unknown/transfer").param("destination", "dest").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
            it("MANAGER가 아니면 403을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.empty()
                mockMvc.perform(post("/api/owner/TestProject/transfer").param("destination", "dest").principal(userAuth))
                    .andExpect(status().isForbidden)
            }
            it("정상적으로 이전 요청을 수행하면 200 OK를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                val transferMock = ProjectTransfer(id = 1L, sender = user, destination = "dest", project = project)
                every { projectService.requestNewTransfer(1L, 10L, "dest") } returns transferMock

                mockMvc.perform(post("/api/owner/TestProject/transfer").param("destination", "dest").principal(userAuth))
                    .andExpect(status().isOk)
            }
            it("IllegalArgumentException이 발생하면 400을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { projectService.requestNewTransfer(1L, 10L, "dest") } throws IllegalArgumentException("error")

                mockMvc.perform(post("/api/owner/TestProject/transfer").param("destination", "dest").principal(userAuth))
                    .andExpect(status().isBadRequest)
            }
        }

        describe("POST /api/projects/transfer/{transferId}/accept") {
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                mockMvc.perform(post("/api/projects/transfer/1/accept").param("confirmKey", "key"))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("정상 수락 시 200 OK를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectService.acceptTransfer(1L, "key", 10L) } returns Unit

                mockMvc.perform(post("/api/projects/transfer/1/accept").param("confirmKey", "key").principal(userAuth))
                    .andExpect(status().isOk)
            }
            it("IllegalArgumentException 발생 시 400을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectService.acceptTransfer(1L, "key", 10L) } throws IllegalArgumentException("err")

                mockMvc.perform(post("/api/projects/transfer/1/accept").param("confirmKey", "key").principal(userAuth))
                    .andExpect(status().isBadRequest)
            }
        }

        describe("POST /api/{owner}/{projectName}/fork") {
            it("로그인하지 않은 경우 401을 반환해야 한다") {
                mockMvc.perform(post("/api/owner/TestProject/fork"))
                    .andDo(MockMvcResultHandlers.print()).andExpect(status().isUnauthorized)
            }
            it("프로젝트가 없으면 404를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "unknown") } returns Optional.empty()
                mockMvc.perform(post("/api/owner/unknown/fork").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
            it("정상적으로 포크하면 200 OK를 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { projectService.forkProject(1L, 10L) } returns project

                mockMvc.perform(post("/api/owner/TestProject/fork").principal(userAuth))
                    .andExpect(status().isOk)
            }
            it("IllegalArgumentException 발생 시 400을 반환해야 한다") {
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectRepository.findByOwnerAndNameOrPreviousPlace("owner", "TestProject") } returns Optional.of(project)
                every { projectService.forkProject(1L, 10L) } throws IllegalArgumentException("error")

                mockMvc.perform(post("/api/owner/TestProject/fork").principal(userAuth))
                    .andExpect(status().isBadRequest)
            }
        }
    }
})
