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
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step8.6 항목1(2026-09-01, 우선순위 1위) — 신규 목록 조회 REST API
// (`web/WebhookRestApiController.kt`, `web/ProjectPermissionRestApiController.kt`)가 기존
// scopedApiPattern(3세그먼트)에 그대로 매칭돼 스코프 기반 인가가 걸리는지 검증한다.
// - webhooks: "webhooks" -> ResourceType.WEBHOOK(WEBHOOKS 그룹, Step1~3부터 이미 매핑)
// - permissions: "permissions" -> ResourceType.PROJECT_SETTING(ADMINISTRATION 그룹, 이번 라운드 신규 매핑)
// 패턴은 ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec과 동일하다.
class ApiTokenScopedWebhookAndPermissionSubpathAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val roleRepository: RoleRepository
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
                User(loginId = "webhook-perm-owner", name = "웹훅권한소유자", email = "webhook-perm-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "webhook-perm-repo"))

            // 컨트롤러 자체 권한 체크(checkWebhookPermission -> Operation.UPDATE,
            // isProjectManager)는 프로젝트 매니저를 요구하므로 owner를 MANAGER ProjectUser로 등록해둔다
            // (ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec과 동일한 패턴).
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
            project = projectRepository.save(project)
        }

        afterSpec {
            apiTokenRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        fun tokenWith(raw: String, scopeGroup: ApiTokenScopeGroup?, permission: ApiTokenPermission): ApiToken {
            val token = ApiToken(
                owner = owner,
                tokenHash = hashApiToken(raw),
                allRepositories = true,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
            if (scopeGroup != null) {
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = scopeGroup, permission = permission))
            }
            return apiTokenRepository.save(token)
        }

        describe("webhooks 목록 조회의 스코프 기반 인가(WEBHOOKS 그룹)") {
            it("webhooks 스코프가 전혀 없는 토큰은 웹훅 목록 조회를 403으로 거부해야 한다") {
                val raw = "webhooks-list-no-scope"
                tokenWith(raw, null, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/webhooks")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("webhooks 읽기 권한이 있는 토큰은 웹훅 목록 조회에서 필터를 통과해 실제로 목록을 반환해야 한다") {
                val raw = "webhooks-list-read"
                tokenWith(raw, ApiTokenScopeGroup.WEBHOOKS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/webhooks")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }
        }

        describe("permissions 목록 조회의 스코프 기반 인가(ADMINISTRATION 그룹)") {
            it("administration 스코프가 전혀 없는 토큰은 권한 목록 조회를 403으로 거부해야 한다") {
                val raw = "permissions-list-no-scope"
                tokenWith(raw, null, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/permissions")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("administration 읽기 권한이 있는 토큰은 권한 목록 조회에서 필터를 통과해 실제로 목록을 반환해야 한다") {
                val raw = "permissions-list-read"
                tokenWith(raw, ApiTokenScopeGroup.ADMINISTRATION, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/permissions")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }
        }
    }
}
