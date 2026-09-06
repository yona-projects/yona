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
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.vcs.RepositoryService
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-10 — ApiTokenAuthenticationFilter의 resourceSegmentToResourceType에 새로 추가한
// "tags"(-> ResourceType.CODE, CODE 그룹) 세그먼트가 기존 scopedApiPattern에 그대로 매칭돼 스코프
// 기반 인가가 걸리는지 검증한다. 패턴은
// ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec의 "fork" 케이스와 동일하다.
class ApiTokenScopedTagAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val roleRepository: RoleRepository,
    private val repositoryService: RepositoryService
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

            owner = userRepository.save(
                User(loginId = "tag-scope-owner", name = "태그스코프소유자", email = "tag-scope-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "tag-scope-repo", vcs = "GIT"))

            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
            project = projectRepository.save(project)

            // "code 읽기 권한이 있는 토큰은 200이어야 한다" 케이스가 실제 저장소 조회까지 성공하는지
            // 검증하려면(단순 "403이 아니다"보다 강한 assertion) 진짜 bare 저장소가 필요하다.
            repositoryService.getRepository(project).create()
        }

        afterSpec {
            apiTokenRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        fun tokenWith(raw: String, scopeGroup: ApiTokenScopeGroup, permission: ApiTokenPermission): ApiToken {
            val token = ApiToken(
                owner = owner,
                tokenHash = hashApiToken(raw),
                allRepositories = true,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = scopeGroup, permission = permission))
            return apiTokenRepository.save(token)
        }

        describe("tags 하위 경로의 스코프 기반 인가(CODE 그룹)") {
            it("code 그룹 스코프가 전혀 없는 토큰은 태그 목록 조회를 403으로 거부해야 한다") {
                val raw = "tags-no-scope"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/tags")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("code 읽기 권한이 있는 토큰은 태그 목록 조회 요청에서 필터를 통과해야 한다") {
                val raw = "tags-read"
                tokenWith(raw, ApiTokenScopeGroup.CODE, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/tags")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
                result.response.status shouldBe 200
            }

            it("code 쓰기 권한이 없는(읽기 전용) 토큰은 태그 생성 요청을 403으로 거부해야 한다") {
                val raw = "tags-create-readonly"
                tokenWith(raw, ApiTokenScopeGroup.CODE, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/tags")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"scope-test-tag"}""")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("code 쓰기 권한이 있는 토큰은 태그 생성 요청에서 필터를 통과해야 한다") {
                val raw = "tags-create-write"
                tokenWith(raw, ApiTokenScopeGroup.CODE, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/tags")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"scope-test-tag"}""")
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }
    }
}
