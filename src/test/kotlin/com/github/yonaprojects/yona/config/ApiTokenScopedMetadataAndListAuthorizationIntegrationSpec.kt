package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.apitoken.hashApiToken
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step6.5 — "metadata 스코프 세그먼트 설계" + "프로젝트 목록 API 스코프 필터링
// 설계"가 필터(ApiTokenAuthenticationFilter)와 ProjectRestApiController를 실제로 함께 통과하는지
// 검증하는 통합테스트. ApiTokenScopedAuthorizationIntegrationSpec(Step3, issues 3세그먼트 경로)과
// 동일한 패턴(실제 시큐리티 필터 체인을 태운 MockMvc)을 리소스 세그먼트가 없는 2세그먼트(개별
// 조회)/1세그먼트(목록) 경로에 적용한다.
class ApiTokenScopedMetadataAndListAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var project: Project
    private lateinit var otherProject: Project

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            // BootstrapSetupInterceptor가 회원 0명일 때 무조건 /bootstrap-setup으로 리다이렉트하므로
            // (ApiTokenScopedAuthorizationIntegrationSpec과 동일한 이유) 먼저 사용자를 만들어 게이트를
            // 우회한다. 공개(PUBLIC) 프로젝트로 만들어 AccessControl 가시성 규칙과 무관하게
            // 필터/컨트롤러의 스코프 필터링 로직만 순수하게 검증한다.
            owner = userRepository.save(
                User(loginId = "metadata-owner", name = "메타데이터소유자", email = "metadata-owner@example.com")
            )
            project = projectRepository.save(
                Project(owner = owner.loginId, name = "metadata-repo", projectScope = ProjectScope.PUBLIC)
            )
            otherProject = projectRepository.save(
                Project(owner = owner.loginId, name = "metadata-repo-2", projectScope = ProjectScope.PUBLIC)
            )
        }

        // api_token.owner_id는 cascade delete가 없는 FK라, 정리하지 않으면 이후 실행되는 다른
        // 스펙들의 userRepository.deleteAll()이 FK 위반으로 깨진다(ApiTokenScopedAuthorizationIntegrationSpec
        // 과 동일한 이유로 실제 확인된 문제).
        afterSpec {
            apiTokenRepository.deleteAll()
            projectRepository.delete(otherProject)
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        describe("metadata 스코프 — 개별 프로젝트 조회(/api/v1/projects/{owner}/{project})") {
            it("그룹 권한이 하나도 없어도 전체 저장소 스코프 토큰이면 조회할 수 있어야 한다(403이 아니어야 한다)") {
                val rawToken = "metadata-token-no-scopes"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("선택 저장소 스코프 토큰의 scopedProjects 밖 프로젝트를 조회하면 403이어야 한다") {
                val rawToken = "metadata-token-wrong-repo"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = false,
                    scopedProjects = mutableSetOf(otherProject),
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }
        }

        describe("목록 스코프 필터링 — /api/v1/projects/{owner}") {
            it("전체 저장소 스코프 토큰은 owner의 모든 프로젝트를 목록에서 볼 수 있어야 한다") {
                val rawToken = "list-token-all-repos"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}")
                        .header("Yona-Token", rawToken)
                ).andReturn().also {
                    it.response.status shouldBe 200
                    it.response.contentAsString.contains(project.name) shouldBe true
                    it.response.contentAsString.contains(otherProject.name) shouldBe true
                }
            }

            it("선택 저장소 스코프 토큰은 scopedProjects에 포함된 프로젝트만 목록에서 볼 수 있어야 한다") {
                val rawToken = "list-token-selective"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = false,
                    scopedProjects = mutableSetOf(project),
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}")
                        .header("Yona-Token", rawToken)
                ).andReturn().also {
                    it.response.status shouldBe 200
                    it.response.contentAsString.contains(project.name) shouldBe true
                    it.response.contentAsString.contains(otherProject.name) shouldBe false
                }
            }

            it("세션 로그인(스코프 토큰 없음)은 기존과 동일하게 AccessControl 통과 목록을 전부 반환해야 한다") {
                mockMvc.perform(get("/api/v1/projects/${owner.loginId}"))
                    .andReturn().also {
                        it.response.status shouldBe 200
                        it.response.contentAsString.contains(project.name) shouldBe true
                        it.response.contentAsString.contains(otherProject.name) shouldBe true
                    }
            }
        }
    }
}
