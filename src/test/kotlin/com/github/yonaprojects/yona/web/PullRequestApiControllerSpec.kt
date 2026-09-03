package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestMergeResult
import com.github.yonaprojects.yona.domain.pullrequest.ReviewComment
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.vcs.FileDiff
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

// yona-wiki P3-02 Step5 — PullRequestApiController(/api/v1/projects/{owner}/{project}/pull-requests)도
// IssueRestApiController와 동일하게 owner/project 이름을 숫자 projectId로 바꿔 기존
// PullRequestController에 위임하는 얇은 어댑터다. 이 스펙은 위임/404 처리만 검증한다 — 머지/리뷰어
// 등록 자체의 업무 로직(Git 머지, 리뷰어 수 검증 등)은 PullRequestControllerSpec/
// PullRequestServiceSpec에서 이미 검증됨. 스코프 토큰 403은 별도 통합테스트에서 다룬다.
class PullRequestApiControllerSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestController = mockk<PullRequestController>()

    val controller = PullRequestApiController(projectRepository, pullRequestController)
    val mockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    beforeTest {
        clearMocks(projectRepository, pullRequestController)
    }

    val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
    val contributor = User(id = 10L, loginId = "contributor", name = "기여자")

    describe("GET /api/v1/projects/{owner}/{project}/pull-requests") {
        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(get("/api/v1/projects/yona/unknown/pull-requests"))
                .andExpect(status().isNotFound)
        }

        it("PullRequestController.getPullRequests에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.getPullRequests(1L, null, null, null, null, any()) } returns ResponseEntity.ok(listOf(pr))

            mockMvc.perform(get("/api/v1/projects/yona/yona/pull-requests"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(3))

            verify(exactly = 1) { pullRequestController.getPullRequests(1L, null, null, null, null, any()) }
        }

        it("author 쿼리 파라미터를 PullRequestController.getPullRequests에 그대로 전달한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.getPullRequests(1L, null, "contributor", null, null, any()) } returns ResponseEntity.ok(listOf(pr))

            mockMvc.perform(get("/api/v1/projects/yona/yona/pull-requests").param("author", "contributor"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.getPullRequests(1L, null, "contributor", null, null, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests") {
        it("PullRequestController.createPullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "새 PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.createPullRequest(1L, any(), any()) } returns ResponseEntity.status(HttpStatus.CREATED).body(pr)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/pull-requests")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"새 PR","body":"내용","fromProjectId":1,"fromBranch":"feature","toBranch":"main"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(3))
        }
    }

    describe("GET /api/v1/projects/{owner}/{project}/pull-requests/{number}") {
        it("PullRequestController.getPullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.getPullRequest(1L, 1L, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(get("/api/v1/projects/yona/yona/pull-requests/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.number").value(1))
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/merge") {
        it("PullRequestController.mergePullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            val mergeResult = PullRequestMergeResult(pullRequest = pr)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.mergePullRequest(1L, 1L, any()) } returns ResponseEntity.ok(mergeResult)

            mockMvc.perform(post("/api/v1/projects/yona/yona/pull-requests/1/merge"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.mergePullRequest(1L, 1L, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/reviewers") {
        it("PullRequestController.addReviewer에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.addReviewer(1L, 1L, any()) } returns ResponseEntity.ok().build()

            mockMvc.perform(post("/api/v1/projects/yona/yona/pull-requests/1/reviewers"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.addReviewer(1L, 1L, any()) }
        }
    }

    // yona-wiki P3-02 12라운드(2026-09-01, 실측으로 발견한 실제 기능 갭) — addReviewer만 있고
    // removeReviewer 어댑터가 없어 리뷰어 등록 취소를 v1 API/CLI로 할 방법이 없었다.
    describe("DELETE /api/v1/projects/{owner}/{project}/pull-requests/{number}/reviewers") {
        it("PullRequestController.removeReviewer에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.removeReviewer(1L, 1L, any()) } returns ResponseEntity.ok().build()

            mockMvc.perform(delete("/api/v1/projects/yona/yona/pull-requests/1/reviewers"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.removeReviewer(1L, 1L, any()) }
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr edit`. 재검증 결과 서비스/컨트롤러 로직
    // 자체는 이미 있었고(PullRequestController.updatePullRequest, PUT) 이 신규 REST API에 PATCH
    // 위임 어댑터만 없었다.
    describe("PATCH /api/v1/projects/{owner}/{project}/pull-requests/{number}") {
        it("PullRequestController.updatePullRequest에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "수정된 PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.updatePullRequest(1L, 1L, any(), any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(
                patch("/api/v1/projects/yona/yona/pull-requests/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"수정된 PR","body":"내용"}""")
            ).andExpect(status().isOk)
                .andExpect(jsonPath("$.title").value("수정된 PR"))

            verify(exactly = 1) { pullRequestController.updatePullRequest(1L, 1L, any(), any()) }
        }

        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(
                patch("/api/v1/projects/yona/unknown/pull-requests/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"t","body":"b"}""")
            ).andExpect(status().isNotFound)
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr close`/`gh pr reopen`. 재검증 결과
    // 서버에는 이미 범용 상태변경 API(PullRequestController.changeState)가 존재했다.
    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/close") {
        it("PullRequestController.changeState(CLOSED)에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor, state = State.CLOSED)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.changeState(1L, 1L, State.CLOSED, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(post("/api/v1/projects/yona/yona/pull-requests/1/close"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.changeState(1L, 1L, State.CLOSED, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/reopen") {
        it("PullRequestController.changeState(OPEN)에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor, state = State.OPEN)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.changeState(1L, 1L, State.OPEN, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(post("/api/v1/projects/yona/yona/pull-requests/1/reopen"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.changeState(1L, 1L, State.OPEN, any()) }
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr diff`.
    describe("GET /api/v1/projects/{owner}/{project}/pull-requests/{number}/diff") {
        it("PullRequestController.getDiff에 위임한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.getDiff(1L, 1L, any()) } returns ResponseEntity.ok(listOf(FileDiff().toResponse()))

            mockMvc.perform(get("/api/v1/projects/yona/yona/pull-requests/1/diff"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.getDiff(1L, 1L, any()) }
        }
    }

    // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `gh pr comment`.
    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/comments") {
        it("PullRequestController.addComment에 위임한다") {
            val comment = ReviewComment(id = 9L, contents = "댓글")
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.addComment(1L, 1L, any(), any()) } returns ResponseEntity.status(HttpStatus.CREATED).body(comment)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/pull-requests/1/comments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"body":"댓글"}""")
            ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(9))

            verify(exactly = 1) { pullRequestController.addComment(1L, 1L, any(), any()) }
        }
    }

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자/라벨 CRUD 어댑터.
    describe("PUT /api/v1/projects/{owner}/{project}/pull-requests/{number}/assignee") {
        it("PullRequestController.setAssignee에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.setAssignee(1L, 1L, any(), any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(
                put("/api/v1/projects/yona/yona/pull-requests/1/assignee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":10}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.setAssignee(1L, 1L, any(), any()) }
        }

        it("프로젝트가 없으면 404를 반환한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            mockMvc.perform(
                put("/api/v1/projects/yona/unknown/pull-requests/1/assignee")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":10}""")
            ).andExpect(status().isNotFound)
        }
    }

    describe("DELETE /api/v1/projects/{owner}/{project}/pull-requests/{number}/assignee") {
        it("PullRequestController.removeAssignee에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.removeAssignee(1L, 1L, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(delete("/api/v1/projects/yona/yona/pull-requests/1/assignee"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.removeAssignee(1L, 1L, any()) }
        }
    }

    describe("POST /api/v1/projects/{owner}/{project}/pull-requests/{number}/labels") {
        it("PullRequestController.addLabel에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.addLabel(1L, 1L, any(), any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(
                post("/api/v1/projects/yona/yona/pull-requests/1/labels")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"labelId":5}""")
            ).andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.addLabel(1L, 1L, any(), any()) }
        }
    }

    describe("DELETE /api/v1/projects/{owner}/{project}/pull-requests/{number}/labels/{labelId}") {
        it("PullRequestController.removeLabel에 위임한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.removeLabel(1L, 1L, 5L, any()) } returns ResponseEntity.ok(pr)

            mockMvc.perform(delete("/api/v1/projects/yona/yona/pull-requests/1/labels/5"))
                .andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.removeLabel(1L, 1L, 5L, any()) }
        }
    }

    // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — `gh pr list --assignee/--label` 위임 확인.
    describe("GET /api/v1/projects/{owner}/{project}/pull-requests (assignee/label 필터)") {
        it("assignee/label 쿼리 파라미터를 PullRequestController.getPullRequests에 그대로 전달한다") {
            val pr = PullRequest(id = 3L, number = 1L, title = "PR", fromProject = project, toProject = project, contributor = contributor)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { pullRequestController.getPullRequests(1L, null, null, "assignee-login", "bug", any()) } returns ResponseEntity.ok(listOf(pr))

            mockMvc.perform(
                get("/api/v1/projects/yona/yona/pull-requests")
                    .param("assignee", "assignee-login")
                    .param("label", "bug")
            ).andExpect(status().isOk)

            verify(exactly = 1) { pullRequestController.getPullRequests(1L, null, null, "assignee-login", "bug", any()) }
        }
    }
})
