package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step4 — IssueRestApiController(/api/v1/projects/{owner}/{project}/issues)는
// owner/project 이름으로 프로젝트를 찾아 기존 IssueController/CommentController에 위임하는 얇은
// 어댑터라, 이 스펙은 "제대로 위임하는지"와 "프로젝트를 못 찾으면 404"만 검증한다(실제 업무 로직/
// 권한 체크는 IssueControllerSpec/CommentController 쪽에서 이미 검증됨). 스코프 토큰 인가(403)는
// 필터 레벨 검증이라 별도 통합테스트(ApiTokenScopedIssueAndPullRequestApiIntegrationSpec)에서 다룬다.
class IssueRestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueController = mockk<IssueController>()
    val commentController = mockk<CommentController>()

    val controller = IssueRestApiController(projectRepository, issueController, commentController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
        .build()

    beforeTest {
        clearMocks(projectRepository, issueController, commentController)
    }

    val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
    val auth = UsernamePasswordAuthenticationToken("testuser", "password")

    describe("GET /api/v1/projects/{owner}/{project}/issues") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/unknown/issues"))
                .andExpect(status().isNotFound)
        }

        it("존재하면 IssueController.getIssues에 위임한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            // P3-30 — IssueController.getIssues()는 이제 Page<IssueResponse>를 반환한다(raw Issue
            // 순환 직렬화/비밀번호 노출 방지).
            val page = PageImpl(listOf(issue.toResponse()), PageRequest.of(0, 15), 1)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.getIssues(1L, null, null, null, null, any<Pageable>(), any()) } returns ResponseEntity.ok(page)

            mockMvc.perform(get("/api/v1/projects/yona/yona/issues"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content[0].id").value(5))

            verify(exactly = 1) { issueController.getIssues(1L, null, null, null, null, any<Pageable>(), any()) }
        }

        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `--assignee`/`--label`/`--author` 필터.
        it("assignee/label/author 쿼리 파라미터를 IssueController.getIssues에 그대로 전달한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            val page = PageImpl(listOf(issue.toResponse()), PageRequest.of(0, 15), 1)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                issueController.getIssues(1L, null, "alice", "bug", "bob", any<Pageable>(), any())
            } returns ResponseEntity.ok(page)

            mockMvc.perform(
                get("/api/v1/projects/yona/yona/issues")
                    .param("assignee", "alice")
                    .param("label", "bug")
                    .param("author", "bob")
            ).andExpect(status().isOk)

            verify(exactly = 1) { issueController.getIssues(1L, null, "alice", "bug", "bob", any<Pageable>(), any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/issues") {
        it("IssueController.createIssue에 위임한다") {
            val issue = Issue(id = 7L, number = 1L, title = "새 이슈", project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.createIssue(1L, any(), any()) } returns ResponseEntity.status(HttpStatus.CREATED).body(issue)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/issues")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"새 이슈","body":"내용"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(7))

            verify(exactly = 1) { issueController.createIssue(1L, IssueController.CreateIssueRequest(title = "새 이슈", body = "내용", milestoneId = null, assigneeId = null, labelIds = null), any()) }
        }
    }

    describe("GET /api/v1/projects/{owner}/{project}/issues/{number}") {
        it("IssueController.getIssue에 위임한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.getIssue(1L, 5L, any()) } returns ResponseEntity.ok(issue)

            mockMvc.perform(get("/api/v1/projects/yona/yona/issues/5"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(5))
        }
    }

    describe("PATCH /api/v1/projects/{owner}/{project}/issues/{number}") {
        it("IssueController.updateIssue에 위임한다") {
            val issue = Issue(id = 5L, number = 5L, title = "수정된 제목", project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.updateIssue(1L, 5L, any(), any()) } returns ResponseEntity.ok(issue)

            mockMvc.perform(
                patch("/api/v1/projects/yona/yona/issues/5")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"수정된 제목","body":"내용"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("수정된 제목"))
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/issues/{number}/comments") {
        it("CommentController.createIssueComment에 위임한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            val comment = IssueComment(id = 9L, contents = "댓글", issue = issue)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { commentController.createIssueComment(1L, 5L, any(), any()) } returns ResponseEntity.status(HttpStatus.CREATED).body(comment)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/issues/5/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"contents":"댓글"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(9))
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/issues/{number}/close") {
        it("IssueController.changeState를 CLOSED로 호출한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", state = State.CLOSED, project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.changeState(1L, 5L, State.CLOSED, any()) } returns ResponseEntity.ok(issue)

            mockMvc.perform(post("/api/v1/projects/yona/yona/issues/5/close"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("CLOSED"))

            verify(exactly = 1) { issueController.changeState(1L, 5L, State.CLOSED, any()) }
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh issue reopen`.
    describe("POST /api/v1/projects/{owner}/{project}/issues/{number}/reopen") {
        it("IssueController.changeState를 OPEN으로 호출한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", state = State.OPEN, project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { issueController.changeState(1L, 5L, State.OPEN, any()) } returns ResponseEntity.ok(issue)

            mockMvc.perform(post("/api/v1/projects/yona/yona/issues/5/reopen"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.state").value("OPEN"))

            verify(exactly = 1) { issueController.changeState(1L, 5L, State.OPEN, any()) }
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh issue transfer`.
    describe("POST /api/v1/projects/{owner}/{project}/issues/{number}/transfer") {
        it("대상 프로젝트 이름을 ID로 변환해 IssueController.moveIssue에 위임한다") {
            val targetProject = Project(id = 2L, owner = "other", name = "target", projectScope = ProjectScope.PUBLIC)
            val moved = Issue(id = 5L, number = 1L, title = "제목", project = targetProject)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { projectRepository.findByOwnerAndName("other", "target") } returns Optional.of(targetProject)
            every {
                issueController.moveIssue(1L, 5L, IssueController.MoveIssueRequest(2L), any())
            } returns ResponseEntity.ok(moved)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/issues/5/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"targetOwner":"other","targetProject":"target"}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) { issueController.moveIssue(1L, 5L, IssueController.MoveIssueRequest(2L), any()) }
        }

        it("대상 프로젝트가 없으면 400을 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { projectRepository.findByOwnerAndName("other", "unknown") } returns Optional.empty()

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/issues/5/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"targetOwner":"other","targetProject":"unknown"}""")
            ).andExpect(status().isBadRequest)
        }

        it("원본 프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(
                post("/api/v1/projects/yona/unknown/issues/5/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"targetOwner":"other","targetProject":"target"}""")
            ).andExpect(status().isNotFound)
        }
    }
})
