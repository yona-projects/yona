package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScope
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.apitoken.hashApiToken
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step3 — 신규 `/api/v1/projects/{owner}/{project}/{resource}` 네임스페이스(Step4~6
// 컨트롤러는 이번 라운드 범위 밖)로 들어오는 요청을 ApiTokenAuthenticationFilter가 스코프만으로
// 거부하는지 검증한다. 컨트롤러가 아직 없어도 필터가 컨트롤러 디스패치 전에 403을 직접 응답하므로
// 검증 가능하다(GitAuthorizationFilter의 sendError+return과 동일한 위치의 방어).
class ApiTokenScopedAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var project: Project

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            // BootstrapSetupInterceptor가 회원 0명일 때 무조건 /bootstrap-setup으로 리다이렉트하므로
            // (SwaggerUiAccessIntegrationSpec과 동일한 이유) 먼저 사용자를 만들어 게이트를 우회한다.
            owner = userRepository.save(
                User(loginId = "scope-owner", name = "스코프소유자", email = "scope-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "scoped-repo"))
        }

        // api_token.owner_id는 cascade delete가 없는 FK라, 이 스펙이 만든 owner/apiToken을 정리하지
        // 않고 두면 이후 실행되는 다른 스펙들의 userRepository.deleteAll()이 FK 위반으로 깨진다
        // (전체 스위트 실행에서 실제로 이렇게 확인됨 — 개별 스펙만 돌릴 땐 안 드러남).
        afterSpec {
            apiTokenRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        describe("스코프 기반 API 토큰 인가") {
            it("issues 그룹 write 권한 없는 토큰으로 쓰기 요청을 보내면 403이어야 한다") {
                val rawToken = "scoped-token-read-only"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 그룹에 스코프가 전혀 없는 토큰은 읽기 요청도 403이어야 한다") {
                val rawToken = "scoped-token-no-scope"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 그룹 write 권한이 있는 토큰은 필터를 통과해 컨트롤러 디스패치까지 가야 한다") {
                val rawToken = "scoped-token-write"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                // 이슈 REST API 컨트롤러 자체는 Step4(이번 라운드 범위 밖)에서 구현되므로 여기서는
                // "필터가 막지 않았다(403이 아니다)"만 확인한다 — 미매핑 경로라 실제로는 404가 온다.
                result.response.status shouldNotBe 403
            }

            it("선택 저장소 스코프 토큰은 스코프에 없는 프로젝트에 대한 요청을 403으로 거부해야 한다") {
                val otherProject = projectRepository.save(Project(owner = owner.loginId, name = "other-repo"))
                val rawToken = "scoped-token-wrong-repo"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = false,
                    scopedProjects = mutableSetOf(otherProject),
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("만료된 토큰은 스코프가 있어도 403이어야 한다") {
                val rawToken = "scoped-token-expired"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().minus(1, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.WRITE))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }
        }
    }
}
