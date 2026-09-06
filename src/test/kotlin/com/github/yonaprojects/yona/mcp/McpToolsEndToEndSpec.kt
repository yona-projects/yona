package com.github.yonaprojects.yona.mcp

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationConsentRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthAuthorizationRepository
import com.github.yonaprojects.yona.domain.oauth2server.OAuthRegisteredClientRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain as stringShouldContain
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport
import io.modelcontextprotocol.spec.McpSchema
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpRequest
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

// yona-wiki P3-07(MCP 서버) Step6 — "Claude Code로 실제 접속해 브라우저 팝업을 눈으로 확인"(계획
// 문서 DoD 1번, 사람만 할 수 있는 부분)의 자동화된 동등 검증(사용자 지시 3번). 이 스펙은
// McpOAuth2SecurityIntegrationSpec이 MockMvc로 이미 검증한 OAuth 플로우(DCR/PKCE/동의/토큰 발급)
// 그대로 재사용해 실제 액세스 토큰을 얻은 뒤, @SpringBootTest(webEnvironment=RANDOM_PORT)로 띄운
// 실제 임베디드 톰캣에 공식 MCP Java SDK 클라이언트(HttpClientStreamableHttpTransport, Claude
// 등 실제 클라이언트와 동일한 라이브러리)로 진짜 소켓을 통해 접속해 initialize -> tools/list ->
// tools/call까지 전부 실제 프로토콜로 태운다 — MockMvc는 DispatcherServlet 안쪽만 태우므로
// Streamable HTTP 세션/SSE 배선 자체가 맞는지는 검증하지 못하는데, 이 스펙이 그 갭을 메운다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpToolsEndToEndSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val clientRepository: OAuthRegisteredClientRepository,
    private val authorizationRepository: OAuthAuthorizationRepository,
    private val consentRepository: OAuthAuthorizationConsentRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    private var port: Int = 0

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var project: Project
    private lateinit var issue: Issue
    private val objectMapper = ObjectMapper()

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(loginId = "mcp-e2e-owner", name = "MCP E2E", email = "mcp-e2e-owner@example.com")
            )
            project = projectRepository.save(
                Project(owner = owner.loginId, name = "mcp-e2e-repo", projectScope = ProjectScope.PUBLIC)
            )
            issue = issueRepository.save(
                Issue(number = 1L, title = "E2E 테스트 이슈", project = project)
            )
        }

        afterSpec {
            issueRepository.delete(issue)
            authorizationRepository.deleteAll()
            consentRepository.deleteAll()
            clientRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        fun userDetails() = YonaUserDetails(
            id = owner.id!!,
            loginId = owner.loginId,
            passwordVal = "",
            passwordSalt = "",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        fun sha256Base64Url(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        // yona-wiki P3-07 Step6 — McpOAuth2SecurityIntegrationSpec에서 이미 실측 확인된 MockMvc
        // 함정과 동일: GET .param(...)은 request의 parameterMap만 채우고 getQueryString()은 비워둬
        // Spring Authorization Server의 OAuth2EndpointUtils.getQueryParameters()(request.
        // getQueryString() 기준으로 다시 필터링)가 모든 파라미터를 빈 것으로 취급한다 — 쿼리 문자열을
        // URL에 직접 인코딩해야 한다.
        fun authorizeGetUrl(vararg params: Pair<String, String>): URI {
            val query = params.joinToString("&") { (k, v) ->
                "$k=" + java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
            }
            return URI.create("/oauth2/authorize?$query")
        }

        // yona-wiki P3-07 Step6(회귀 수정, 2026-09-06) — /oauth2/authorize가 동의 화면으로 리다이렉트할
        // 때 붙이는 "state" 쿼리 파라미터는 원래 인가 요청의 state 값이 아니라 Spring Authorization
        // Server가 동의 단계 CSRF 방지를 위해 새로 발급하는 별도의 불투명한 값이다(실측 확인 — 원래
        // state를 그대로 재사용해 동의 POST를 보내면 매번 "invalid_request: state"로 거부된다).
        // 실제 브라우저 폼 제출과 동일하게 리다이렉트 URL에서 이 값을 그대로 꺼내 동의 POST에
        // 되돌려줘야 한다.
        fun extractQueryParam(uri: String, name: String): String {
            val query = URI.create(uri).query
            return query.split("&")
                .map { it.substringBefore("=") to java.net.URLDecoder.decode(it.substringAfter("="), "UTF-8") }
                .first { it.first == name }.second
        }

        // McpOAuth2SecurityIntegrationSpec과 동일한 DCR+PKCE+동의+토큰 교환 절차를 그대로 반복한다
        // (라이브러리를 새로 재구현하지 않고 동일한 실제 엔드포인트를 다시 태우는 것뿐이라 중복
        // 구현이 아니다) — 다만 이 스펙은 그 위에서 실제 MCP 프로토콜 호출까지 검증하는 것이
        // 차별점이다. scope로 요청 스코프를 의도적으로 제어해 merge_pull_request 거부 케이스를 만든다.
        fun issueAccessToken(scope: String): String {
            val redirectUri = "http://127.0.0.1:0/callback"
            val registerBody = """
                {
                  "client_name": "E2E Test Client ${UUID.randomUUID()}",
                  "redirect_uris": ["$redirectUri"],
                  "grant_types": ["authorization_code", "refresh_token"],
                  "response_types": ["code"],
                  "token_endpoint_auth_method": "none"
                }
            """.trimIndent()
            val registerResult = mockMvc.perform(
                post("/oauth2/register").contentType(MediaType.APPLICATION_JSON).content(registerBody)
            ).andReturn()
            registerResult.response.status shouldBe 201
            val clientId = objectMapper.readTree(registerResult.response.contentAsString)["client_id"].asText()

            val codeVerifier = "verifier-" + UUID.randomUUID().toString().replace("-", "") + "-0123456789"
            val codeChallenge = sha256Base64Url(codeVerifier)
            val mcpResourceUri = "http://localhost:8080/mcp"
            // yona-wiki P3-07 Step6 — state는 매 호출마다 유일해야 한다: OAuthAuthorizationRepository.
            // findByState()는 client_id로 좁히지 않고 state 문자열 하나만으로 조회하므로(P3-02/07의
            // JpaOAuth2AuthorizationService 구현 참고), 이 스펙처럼 한 테스트 안에서
            // issueAccessToken()을 여러 번 호출할 때 고정 문자열을 재사용하면 이전 호출이 남긴 행과
            // 충돌해 "state" 관련 invalid_request로 실패한다(실측 확인).
            val state = "e2e-state-" + UUID.randomUUID()

            val authorizeResult = mockMvc.perform(
                get(
                    authorizeGetUrl(
                        "response_type" to "code",
                        "client_id" to clientId,
                        "redirect_uri" to redirectUri,
                        "scope" to scope,
                        "state" to state,
                        "code_challenge" to codeChallenge,
                        "code_challenge_method" to "S256",
                        "resource" to mcpResourceUri
                    )
                ).with(user(userDetails()))
            ).andReturn()
            authorizeResult.response.status shouldBe 302
            val locationAfterAuthorize = authorizeResult.response.getHeader(HttpHeaders.LOCATION)!!
            locationAfterAuthorize.contains("/oauth2/consent") shouldBe true
            val consentState = extractQueryParam(locationAfterAuthorize, "state")

            var consentPostRequest = post("/oauth2/authorize")
                .param("client_id", clientId)
                .param("state", consentState)
                .with(user(userDetails()))
            scope.split(" ").forEach { consentPostRequest = consentPostRequest.param("scope", it) }
            val consentPost = mockMvc.perform(consentPostRequest).andReturn()
            consentPost.response.status shouldBe 302
            val redirectLocation = consentPost.response.getHeader(HttpHeaders.LOCATION)!!
            val code = URI.create(redirectLocation).query
                .split("&").associate { it.substringBefore("=") to it.substringAfter("=") }["code"]!!

            val tokenResult = mockMvc.perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("grant_type", "authorization_code")
                    .param("code", code)
                    .param("redirect_uri", redirectUri)
                    .param("client_id", clientId)
                    .param("code_verifier", codeVerifier)
                    .param("resource", mcpResourceUri)
            ).andReturn()

            return objectMapper.readTree(tokenResult.response.contentAsString)["access_token"].asText()
        }

        fun mcpClientWithToken(accessToken: String) = McpClient.sync(
            HttpClientStreamableHttpTransport.builder("http://localhost:$port")
                .endpoint("/mcp")
                .requestBuilder(HttpRequest.newBuilder().header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"))
                .build()
        ).clientInfo(McpSchema.Implementation("yona-mcp-e2e-test", "1.0.0")).build()

        describe("실제 MCP 클라이언트(공식 SDK)로 initialize -> tools/list -> tools/call") {
            it("issues:read/write, pull_requests:read 스코프 토큰으로 도구 목록을 조회하고 list_issues를 성공적으로 호출해야 한다") {
                val token = issueAccessToken("issues:read issues:write pull_requests:read")
                val client = mcpClientWithToken(token)
                try {
                    client.initialize()

                    val tools = client.listTools().tools().map { it.name() }.toSet()
                    tools shouldContain "list_issues"
                    tools shouldContain "get_issue"
                    tools shouldContain "create_issue"
                    tools shouldContain "comment_issue"
                    tools shouldContain "close_issue"
                    tools shouldContain "list_pull_requests"
                    tools shouldContain "get_pull_request"
                    tools shouldContain "create_pull_request"
                    tools shouldContain "review_pull_request"
                    tools shouldContain "comment_pull_request"
                    tools shouldContain "merge_pull_request"

                    val listResult = client.callTool(
                        McpSchema.CallToolRequest(
                            "list_issues",
                            mapOf("owner" to owner.loginId, "project" to project.name)
                        )
                    )
                    (listResult.isError() == true) shouldBe false
                    val text = (listResult.content().first() as McpSchema.TextContent).text()
                    text.stringShouldContain("E2E 테스트 이슈")
                } finally {
                    client.closeGracefully()
                }
            }

            it("pull_requests:write 스코프가 없는 토큰으로 merge_pull_request를 호출하면 CallToolResult가 오류로 표시되어야 한다") {
                val token = issueAccessToken("issues:read pull_requests:read")
                val client = mcpClientWithToken(token)
                try {
                    client.initialize()

                    val result = client.callTool(
                        McpSchema.CallToolRequest(
                            "merge_pull_request",
                            mapOf("owner" to owner.loginId, "project" to project.name, "number" to 1)
                        )
                    )

                    result.isError() shouldBe true
                } finally {
                    client.closeGracefully()
                }
            }

            it("인증 헤더 없이 접속하면 initialize 자체가 실패해야 한다(리소스 서버 인증 강제)") {
                val transport = HttpClientStreamableHttpTransport.builder("http://localhost:$port")
                    .endpoint("/mcp")
                    .build()
                val client = McpClient.sync(transport)
                    .clientInfo(McpSchema.Implementation("yona-mcp-e2e-test-noauth", "1.0.0"))
                    .build()

                var threw = false
                try {
                    client.initialize()
                } catch (e: Exception) {
                    threw = true
                } finally {
                    runCatching { client.closeGracefully() }
                }
                threw shouldBe true
            }
        }
    }
}
