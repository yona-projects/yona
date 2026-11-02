package dev.yona.mcp

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 조직의 OAuth 앱 정책(X-39, D-78)은 MCP 도구에도 같은 뜻으로 적용된다(도구는 외부 API 요청으로 바뀌어 같은 검사를 지난다).
 */
@AutoConfigureMockMvc
@Transactional
@Import(McpToolsSupport::class)
@org.springframework.test.context.TestPropertySource(properties = ["yona.data-dir=build/tmp/it-mcp-org-policy"])
class McpOrgPolicyTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var support: McpToolsSupport
    @Autowired private lateinit var policy: dev.yona.domain.oauth2server.OAuthOrgPolicyService
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService
    @Autowired private lateinit var projects: dev.yona.domain.project.ProjectRepository

    @Test
    fun `제한한 조직의 비공개 프로젝트는 승인하지 않은 앱의 MCP 도구로 읽을 수 없다`() {
        val f = support.project(ProjectScope.PRIVATE)
        val org = organizationService.create("mo${System.nanoTime() % 1000000}", null, f.manager)
        f.project.organization = org; f.project.owner = org.name; projects.save(f.project)
        val token = support.token(f.member, "read:user,read:repo")
        assertFalse(support.call(mockMvc, token, "get_project", mapOf("owner" to org.name, "project" to f.project.name)).error)
        policy.setRestricted(org, true, f.manager)
        val denied = support.call(mockMvc, token, "get_project", mapOf("owner" to org.name, "project" to f.project.name))
        assertTrue(denied.error, denied.text); assertTrue(denied.text.contains("restricts"), denied.text)
    }
}
