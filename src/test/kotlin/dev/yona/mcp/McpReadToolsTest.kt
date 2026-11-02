package dev.yona.mcp

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * MCP 읽기 도구(X-6 단계 2, `decisions.md` D-72)를 검증한다. 도구는 기존 REST API를 같은 길(접근 검사와 범위 검사 포함)로 부르므로, 권한과 범위 규칙이 REST와 같은지가 핵심이다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(McpToolsSupport::class)
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-read"])
class McpReadToolsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var support: McpToolsSupport

    @Test
    fun `읽기 도구가 모두 등록되어 있다`() {
        val names = support.toolNames(mockMvc, support.token(support.user(), "read:repo"))
        assertTrue(
            names.containsAll(setOf("list_projects", "get_project", "list_issues", "get_issue", "list_issue_comments", "list_pull_requests", "get_pull_request", "get_pull_request_diff", "list_wiki_pages", "get_wiki_page", "search_project")),
            "등록된 도구: $names"
        )
    }

    @Test
    fun `프로젝트 도구는 구성원인 프로젝트와 한 프로젝트의 정보를 돌려준다`() {
        val f = support.project(ProjectScope.PRIVATE)
        val token = support.token(f.member, "read:repo")
        val list = support.call(mockMvc, token, "list_projects")
        assertFalse(list.error, list.text)
        assertEquals(f.project.name, list.json!!.get("projects").get(0).get("name").asString())
        val one = support.call(mockMvc, token, "get_project", mapOf("owner" to f.project.owner, "project" to f.project.name))
        assertFalse(one.error, one.text)
        assertEquals("PRIVATE", one.json!!.get("scope").asString())
    }

    @Test
    fun `비공개 프로젝트는 구성원이 아니면 읽지 못하고 오류로 알린다`() {
        val f = support.project(ProjectScope.PRIVATE)
        val outsider = support.token(support.user(), "read:repo")
        val result = support.call(mockMvc, outsider, "get_project", mapOf("owner" to f.project.owner, "project" to f.project.name))
        assertTrue(result.error, result.text)
        assertTrue(result.text.contains("403"), result.text)
    }

    @Test
    fun `이슈 도구는 목록과 한 건과 댓글을 돌려준다`() {
        val f = support.project()
        val issue = support.issue(f, "첫 이슈", f.member)
        val token = support.token(f.member, "read:repo")
        val args = mapOf("owner" to f.project.owner, "project" to f.project.name)
        val list = support.call(mockMvc, token, "list_issues", args + mapOf("state" to "all"))
        assertFalse(list.error, list.text)
        assertEquals("첫 이슈", list.json!!.get("items").get(0).get("title").asString())
        val one = support.call(mockMvc, token, "get_issue", args + mapOf("number" to issue.number))
        assertFalse(one.error, one.text)
        assertNotNull(one.json)
        val comments = support.call(mockMvc, token, "list_issue_comments", args + mapOf("number" to issue.number))
        assertFalse(comments.error, comments.text)
    }

    @Test
    fun `목록은 쪽 크기를 한도 안으로 줄인다`() {
        val f = support.project()
        support.issue(f, "하나", f.member)
        val token = support.token(f.member, "read:repo")
        val result = support.call(mockMvc, token, "list_issues", mapOf("owner" to f.project.owner, "project" to f.project.name, "pageSize" to 100000))
        assertFalse(result.error, result.text)
        assertTrue(result.json!!.get("pageSize").asInt() <= 100, result.text)
    }

    @Test
    fun `풀 리퀘스트와 위키와 검색 도구는 없는 대상을 오류로 알린다`() {
        val f = support.project()
        val token = support.token(f.member, "read:repo")
        val args = mapOf("owner" to f.project.owner, "project" to f.project.name)
        assertTrue(support.call(mockMvc, token, "get_pull_request", args + mapOf("number" to 999)).error)
        assertTrue(support.call(mockMvc, token, "get_wiki_page", args + mapOf("name" to "없는 문서")).error)
        assertFalse(support.call(mockMvc, token, "list_wiki_pages", args).let { it.error && !it.text.contains("40") }, "목록은 오류 없이 답하거나 저장소가 없다고 알린다")
        assertFalse(support.call(mockMvc, token, "search_project", args + mapOf("keyword" to "첫")).error)
    }

    @Test
    fun `읽기 범위가 모자라면 도구가 아니라 요청 단계에서 막힌다`() {
        val f = support.project()
        val token = support.token(f.member, "read:user")
        val response = mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/mcp").contentType("application/json").accept("application/json", "text/event-stream")
                .header("Authorization", "Bearer $token").content("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")
        ).andReturn().response
        assertEquals(403, response.status)
    }
}
