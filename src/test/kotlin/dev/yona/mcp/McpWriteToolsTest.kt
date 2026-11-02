package dev.yona.mcp

import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.site.SiteSettingService
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MCP 쓰기 도구(X-6 단계 3, `decisions.md` D-72)를 검증한다: 이슈 만들기, 댓글, 닫기와 다시 열기, 풀 리퀘스트 댓글, 위키 만들기와 수정.
 * 쓰기는 `repo` 범위가 필요하고(`read:repo`로는 거부), 관리자가 쓰기 도구를 끄면 거부하며, 풀 리퀘스트 만들기와 병합 같은 도구는 없다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(McpToolsSupport::class)
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-write"])
class McpWriteToolsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var support: McpToolsSupport
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var settings: SiteSettingService
    @Autowired private lateinit var repositories: RepositoryService
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean private lateinit var audit: dev.yona.domain.audit.AuditRecorder

    private fun args(f: McpToolsSupport.Fixture) = mapOf("owner" to f.project.owner, "project" to f.project.name)

    @Test
    fun `이슈와 위키 쓰기 도구가 등록되어 있고 병합과 삭제 도구는 없다`() {
        val names = support.toolNames(mockMvc, support.token(support.user(), "repo"))
        assertTrue(names.containsAll(setOf("create_issue", "comment_issue", "close_issue", "reopen_issue", "comment_pull_request", "create_wiki_page", "update_wiki_page")), "등록된 도구: $names")
        assertTrue(names.none { it.contains("merge") || it.contains("delete") }, "등록된 도구: $names")
    }

    @Test
    fun `repo 범위로 이슈를 만들고 댓글을 달고 닫고 다시 연다`() {
        val f = support.project()
        val token = support.token(f.member, "repo")
        val created = support.call(mockMvc, token, "create_issue", args(f) + mapOf("title" to "에이전트가 만든 이슈", "body" to "본문"))
        assertFalse(created.error, created.text)
        val issue = issues.findAll().single { it.project?.id == f.project.id }
        assertEquals("에이전트가 만든 이슈", issue.title)
        val number = issue.number!!

        assertFalse(support.call(mockMvc, token, "comment_issue", args(f) + mapOf("number" to number, "comment" to "댓글입니다")).also { assertFalse(it.error, it.text) }.error)
        assertEquals(1, issues.findById(issue.id!!).get().numOfComments)

        assertFalse(support.call(mockMvc, token, "close_issue", args(f) + mapOf("number" to number)).also { assertFalse(it.error, it.text) }.error)
        assertEquals(dev.yona.domain.enumeration.State.CLOSED, issues.findById(issue.id!!).get().state)
        assertFalse(support.call(mockMvc, token, "reopen_issue", args(f) + mapOf("number" to number)).also { assertFalse(it.error, it.text) }.error)
        assertEquals(dev.yona.domain.enumeration.State.OPEN, issues.findById(issue.id!!).get().state)
    }

    @Test
    fun `읽기 전용 토큰은 쓰기 도구를 쓸 수 없다`() {
        val f = support.project()
        val result = support.call(mockMvc, support.token(f.member, "read:repo"), "create_issue", args(f) + mapOf("title" to "막혀야 함"))
        assertTrue(result.error, result.text)
        assertTrue(result.text.contains("403"), result.text)
        assertTrue(issues.findAll().none { it.project?.id == f.project.id })
    }

    @Test
    fun `프로젝트에 쓸 권한이 없는 사용자는 repo 범위가 있어도 막힌다`() {
        val f = support.project(dev.yona.domain.enumeration.ProjectScope.PRIVATE)
        val result = support.call(mockMvc, support.token(support.user(), "repo"), "create_issue", args(f) + mapOf("title" to "남의 프로젝트"))
        assertTrue(result.error, result.text)
        assertTrue(issues.findAll().none { it.project?.id == f.project.id })
    }

    @Test
    fun `관리자가 쓰기 도구를 끄면 거부하고 읽기는 계속된다`() {
        val f = support.project()
        val token = support.token(f.member, "repo")
        settings.setMcpWriteEnabled(false)
        val denied = support.call(mockMvc, token, "create_issue", args(f) + mapOf("title" to "끈 뒤"))
        assertTrue(denied.error && denied.text.contains("MCP"), denied.text)
        assertTrue(issues.findAll().none { it.project?.id == f.project.id })
        assertFalse(support.call(mockMvc, token, "get_project", args(f)).error)
        settings.setMcpWriteEnabled(true)
        assertFalse(support.call(mockMvc, token, "create_issue", args(f) + mapOf("title" to "켠 뒤")).error)
    }

    @Test
    fun `없는 풀 리퀘스트에 댓글을 달면 오류로 알린다`() {
        val f = support.project()
        assertTrue(support.call(mockMvc, support.token(f.member, "repo"), "comment_pull_request", args(f) + mapOf("number" to 999, "body" to "댓글")).error)
    }

    @Test
    fun `위키 페이지를 만들고 고친다`() {
        val f = support.project()
        repositories.createRepository(f.project)
        val token = support.token(f.manager, "repo")
        val page = "Setup${System.nanoTime()}"
        val created = support.call(mockMvc, token, "create_wiki_page", args(f) + mapOf("title" to page, "content" to "처음 내용"))
        assertFalse(created.error, created.text)
        val updated = support.call(mockMvc, token, "update_wiki_page", args(f) + mapOf("name" to page, "content" to "바뀐 내용", "message" to "수정"))
        assertFalse(updated.error, updated.text)
        val read = support.call(mockMvc, token, "get_wiki_page", args(f) + mapOf("name" to page))
        assertFalse(read.error, read.text)
        assertTrue(read.text.contains("바뀐 내용"), read.text)
    }

    @Test
    fun `쓰기 도구는 성공과 실패 모두 감사 기록을 남기고 읽기 도구는 남기지 않는다`() {
        val f = support.project()
        val token = support.token(f.member, "repo")
        org.mockito.Mockito.clearInvocations(audit)
        support.call(mockMvc, token, "create_issue", args(f) + mapOf("title" to "감사"))
        support.call(mockMvc, token, "comment_pull_request", args(f) + mapOf("number" to 999, "body" to "없는 PR"))
        support.call(mockMvc, token, "get_project", args(f))
        val events = org.mockito.Mockito.mockingDetails(audit).invocations.filter { it.method.name == "recordIndependent" }.map { it.arguments[0] as dev.yona.domain.audit.AuditEvent }.filter { it.action == "mcp.tool_called" }
        assertEquals(listOf("create_issue", "comment_pull_request"), events.map { it.details["tool"] })
        assertEquals(listOf(dev.yona.domain.audit.AuditOutcome.SUCCESS, dev.yona.domain.audit.AuditOutcome.FAILURE), events.map { it.outcome })
        assertEquals("${f.project.owner}/${f.project.name}", events.first().targetLabel)
    }

    @Test
    fun `읽기 범위만 있는 토큰은 모든 쓰기 도구가 403으로 막힌다`() {
        val f = support.project()
        val token = support.token(f.member, "read:repo")
        val calls = listOf(
            "create_issue" to mapOf("title" to "x"), "comment_issue" to mapOf("number" to 1, "comment" to "x"), "close_issue" to mapOf("number" to 1), "reopen_issue" to mapOf("number" to 1),
            "comment_pull_request" to mapOf("number" to 1, "body" to "x"), "create_wiki_page" to mapOf("title" to "x"), "update_wiki_page" to mapOf("name" to "x", "content" to "y")
        )
        for ((tool, extra) in calls) {
            val result = support.call(mockMvc, token, tool, args(f) + extra)
            assertTrue(result.error && result.text.contains("403"), "$tool: ${result.text}")
        }
    }
}
