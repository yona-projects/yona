package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.config.ApiTokenAuthenticationFilter
import com.github.yonaprojects.yona.config.security.AccessControl
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.organization.OrganizationRepository
import com.github.yonaprojects.yona.domain.organization.OrganizationUserRepository
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.pullrequest.ReviewCommentRepository
import com.github.yonaprojects.yona.domain.pullrequest.CommitCommentRepository
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectService
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.watch.WatchService
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step6 — ProjectRestApiController(/api/v1/projects/{owner}[/{project}])는 새
// 서비스 로직 없이 ProjectRepository + 실제 AccessControl(웹 UI와 동일한 가시성 규칙)만으로
// 구성된다. 이 스펙은 공개/비공개 프로젝트 가시성 규칙과 404/403 분기를 검증한다.
//
// AccessControl 기반 익명/비멤버 403·404 케이스로 "권한 없는 요청 거부"를 검증한다(개별 조회는
// ApiTokenAuthenticationFilter가 이미 스코프 밖 요청을 403으로 막으므로 이 컨트롤러 자체는 별도
// 스코프 검증이 필요 없다 — 필터 레벨 검증은 config/ApiTokenScopedMetadataAndListAuthorizationIntegrationSpec
// 참고).
//
// yona-wiki P3-02 Step6.5 — 목록(list())은 request attribute(SCOPED_API_TOKEN_ATTRIBUTE)로 넘어온
// ApiToken에 따라 AccessControl 통과 목록을 한 번 더 좁힌다. 아래 "GET /api/v1/projects/{owner} -
// 스코프 토큰 기반 필터링(Step6.5)" describe가 이 분기(속성 없음/전체스코프/선택스코프)를 컨트롤러
// 단위로 검증한다.
class ProjectRestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val userRepository = mockk<UserRepository>()
    val projectUserRepository = mockk<ProjectUserRepository>()
    val organizationUserRepository = mockk<OrganizationUserRepository>()
    every { organizationUserRepository.findByOrganizationIdAndUserId(any(), any()) } returns Optional.empty()
    val organizationRepository = mockk<OrganizationRepository>()
    val issueRepository = mockk<IssueRepository>()
    val postingRepository = mockk<PostingRepository>()
    val reviewCommentRepository = mockk<ReviewCommentRepository>()
    val commitCommentRepository = mockk<CommitCommentRepository>()
    val milestoneRepository = mockk<MilestoneRepository>()
    val accessControl = AccessControl(
        projectUserRepository, organizationUserRepository,
        userRepository, organizationRepository,
        issueRepository, postingRepository,
        reviewCommentRepository, commitCommentRepository,
        milestoneRepository
    )

    val projectService = mockk<ProjectService>()
    val watchService = mockk<WatchService>()
    val projectController = mockk<ProjectController>()

    val controller = ProjectRestApiController(
        projectRepository, userRepository, accessControl,
        projectService, organizationRepository, watchService, projectController
    )
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, userRepository, projectUserRepository, projectService, watchService, projectController)
        every { organizationRepository.findByName(any()) } returns Optional.empty()
    }

    val publicProject = Project(id = 1L, owner = "yona", name = "public-repo", overview = "공개 저장소", projectScope = ProjectScope.PUBLIC)
    val privateProject = Project(id = 2L, owner = "yona", name = "private-repo", overview = "비공개 저장소", projectScope = ProjectScope.PRIVATE)
    val outsider = User(id = 10L, loginId = "outsider", name = "외부인")
    val auth = UsernamePasswordAuthenticationToken("outsider", "password")

    describe("GET /api/v1/projects/{owner}") {
        it("비로그인 상태에서도 공개 프로젝트만 목록에 포함한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, privateProject)

            mockMvc.perform(get("/api/v1/projects/yona"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("public-repo"))
        }
    }

    describe("GET /api/v1/projects/{owner}/{project}") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/unknown"))
                .andExpect(status().isNotFound)
        }

        it("공개 프로젝트는 비로그인 사용자도 조회할 수 있다") {
            every { projectRepository.findByOwnerAndName("yona", "public-repo") } returns Optional.of(publicProject)

            mockMvc.perform(get("/api/v1/projects/yona/public-repo"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.name").value("public-repo"))
        }

        it("비공개 프로젝트는 멤버가 아닌 로그인 사용자에게 403을 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "private-repo") } returns Optional.of(privateProject)
            every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)

            mockMvc.perform(get("/api/v1/projects/yona/private-repo").principal(auth))
                .andExpect(status().isForbidden)
        }
    }

    // yona-wiki P3-02 Step6.5 — "프로젝트 목록 API 스코프 필터링 설계" 검증. request attribute에
    // ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE로 넘어온 ApiToken 유무/종류에 따라
    // AccessControl 통과 목록을 한 번 더 좁혀야 한다.
    describe("GET /api/v1/projects/{owner} - 스코프 토큰 기반 필터링(Step6.5)") {
        val otherPublicProject = Project(id = 3L, owner = "yona", name = "public-repo-2", projectScope = ProjectScope.PUBLIC)

        it("SCOPED_API_TOKEN 속성이 없으면(세션/레거시 인증) 기존 AccessControl 통과 목록을 그대로 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)

            mockMvc.perform(get("/api/v1/projects/yona"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        it("전체 저장소 스코프(allRepositories=true) 토큰이면 AccessControl 통과 목록을 모두 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)
            val allRepoToken = ApiToken(owner = null, tokenHash = "irrelevant", allRepositories = true)

            mockMvc.perform(
                get("/api/v1/projects/yona")
                    .requestAttr(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE, allRepoToken)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(2))
        }

        it("선택 저장소 스코프 토큰이면 scopedProjects에 포함된 프로젝트만 반환한다") {
            every { projectRepository.findByOwner("yona") } returns listOf(publicProject, otherPublicProject)
            val selectiveToken = ApiToken(
                owner = null,
                tokenHash = "irrelevant",
                allRepositories = false,
                scopedProjects = mutableSetOf(otherPublicProject)
            )

            mockMvc.perform(
                get("/api/v1/projects/yona")
                    .requestAttr(ApiTokenAuthenticationFilter.SCOPED_API_TOKEN_ATTRIBUTE, selectiveToken)
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("public-repo-2"))
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona project create`. 이 엔드포인트는
    // ApiTokenAuthenticationFilter의 어떤 패턴과도 매칭되지 않는 `POST /api/v1/projects`(세그먼트
    // 없음)라 Fine-grained 스코프 토큰으로는 호출할 수 없고 세션/레거시 전권 토큰만 가능하다
    // (GitHub Fine-grained PAT도 저장소 생성 자체는 지원하지 않는 것과 동일한 제약 - 의도적 설계,
    // 계획 문서 리스크 표 참고).
    describe("POST /api/v1/projects") {
        it("로그인하지 않으면 401을 반환한다") {
            mockMvc.perform(
                post("/api/v1/projects")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"owner":"outsider","name":"new-repo","overview":"설명","projectScope":"PUBLIC","vcs":"GIT"}""")
            ).andExpect(status().isUnauthorized)
        }

        it("owner가 기존 조직명이고 조직 관리자가 아니면 403을 반환한다") {
            every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)
            val org = com.github.yonaprojects.yona.domain.organization.Organization(id = 1L, name = "someorg")
            every { organizationRepository.findByName("someorg") } returns Optional.of(org)

            mockMvc.perform(
                post("/api/v1/projects")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"owner":"someorg","name":"new-repo","overview":"설명","projectScope":"PUBLIC","vcs":"GIT"}""")
            ).andExpect(status().isForbidden)
        }

        it("ProjectService.createProject에 위임하고 생성된 프로젝트를 반환한다") {
            every { userRepository.findByLoginId("outsider") } returns Optional.of(outsider)
            val created = Project(id = 5L, owner = "outsider", name = "new-repo", overview = "설명", projectScope = ProjectScope.PUBLIC)
            every { projectService.createProject(any(), any()) } returns created
            every { watchService.watch(any(), any(), any()) } returns Unit

            mockMvc.perform(
                post("/api/v1/projects")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"owner":"outsider","name":"new-repo","overview":"설명","projectScope":"PUBLIC","vcs":"GIT"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("new-repo"))

            verify(exactly = 1) { projectService.createProject(any(), any()) }
            verify(exactly = 1) { watchService.watch(outsider, any(), "5") }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/fork") {
        it("ProjectController.forkProject에 위임한다") {
            every { projectController.forkProject("yona", "public-repo", any(), any()) } returns ResponseEntity.ok(publicProject)

            mockMvc.perform(post("/api/v1/projects/yona/public-repo/fork").principal(auth))
                .andExpect(status().isOk)

            verify(exactly = 1) { projectController.forkProject("yona", "public-repo", any(), any()) }
        }

        it("요청 바디의 destinationOwner를 ProjectController.forkProject에 그대로 전달한다") {
            every { projectController.forkProject("yona", "public-repo", any(), any()) } returns ResponseEntity.ok(publicProject)

            mockMvc.perform(
                post("/api/v1/projects/yona/public-repo/fork").principal(auth)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("""{"destinationOwner":"some-org"}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) {
                projectController.forkProject(
                    "yona", "public-repo",
                    ProjectController.ForkProjectRequest(destinationOwner = "some-org", destinationName = null),
                    any()
                )
            }
        }
    }

    describe("PATCH /api/v1/projects/{owner}/{project}/settings") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(
                patch("/api/v1/projects/yona/unknown/settings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"overview":"설명","projectScope":"PUBLIC"}""")
            ).andExpect(status().isNotFound)
        }

        it("ProjectController.updateProject에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "public-repo") } returns Optional.of(publicProject)
            every { projectController.updateProject(1L, any(), any()) } returns ResponseEntity.ok(publicProject)

            mockMvc.perform(
                patch("/api/v1/projects/yona/public-repo/settings")
                    .principal(auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"overview":"새 설명","projectScope":"PUBLIC"}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) { projectController.updateProject(1L, any(), any()) }
        }
    }

    describe("DELETE /api/v1/projects/{owner}/{project}/settings") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(delete("/api/v1/projects/yona/unknown/settings"))
                .andExpect(status().isNotFound)
        }

        it("ProjectController.deleteProject에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "public-repo") } returns Optional.of(publicProject)
            every { projectController.deleteProject(1L, any()) } returns ResponseEntity.ok(mapOf("status" to "success"))

            mockMvc.perform(delete("/api/v1/projects/yona/public-repo/settings").principal(auth))
                .andExpect(status().isOk)

            verify(exactly = 1) { projectController.deleteProject(1L, any()) }
        }
    }
})
