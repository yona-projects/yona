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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 4라운드(Step8.5 서버 보강) — ApiTokenAuthenticationFilter의
// resourceSegmentToResourceType에 새로 추가한 "fork"(-> ResourceType.FORK, CODE 그룹)/
// "labels"(-> ResourceType.ISSUE_LABEL, ISSUES 그룹) 세그먼트가 기존 scopedApiPattern(3세그먼트,
// (?:/.*)? 접미부)에 그대로 매칭돼 스코프 기반 인가가 걸리는지 검증한다. 패턴은
// ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec과 동일하다.
class ApiTokenScopedProjectForkAndLabelSubpathAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var forkRequester: User
    private lateinit var project: Project

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()

            owner = userRepository.save(
                User(loginId = "fork-label-owner", name = "포크라벨소유자", email = "fork-label-owner@example.com")
            )
            // ProjectServiceImpl.forkProject()는 destinationOwner가 비어 있으면 forker.loginId로
            // 기본값을 채운다 - fork 토큰의 소유자를 project.owner와 동일하게 두면(자기 자신을
            // 포크) destOwner/destName이 원본과 완전히 같아져 findByOwnerAndName()의 유일성이
            // 깨진다(IncorrectResultSizeDataAccessException, 실측 확인) - 그래서 fork 요청은 반드시
            // 프로젝트 소유자가 아닌 별도 사용자의 토큰으로 보낸다.
            forkRequester = userRepository.save(
                User(loginId = "fork-label-requester", name = "포크요청자", email = "fork-label-requester@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "fork-label-repo"))

            // settings 하위 경로(PATCH) 검증에 필요 - ProjectController.updateProject()가
            // isProjectManager()를 요구해서(project.owner 문자열 일치만으로는 통과 안 됨) owner를
            // MANAGER ProjectUser로도 등록해둔다(ApiTokenScopedIssueAndPullRequestSubpathAuthorization
            // IntegrationSpec과 동일한 패턴).
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
            project = projectRepository.save(project)
        }

        afterSpec {
            apiTokenRepository.deleteAll()
            // fork 테스트가 실제로 fork-label-requester/fork-label-repo 프로젝트를 새로 만들어내므로
            // (webAppContextSetup 통합테스트라 실제 서비스 로직이 전부 실행됨) 원본을 지우기 전에
            // 그 fork부터 정리해야 FK(original_project_id) 위반 없이 삭제된다.
            projectRepository.findByOwner(forkRequester.loginId).forEach { projectRepository.delete(it) }
            projectRepository.delete(project)
            userRepository.delete(forkRequester)
            userRepository.delete(owner)
        }

        fun tokenWith(raw: String, scopeGroup: ApiTokenScopeGroup, permission: ApiTokenPermission, tokenOwner: User = owner): ApiToken {
            val token = ApiToken(
                owner = tokenOwner,
                tokenHash = hashApiToken(raw),
                allRepositories = true,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
            token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = scopeGroup, permission = permission))
            return apiTokenRepository.save(token)
        }

        describe("fork 하위 경로의 스코프 기반 인가(CODE 그룹)") {
            it("code 쓰기 권한이 없는 토큰은 fork 요청을 403으로 거부해야 한다") {
                val raw = "fork-readonly"
                tokenWith(raw, ApiTokenScopeGroup.CODE, ApiTokenPermission.READ, forkRequester)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/fork")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("code 쓰기 권한이 있는 토큰은 fork 요청에서 필터를 통과해야 한다") {
                val raw = "fork-write"
                tokenWith(raw, ApiTokenScopeGroup.CODE, ApiTokenPermission.WRITE, forkRequester)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/fork")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }

        describe("labels 하위 경로의 스코프 기반 인가(ISSUES 그룹)") {
            it("issues 쓰기 권한이 없는 토큰은 라벨 생성 요청을 403으로 거부해야 한다") {
                val raw = "labels-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/labels")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 쓰기 권한이 있는 토큰은 라벨 생성 요청에서 필터를 통과해야 한다") {
                val raw = "labels-write"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/labels")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }

        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — `yona project edit`/`delete`. 개별 프로젝트
        // 조회(2세그먼트)는 이미 "metadata" 스코프(repo scope만 확인)로 처리되므로, 쓰기 작업까지
        // 그 경로에 얹으면 ADMINISTRATION 권한이 전혀 없는 토큰도 프로젝트를 수정/삭제할 수 있는
        // 구멍이 생긴다 - 그래서 PATCH/DELETE는 반드시 별도 "settings" 세그먼트(3세그먼트,
        // ResourceType.PROJECT_SETTING -> ADMINISTRATION)로 노출했다. 이 describe가 그 설계가
        // 실제로 강제되는지 확인한다.
        describe("settings 하위 경로(project edit/delete)의 스코프 기반 인가(ADMINISTRATION 그룹)") {
            it("administration 쓰기 권한이 없는 토큰은 프로젝트 삭제 요청을 403으로 거부해야 한다") {
                val raw = "settings-delete-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ADMINISTRATION, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    delete("/api/v1/projects/${owner.loginId}/${project.name}/settings")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("administration 그룹에 스코프가 전혀 없는 토큰은 프로젝트 수정 요청도 403이어야 한다") {
                val raw = "settings-edit-no-scope"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(raw),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    patch("/api/v1/projects/${owner.loginId}/${project.name}/settings")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"overview":"설명","projectScope":"PUBLIC"}""")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("administration 쓰기 권한이 있는 토큰은 프로젝트 수정 요청에서 필터를 통과해 실제로 반영돼야 한다") {
                val raw = "settings-edit-write"
                tokenWith(raw, ApiTokenScopeGroup.ADMINISTRATION, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    patch("/api/v1/projects/${owner.loginId}/${project.name}/settings")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"overview":"필터 통과 확인용 설명","projectScope":"PUBLIC"}""")
                ).andReturn()

                result.response.status shouldBe 200
            }
        }
    }
}
