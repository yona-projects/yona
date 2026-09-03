package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneService
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
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
import io.mockk.clearMocks
import io.mockk.slot

class MilestoneControllerSpec : DescribeSpec({
    val milestoneService = mockk<MilestoneService>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
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

    val milestoneController = MilestoneController(
        milestoneService,
        milestoneRepository,
        projectRepository,
        projectUserRepository,
        userRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(milestoneController).build()

    beforeTest {
        clearMocks(milestoneService, milestoneRepository, projectRepository, projectUserRepository, userRepository)
    }

    describe("MilestoneController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)
        user.projectUsers.add(projectUser)
        managerUser.projectUsers.add(projectManagerUser)

        val milestone = Milestone(id = 30L, title = "마일스톤 1", contents = "상세 내용", state = State.OPEN, project = project)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

        describe("GET /api/projects/{projectId}/milestones") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 마일스톤 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { milestoneService.getMilestones(1L, State.OPEN) } returns listOf(milestone)

                mockMvc.perform(get("/api/projects/1/milestones").param("state", "OPEN").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("마일스톤 1"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/milestones"))
                    .andExpect(status().isNotFound)
            }

            it("읽기 권한이 없으면(비공개 프로젝트의 비멤버) 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 99L) } returns false

                mockMvc.perform(get("/api/projects/1/milestones").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("GET /api/projects/{projectId}/milestones/{milestoneId}") {
            it("마일스톤 상세 정보를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/milestones/30").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("마일스톤 1"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/milestones/30"))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 마일스톤이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(999L) } returns null

                mockMvc.perform(get("/api/projects/1/milestones/999").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("마일스톤이 해당 프로젝트 소속이 아니면 400을 반환해야 한다") {
                val otherProject = Project(id = 2L, name = "Other", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 31L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(31L) } returns otherMilestone

                mockMvc.perform(get("/api/projects/1/milestones/31").principal(userAuth))
                    .andExpect(status().isBadRequest)
            }

            it("읽기 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 99L) } returns false

                mockMvc.perform(get("/api/projects/1/milestones/30").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /api/projects/{projectId}/milestones") {
            it("관리자가 마일스톤을 생성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.createMilestone(1L, any()) } returns milestone

                val jsonContent = """
                    {
                        "title": "마일스톤 1",
                        "contents": "상세 내용",
                        "dueDate": null,
                        "state": "OPEN"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("일반 멤버가 마일스톤을 생성해도 201 Created를 반환해야 한다 (P1-95, legacy는 프로젝트 멤버 전원에게 생성 권한이 있음, 매니저 전용 아님)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { milestoneService.createMilestone(1L, any()) } returns milestone

                val jsonContent = """
                    {
                        "title": "마일스톤 1"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)

                val jsonContent = """
                    {
                        "title": "마일스톤 1"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "마일스톤" }""")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/milestones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "마일스톤" }""")
                ).andExpect(status().isUnauthorized)
            }
        }

        // yona controllers/api/MilestoneApi.java:29-50 newMilestone() 대응 (P1-129). [GL-controllers_api_MilestoneApi-001;GL-controllers_api_MilestoneApi-002]
        describe("POST /api/projects/{projectId}/milestones/bulk") {
            it("프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)

                val jsonContent = """{ "milestones": [ { "title": "임포트된 마일스톤" } ] }"""

                mockMvc.perform(
                    post("/api/projects/1/milestones/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(strangerAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { milestoneRepository.save(any()) }
            }

            it("이미 존재하는 제목이면 생성하지 않고 중복 메시지를 반환하고, 새 제목은 정상 생성되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { milestoneRepository.findByProjectAndTitle(project, "이미 있는 제목") } returns milestone
                every { milestoneRepository.findByProjectAndTitle(project, "새 제목") } returns null
                val savedSlot = slot<Milestone>()
                every { milestoneRepository.save(capture(savedSlot)) } answers { savedSlot.captured.apply { id = 40L } }

                val jsonContent = """
                    {
                        "milestones": [
                            { "title": "이미 있는 제목" },
                            { "title": "새 제목", "description": "설명", "state": "closed" }
                        ]
                    }
                """.trimIndent()

                val result = mockMvc.perform(
                    post("/api/projects/1/milestones/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].message").value("이미 존재하는 마일스톤 제목입니다."))
                    .andExpect(jsonPath("$[1].title").value("새 제목"))
                    .andExpect(jsonPath("$[1].state").value("closed"))
                    .andReturn()

                verify(exactly = 1) { milestoneRepository.save(any()) }
                savedSlot.captured.state shouldBe State.CLOSED
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/milestones/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "milestones": [] }""")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/milestones/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "milestones": [] }""")
                ).andExpect(status().isUnauthorized)
            }

            it("title이 없으면 'No title'을 기본값으로, state가 없으면 OPEN을 기본값으로, due_on은 ISO 전체 일시와 날짜만 온 경우 모두 파싱해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { milestoneRepository.findByProjectAndTitle(project, "No title") } returns null
                every { milestoneRepository.findByProjectAndTitle(project, "날짜만있음") } returns null
                val savedSlot = slot<Milestone>()
                every { milestoneRepository.save(capture(savedSlot)) } answers { savedSlot.captured.apply { id = 50L } }

                val jsonContent = """
                    {
                        "milestones": [
                            { "due_on": "2026-01-15T00:00:00+09:00" },
                            { "title": "날짜만있음", "due_on": "2026-02-20" }
                        ]
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/milestones/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
                    .andExpect(jsonPath("$[0].title").value("No title"))
                    .andExpect(jsonPath("$[0].state").value("open"))
                    .andExpect(jsonPath("$[0].due_on").exists())
                    .andExpect(jsonPath("$[1].due_on").exists())

                verify(exactly = 2) { milestoneRepository.save(any()) }
            }
        }

        describe("PUT /api/projects/{projectId}/milestones/{milestoneId}") {
            it("관리자가 마일스톤을 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.updateMilestone(30L, "수정된 마일스톤", "수정내용", null, State.OPEN) } returns milestone

                val jsonContent = """
                    {
                        "title": "수정된 마일스톤",
                        "contents": "수정내용",
                        "dueDate": null,
                        "state": "OPEN"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/milestones/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(managerAuth)
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/milestones/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "수정" }""")
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 마일스톤이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(999L) } returns null

                mockMvc.perform(
                    put("/api/projects/1/milestones/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "수정" }""")
                ).andExpect(status().isNotFound)
            }

            it("마일스톤이 해당 프로젝트 소속이 아니면 400을 반환해야 한다") {
                val otherProject = Project(id = 2L, name = "Other", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 32L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(32L) } returns otherMilestone

                mockMvc.perform(
                    put("/api/projects/1/milestones/32")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "수정" }""")
                ).andExpect(status().isBadRequest)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone

                mockMvc.perform(
                    put("/api/projects/1/milestones/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "수정" }""")
                ).andExpect(status().isUnauthorized)
            }

            it("수정 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/milestones/30")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{ "title": "수정" }""")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)
            }
        }

        describe("DELETE /api/projects/{projectId}/milestones/{milestoneId}") {
            it("관리자가 마일스톤을 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { milestoneService.deleteMilestone(30L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/milestones/30").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/999/milestones/30"))
                    .andExpect(status().isNotFound)
            }

            it("존재하지 않는 마일스톤이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(999L) } returns null

                mockMvc.perform(delete("/api/projects/1/milestones/999"))
                    .andExpect(status().isNotFound)
            }

            it("마일스톤이 해당 프로젝트 소속이 아니면 400을 반환해야 한다") {
                val otherProject = Project(id = 2L, name = "Other", projectScope = ProjectScope.PRIVATE)
                val otherMilestone = Milestone(id = 33L, title = "다른 프로젝트 마일스톤", project = otherProject)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(33L) } returns otherMilestone

                mockMvc.perform(delete("/api/projects/1/milestones/33"))
                    .andExpect(status().isBadRequest)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone

                mockMvc.perform(delete("/api/projects/1/milestones/30"))
                    .andExpect(status().isUnauthorized)
            }

            it("삭제 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { milestoneService.getMilestone(30L) } returns milestone
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/1/milestones/30").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }
    }
})
