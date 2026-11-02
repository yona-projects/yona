package dev.yona.mcp

import dev.yona.mcp.McpRestBridge.Companion.pageSize
import dev.yona.mcp.McpRestBridge.Companion.seg
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * MCP 읽기 도구이다(추가 개발 항목 X-6, `decisions.md` D-72). 모두 기존 외부 REST API를 [McpRestBridge]로 부르므로 범위(`read:repo`나 `repo`)와 프로젝트 접근 권한이 REST와 같다.
 * 설명은 AI 에이전트가 읽으므로 영어와 한국어를 함께 쓴다.
 */
@Component
class McpReadTools(private val bridge: McpRestBridge) {

    private fun project(owner: String, project: String) = "/-_-api/v1/owners/${seg(owner)}/projects/${seg(project)}"

    @Tool(name = "list_projects", description = "List projects. Without owner: projects you belong to. With owner: that user's or organization's projects you can read. / 프로젝트 목록. owner가 없으면 내가 속한 프로젝트, 있으면 그 사용자나 조직의 읽을 수 있는 프로젝트.")
    fun listProjects(@ToolParam(description = "Owner login ID or organization name (optional) / 소유자 로그인 ID나 조직 이름(생략 가능)", required = false) owner: String?): Any? =
        bridge.call("list_projects", "GET", if (owner.isNullOrBlank()) "/-_-api/v1/user/projects" else "/-_-api/v1/owners/${seg(owner)}/projects")

    @Tool(name = "get_project", description = "Get a project's information. / 프로젝트 정보를 조회한다.")
    fun getProject(@ToolParam(description = "Owner login ID or organization name / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String): Any? =
        bridge.call("get_project", "GET", project(owner, project), target = "$owner/$project")

    @Tool(name = "list_issues", description = "List a project's issues with filters and paging. / 프로젝트의 이슈 목록(조건과 쪽 지정).")
    fun listIssues(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "open, closed or all (default open) / 상태", required = false) state: String?,
        @ToolParam(description = "Search words in title or body / 제목이나 본문의 낱말", required = false) query: String?,
        @ToolParam(description = "Assignee login ID / 담당자 로그인 ID", required = false) assignee: String?,
        @ToolParam(description = "Author login ID / 작성자 로그인 ID", required = false) author: String?,
        @ToolParam(description = "Page, from 1 / 쪽 번호(1부터)", required = false) page: Int?,
        @ToolParam(description = "Items per page (default 30, max 100) / 쪽당 개수", required = false) pageSize: Int?
    ): Any? = bridge.call(
        "list_issues", "GET", "${project(owner, project)}/issues",
        mapOf("state" to state, "q" to query, "assignee" to assignee, "author" to author, "page" to page, "pageSize" to pageSize(pageSize)), target = "$owner/$project"
    )

    @Tool(name = "get_issue", description = "Get one issue with its comments and events. / 이슈 한 건을 댓글과 이벤트와 함께 조회한다.")
    fun getIssue(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Issue number / 이슈 번호") number: Long
    ): Any? = bridge.call("get_issue", "GET", "${project(owner, project)}/issues/$number", target = "$owner/$project")

    @Tool(name = "list_issue_comments", description = "List an issue's comments. / 이슈의 댓글 목록.")
    fun listIssueComments(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Issue number / 이슈 번호") number: Long
    ): Any? = bridge.call("list_issue_comments", "GET", "${project(owner, project)}/issues/$number/comments", target = "$owner/$project")

    @Tool(name = "list_pull_requests", description = "List a project's pull requests. / 프로젝트의 풀 리퀘스트 목록.")
    fun listPullRequests(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "open or closed (default open) / 상태", required = false) state: String?,
        @ToolParam(description = "Page, from 1 / 쪽 번호", required = false) page: Int?, @ToolParam(description = "Items per page (default 30, max 100) / 쪽당 개수", required = false) pageSize: Int?
    ): Any? = bridge.call("list_pull_requests", "GET", "${project(owner, project)}/pullrequests", mapOf("state" to state, "page" to page, "pageSize" to pageSize(pageSize)), target = "$owner/$project")

    @Tool(name = "get_pull_request", description = "Get one pull request. / 풀 리퀘스트 한 건을 조회한다.")
    fun getPullRequest(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long
    ): Any? = bridge.call("get_pull_request", "GET", "${project(owner, project)}/pullrequests/$number", target = "$owner/$project")

    @Tool(name = "get_pull_request_diff", description = "Get the changed files and their diffs of a pull request. Long results are truncated. / 풀 리퀘스트의 변경 파일과 차이. 길면 앞부분만 보낸다.")
    fun getPullRequestDiff(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String, @ToolParam(description = "Pull request number / 풀 리퀘스트 번호") number: Long
    ): Any? = bridge.call("get_pull_request_diff", "GET", "${project(owner, project)}/pullrequests/$number/files", target = "$owner/$project")

    @Tool(name = "list_wiki_pages", description = "List a project's wiki pages, optionally filtered by title. / 프로젝트 위키 페이지 목록(제목으로 거를 수 있다).")
    fun listWikiPages(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Title filter / 제목 검색어", required = false) query: String?
    ): Any? = bridge.call("list_wiki_pages", "GET", "${project(owner, project)}/wiki/pages", mapOf("q" to query), target = "$owner/$project")

    @Tool(name = "get_wiki_page", description = "Get a wiki page by name (use slashes for sub paths, e.g. Guides/Setup). / 위키 페이지를 이름으로 조회한다(하위 경로는 슬래시).")
    fun getWikiPage(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Page name / 페이지 이름") name: String, @ToolParam(description = "Revision (optional) / 리비전(생략 가능)", required = false) revision: String?
    ): Any? = bridge.call("get_wiki_page", "GET", "${project(owner, project)}/wiki/pages/${seg(name)}", mapOf("revision" to revision), target = "$owner/$project")

    @Tool(name = "search_project", description = "Search words in a project's issues, board posts, comments and wiki. / 프로젝트의 이슈, 게시글, 댓글, 위키에서 낱말을 찾는다.")
    fun searchProject(
        @ToolParam(description = "Owner / 소유자") owner: String, @ToolParam(description = "Project name / 프로젝트 이름") project: String,
        @ToolParam(description = "Words to search / 찾을 낱말") keyword: String,
        @ToolParam(description = "Result kind: auto, issue, post, comment, milestone, code (optional) / 결과 종류", required = false) type: String?,
        @ToolParam(description = "Page, from 1 / 쪽 번호", required = false) page: Int?
    ): Any? = bridge.call("search_project", "GET", "${project(owner, project)}/search", mapOf("keyword" to keyword, "searchType" to type, "pageNum" to page), target = "$owner/$project")
}
