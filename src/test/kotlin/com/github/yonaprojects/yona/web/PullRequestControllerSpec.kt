package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestService
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEventRepository
import io.mockk.clearMocks
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestEvent
import com.github.yonaprojects.yona.domain.enumeration.EventType
import com.github.yonaprojects.yona.domain.pullrequest.DuplicatedPullRequestException
import com.github.yonaprojects.yona.domain.pullrequest.LackingReviewerException
import com.github.yonaprojects.yona.domain.pullrequest.CodeReviewService

class PullRequestControllerSpec : DescribeSpec({
    val pullRequestService = mockk<PullRequestService>()
    val projectRepository = mockk<ProjectRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val userRepository = mockk<UserRepository>()
    val pullRequestEventRepository = mockk<PullRequestEventRepository>()
    val codeReviewService = mockk<CodeReviewService>()
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

    val pullRequestController = PullRequestController(
        pullRequestService,
        projectRepository,
        projectUserRepository,
        userRepository,
        pullRequestEventRepository,
        accessControl,
        codeReviewService
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(pullRequestController).build()

    beforeTest {
        clearMocks(pullRequestService, projectRepository, projectUserRepository, userRepository, pullRequestEventRepository, codeReviewService)
    }

    describe("PullRequestController 웹 API 테스트") {
        val project = Project(id = 1L, name = "TestProject", projectScope = ProjectScope.PRIVATE)
        val fromProject = Project(id = 2L, name = "ForkProject", projectScope = ProjectScope.PRIVATE)
        val user = User(id = 10L, loginId = "testuser", name = "테스트유저")
        val managerUser = User(id = 20L, loginId = "manageruser", name = "관리자유저")
        
        val memberRole = Role(id = RoleType.MEMBER.roleType)
        val managerRole = Role(id = RoleType.MANAGER.roleType)

        // isMemberOf()/isManagerOf()는 project.id/role.id만 보고 .user는 읽지 않는다 — 여기서 user 자신을
        // .user로 넣으면 PullRequest.contributor로 그대로 직렬화될 때 user->projectUsers->user 순환 참조로
        // Jackson이 무한 재귀에 빠진다(IssueSharer.kt의 기존 @JsonIgnore 사례와 동일한 문제군). 멤버십 판정에는
        // 영향 없는 더미 User로 배선해 순환을 끊는다.
        val projectUser = ProjectUser(id = 100L, user = User(id = 999_910L, loginId = "_membership_placeholder"), project = project, role = memberRole)
        val projectManagerUser = ProjectUser(id = 101L, user = User(id = 999_911L, loginId = "_membership_placeholder"), project = project, role = managerRole)
        user.projectUsers.add(projectUser)
        managerUser.projectUsers.add(projectManagerUser)

        val pullRequest = PullRequest(
            id = 50L,
            title = "PR 제목",
            body = "PR 본문",
            toProject = project,
            fromProject = fromProject,
            toBranch = "master",
            fromBranch = "feature",
            contributor = user,
            state = State.OPEN,
            number = 1L
        )

        val userAuth = UsernamePasswordAuthenticationToken("testuser", "password")
        val managerAuth = UsernamePasswordAuthenticationToken("manageruser", "password")

        // changeState/deleteFromBranch/restoreFromBranch의 isManagerOrContributor 3분기(계약자/매니저/일반멤버)와
        // checkWritePermission의 멤버 허용 분기를 실제로 밟기 위한 추가 액터.
        val plainMemberUser = User(id = 60L, loginId = "plainmember", name = "일반멤버")
        plainMemberUser.projectUsers.add(ProjectUser(id = 104L, user = User(id = 999_913L, loginId = "_membership_placeholder"), project = project, role = memberRole))
        val plainMemberAuth = UsernamePasswordAuthenticationToken("plainmember", "password")

        val outsiderUser = User(id = 70L, loginId = "outsider", name = "비멤버")
        val outsiderAuth = UsernamePasswordAuthenticationToken("outsider", "password")

        describe("GET /api/projects/{projectId}/pullrequests") {
            it("비공개 프로젝트일 때 프로젝트 멤버라면 200 OK와 PR 목록을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequests(1L, State.OPEN) } returns listOf(pullRequest)

                mockMvc.perform(get("/api/projects/1/pullrequests").param("state", "OPEN").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].title").value("PR 제목"))
            }

            it("존재하지 않는 프로젝트를 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/pullrequests").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            // getLoginUser()의 authentication==null 분기와 checkReadPermission() false 분기를 함께 검증한다.
            it("비로그인 사용자가 비공개 프로젝트를 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(get("/api/projects/1/pullrequests"))
                    .andExpect(status().isForbidden)
            }

            // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr list --author` 대응.
            it("author 파라미터가 있으면 contributor.loginId가 일치하는 PR만 반환해야 한다") {
                val otherContributor = User(id = 30L, loginId = "othercontributor", name = "다른기여자")
                val otherPr = PullRequest(
                    id = 51L, title = "다른 PR", toProject = project, fromProject = fromProject,
                    contributor = otherContributor, state = State.OPEN, number = 2L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequests(1L, null) } returns listOf(pullRequest, otherPr)

                mockMvc.perform(get("/api/projects/1/pullrequests").param("author", "testuser").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("PR 제목"))
            }

            // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — `gh pr list --assignee/--label` 대응.
            it("assignee 파라미터가 있으면 담당자 loginId가 일치하는 PR만 반환해야 한다") {
                val assignedPr = PullRequest(
                    id = 52L, title = "담당자 있는 PR", toProject = project, fromProject = fromProject,
                    contributor = user, state = State.OPEN, number = 3L,
                    assignee = com.github.yonaprojects.yona.domain.issue.Assignee(user = user, project = project)
                )
                val unassignedPr = PullRequest(
                    id = 53L, title = "담당자 없는 PR", toProject = project, fromProject = fromProject,
                    contributor = user, state = State.OPEN, number = 4L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequests(1L, null) } returns listOf(assignedPr, unassignedPr)

                mockMvc.perform(get("/api/projects/1/pullrequests").param("assignee", "testuser").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("담당자 있는 PR"))
            }

            it("label 파라미터가 있으면 해당 이름의 라벨이 붙은 PR만 반환해야 한다") {
                val category = com.github.yonaprojects.yona.domain.issue.IssueLabelCategory(id = 1L, name = "type", project = project)
                val bugLabel = com.github.yonaprojects.yona.domain.issue.IssueLabel(id = 1L, name = "bug", color = "red", category = category, project = project)
                val labeledPr = PullRequest(
                    id = 54L, title = "라벨 있는 PR", toProject = project, fromProject = fromProject,
                    contributor = user, state = State.OPEN, number = 5L,
                    labels = mutableSetOf(bugLabel)
                )
                val unlabeledPr = PullRequest(
                    id = 55L, title = "라벨 없는 PR", toProject = project, fromProject = fromProject,
                    contributor = user, state = State.OPEN, number = 6L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequests(1L, null) } returns listOf(labeledPr, unlabeledPr)

                mockMvc.perform(get("/api/projects/1/pullrequests").param("label", "bug").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("라벨 있는 PR"))
            }
        }

        describe("GET /api/projects/{projectId}/pullrequests/{number}") {
            it("PR 번호로 풀 리퀘스트 상세 정보를 조회할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(get("/api/projects/1/pullrequests/1").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.title").value("PR 제목"))
            }

            it("존재하지 않는 프로젝트를 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/pullrequests/1").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비공개 프로젝트를 비멤버가 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)

                mockMvc.perform(get("/api/projects/1/pullrequests/1").principal(outsiderAuth))
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 PR 번호를 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/pullrequests/999").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("GET /api/projects/{projectId}/pullrequests/{number}/timeline") {
            it("PR의 변경 이력을 시간순으로 반환해야 한다") {
                val prEvent = PullRequestEvent(
                    id = 1L, pullRequest = pullRequest,
                    eventType = EventType.PULL_REQUEST_STATE_CHANGED,
                    oldValue = "OPEN", newValue = "MERGED"
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestEventRepository.findByPullRequestOrderByCreatedAsc(pullRequest) } returns listOf(prEvent)

                mockMvc.perform(get("/api/projects/1/pullrequests/1/timeline").principal(userAuth))
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].newValue").value("MERGED"))
            }

            it("존재하지 않는 프로젝트의 타임라인을 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/pullrequests/1/timeline").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비공개 프로젝트를 비멤버가 타임라인 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)

                mockMvc.perform(get("/api/projects/1/pullrequests/1/timeline").principal(outsiderAuth))
                    .andExpect(status().isForbidden)
            }

            it("존재하지 않는 PR 번호의 타임라인을 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(get("/api/projects/1/pullrequests/999/timeline").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests") {
            it("프로젝트 멤버인 유저가 새 PR을 제출하면 201 Created를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.createPullRequest("PR 제목", "PR 본문", 2L, 1L, "feature", "master", user) } returns pullRequest

                val jsonContent = """
                    {
                        "title": "PR 제목",
                        "body": "PR 본문",
                        "fromProjectId": 2,
                        "fromBranch": "feature",
                        "toBranch": "master"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isCreated)
            }

            // yona PullRequestApp.java:254 @IsCreatable(ResourceType.FORK) 대응 (P1-141) — 공개 프로젝트는
            // 로그인한 비멤버도 PR을 보낼 수 있어야 하는데, checkWritePermission(멤버/그룹멤버 전용)만
            // 쓰면 이 케이스가 차단돼 yona보다 과도하게 제한됐었다.
            it("공개 프로젝트는 로그인한 비멤버도 새 PR을 제출하면 201 Created를 반환해야 한다") {
                val publicProject = Project(id = 3L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val nonMember = User(id = 30L, loginId = "nonmember", name = "비멤버")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember", "password")

                every { projectRepository.findById(3L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("nonmember") } returns Optional.of(nonMember)
                every {
                    pullRequestService.createPullRequest("PR 제목", "PR 본문", 2L, 3L, "feature", "master", nonMember)
                } returns pullRequest

                val jsonContent = """
                    {
                        "title": "PR 제목",
                        "body": "PR 본문",
                        "fromProjectId": 2,
                        "fromBranch": "feature",
                        "toBranch": "master"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/3/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(nonMemberAuth)
                )
                    .andExpect(status().isCreated)
            }

            it("비공개 프로젝트는 로그인한 비멤버가 PR을 제출하면 403 Forbidden을 반환해야 한다") {
                val nonMember = User(id = 31L, loginId = "nonmember2", name = "비멤버2")
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmember2", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("nonmember2") } returns Optional.of(nonMember)

                val jsonContent = """
                    {
                        "title": "PR 제목",
                        "body": "PR 본문",
                        "fromProjectId": 2,
                        "fromBranch": "feature",
                        "toBranch": "master"
                    }
                """.trimIndent()

                mockMvc.perform(
                    post("/api/projects/1/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(nonMemberAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.createPullRequest(any(), any(), any(), any(), any(), any(), any()) }
            }

            it("존재하지 않는 프로젝트로 PR을 제출하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                val jsonContent = """{"title": "PR 제목", "body": "PR 본문", "fromProjectId": 2, "fromBranch": "feature", "toBranch": "master"}"""

                mockMvc.perform(
                    post("/api/projects/999/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 PR을 제출하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                val jsonContent = """{"title": "PR 제목", "body": "PR 본문", "fromProjectId": 2, "fromBranch": "feature", "toBranch": "master"}"""

                mockMvc.perform(
                    post("/api/projects/1/pullrequests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                )
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("PUT /api/projects/{projectId}/pullrequests/{number}") {
            it("작성자가 PR의 제목과 본문을 수정하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "feature", "master") } returns pullRequest

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                verify(exactly = 1) { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "feature", "master") }
            }

            // TASK-0420 — 실서버 재현: PR 생성 시 본문을 채우고 title만 바꾸는 `pr edit`을 실행하면
            // yona-cli는 Body *string 필드가 nil이라 JSON에 "body" 키 자체를 아예 생략한다
            // (`json:"body,omitempty"`). fromBranch/toBranch처럼 body도 null이면 기존 값으로
            // 폴백해야 하는데 그 폴백이 없어 본문이 통째로 지워지던 버그.
            it("요청 본문에 body가 아예 없으면(null) 기존 PR의 body를 그대로 유지해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { pullRequestService.updatePullRequest(50L, "제목만 수정", "PR 본문", "feature", "master") } returns pullRequest

                // body 키 자체가 없는 JSON — yona-cli가 --body를 생략했을 때와 동일한 페이로드.
                val jsonContent = """{"title": "제목만 수정"}"""

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)

                // "PR 본문"은 pullRequest 픽스처의 기존 body 값 — null이 그대로 서비스에 전달돼
                // 본문이 지워지지 않고 기존 값으로 폴백됐는지 확인한다.
                verify(exactly = 1) { pullRequestService.updatePullRequest(50L, "제목만 수정", "PR 본문", "feature", "master") }
            }

            it("컨트리뷰터가 아닌 일반 프로젝트 멤버가 수정해도 200 OK를 반환해야 한다 (P1-92, legacy는 프로젝트 멤버 전원 허용)") {
                val otherMember = User(id = 40L, loginId = "othermember3", name = "다른멤버3")
                otherMember.projectUsers.add(ProjectUser(id = 103L, user = User(id = 999_912L, loginId = "_membership_placeholder"), project = project, role = memberRole))
                val otherMemberAuth = UsernamePasswordAuthenticationToken("othermember3", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("othermember3") } returns Optional.of(otherMember)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "feature", "master") } returns pullRequest

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(otherMemberAuth)
                )
                    .andExpect(status().isOk)
            }

            it("컨트리뷰터라도 프로젝트 멤버가 아니면 403 Forbidden을 반환해야 한다 (legacy는 컨트리뷰터 자체를 author 우회 대상으로 두지 않음)") {
                val nonMemberContributor = User(id = 41L, loginId = "nonmembercontributor", name = "비멤버컨트리뷰터")
                val nonMemberPr = PullRequest(
                    id = 51L, title = "PR", body = "본문",
                    toProject = project, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature",
                    contributor = nonMemberContributor, state = State.OPEN, number = 2L
                )
                val nonMemberAuth = UsernamePasswordAuthenticationToken("nonmembercontributor", "password")

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("nonmembercontributor") } returns Optional.of(nonMemberContributor)
                every { pullRequestService.getPullRequest(1L, 2L) } returns nonMemberPr

                val jsonContent = """{"title": "수정 시도", "body": "본문"}"""

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(nonMemberAuth)
                )
                    .andExpect(status().isForbidden)
            }

            // yona PullRequest.updateWith()의 from/toBranch 재할당 대응 (P1-68).
            it("요청에 fromBranch/toBranch가 포함되면 브랜치 재할당까지 서비스에 전달해야 한다") {
                val rebranched = PullRequest(
                    id = 50L, title = "수정된 PR 제목", body = "수정된 PR 본문",
                    toProject = project, fromProject = fromProject,
                    toBranch = "release", fromBranch = "hotfix",
                    contributor = user, state = State.OPEN, number = 1L
                )

                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every { pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "hotfix", "release") } returns rebranched

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문",
                        "fromBranch": "hotfix",
                        "toBranch": "release"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.fromBranch").value("hotfix"))
                    .andExpect(jsonPath("$.toBranch").value("release"))
            }

            it("브랜치를 재할당했을 때 동일 조합의 PR이 이미 열려있으면 409 Conflict를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { projectUserRepository.findByProjectIdAndUserId(1L, 10L) } returns Optional.of(projectUser)
                every {
                    pullRequestService.updatePullRequest(50L, "수정된 PR 제목", "수정된 PR 본문", "hotfix", "release")
                } throws DuplicatedPullRequestException("중복된 PR")

                val jsonContent = """
                    {
                        "title": "수정된 PR 제목",
                        "body": "수정된 PR 본문",
                        "fromBranch": "hotfix",
                        "toBranch": "release"
                    }
                """.trimIndent()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent)
                        .principal(userAuth)
                )
                    .andExpect(status().isConflict)
            }

            it("존재하지 않는 프로젝트의 PR을 수정하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/999/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title": "제목", "body": "본문"}""")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 PR을 수정하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title": "제목", "body": "본문"}""")
                )
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호를 수정하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title": "제목", "body": "본문"}""")
                        .principal(userAuth)
                )
                    .andExpect(status().isNotFound)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/merge") {
            it("프로젝트 멤버가 PR 머지를 시도하면 200 OK와 머지 결과를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                
                val mergeResult = PullRequestMergeResult(pullRequest = pullRequest)
                every { pullRequestService.merge(50L, user) } returns mergeResult

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/merge")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("존재하지 않는 프로젝트에서 머지를 시도하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/pullrequests/1/merge").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 머지를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(post("/api/projects/1/pullrequests/1/merge"))
                    .andExpect(status().isUnauthorized)
            }

            it("프로젝트 멤버가 아니면 머지를 403 Forbidden으로 거부해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 70L) } returns false

                mockMvc.perform(post("/api/projects/1/pullrequests/1/merge").principal(outsiderAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.merge(any(), any()) }
            }

            it("존재하지 않는 PR 번호를 머지하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/pullrequests/999/merge").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        // yona AccessControl.isProjectResourceAllowed()의 PULL_REQUEST Operation.ACCEPT 분기 대응 (P1-78).
        describe("POST /api/projects/{projectId}/pullrequests/{number}/reviewers") {
            it("로그인한 프로젝트 멤버가 리뷰어로 참여하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.addReviewer(50L, user) } returns Unit

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/reviewers")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어로 등록할 수 없어야 한다(인가 우회 방지)") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
                val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false

                mockMvc.perform(
                    post("/api/projects/2/pullrequests/1/reviewers")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.addReviewer(any(), any()) }
            }

            it("존재하지 않는 프로젝트에 리뷰어를 등록하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/pullrequests/1/reviewers").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 리뷰어 등록을 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(post("/api/projects/1/pullrequests/1/reviewers"))
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호에 리뷰어를 등록하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/pullrequests/999/reviewers").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/reviewers") {
            it("로그인한 프로젝트 멤버가 리뷰어 해제를 요청하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.removeReviewer(50L, user) } returns Unit

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/reviewers")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("PUBLIC 프로젝트여도 멤버가 아니면 리뷰어를 해제할 수 없어야 한다(인가 우회 방지)") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
                val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false

                mockMvc.perform(
                    delete("/api/projects/2/pullrequests/1/reviewers")
                        .principal(otherAuth)
                )
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.removeReviewer(any(), any()) }
            }

            it("존재하지 않는 프로젝트에서 리뷰어를 해제하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/999/pullrequests/1/reviewers").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 리뷰어 해제를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/reviewers"))
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호의 리뷰어를 해제하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(delete("/api/projects/1/pullrequests/999/reviewers").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자 지정/해제.
        // addReviewer/removeReviewer와 동일한 권한 체크(checkWritePermission) 패턴을 그대로 검증한다.
        describe("PUT /api/projects/{projectId}/pullrequests/{number}/assignee") {
            it("로그인한 프로젝트 멤버가 담당자를 지정하면 200 OK와 갱신된 PR을 반환해야 한다") {
                val assigned = PullRequest(
                    id = 50L, title = "PR 제목", toProject = project, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, number = 1L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { userRepository.findById(10L) } returns Optional.of(user)
                every { pullRequestService.setAssignee(50L, user) } returns assigned

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":10}""")
                        .principal(userAuth)
                ).andExpect(status().isOk)
            }

            it("존재하지 않는 사용자를 담당자로 지정하려 하면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { userRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":999}""")
                        .principal(userAuth)
                ).andExpect(status().isBadRequest)

                verify(exactly = 0) { pullRequestService.setAssignee(any(), any()) }
            }

            it("PUBLIC 프로젝트여도 멤버가 아니면 담당자를 지정할 수 없어야 한다(인가 우회 방지)") {
                val publicProject = Project(id = 2L, name = "PublicProject", projectScope = ProjectScope.PUBLIC)
                val otherUser = User(id = 30L, loginId = "otheruser", name = "외부유저")
                val otherAuth = UsernamePasswordAuthenticationToken("otheruser", "password")

                every { projectRepository.findById(2L) } returns Optional.of(publicProject)
                every { userRepository.findByLoginId("otheruser") } returns Optional.of(otherUser)
                every { projectUserRepository.existsByProjectIdAndUserId(2L, 30L) } returns false

                mockMvc.perform(
                    put("/api/projects/2/pullrequests/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":10}""")
                        .principal(otherAuth)
                ).andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.setAssignee(any(), any()) }
            }

            it("비로그인 사용자가 담당자 지정을 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":10}""")
                ).andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호에 담당자를 지정하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(
                    put("/api/projects/1/pullrequests/999/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":10}""")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/assignee") {
            it("로그인한 프로젝트 멤버가 담당자 해제를 요청하면 200 OK를 반환해야 한다") {
                val cleared = PullRequest(
                    id = 50L, title = "PR 제목", toProject = project, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, number = 1L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.setAssignee(50L, null) } returns cleared

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/assignee").principal(userAuth)
                ).andExpect(status().isOk)
            }

            it("비로그인 사용자가 담당자 해제를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/assignee"))
                    .andExpect(status().isUnauthorized)
            }
        }

        // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 라벨 추가/제거.
        describe("POST /api/projects/{projectId}/pullrequests/{number}/labels") {
            it("로그인한 프로젝트 멤버가 라벨을 추가하면 200 OK와 갱신된 PR을 반환해야 한다") {
                val labeled = PullRequest(
                    id = 50L, title = "PR 제목", toProject = project, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, number = 1L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.addLabel(50L, 5L) } returns labeled

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"labelId":5}""")
                        .principal(userAuth)
                ).andExpect(status().isOk)
            }

            it("존재하지 않는 라벨을 추가하려 하면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.addLabel(50L, 999L) } throws IllegalArgumentException("IssueLabel not found: 999")

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"labelId":999}""")
                        .principal(userAuth)
                ).andExpect(status().isBadRequest)
            }

            it("비로그인 사용자가 라벨 추가를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/labels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"labelId":5}""")
                ).andExpect(status().isUnauthorized)
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/labels/{labelId}") {
            it("로그인한 프로젝트 멤버가 라벨을 제거하면 200 OK를 반환해야 한다") {
                val unlabeled = PullRequest(
                    id = 50L, title = "PR 제목", toProject = project, fromProject = fromProject,
                    toBranch = "master", fromBranch = "feature", contributor = user, number = 1L
                )
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.removeLabel(50L, 5L) } returns unlabeled

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/labels/5").principal(userAuth)
                ).andExpect(status().isOk)
            }

            it("비로그인 사용자가 라벨 제거를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/labels/5"))
                    .andExpect(status().isUnauthorized)
            }
        }

        describe("DELETE /api/projects/{projectId}/pullrequests/{number}/fromBranch") {
            it("PR 작성자가 원본 브랜치 삭제를 요청하면 200 OK와 갱신된 PR을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.deleteFromBranch(50L) } returns pullRequest

                mockMvc.perform(
                    delete("/api/projects/1/pullrequests/1/fromBranch")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            // isManagerOrContributor()가 false(계약자도 매니저도 아님)여도 checkWritePermission()의
            // 프로젝트 멤버 허용 분기로 통과하는 케이스 — !isManagerOrContributor && !checkWritePermission
            // 호출부의 두 서브식 조합 중 "멤버라서 허용"에 해당한다.
            it("계약자도 매니저도 아닌 일반 프로젝트 멤버도 원본 브랜치를 삭제할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("plainmember") } returns Optional.of(plainMemberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 60L) } returns Optional.of(
                    ProjectUser(id = 105L, user = plainMemberUser, project = project, role = memberRole)
                )
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 60L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.deleteFromBranch(50L) } returns pullRequest

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/fromBranch").principal(plainMemberAuth))
                    .andExpect(status().isOk)
            }

            it("계약자도 매니저도 프로젝트 멤버도 아니면 원본 브랜치 삭제를 403 Forbidden으로 거부해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 70L) } returns Optional.empty()
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 70L) } returns false
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/fromBranch").principal(outsiderAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.deleteFromBranch(any()) }
            }

            it("존재하지 않는 프로젝트의 원본 브랜치를 삭제하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(delete("/api/projects/999/pullrequests/1/fromBranch").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 원본 브랜치 삭제를 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(delete("/api/projects/1/pullrequests/1/fromBranch"))
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호의 원본 브랜치를 삭제하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(delete("/api/projects/1/pullrequests/999/fromBranch").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/fromBranch") {
            it("PR 작성자가 원본 브랜치 복원을 요청하면 200 OK와 갱신된 PR을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.restoreFromBranch(50L) } returns pullRequest

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/fromBranch")
                        .principal(userAuth)
                )
                    .andExpect(status().isOk)
            }

            it("계약자도 매니저도 아닌 일반 프로젝트 멤버도 원본 브랜치를 복원할 수 있어야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("plainmember") } returns Optional.of(plainMemberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 60L) } returns Optional.of(
                    ProjectUser(id = 106L, user = plainMemberUser, project = project, role = memberRole)
                )
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 60L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.restoreFromBranch(50L) } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/fromBranch").principal(plainMemberAuth))
                    .andExpect(status().isOk)
            }

            it("계약자도 매니저도 프로젝트 멤버도 아니면 원본 브랜치 복원을 403 Forbidden으로 거부해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 70L) } returns Optional.empty()
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 70L) } returns false
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/fromBranch").principal(outsiderAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.restoreFromBranch(any()) }
            }

            it("존재하지 않는 프로젝트의 원본 브랜치를 복원하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/pullrequests/1/fromBranch").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 원본 브랜치 복원을 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(post("/api/projects/1/pullrequests/1/fromBranch"))
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호의 원본 브랜치를 복원하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/pullrequests/999/fromBranch").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        describe("POST /api/projects/{projectId}/pullrequests/{number}/merge - 리뷰어 부족 케이스") {
            it("최소 리뷰어 수 미달 시 LackingReviewerException이 던져지면 400 Bad Request를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.merge(50L, user) } throws LackingReviewerException("리뷰어 부족")

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/merge")
                        .principal(userAuth)
                )
                    .andExpect(status().isBadRequest)
            }
        }

        // changeState()는 isManagerOrContributor(계약자 또는 매니저) 또는 checkWritePermission(일반 멤버)
        // 중 하나라도 허용하면 통과한다. 4가지 액터(계약자/매니저/일반멤버/비멤버)로 전체 조합을 검증한다.
        describe("POST /api/projects/{projectId}/pullrequests/{number}/state") {
            it("PR 작성자(계약자)가 상태를 변경하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.changeState(50L, State.CLOSED, "testuser") } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isOk)
            }

            // isManagerOrContributor()의 projectUserRepository.findByProjectIdAndUserId().map{ role==MANAGER }
            // 분기(true)를 검증 — 계약자가 아니어도 매니저면 checkWritePermission() 평가 없이 허용된다.
            it("계약자가 아닌 프로젝트 매니저가 상태를 변경하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("manageruser") } returns Optional.of(managerUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 20L) } returns Optional.of(
                    ProjectUser(id = 107L, user = managerUser, project = project, role = managerRole)
                )
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.changeState(50L, State.CLOSED, "manageruser") } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/state").param("state", "CLOSED").principal(managerAuth))
                    .andExpect(status().isOk)
            }

            it("계약자도 매니저도 아닌 일반 프로젝트 멤버가 상태를 변경하면 200 OK를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("plainmember") } returns Optional.of(plainMemberUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 60L) } returns Optional.of(
                    ProjectUser(id = 108L, user = plainMemberUser, project = project, role = memberRole)
                )
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 60L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.changeState(50L, State.CLOSED, "plainmember") } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/state").param("state", "CLOSED").principal(plainMemberAuth))
                    .andExpect(status().isOk)
            }

            it("계약자도 매니저도 프로젝트 멤버도 아니면 상태 변경을 403 Forbidden으로 거부해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)
                every { projectUserRepository.findByProjectIdAndUserId(1L, 70L) } returns Optional.empty()
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 70L) } returns false
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest

                mockMvc.perform(post("/api/projects/1/pullrequests/1/state").param("state", "CLOSED").principal(outsiderAuth))
                    .andExpect(status().isForbidden)

                verify(exactly = 0) { pullRequestService.changeState(any(), any(), any()) }
            }

            it("존재하지 않는 프로젝트의 PR 상태를 변경하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(post("/api/projects/999/pullrequests/1/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비로그인 사용자가 PR 상태 변경을 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(post("/api/projects/1/pullrequests/1/state").param("state", "CLOSED"))
                    .andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 PR 번호의 상태를 변경하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 999L) } returns null

                mockMvc.perform(post("/api/projects/1/pullrequests/999/state").param("state", "CLOSED").principal(userAuth))
                    .andExpect(status().isNotFound)
            }
        }

        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr diff` 대응.
        describe("GET /api/projects/{projectId}/pullrequests/{number}/diff") {
            it("PR의 diff 목록을 반환해야 한다") {
                val diffs = listOf(com.github.yonaprojects.yona.domain.vcs.FileDiff())
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.getDiff(pullRequest) } returns diffs

                mockMvc.perform(get("/api/projects/1/pullrequests/1/diff").principal(userAuth))
                    .andExpect(status().isOk)

                verify(exactly = 1) { pullRequestService.getDiff(pullRequest) }
            }

            // TASK-0419 — FileDiff 엔티티를 그대로 반환하면 JGit 내부 타입(RawText/EditList/FileMode)이
            // 노출된다(그중 RawText는 rawContent라는 이름의 byte[] 필드를 base64로 노출). 응답이
            // pathA/pathB/changeType/patch 같은 단순 필드로만 구성된 FileDiffResponse여야 하고,
            // JGit 내부 표현(rawContent/editList)은 응답에 아예 없어야 한다.
            it("diff 응답은 JGit 내부 타입을 노출하지 않고 pathA/pathB/changeType/patch만 담은 단순 필드여야 한다") {
                val diff = com.github.yonaprojects.yona.domain.vcs.FileDiff().apply {
                    pathA = "a.txt"
                    pathB = "a.txt"
                    changeType = org.eclipse.jgit.diff.DiffEntry.ChangeType.MODIFY
                    a = org.eclipse.jgit.diff.RawText(byteArrayOf('o'.code.toByte(), '\n'.code.toByte()))
                    b = org.eclipse.jgit.diff.RawText(byteArrayOf('n'.code.toByte(), '\n'.code.toByte()))
                    editList = org.eclipse.jgit.diff.EditList().apply {
                        add(org.eclipse.jgit.diff.Edit(0, 1, 0, 1))
                    }
                }
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { projectUserRepository.existsByProjectIdAndUserId(1L, 10L) } returns true
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every { pullRequestService.getDiff(pullRequest) } returns listOf(diff)

                val body = mockMvc.perform(get("/api/projects/1/pullrequests/1/diff").principal(userAuth))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "\"pathA\":\"a.txt\""
                body shouldContain "\"changeType\":\"MODIFY\""
                body shouldContain "\"patch\""
                body shouldNotContain "rawContent"
                body shouldNotContain "editList"
            }

            it("존재하지 않는 프로젝트의 diff를 조회하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(get("/api/projects/999/pullrequests/1/diff").principal(userAuth))
                    .andExpect(status().isNotFound)
            }

            it("비공개 프로젝트를 비멤버가 diff 조회하면 403 Forbidden을 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("outsider") } returns Optional.of(outsiderUser)

                mockMvc.perform(get("/api/projects/1/pullrequests/1/diff").principal(outsiderAuth))
                    .andExpect(status().isForbidden)
            }
        }

        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr comment` 대응.
        describe("POST /api/projects/{projectId}/pullrequests/{number}/comments") {
            it("로그인한 사용자는 PR에 댓글을 남길 수 있다(멤버십과 무관 - REVIEW_COMMENT는 항상 생성 가능)") {
                val comment = com.github.yonaprojects.yona.domain.pullrequest.ReviewComment(id = 5L, contents = "댓글")
                every { projectRepository.findById(1L) } returns Optional.of(project)
                every { userRepository.findByLoginId("testuser") } returns Optional.of(user)
                every { pullRequestService.getPullRequest(1L, 1L) } returns pullRequest
                every {
                    codeReviewService.createReviewComment(project, pullRequest, null, "댓글", null, null, user)
                } returns comment

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"body":"댓글"}""")
                        .principal(userAuth)
                ).andExpect(status().isCreated)
                    .andExpect(jsonPath("$.id").value(5))
            }

            it("비로그인 사용자가 댓글을 시도하면 401 Unauthorized를 반환해야 한다") {
                every { projectRepository.findById(1L) } returns Optional.of(project)

                mockMvc.perform(
                    post("/api/projects/1/pullrequests/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"body":"댓글"}""")
                ).andExpect(status().isUnauthorized)
            }

            it("존재하지 않는 프로젝트에 댓글을 시도하면 404 Not Found를 반환해야 한다") {
                every { projectRepository.findById(999L) } returns Optional.empty()

                mockMvc.perform(
                    post("/api/projects/999/pullrequests/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"body":"댓글"}""")
                        .principal(userAuth)
                ).andExpect(status().isNotFound)
            }
        }
    }
})
