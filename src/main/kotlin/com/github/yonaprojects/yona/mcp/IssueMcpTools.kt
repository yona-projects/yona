package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.web.CommentController
import com.github.yonaprojects.yona.web.IssueController
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 이슈 읽기/쓰기 MCP 도구. IssueRestApiController/
 * PullRequestApiController와 완전히 동일한 원칙: 신규 비즈니스 로직 없이 기존
 * IssueController/CommentController(이미 AccessControl/브랜치 보호 등을 전부 갖춘 웹 컨트롤러)에
 * 위임만 한다 — 이 클래스가 새로 하는 일은 (1) owner/project 이름으로 프로젝트를 찾는 것과
 * (2) McpScopeGuard로 OAuth/PAT 스코프를 도구 호출 "전"에 검증하는 것 두 가지뿐이다.
 */
@Component
class IssueMcpTools(
    private val projectRepository: ProjectRepository,
    private val issueController: IssueController,
    private val commentController: CommentController,
    private val scopeGuard: McpScopeGuard
) {
    private fun currentAuth() = SecurityContextHolder.getContext().authentication

    private fun findProject(owner: String, project: String): Project =
        projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: throw McpToolException("저장소 $owner/$project 를 찾을 수 없습니다.")

    @Tool(description = "저장소의 이슈 목록을 조회합니다.")
    fun list_issues(
        @ToolParam(description = "저장소 소유자(로그인 ID 또는 조직명)") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "이슈 상태로 필터링(OPEN 또는 CLOSED), 생략 시 전체", required = false) state: State?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, found)
        // IssueController.getIssues()가 자체적으로 Page<IssueResponse>를
        // 반환하므로(raw Issue 엔티티 순환 직렬화/비밀번호 노출 방지), 여기서 다시 .toResponse()를
        // 부를 필요가 없다(이미 DTO).
        val page = issueController.getIssues(
            found.id!!, state, null, null, null,
            PageRequest.of(0, IssueController.ITEMS_PER_PAGE), currentAuth()
        ).unwrapForMcp()
        return page.content
    }

    @Tool(description = "이슈 번호로 이슈 하나를 조회합니다.")
    fun get_issue(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "이슈 번호") number: Long
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ, found)
        // IssueController.getIssue()가 이미 IssueResponse를 반환한다.
        return issueController.getIssue(found.id!!, number, currentAuth())
            .unwrapForMcp("이슈 #$number 를 찾을 수 없습니다.")
    }

    @Tool(description = "새 이슈를 생성합니다.")
    fun create_issue(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "이슈 제목") title: String,
        @ToolParam(description = "이슈 본문(마크다운)", required = false) body: String?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, found)
        val request = IssueController.CreateIssueRequest(
            title = title,
            body = body,
            milestoneId = null,
            assigneeId = null,
            labelIds = null
        )
        // IssueController.createIssue()가 이미 IssueResponse를 반환한다.
        return issueController.createIssue(found.id!!, request, currentAuth()).unwrapForMcp()
    }

    @Tool(description = "이슈에 코멘트를 답니다.")
    fun comment_issue(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "이슈 번호") number: Long,
        @ToolParam(description = "코멘트 본문(마크다운)") body: String
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, found)
        val request = CommentController.CommentRequest(contents = body)
        // CommentController.createIssueComment()가 이미 IssueCommentResponse를 반환한다.
        return commentController.createIssueComment(found.id!!, number, request, currentAuth())
            .unwrapForMcp("이슈 #$number 를 찾을 수 없습니다.")
    }

    @Tool(description = "이슈를 닫습니다(CLOSED 상태로 변경).")
    fun close_issue(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "이슈 번호") number: Long
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE, found)
        // IssueController.changeState()가 이미 IssueResponse를 반환한다.
        return issueController.changeState(found.id!!, number, State.CLOSED, currentAuth())
            .unwrapForMcp("이슈 #$number 를 찾을 수 없습니다.")
    }
}
