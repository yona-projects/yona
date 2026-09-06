package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueComment
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.web.CommentController
import com.github.yonaprojects.yona.web.IssueController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.util.Optional

// yona-wiki P3-07(MCP 서버) Step3~4 — IssueRestApiControllerSpec과 동일한 접근(위임 여부만 검증,
// 업무 로직 자체는 IssueController/CommentController 쪽에서 이미 검증됨)에 한 가지를 더 추가한다:
// McpScopeGuard가 거부하면 하부 컨트롤러가 "절대 호출되지 않아야 한다"는 것 — 이게 곧 계획 문서
// 보안 검증 항목("토큰 스코프가 실제로 각 도구 호출 전에 검증되는지")의 직접적인 증거다.
class IssueMcpToolsSpec : DescribeSpec({
    val projectRepository = mockk<ProjectRepository>()
    val issueController = mockk<IssueController>()
    val commentController = mockk<CommentController>()
    val scopeGuard = mockk<McpScopeGuard>()
    val tools = IssueMcpTools(projectRepository, issueController, commentController, scopeGuard)

    val project = Project(id = 1L, owner = "yona", name = "yona", projectScope = ProjectScope.PUBLIC)
    val auth = UsernamePasswordAuthenticationToken("tester", "password")

    beforeTest {
        clearMocks(projectRepository, issueController, commentController, scopeGuard)
        SecurityContextHolder.getContext().authentication = auth
    }

    describe("프로젝트를 찾지 못하면") {
        it("스코프 검증조차 하지 않고 McpToolException을 던져야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "unknown") } returns Optional.empty()

            shouldThrow<McpToolException> { tools.list_issues("yona", "unknown", null) }

            verify(exactly = 0) { scopeGuard.require(any(), any(), any(), any()) }
        }
    }

    describe("list_issues") {
        it("ISSUES:READ 스코프를 검증한 뒤 IssueController.getIssues에 위임해야 한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            val page = PageImpl(listOf(issue), PageRequest.of(0, 15), 1)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, project) } returns Unit
            every { issueController.getIssues(1L, null, null, null, null, any<Pageable>(), auth) } returns ResponseEntity.ok(page)

            val result = tools.list_issues("yona", "yona", null)

            (result as List<*>).size shouldBe 1
            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, project) }
        }

        it("스코프가 없으면 IssueController를 호출하지 않고 예외를 전파해야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.list_issues("yona", "yona", null) }

            verify(exactly = 0) { issueController.getIssues(any(), any(), any(), any(), any(), any(), any()) }
        }
    }

    describe("create_issue") {
        it("ISSUES:WRITE 스코프를 검증한 뒤 IssueController.createIssue에 위임해야 한다") {
            val created = Issue(id = 7L, number = 7L, title = "새 이슈", project = project)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                issueController.createIssue(1L, IssueController.CreateIssueRequest("새 이슈", "본문", null, null, null), auth)
            } returns ResponseEntity.ok(created)

            tools.create_issue("yona", "yona", "새 이슈", "본문")

            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) }
        }

        it("WRITE 스코프가 없으면 거부되고 IssueController를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.create_issue("yona", "yona", "제목", null) }

            verify(exactly = 0) { issueController.createIssue(any(), any(), any()) }
        }
    }

    describe("comment_issue") {
        it("ISSUES:WRITE 스코프를 검증한 뒤 CommentController.createIssueComment에 위임해야 한다") {
            val issue = Issue(id = 5L, number = 5L, title = "제목", project = project)
            val comment = IssueComment(id = 1L, contents = "댓글", issue = issue)
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) } returns Unit
            every {
                commentController.createIssueComment(1L, 5L, CommentController.CommentRequest(contents = "댓글"), auth)
            } returns ResponseEntity.ok(comment)

            tools.comment_issue("yona", "yona", 5L, "댓글")

            verify(exactly = 1) { scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project) }
        }
    }

    describe("close_issue") {
        it("ISSUES:WRITE 스코프가 없으면 IssueController.changeState를 호출하지 않아야 한다") {
            every { projectRepository.findByOwnerAndName("yona", "yona") } returns Optional.of(project)
            every {
                scopeGuard.require(auth, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, project)
            } throws AccessDeniedException("no scope")

            shouldThrow<AccessDeniedException> { tools.close_issue("yona", "yona", 5L) }

            verify(exactly = 0) { issueController.changeState(any(), any(), any(), any()) }
        }
    }
})
