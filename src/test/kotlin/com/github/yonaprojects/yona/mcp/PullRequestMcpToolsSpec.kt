package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.web.PullRequestReviewResponse
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.web.PullRequestController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

// yona-wiki P3-07(MCP 서버) Step5 — merge_pull_request는 계획 문서 리스크 표 1순위(AI 에이전트가
// PR을 임의 머지할 위험)라, "스코프가 없으면 PullRequestController.mergePullRequest()가 절대
// 호출되지 않는다"는 것을 다른 어떤 도구보다도 명확하게 검증한다. checkBranchProtectionForMerge()
// 자체([[p3-04-branch-protection]])는 PullRequestServiceImplSpec에서 이미 검증돼 있으므로 여기서는
// "이 도구가 그 경로를 그대로 타는지"(같은 컨트롤러 메서드 호출)만 확인한다.
class PullRequestMcpToolsSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val pullRequestController = mockk<PullRequestController>()
    val scopeGuard = mockk<McpScopeGuard>()
    val tools = PullRequestMcpTools(projectRepository, pullRequestController, scopeGuard)

    val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
    val auth = UsernamePasswordAuthenticationToken("tester", "password")

    beforeTest {
        clearMocks(projectRepository, pullRequestController, scopeGuard)
        SecurityContextHolder.getContext().authentication = auth
    }

    describe("merge_pull_request") {
        it("PULL_REQUESTS:WRITE 스코프를 검증한 뒤 PullRequestController.mergePullRequest에 위임해야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit
            every { pullRequestController.mergePullRequest(1L, 3L, auth) } returns ResponseEntity.ok(mapOf("merged" to true))

            tools.merge_pull_request("yona", "yona", 3L)

            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) }
            verify(exactly = 1) { pullRequestController.mergePullRequest(1L, 3L, auth) }
        }

        it("WRITE 스코프가 없으면 mergePullRequest를 절대 호출하지 않아야 한다(브랜치 보호 우회 방지 1차 방어선)") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.merge_pull_request("yona", "yona", 3L) }

            verify(exactly = 0) { pullRequestController.mergePullRequest(any(), any(), any()) }
        }

        it("READ 스코프만 있으면 거부되어야 한다(read로는 머지 불가)") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("read-only token")

            shouldThrow<AccessDeniedException> { tools.merge_pull_request("yona", "yona", 3L) }

            verify(exactly = 0) { pullRequestController.mergePullRequest(any(), any(), any()) }
        }
    }

    describe("list_pull_requests") {
        it("PULL_REQUESTS:READ 스코프를 검증한 뒤 위임해야 한다") {
            val pr = PullRequest(
                id = 2L, number = 3L, title = "제목",
                toProject = project, fromProject = project,
                fromBranch = "feature", toBranch = "main",
                contributor = User(id = 9L, loginId = "author", name = "작성자", email = "author@example.com")
            )
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ, project) } returns Unit
            every { pullRequestController.getPullRequests(1L, null, null, null, null, auth) } returns ResponseEntity.ok(listOf(pr))

            tools.list_pull_requests("yona", "yona", null)

            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ, project) }
        }
    }

    // 2026-09-09 코디네이터 재작성(사용자 지시) — review_pull_request가 이제 실제 Approve/Request
    // changes/Comment 판정(P3-15 submitReview())에 연결됐다(예전에는 addReviewer로만 매핑돼
    // 실제 승인/변경요청을 전혀 못 냈었다 — 실제 기능 갭이었음).
    describe("review_pull_request") {
        it("PULL_REQUESTS:WRITE 스코프를 검증한 뒤 submitReview에 실제 판정을 그대로 넘겨야 한다") {
            val review = PullRequestReviewResponse(
                id = 5L, state = PullRequestReview.ReviewState.APPROVE, body = "looks good",
                createdDate = java.time.Instant.now(), reviewerId = 9L, reviewerLoginId = "author", reviewerName = "작성자"
            )
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                pullRequestController.submitReview(
                    1L, 3L,
                    PullRequestController.SubmitPullRequestReviewRequest(PullRequestReview.ReviewState.APPROVE, "looks good"),
                    auth
                )
            } returns ResponseEntity.ok(review)

            val result = tools.review_pull_request("yona", "yona", 3L, "approve", "looks good")

            result shouldBe review
            verify(exactly = 1) {
                pullRequestController.submitReview(
                    1L, 3L,
                    PullRequestController.SubmitPullRequestReviewRequest(PullRequestReview.ReviewState.APPROVE, "looks good"),
                    auth
                )
            }
        }

        it("state가 대소문자/공백과 무관하게 정확히 매핑돼야 한다(REQUEST_CHANGES)") {
            val review = PullRequestReviewResponse(
                id = 6L, state = PullRequestReview.ReviewState.REQUEST_CHANGES, body = null,
                createdDate = java.time.Instant.now(), reviewerId = 9L, reviewerLoginId = "author", reviewerName = "작성자"
            )
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                pullRequestController.submitReview(
                    1L, 3L,
                    PullRequestController.SubmitPullRequestReviewRequest(PullRequestReview.ReviewState.REQUEST_CHANGES, null),
                    auth
                )
            } returns ResponseEntity.ok(review)

            tools.review_pull_request("yona", "yona", 3L, "  request_changes  ", null)

            verify(exactly = 1) {
                pullRequestController.submitReview(
                    1L, 3L,
                    PullRequestController.SubmitPullRequestReviewRequest(PullRequestReview.ReviewState.REQUEST_CHANGES, null),
                    auth
                )
            }
        }

        it("알 수 없는 state 값이면 McpToolException을 던지고 submitReview를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit

            shouldThrow<McpToolException> { tools.review_pull_request("yona", "yona", 3L, "nonsense", null) }

            verify(exactly = 0) { pullRequestController.submitReview(any(), any(), any(), any()) }
        }

        it("스코프가 없으면 submitReview를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.review_pull_request("yona", "yona", 3L, "approve", null) }

            verify(exactly = 0) { pullRequestController.submitReview(any(), any(), any(), any()) }
        }
    }

    // 2026-09-09 코디네이터 신설(사용자 지시) — review_pull_request가 예전에 하던 "판정 없이
    // 리뷰어로만 등록" 동작을 하위 호환용으로 분리한 도구.
    describe("add_reviewer") {
        it("PULL_REQUESTS:WRITE 스코프를 검증한 뒤 addReviewer에 위임해야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit
            every { pullRequestController.addReviewer(1L, 3L, auth) } returns ResponseEntity.ok().build()

            tools.add_reviewer("yona", "yona", 3L)

            verify(exactly = 1) { pullRequestController.addReviewer(1L, 3L, auth) }
        }

        it("스코프가 없으면 addReviewer를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.add_reviewer("yona", "yona", 3L) }

            verify(exactly = 0) { pullRequestController.addReviewer(any(), any(), any()) }
        }
    }
})
