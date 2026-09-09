package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestReview
import com.github.yonaprojects.yona.web.PullRequestController
import com.github.yonaprojects.yona.web.toResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * PR 읽기/쓰기 MCP 도구. PullRequestApiController와 완전히 동일한 원칙(신규 비즈니스 로직 없이
 * PullRequestController에 위임만 함)을 따른다.
 *
 * `merge_pull_request`는 반드시 `pullRequestController.mergePullRequest()`를 그대로 호출해야
 * `PullRequestServiceImpl.merge() -> checkBranchProtectionForMerge()`를 우회 없이 통과한다(새
 * 머지 경로를 만들지 않음). scopeGuard.require()는 이 호출보다 반드시 먼저 실행돼야 한다 — AI
 * 에이전트가 PULL_REQUESTS:write 스코프 없이 보호된 브랜치는커녕 어떤 PR도 머지하지 못하게 막는
 * 첫 번째 방어선이다(두 번째 방어선은 브랜치 보호 자체).
 *
 * `review_pull_request`는 한때 "yona에는 GitHub의 APPROVE/REQUEST_CHANGES 같은 정식 리뷰 상태
 * 기계가 없다"는 전제로 단순 리뷰어 등록에만 매핑돼 있었으나, 이후 `PullRequestReview`(APPROVE/
 * REQUEST_CHANGES/COMMENT, `PullRequestController.submitReview()`)가 정식으로 구현되면서 이 MCP
 * 도구가 그 사실을 반영하지 못한 채 낡은 방식에 머물러 있던 시기가 있었다 — AI 에이전트가 이 MCP
 * 서버로는 실제 Approve/Request changes 판정을 전혀 할 수 없던 기능 갭이었다. `review_pull_request`를
 * `submitReview()`에 연결하도록 고쳤고, 순수 "리뷰어로 등록만" 하고 싶은 경우를 위해
 * `add_reviewer`를 별도 도구로 새로 뒀다(기존 `review_pull_request`의 동작을 그대로 보존 — 하위
 * 호환).
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
        // getPullRequest()가 순환 직렬화/비밀번호 노출 방지를 위해 이미 PullRequestResponse를 담은
        // ResponseEntity<Any>를 돌려준다(unwrapForMcp()가 반환하는 정적 타입이 Any가 돼
        // .toResponse()를 다시 호출할 수 없다).
        return pullRequestController.getPullRequest(found.id!!, number, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
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
        // createPullRequest()도 위와 동일한 이유.
        return pullRequestController.createPullRequest(found.id!!, request, currentAuth())
            .unwrapForMcp()
    }

    // GitHub의 pulls/{number}/reviews와 동일하게 실제 Approve/Request changes/Comment 판정을
    // 남긴다. PullRequestController.submitReview()에 연결 — 자기 자신의 PR은
    // SelfReviewException(400)으로 거부되며 unwrapForMcp()가 그 사유를 그대로 McpToolException으로
    // 변환한다.
    @Tool(description = "풀 리퀘스트를 리뷰합니다 — APPROVE(승인)/REQUEST_CHANGES(변경 요청)/COMMENT(코멘트만, 판정 없음) 중 하나로 실제 판정을 남깁니다(자기 자신의 PR은 APPROVE/REQUEST_CHANGES 불가).")
    fun review_pull_request(
        @ToolParam(description = "저장소 소유자") owner: String,
        @ToolParam(description = "저장소 이름") project: String,
        @ToolParam(description = "PR 번호") number: Long,
        @ToolParam(description = "리뷰 판정: APPROVE, REQUEST_CHANGES, COMMENT 중 하나") state: String,
        @ToolParam(description = "리뷰 코멘트 본문(선택)", required = false) body: String?
    ): Any {
        val found = findProject(owner, project)
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        val reviewState = try {
            PullRequestReview.ReviewState.valueOf(state.trim().uppercase())
        } catch (e: IllegalArgumentException) {
            throw McpToolException("state는 APPROVE, REQUEST_CHANGES, COMMENT 중 하나여야 합니다(입력값: $state).")
        }
        val request = PullRequestController.SubmitPullRequestReviewRequest(state = reviewState, body = body)
        return pullRequestController.submitReview(found.id!!, number, request, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
    }

    // review_pull_request가 예전에 하던 "판정 없이 리뷰어 목록에만 등록" 동작을 이 이름으로 그대로
    // 보존한다(하위 호환 — 기존에 review_pull_request를 이 용도로 쓰던 MCP 클라이언트가 있을 수
    // 있어 동작 자체는 없애지 않고 도구만 분리).
    @Tool(description = "풀 리퀘스트에 판정 없이 리뷰어로만 등록합니다(Approve/Request changes 판정을 남기려면 review_pull_request를 쓰세요).")
    fun add_reviewer(
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
        // 이 require() 호출이 pullRequestController.mergePullRequest()보다 먼저 실행되는 것이 이
        // 도구의 핵심 안전장치다(클래스 KDoc 참고).
        scopeGuard.require(currentAuth(), ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE, found)
        return pullRequestController.mergePullRequest(found.id!!, number, currentAuth())
            .unwrapForMcp("PR #$number 를 찾을 수 없습니다.")
    }
}
