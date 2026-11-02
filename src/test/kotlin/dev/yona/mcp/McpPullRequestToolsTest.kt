package dev.yona.mcp

import dev.yona.domain.pullrequest.PullRequestRepository
import dev.yona.repository.RepositoryService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.GitTestSupport
import dev.yona.support.GitTestSupport.TestCommit
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
 * MCP의 풀 리퀘스트 쓰기 도구(X-41, `decisions.md` D-74)를 검증한다: 만들기, 리뷰 판정, 닫기와 다시 열기. 병합과 이슈와 이슈 댓글 말고는 삭제 도구가 없다. 도구는 REST와 같은 길로 부르므로
 * 리뷰 판정의 권한 규칙(자기 풀 리퀘스트는 승인할 수 없다)도 REST와 같다.
 */
@AutoConfigureMockMvc
@Transactional
@Import(McpToolsSupport::class)
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-pr"])
class McpPullRequestToolsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var support: McpToolsSupport
    @Autowired private lateinit var repositories: RepositoryService
    @Autowired private lateinit var pullRequests: PullRequestRepository
    @Autowired private lateinit var rulesets: dev.yona.domain.project.RulesetRepository
    @jakarta.persistence.PersistenceContext private lateinit var em: jakarta.persistence.EntityManager

    private fun args(f: McpToolsSupport.Fixture) = mapOf("owner" to f.project.owner, "project" to f.project.name)

    /** 기반 커밋이 있는 저장소와 `feature` 브랜치(커밋 하나 앞섬)를 만든다. */
    private fun withBranch(f: McpToolsSupport.Fixture): McpToolsSupport.Fixture {
        repositories.createRepository(f.project)
        GitTestSupport.pushCommits(repositories, f.project, TestCommit("기반", mapOf("a.txt" to "line1\n".toByteArray())))
        GitTestSupport.appendCommits(repositories, f.project, TestCommit("기능", mapOf("b.txt" to "b\n".toByteArray())), branch = "feature")
        return f
    }

    private fun create(f: McpToolsSupport.Fixture, token: String, title: String = "에이전트의 풀 리퀘스트") =
        support.call(mockMvc, token, "create_pull_request", args(f) + mapOf("title" to title, "body" to "본문", "fromBranch" to "feature", "toBranch" to "master"))

    @Test
    fun `풀 리퀘스트 쓰기 도구가 등록되어 있고 이슈와 이슈 댓글 말고는 삭제 도구가 없다`() {
        val names = support.toolNames(mockMvc, support.token(support.user(), "repo"))
        assertTrue(names.containsAll(setOf("create_pull_request", "review_pull_request", "close_pull_request", "reopen_pull_request", "merge_pull_request")), "등록된 도구: $names")
        assertTrue(names.none { it.contains("delete") && it !in setOf("delete_issue", "delete_issue_comment") }, "등록된 도구: $names")
    }

    @Test
    fun `같은 프로젝트의 브랜치로 풀 리퀘스트를 만든다`() {
        val f = withBranch(support.project())
        val result = create(f, support.token(f.member, "repo"))
        assertFalse(result.error, result.text)
        val pr = pullRequests.findAll().single { it.toProject?.id == f.project.id }
        assertEquals("에이전트의 풀 리퀘스트", pr.title)
        assertTrue(result.text.contains("\"number\""), result.text)
    }

    @Test
    fun `없는 브랜치로는 만들 수 없다`() {
        val f = withBranch(support.project())
        val result = support.call(mockMvc, support.token(f.member, "repo"), "create_pull_request", args(f) + mapOf("title" to "x", "fromBranch" to "nothing", "toBranch" to "master"))
        assertTrue(result.error, result.text)
        assertTrue(pullRequests.findAll().none { it.toProject?.id == f.project.id })
    }

    @Test
    fun `다른 사람이 승인하고 변경을 요청하고 댓글로 판정한다 그리고 자기 풀 리퀘스트는 승인하지 못한다`() {
        val f = withBranch(support.project())
        val author = support.token(f.member, "repo")
        val number = pullRequests.also { create(f, author) }.findAll().single { it.toProject?.id == f.project.id }.number
        val reviewer = support.token(f.manager, "repo")
        val base = args(f) + mapOf("number" to number)

        assertFalse(support.call(mockMvc, reviewer, "review_pull_request", base + mapOf("verdict" to "APPROVED", "body" to "좋습니다")).also { assertFalse(it.error, it.text) }.error)
        assertFalse(support.call(mockMvc, reviewer, "review_pull_request", base + mapOf("verdict" to "CHANGES_REQUESTED", "body" to "고쳐 주세요")).also { assertFalse(it.error, it.text) }.error)
        assertFalse(support.call(mockMvc, reviewer, "review_pull_request", base + mapOf("verdict" to "COMMENTED", "body" to "참고")).also { assertFalse(it.error, it.text) }.error)
        val own = support.call(mockMvc, author, "review_pull_request", base + mapOf("verdict" to "APPROVED"))
        assertTrue(own.error && own.text.contains("403"), own.text)
        val bad = support.call(mockMvc, reviewer, "review_pull_request", base + mapOf("verdict" to "MERGE"))
        assertTrue(bad.error && bad.text.contains("400"), bad.text)
    }

    @Test
    fun `풀 리퀘스트를 닫고 다시 연다`() {
        val f = withBranch(support.project())
        val token = support.token(f.manager, "repo")
        assertFalse(create(f, token).error)
        val pr = pullRequests.findAll().single { it.toProject?.id == f.project.id }
        val base = args(f) + mapOf("number" to pr.number)
        assertFalse(support.call(mockMvc, token, "close_pull_request", base).also { assertFalse(it.error, it.text) }.error)
        assertEquals(dev.yona.domain.enumeration.State.CLOSED, pullRequests.findById(pr.id!!).get().state)
        assertFalse(support.call(mockMvc, token, "reopen_pull_request", base).also { assertFalse(it.error, it.text) }.error)
        assertEquals(dev.yona.domain.enumeration.State.OPEN, pullRequests.findById(pr.id!!).get().state)
    }

    @Test
    fun `읽기 전용 토큰은 풀 리퀘스트 쓰기 도구를 쓸 수 없다`() {
        val f = withBranch(support.project())
        val token = support.token(f.manager, "read:repo")
        val calls = listOf(
            "create_pull_request" to mapOf("title" to "x", "fromBranch" to "feature", "toBranch" to "master"), "review_pull_request" to mapOf("number" to 1, "verdict" to "COMMENTED", "body" to "x"),
            "close_pull_request" to mapOf("number" to 1), "reopen_pull_request" to mapOf("number" to 1), "merge_pull_request" to mapOf("number" to 1)
        )
        for ((tool, extra) in calls) {
            val result = support.call(mockMvc, token, tool, args(f) + extra)
            assertTrue(result.error && result.text.contains("403"), "$tool: ${result.text}")
        }
        assertTrue(pullRequests.findAll().none { it.toProject?.id == f.project.id })
    }

    private fun numberOf(f: McpToolsSupport.Fixture) = pullRequests.findAll().single { it.toProject?.id == f.project.id }.number

    @Test
    fun `프로젝트 관리자가 풀 리퀘스트를 병합하고 다시 병합하면 오류이다`() {
        val f = withBranch(support.project())
        assertFalse(create(f, support.token(f.member, "repo")).error)
        val number = numberOf(f)
        val manager = support.token(f.manager, "repo")
        val merged = support.call(mockMvc, manager, "merge_pull_request", args(f) + mapOf("number" to number, "method" to "merge"))
        assertFalse(merged.error, merged.text)
        assertEquals("merged", merged.json!!.get("state").asString())
        assertEquals(dev.yona.domain.enumeration.State.MERGED, pullRequests.findAll().single { it.toProject?.id == f.project.id }.state)
        val again = support.call(mockMvc, manager, "merge_pull_request", args(f) + mapOf("number" to number))
        assertTrue(again.error && again.text.contains("409"), again.text)
    }

    @Test
    fun `스쿼시로 병합하며 제목과 본문을 정할 수 있고 모르는 방식은 거부한다`() {
        val f = withBranch(support.project())
        assertFalse(create(f, support.token(f.member, "repo")).error)
        val number = numberOf(f)
        val manager = support.token(f.manager, "repo")
        val unknown = support.call(mockMvc, manager, "merge_pull_request", args(f) + mapOf("number" to number, "method" to "fast-forward"))
        assertTrue(unknown.error && unknown.text.contains("400"), unknown.text)
        val squash = support.call(mockMvc, manager, "merge_pull_request", args(f) + mapOf("number" to number, "method" to "squash", "commitTitle" to "에이전트 병합", "commitMessage" to "본문"))
        assertFalse(squash.error, squash.text)
        assertEquals("squash", squash.json!!.get("mergeMethod").asString())
    }

    @Test
    fun `프로젝트 구성원이 아닌 사용자는 repo 범위가 있어도 병합하지 못한다`() {
        val f = withBranch(support.project())
        assertFalse(create(f, support.token(f.member, "repo")).error)
        val result = support.call(mockMvc, support.token(support.user(), "repo"), "merge_pull_request", args(f) + mapOf("number" to numberOf(f)))
        assertTrue(result.error && result.text.contains("403"), result.text)
        assertEquals(dev.yona.domain.enumeration.State.OPEN, pullRequests.findAll().single { it.toProject?.id == f.project.id }.state)
    }

    @Test
    fun `닫힌 풀 리퀘스트는 병합하지 못한다`() {
        val f = withBranch(support.project())
        val manager = support.token(f.manager, "repo")
        assertFalse(create(f, manager).error)
        val base = args(f) + mapOf("number" to numberOf(f))
        assertFalse(support.call(mockMvc, manager, "close_pull_request", base).error)
        val result = support.call(mockMvc, manager, "merge_pull_request", base)
        assertTrue(result.error && result.text.contains("409"), result.text)
    }

    @Test
    fun `필요한 승인 수를 채우지 못하면 병합은 409로 막히고 승인한 뒤에는 병합된다`() {
        val f = withBranch(support.project())
        rulesets.save(dev.yona.domain.project.Ruleset(project = f.project, name = "master", pattern = "master", requiredApprovals = 1, createdDate = java.util.Date()))
        em.flush(); em.refresh(f.project) // 같은 트랜잭션에서 이미 읽은 프로젝트의 규칙 묶음 목록을 새로 읽는다
        val author = support.token(f.member, "repo")
        assertFalse(create(f, author).error)
        val blocked = support.call(mockMvc, author, "merge_pull_request", args(f) + mapOf("number" to numberOf(f)))
        assertTrue(blocked.error && blocked.text.contains("409") && blocked.text.contains("approvals"), blocked.text)
        assertEquals(dev.yona.domain.enumeration.State.OPEN, pullRequests.findAll().single { it.toProject?.id == f.project.id }.state)
        // 조건에 막힌 병합이 병합 중 표시를 남기지 않으므로(R-76 개선) 승인한 뒤에는 병합된다
        assertFalse(support.call(mockMvc, support.token(f.manager, "repo"), "review_pull_request", args(f) + mapOf("number" to numberOf(f), "verdict" to "APPROVED", "body" to "ok")).error)
        val merged = support.call(mockMvc, author, "merge_pull_request", args(f) + mapOf("number" to numberOf(f)))
        assertFalse(merged.error, merged.text)
    }
}
