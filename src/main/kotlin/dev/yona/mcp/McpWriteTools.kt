package dev.yona.mcp

import dev.yona.mcp.McpRestBridge.Companion.seg
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * MCP 쓰기 도구이다(추가 개발 항목 X-6, `decisions.md` D-72 4번, X-41의 D-74). 되돌리기 쉬운 쓰기만 둔다: 이슈 만들기, 댓글, 닫기와 다시 열기, 풀 리퀘스트 만들기와 댓글과
 * 리뷰 판정과 닫기와 다시 열기, 위키 만들기와 수정. 병합(`merge_pull_request`)은 D-75로 더했다. 삭제 도구는 두지 않는다. 쓰기는 `repo` 범위와 사용자의 실제 쓰기 권한이 필요하며, 관리자가 "MCP 쓰기 도구 허용"을 끄면 거부한다.
 * 모든 호출은 감사 기록(`mcp.tool_called`)을 남긴다.
 */
@Component
class McpWriteTools(private val bridge: McpRestBridge) {

    private fun project(owner: String, project: String) = "/-_-api/v1/owners/${seg(owner)}/projects/${seg(project)}"

    @Tool(name = "create_issue", description = "Create an issue. Requires the repo scope. / 이슈를 만든다. repo 범위가 필요하다.")
    fun createIssue(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Title / 제목") title: String, @ToolParam(description = "Body in Markdown / 본문(마크다운)", required = false) body: String?
    ): Any? = bridge.call("create_issue", "POST", "${project(owner, project)}/issues", body = mapOf("issues" to listOf(mapOf("title" to title, "body" to body.orEmpty()))), target = "$owner/$project")

    @Tool(name = "comment_issue", description = "Add a comment to an issue. Requires the repo scope. / 이슈에 댓글을 단다. repo 범위가 필요하다.")
    fun commentIssue(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Issue number / 이슈 번호") number: Long, @ToolParam(description = "Comment in Markdown / 댓글(마크다운)") comment: String
    ): Any? = bridge.call("comment_issue", "POST", "${project(owner, project)}/issues/$number/comments", body = mapOf("comment" to comment), target = "$owner/$project")

    @Tool(name = "close_issue", description = "Close an issue. Requires the repo scope. / 이슈를 닫는다. repo 범위가 필요하다.")
    fun closeIssue(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Issue number / 이슈 번호") number: Long
    ): Any? = bridge.call("close_issue", "PATCH", "${project(owner, project)}/issues/$number", body = mapOf("state" to "closed"), target = "$owner/$project")

    @Tool(name = "reopen_issue", description = "Reopen a closed issue. Requires the repo scope. / 닫힌 이슈를 다시 연다. repo 범위가 필요하다.")
    fun reopenIssue(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Issue number / 이슈 번호") number: Long
    ): Any? = bridge.call("reopen_issue", "PATCH", "${project(owner, project)}/issues/$number", body = mapOf("state" to "open"), target = "$owner/$project")

