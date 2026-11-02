package dev.yona.mcp

import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.issue.IssueRepository
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
 * MCP의 삭제 도구(X-41, `decisions.md` D-76): 이슈 삭제와 이슈 댓글 삭제를 검증한다. 도구는 REST를 부르고 REST는 웹 화면과 같은 규칙으로 거른다:
 * 이슈는 작성자나 프로젝트 관리자만 지울 수 있고, **이슈 작성자가 아닌 사람의 댓글이 있으면 누구도 지울 수 없다**(v1.6 `canBeDeleted`). 댓글은 작성자나 프로젝트 관리자만 지운다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(McpToolsSupport::class)
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-delete"])
class McpDeleteToolsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var support: McpToolsSupport
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var settings: dev.yona.domain.site.SiteSettingService

    private fun args(f: McpToolsSupport.Fixture) = mapOf("owner" to f.project.owner, "project" to f.project.name)

    private fun comment(f: McpToolsSupport.Fixture, token: String, number: Long, text: String) {
        assertFalse(support.call(mockMvc, token, "comment_issue", args(f) + mapOf("number" to number, "comment" to text)).also { assertFalse(it.error, it.text) }.error)
    }

    private fun commentId(issueId: Long, text: String): Long = comments.findAll().single { it.issue?.id == issueId && it.contents == text }.id!!

    @Test
    fun `이슈와 이슈 댓글 삭제 도구가 등록되어 있다`() {
        val names = support.toolNames(mockMvc, support.token(support.user(), "repo"))
        assertTrue(names.containsAll(setOf("delete_issue", "delete_issue_comment")), "등록된 도구: $names")
    }

    @Test
    fun `작성자는 댓글이 없는 자기 이슈를 지울 수 있다`() {
        val f = support.project()
        val issue = support.issue(f, "지울 이슈", f.member)
        val result = support.call(mockMvc, support.token(f.member, "repo"), "delete_issue", args(f) + mapOf("number" to issue.number))
        assertFalse(result.error, result.text)
        assertTrue(issues.findById(issue.id!!).isEmpty)
    }

    @Test
    fun `프로젝트 관리자는 다른 사람의 이슈를 지울 수 있다`() {
        val f = support.project()
        val issue = support.issue(f, "관리자가 지움", f.member)
        assertFalse(support.call(mockMvc, support.token(f.manager, "repo"), "delete_issue", args(f) + mapOf("number" to issue.number)).error)
        assertTrue(issues.findById(issue.id!!).isEmpty)
    }

    @Test
    fun `작성자도 관리자도 아닌 구성원은 이슈를 지우지 못한다`() {
        val f = support.project()
        val issue = support.issue(f, "남의 이슈", f.member)
        val other = support.token(support.extraMember(f), "repo")
        val result = support.call(mockMvc, other, "delete_issue", args(f) + mapOf("number" to issue.number))
        assertTrue(result.error && result.text.contains("403"), result.text)
        assertTrue(issues.findById(issue.id!!).isPresent)
    }

    @Test
    fun `이슈 작성자가 아닌 사람의 댓글이 있으면 관리자도 이슈를 지우지 못한다`() {
        val f = support.project()
        val issue = support.issue(f, "댓글이 달린 이슈", f.member)
        comment(f, support.token(f.manager, "repo"), issue.number!!, "관리자의 댓글")
        for (who in listOf(f.member, f.manager)) {
            val result = support.call(mockMvc, support.token(who, "repo"), "delete_issue", args(f) + mapOf("number" to issue.number))
            assertTrue(result.error && result.text.contains("409"), result.text)
        }
        assertTrue(issues.findById(issue.id!!).isPresent)
    }

    @Test
    fun `작성자 본인의 댓글만 있으면 이슈를 지울 수 있다`() {
        val f = support.project()
        val issue = support.issue(f, "내 댓글만", f.member)
        val author = support.token(f.member, "repo")
        comment(f, author, issue.number!!, "내 댓글")
        assertFalse(support.call(mockMvc, author, "delete_issue", args(f) + mapOf("number" to issue.number)).also { assertFalse(it.error, it.text) }.error)
        assertTrue(issues.findById(issue.id!!).isEmpty)
    }

    @Test
    fun `댓글은 작성자와 프로젝트 관리자만 지운다`() {
        val f = support.project()
        val issue = support.issue(f, "댓글 삭제", f.member)
        val author = support.token(f.member, "repo")
        val other = support.token(support.extraMember(f), "repo")
        comment(f, author, issue.number!!, "작성자 댓글")
        comment(f, author, issue.number!!, "관리자가 지울 댓글")
        val mine = commentId(issue.id!!, "작성자 댓글"); val toBeRemovedByManager = commentId(issue.id!!, "관리자가 지울 댓글")
        val base = args(f) + mapOf("number" to issue.number)

        val denied = support.call(mockMvc, other, "delete_issue_comment", base + mapOf("commentId" to mine))
        assertTrue(denied.error && denied.text.contains("403"), denied.text)
        assertTrue(comments.findById(mine).isPresent)

        assertFalse(support.call(mockMvc, author, "delete_issue_comment", base + mapOf("commentId" to mine)).also { assertFalse(it.error, it.text) }.error)
        assertTrue(comments.findById(mine).isEmpty)
        assertFalse(support.call(mockMvc, support.token(f.manager, "repo"), "delete_issue_comment", base + mapOf("commentId" to toBeRemovedByManager)).also { assertFalse(it.error, it.text) }.error)
        assertTrue(comments.findById(toBeRemovedByManager).isEmpty)
    }

    @Test
    fun `다른 이슈의 댓글 번호나 없는 댓글은 404이다`() {
        val f = support.project()
        val first = support.issue(f, "첫째", f.member); val second = support.issue(f, "둘째", f.member)
        val manager = support.token(f.manager, "repo")
        comment(f, manager, first.number!!, "첫째의 댓글")
        val id = commentId(first.id!!, "첫째의 댓글")
        val wrongIssue = support.call(mockMvc, manager, "delete_issue_comment", args(f) + mapOf("number" to second.number, "commentId" to id))
        assertTrue(wrongIssue.error && wrongIssue.text.contains("404"), wrongIssue.text)
        assertTrue(support.call(mockMvc, manager, "delete_issue_comment", args(f) + mapOf("number" to first.number, "commentId" to 99999999L)).error)
        assertTrue(comments.findById(id).isPresent)
    }

    @Test
    fun `읽기 전용 토큰은 삭제 도구를 쓸 수 없다`() {
        val f = support.project()
        val issue = support.issue(f, "지키기", f.member)
        val token = support.token(f.manager, "read:repo")
        for ((tool, extra) in listOf("delete_issue" to mapOf("number" to issue.number), "delete_issue_comment" to mapOf("number" to issue.number, "commentId" to 1L))) {
            val result = support.call(mockMvc, token, tool, args(f) + extra)
            assertTrue(result.error && result.text.contains("403"), "$tool: ${result.text}")
        }
        assertEquals(1, issues.findAll().count { it.project?.id == f.project.id })
    }

    @Test
    fun `관리자가 쓰기 도구를 끄면 삭제 도구도 거부된다`() {
        val f = support.project()
        val issue = support.issue(f, "끈 뒤", f.member)
        settings.setMcpWriteEnabled(false)
        val result = support.call(mockMvc, support.token(f.manager, "repo"), "delete_issue", args(f) + mapOf("number" to issue.number))
        assertTrue(result.error && result.text.contains("MCP"), result.text)
        assertTrue(issues.findById(issue.id!!).isPresent)
    }
}
