package com.github.yonaprojects.yona.web

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
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
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

// 실제 VS Code 확장(yonaco)이 `?state=open`(소문자, State enum의 자체 value 필드와 동일한
// 표기)으로 호출했다가 400을 받은 걸 재현/회귀 방지한다. State enum이 자기 자신을 문자열로
// 표현할 때 쓰는 값(OPEN("open") 등)과, Spring이 @RequestParam을 enum으로 바인딩할 때 기본으로
// 쓰는 규칙(Enum.valueOf, 즉 상수명 그대로인 "OPEN")이 서로 다른데, State.getValue()라는
// 소문자 변환 헬퍼가 있어도 컨트롤러가 `state: State?`로 직접 바인딩하는 곳(Issue/PullRequest/
// Milestone 전부, 9곳)에는 전혀 연결돼 있지 않았다 — UserIssueStatusRestApiController처럼
// `state: String`을 받아 직접 State.getValue()를 호출하는 곳만 우연히 소문자를 받아줬다.
class StateQueryParamIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val projectUserRepository: ProjectUserRepository
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
                User(loginId = "state-param-owner", name = "테스터", email = "state-param-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "state-param-repo"))
            // project.owner는 표시용 문자열일 뿐 AccessControl의 실제 권한 판정은 ProjectUser
            // 관계(user.isMemberOf(project))를 본다.
            projectUserRepository.save(
                ProjectUser(user = owner, project = project, role = Role(id = RoleType.MANAGER.roleType))
            )
        }

        afterSpec {
            apiTokenRepository.deleteAll()
            projectUserRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        describe("state 쿼리 파라미터 대소문자") {
            it("GET .../issues?state=open(소문자)은 400이 아니라 정상 처리돼야 한다") {
                val rawToken = "state-param-issues"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/issues")
                        .param("state", "open")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET .../pull-requests?state=open(소문자)도 400이 아니라 정상 처리돼야 한다") {
                val rawToken = "state-param-prs"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.PULL_REQUESTS, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests")
                        .param("state", "open")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 200
            }
        }
    }
}
