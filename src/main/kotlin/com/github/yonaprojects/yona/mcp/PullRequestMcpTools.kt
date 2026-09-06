package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.web.PullRequestController
import com.github.yonaprojects.yona.web.toResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * yona-wiki P3-07(MCP 서버) Step3~5 — PR 읽기/쓰기 MCP 도구. PullRequestApiController와 완전히
 * 동일한 원칙(신규 비즈니스 로직 없이 PullRequestController에 위임만 함)을 따른다.
 *
 * `merge_pull_request`는 이 계획의 리스크 표 1순위 항목이다 — 반드시
 * `pullRequestController.mergePullRequest()`를 그대로 호출해야
 * [[p3-04-branch-protection]]의 `PullRequestServiceImpl.merge() -> checkBranchProtectionForMerge()`를
 * 우회 없이 통과한다(새 머지 경로를 만들지 않음). scopeGuard.require()는 이 호출보다 반드시 먼저
 * 실행돼야 한다 — AI 에이전트가 PULL_REQUESTS:write 스코프 없이 보호된 브랜치는커녕 어떤 PR도
 * 머지하지 못하게 막는 첫 번째 방어선이다(두 번째 방어선은 브랜치 보호 자체).
 *
 * `review_pull_request`는 GitHub의 "APPROVE/REQUEST_CHANGES" 같은 정식 리뷰 상태 기계가 yona에는
 * 없어(PullRequestController에 그런 API 자체가 없음, 확인됨) 실제로 존재하는 유일한 "리뷰" 동작인
 * 리뷰어 등록(addReviewer, PullRequestApiController의 `POST .../reviewers`와 동일)에 매핑한다 —
 * 신규 리뷰 상태 기계를 만들지 않는다(과도한 설계 금지).
 */
@Component
class PullRequestMcpTools(
    private val projectRepository: ProjectRepository,
    private val pullRequestController: PullRequestController,
    private val scopeGuard: McpScopeGuard
) {
    private fun currentAuth() = SecurityContextHolder.getContext().authentication

    private fun findProject(owner: String, project: String): Project =
        projectRepository.findByOwnerAndName(owner, project).orElse(null)
            ?: throw McpToolException("저장소 $owner/$project 를 찾을 수 없습니다.")

    @Tool(description = "저장소의 풀 리퀘스트 목록을 조회합니다.")
    fun list_pull_requests(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 상태로 필터링(OPEN/CLOSED/MERGED 등), 생략 시 전체", required = false) state: State?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ, found)
        val list = pullRequestController.getPullRequests(found.id!!, state, null, null, null, currentAuth())
            .unwrapForMcp()
        return list.map { it.toResponse() }
    }

    @Tool(description = "PR 번호로 풀 리퀘스트 하나를 조회합니다.")
    fun get_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 번호") number: Long
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ, found)
        return pullRequestController.getPullRequest(found.id!!, number, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
            .toResponse()
    }

    @Tool(description = "새 풀 리퀘스트를 생성합니다(같은 저장소 안의 브랜치 간, 포크 간 PR은 지원하지 않습니다).")
    fun create_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 제목") title: String,
        @ToolParam(description = "PR 본문(마크다운)", required = false) body: String?,
        @ToolParam(description = "머지할 브랜치(기능 브랜치)") fromBranch: String,
        @ToolParam(description = "대상 브랜치(예: main)") toBranch: String
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        val request = PullRequestController.CreatePullRequestRequest(
            title = title,
            body = body,
            fromProjectId = found.id!!,
            fromBranch = fromBranch,
            toBranch = toBranch
        )
        return pullRequestController.createPullRequest(found.id!!, request, currentAuth())
            .unwrapForMcp()
            .toResponse()
    }

    @Tool(description = "풀 리퀘스트를 리뷰합니다(yona는 승인/변경요청 상태가 없어 리뷰어로 등록하는 동작으로 처리됩니다).")
    fun review_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 번호") number: Long
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        pullRequestController.addReviewer(found.id!!, number, currentAuth())
            .requireSuccessForMcp("PR #$number 를 찾을 수 없습니다.")
        return mapOf("status" to "reviewer_added", "number" to number)
    }

    @Tool(description = "풀 리퀘스트에 코멘트를 답니다.")
    fun comment_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 번호") number: Long,
        @ToolParam(description = "코멘트 본문(마크다운)") body: String
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        val request = PullRequestController.PullRequestCommentRequest(body = body)
        return pullRequestController.addComment(found.id!!, number, request, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
            .toResponse()
    }

    @Tool(description = "풀 리퀘스트를 머지합니다. 대상 브랜치가 보호된 브랜치라면 그 정책을 그대로 적용받습니다.")
    fun merge_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 번호") number: Long
    ): Any {
        val found = findProject(owner, project)
        // yona-wiki P3-07 Step5 — 이 require() 호출이 pullRequestController.mergePullRequest()보다
        // 먼저 실행되는 것이 이 도구의 핵심 안전장치다(클래스 KDoc 참고).
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        return pullRequestController.mergePullRequest(found.id!!, number, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
    }
}