    @Tool(name = "comment_pull_request", description = "Add a comment to a pull request. Requires the repo scope. / 풀 리퀘스트에 댓글을 단다. repo 범위가 필요하다.")
    fun commentPullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long, @ToolParam(description = "Comment in Markdown / 댓글(마크다운)") body: String
    ): Any? = bridge.call("comment_pull_request", "POST", "${project(owner, project)}/pullrequests/$number/comments", body = mapOf("body" to body), target = "$owner/$project")

    @Tool(name = "create_wiki_page", description = "Create a wiki page. Requires the repo scope. / 위키 페이지를 만든다. repo 범위가 필요하다.")
    fun createWikiPage(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Page title / 페이지 제목") title: String, @ToolParam(description = "Content in Markdown / 내용(마크다운)", required = false) content: String?,
        @ToolParam(description = "Commit message (optional) / 커밋 메시지", required = false) message: String?
    ): Any? = bridge.call("create_wiki_page", "POST", "${project(owner, project)}/wiki/pages", body = mapOf("title" to title, "content" to content, "message" to message).filterValues { it != null }, target = "$owner/$project")

    @Tool(name = "update_wiki_page", description = "Edit a wiki page's content and/or rename it. Requires the repo scope. / 위키 페이지의 내용을 고치거나 이름을 바꾼다. repo 범위가 필요하다.")
    fun updateWikiPage(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Current page name / 현재 페이지 이름") name: String, @ToolParam(description = "New title for renaming (optional) / 새 제목(이름 변경)", required = false) title: String?,
        @ToolParam(description = "New content (optional) / 새 내용", required = false) content: String?, @ToolParam(description = "Commit message (optional) / 커밋 메시지", required = false) message: String?
    ): Any? = bridge.call(
        "update_wiki_page", "PATCH", "${project(owner, project)}/wiki/pages/${seg(name)}", body = mapOf("title" to title, "content" to content, "message" to message).filterValues { it != null }, target = "$owner/$project"
    )

    @Tool(
        name = "create_pull_request",
        description = "Create a pull request between two branches of the same project (pull requests from forks are not supported here). Requires the repo scope. / 같은 프로젝트의 두 브랜치 사이에 풀 리퀘스트를 만든다(포크에서 보내는 것은 지원하지 않는다). repo 범위가 필요하다."
    )
    fun createPullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Title / 제목") title: String, @ToolParam(description = "Body in Markdown / 본문(마크다운)", required = false) body: String?,
        @ToolParam(description = "Branch with the changes, e.g. feature / 변경이 있는 브랜치") fromBranch: String, @ToolParam(description = "Target branch, e.g. master / 합칠 대상 브랜치") toBranch: String
    ): Any? = bridge.call(
        "create_pull_request", "POST", "${project(owner, project)}/pullrequests",
        body = mapOf("title" to title, "body" to body.orEmpty(), "fromBranch" to fromBranch, "toBranch" to toBranch), target = "$owner/$project"
    )

    @Tool(
        name = "review_pull_request",
        description = "Review a pull request with a verdict: APPROVED, CHANGES_REQUESTED or COMMENTED. You cannot approve or request changes on your own pull request. Requires the repo scope. / 풀 리퀘스트를 리뷰한다. 판정은 APPROVED(승인), CHANGES_REQUESTED(변경 요청), COMMENTED(댓글만). 자기 풀 리퀘스트는 승인하거나 변경을 요청할 수 없다. repo 범위가 필요하다."
    )
    fun reviewPullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long,
        @ToolParam(description = "APPROVED, CHANGES_REQUESTED or COMMENTED / 판정") verdict: String, @ToolParam(description = "Review comment (required for CHANGES_REQUESTED and COMMENTED) / 리뷰 내용", required = false) body: String?
    ): Any? = bridge.call("review_pull_request", "POST", "${project(owner, project)}/pullrequests/$number/reviews", body = mapOf("verdict" to verdict, "body" to body).filterValues { it != null }, target = "$owner/$project")

    @Tool(name = "close_pull_request", description = "Close a pull request without merging. Requires the repo scope. / 풀 리퀘스트를 병합하지 않고 닫는다. repo 범위가 필요하다.")
    fun closePullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long
    ): Any? = bridge.call("close_pull_request", "POST", "${project(owner, project)}/pullrequests/$number/close", target = "$owner/$project")

    @Tool(name = "reopen_pull_request", description = "Reopen a closed pull request. Requires the repo scope. / 닫힌 풀 리퀘스트를 다시 연다. repo 범위가 필요하다.")
    fun reopenPullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long
    ): Any? = bridge.call("reopen_pull_request", "POST", "${project(owner, project)}/pullrequests/$number/reopen", target = "$owner/$project")

    @Tool(
        name = "merge_pull_request",
        description = "Merge an open pull request. Only users allowed to merge (project managers) can; branch protection rules (required approvals, signed commits, restricted updates) still apply and a failed condition is reported as an error. " +
            "This changes the target branch and cannot be undone by this server; use it only when the user explicitly asked to merge. Requires the repo scope. / 열린 풀 리퀘스트를 병합한다. 병합 권한이 있는 사용자(프로젝트 관리자)만 할 수 있고 " +
            "브랜치 보호 규칙(필요한 승인, 서명 필수, 갱신 제한)이 그대로 적용되어 조건을 채우지 못하면 오류로 알린다. 대상 브랜치가 바뀌고 이 서버에서 되돌릴 수 없으므로 사용자가 병합을 분명히 요청했을 때만 쓴다. repo 범위가 필요하다."
    )
    fun mergePullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long,
        @ToolParam(description = "merge, squash or rebase (default: the project's default method) / 병합 방식(생략하면 프로젝트의 기본 방식)", required = false) method: String?,
        @ToolParam(description = "Commit title for merge and squash (optional) / 병합 커밋 제목", required = false) commitTitle: String?,
        @ToolParam(description = "Commit message for merge and squash (optional) / 병합 커밋 본문", required = false) commitMessage: String?
    ): Any? = bridge.call(
        "merge_pull_request", "POST", "${project(owner, project)}/pullrequests/$number/merge",
        body = mapOf("method" to method, "commitTitle" to commitTitle, "commitMessage" to commitMessage).filterValues { it != null }, target = "$owner/$project"
    )
}
