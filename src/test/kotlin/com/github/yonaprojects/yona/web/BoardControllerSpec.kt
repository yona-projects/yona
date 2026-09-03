package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingService
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
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
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import io.mockk.clearMocks
import io.mockk.slot
import org.springframework.data.domain.Pageable
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabel

class BoardControllerSpec : DescribeSpec({
    val postingService = mockk<PostingService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val postingRepository = mockk<PostingRepository>()
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

    val boardController = BoardController(
        postingService,
        projectRepository,
        projectUserRepository,
        userRepository,
        postingRepository,
        issueLabelRepository,
        accessControl
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(boardController)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(postingService, projectRepository, projectUserRepository, userRepository, postingRepository, issueLabelRepository)
    }

    describe("BoardController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        val projectUser = ProjectUser(id = 100L, user = user, project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = managerUser, project = project, role = managerRole)
        user.projectUsers.add(projectUser)
        managerUser.projectUsers.add(projectManagerUser)

        val posting = Posting(id = 50L, title = "포스트 제목", body = "포스트 내용", project = project, authorId = user.id, number = 1L)

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")
        val pageRequest = PageRequest.of(0, 25)

        describe("GET /api/projects/{projectId}/posts") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 게시판 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.getPostings(1L, any()) } returns PageImpl(listOf(posting), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/posts").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.content[0].title").value("포스트 제목"))
            }

            // yona AbstractPostingApp.java:35 ITEMS_PER_PAGE(15) 대응 (P1-105). 게시글 목록은 클라이언트가
            // size를 요청해도 항상 고정 15로 무시되어야 한다(이슈와 다름).
            it("size 파라미터를 크게 요청해도 항상 15로 고정되어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val pageableSlot = slot<Pageable>()
                every { postingService.getPostings(1L, capture(pageableSlot)) } returns PageImpl(listOf(posting), pageRequest, 1)

                mockMvc.perform(get("/api/projects/1/posts").param("size", "999").principal(userAuth))
                    .andExpect(status().isOk)

                pageableSlot.captured.pageSize shouldBe 15
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/posts")).andExpect(status().isNotFound)
            }

            it("읽기 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 99L) } returns false

                mockMvc.perform(get("/api/projects/1/posts").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("GET /api/projects/{projectId}/posts/{postId}") {
            it("게시글 번호로 포스트 상세 정보를 조회할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true

                mockMvc.perform(get("/api/projects/1/posts/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("포스트 제목"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/posts/1")).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/posts/999")).andExpect(status().isNotFound)
            }

            it("읽기 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 99L) } returns false

                mockMvc.perform(get("/api/projects/1/posts/1").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("POST /api/projects/{projectId}/posts") {
            it("새로운 글을 작성하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { postingService.createPosting(1L, any(), 10L) } returns posting

                val jsonContent = """
                    {
                        "title": "포스트 제목",
                        "body": "포스트 내용",
                        "notice": false,
                        "readme": false
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            // yona BoardApp.java:211 @IsCreatable(ResourceType.BOARD_POST) 대응 (P1-113) — 공개 [GL-controllers_BoardApp-009]
            // 프로젝트의 비멤버 로그인 사용자도 게시글을 쓸 수 있어야 한다(회귀 수정 검증).
            it("공개 프로젝트의 비멤버 로그인 사용자도 게시글을 작성할 수 있어야 한다") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val nonMember = User(id = 30L, loginId = "nonmember", name = "비멤버")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember", "password")
                val publicPosting = Posting(id = 2L, number = 1L, title = "포스트 제목", body = "포스트 내용", project = publicProject)

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("nonmember") } returns Optional.of(nonMember)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false
                every { postingService.createPosting(2L, any(), 30L) } returns publicPosting

                val jsonContent = """
                    {
                        "title": "포스트 제목",
                        "body": "포스트 내용",
                        "notice": false,
                        "readme": false
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/2/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(nonMemberAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("notice/readme 필드를 생략하면 기본값 false로 생성해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                val postingSlot = slot<Posting>()
                every { postingService.createPosting(1L, capture(postingSlot), 10L) } returns posting

                mockMvc.perform(
                    post("/api/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                        .principal(userAuth)
                ).andExpect(status().isCreated)

                postingSlot.captured.notice shouldBe false
                postingSlot.captured.readme shouldBe false
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                ).andExpect(status().isUnauthorized)
            }

            it("작성 권한이 없으면(비공개 프로젝트의 비멤버) 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)

                mockMvc.perform(
                    post("/api/projects/1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)
            }
        }

        describe("PUT /api/projects/{projectId}/posts/{postId}") {
            it("작성자가 포스트를 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingService.updatePosting(1L, 1L, "수정된 포스트 제목", "수정된 포스트 내용", false, false, 10L, false) } returns posting

                val jsonContent = """
                    {
                        "title": "수정된 포스트 제목",
                        "body": "수정된 포스트 내용",
                        "notice": false,
                        "readme": false
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("sendNotificationMail 옵션을 서비스로 그대로 전달해야 한다(P1-44)") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingService.updatePosting(1L, 1L, "수정된 포스트 제목", "수정된 포스트 내용", false, false, 10L, true) } returns posting

                val jsonContent = """
                    {
                        "title": "수정된 포스트 제목",
                        "body": "수정된 포스트 내용",
                        "notice": false,
                        "readme": false,
                        "sendNotificationMail": true
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "수정된 포스트 제목", "수정된 포스트 내용", false, false, 10L, true) }
            }

            it("notice/readme/sendNotificationMail 필드를 생략하면 기본값 false로 전달해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingService.updatePosting(1L, 1L, "t", "b", false, false, 10L, false) } returns posting

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                        .principal(userAuth)
                ).andExpect(status().isOk)

                verify(exactly = 1) { postingService.updatePosting(1L, 1L, "t", "b", false, false, 10L, false) }
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(
                    put("/api/projects/1/posts/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                ).andExpect(status().isUnauthorized)
            }

            it("수정 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/posts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"t","body":"b"}""")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)
            }
        }

        // yona BoardApi.java:128-159 updatePostingContent() 대응 (P1-107). [GL-controllers_api_BoardApi-006]
        describe("PATCH /api/projects/{projectId}/posts/{postId}/content") {
            it("original이 현재 본문과 일치하면 정상적으로 갱신해야 한다") {
                val editablePosting = Posting(id = 52L, title = "포스트3", body = "원본 내용", project = project, authorId = user.id, number = 3L)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 3L) } returns editablePosting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingRepository.save(editablePosting) } returns editablePosting

                mockMvc.perform(
                    patch("/api/projects/1/posts/3/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"수정된 내용\", \"original\": \"원본 내용\"}")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.body").value("수정된 내용"))
            }

            it("original이 현재 본문과 다르면 409 Conflict와 storedContent를 반환하고 저장하지 않아야 한다") {
                val contentPosting = Posting(id = 51L, title = "포스트2", body = "원본 내용", project = project, authorId = user.id, number = 2L)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 2L) } returns contentPosting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)

                mockMvc.perform(
                    patch("/api/projects/1/posts/2/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"수정된 내용\", \"original\": \"다른사람이 이미 바꾼 내용\"}")
                        .principal(userAuth)
                )
                    .andExpect(status().isConflict)
                    .andExpect(jsonPath("$.message").value("Already modified by someone."))
                    .andExpect(jsonPath("$.storedContent").value("원본 내용"))

                verify(exactly = 0) { postingRepository.save(any()) }
            }

            it("posting.body가 null이면 빈 문자열로 취급해 original이 빈 문자열일 때만 통과해야 한다") {
                val blankPosting = Posting(id = 53L, title = "포스트4", body = null, project = project, authorId = user.id, number = 4L)
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 4L) } returns blankPosting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { postingRepository.save(blankPosting) } returns blankPosting

                mockMvc.perform(
                    patch("/api/projects/1/posts/4/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"새 내용\", \"original\": \"\"}")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.body").value("새 내용"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    patch("/api/projects/999/posts/1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"c","original":"o"}""")
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(
                    patch("/api/projects/1/posts/999/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"c","original":"o"}""")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting

                mockMvc.perform(
                    patch("/api/projects/1/posts/1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"c","original":"o"}""")
                ).andExpect(status().isUnauthorized)
            }

            it("수정 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(
                    patch("/api/projects/1/posts/1/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"c","original":"o"}""")
                        .principal(strangerAuth)
                ).andExpect(status().isForbidden)
            }
        }

        describe("DELETE /api/projects/{projectId}/posts/{postId}") {
            it("관리자가 포스트를 삭제하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(projectManagerUser)
                every { postingService.deletePosting(1L, 1L, 20L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/posts/1").principal(managerAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("작성자도 매니저도 아닌 일반 멤버가 삭제해도 200 OK를 반환해야 한다 (P1-91, legacy는 프로젝트 멤버 전원 허용)") {
                val plainMember = User(id = 30L, loginId = "plainmember", name = "일반멤버")
                plainMember.projectUsers.add(ProjectUser(id = 102L, user = plainMember, project = project, role = memberRole))
                val plainMemberAuth = UsernamePasswordAuthenticationToken("plainmember", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("plainmember") } returns Optional.of(plainMember)
                every { postingService.deletePosting(1L, 1L, 30L) } returns Unit

                mockMvc.perform(delete("/api/projects/1/posts/1").principal(plainMemberAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("success"))
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/999/posts/1")).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(delete("/api/projects/1/posts/999")).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting

                mockMvc.perform(delete("/api/projects/1/posts/1")).andExpect(status().isUnauthorized)
            }

            it("삭제 권한이 없으면 403을 반환해야 한다") {
                val stranger = User(id = 99L, loginId = "stranger", name = "외부인")
                val strangerAuth = UsernamePasswordAuthenticationToken("stranger", "password")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("stranger") } returns Optional.of(stranger)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/1/posts/1").principal(strangerAuth))
                    .andExpect(status().isForbidden)
            }
        }

        describe("PUT /api/projects/{projectId}/posts/{postId}/labels") {
            it("작성자가 라벨 ID 목록으로 게시글 라벨을 교체하면 200 OK를 반환해야 한다") {
                val category = IssueLabelCategory(id = 1L, name = "기본", project = project)
                val label1 = IssueLabel(id = 1L, name = "버그", color = "red", category = category, project = project)
                val label2 = IssueLabel(id = 2L, name = "긴급", color = "orange", category = category, project = project)

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every {
                    issueLabelRepository.findAllById(listOf(1L, 2L))
                } returns listOf(label1, label2)
                every { postingRepository.save(posting) } returns posting

                mockMvc.perform(
                    put("/api/projects/1/posts/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1, 2]")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                posting.labels.map { it.id }.toSet() shouldBe setOf(1L, 2L)
                verify(exactly = 1) { postingRepository.save(posting) }
            }

            it("작성자도 관리자도 아니면 403 Forbidden을 반환해야 한다") {
                val otherUser = User(id = 99L, loginId = "other", name = "타인")
                val otherAuth = UsernamePasswordAuthenticationToken("other", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting
                every { userRepository.findByLoginId("other") } returns Optional.of(otherUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 99L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/posts/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1]")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 프로젝트면 404를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/posts/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1]")
                ).andExpect(status().isNotFound)
            }

            it("존재하지 않는 게시글이면 404를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 999L) } returns null

                mockMvc.perform(
                    put("/api/projects/1/posts/999/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1]")
                ).andExpect(status().isNotFound)
            }

            it("인증되지 않은 요청은 401을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { postingService.getPosting(1L, 1L) } returns posting

                mockMvc.perform(
                    put("/api/projects/1/posts/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[1]")
                ).andExpect(status().isUnauthorized)
            }
        }
    }
})
