package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.web.PullRequestController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
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

    describe("review_pull_request") {
        it("PULL_REQUESTS:WRITE 스코프를 검증한 뒤 addReviewer에 위임해야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project) } returns Unit
            every { pullRequestController.addReviewer(1L, 3L, auth) } returns ResponseEntity.ok().build()

            tools.review_pull_request("yona", "yona", 3L)

            verify(exactly = 1) { pullRequestController.addReviewer(1L, 3L, auth) }
        }

        it("스코프가 없으면 addReviewer를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.review_pull_request("yona", "yona", 3L) }

            verify(exactly = 0) { pullRequestController.addReviewer(any(), any(), any()) }
        }
    }
})
