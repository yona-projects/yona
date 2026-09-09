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
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.notification.NotificationEventRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.UserState
import com.github.yonaprojects.yona.domain.watch.WatchRepository
import com.github.yonaprojects.yona.domain.webhook.WebhookRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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

// TASK-0417 — 실제 서버 + 실제 yona-cli로 재현한 "Fine-grained PAT이 인식 안 되는 5개 URL" 중
// `/api/projects/{id}/members`의 500(item3-3)을 뺀 4개의 스코프 인식 갭 회귀 방지.
// 공통 근본원인: ApiTokenAuthenticationFilter의 scopedApiPattern/individualProjectPattern/
// ownerOnlyPattern이 전부 `/api/v1/projects/{owner}/...`(최소 owner 세그먼트 필요) 형태만
// 인식해, 그 밖의 URL(세그먼트가 없거나, prefix가 다르거나, PK 기반인 URL)로 들어온 요청은
// authenticateLegacy(레거시 전권 토큰 조회)로 새 버려 fine-grained PAT을 전혀 인식하지 못했다.
class ApiTokenAccountLevelAndLegacyAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val roleRepository: RoleRepository,
    private val webhookRepository: WebhookRepository,
    private val watchRepository: WatchRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val notificationEventRepository: NotificationEventRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            val securityFilter = wac.getBean("springSecurityFilterChain", Filter::class.java)
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters<DefaultMockMvcBuilder>(securityFilter)
                .build()
        }

        // TASK-0417 회귀 — 이 스펙의 여러 테스트가 실제 POST로 부수효과 있는 엔티티를 만든다
        // (webhooks: 실제 Webhook, project create: WatchService.watch()가 만드는 Watch +
        // ProjectServiceImpl.createProject()가 만드는 매니저 ProjectUser). AbstractIntegrationTest는
        // 같은 forked 테스트 JVM 안의 스펙끼리 H2 인메모리 DB를 공유하므로, 이 정리가 없으면 남은
        // 행들이 project_id/user_id FK를 계속 참조해 뒤에 실행되는 무관한 스펙(예: 프로젝트/유저를
        // deleteAll()하는 스펙)에서 "Referential integrity constraint violation" 연쇄 실패를
        // 일으킨다(실제로 전체 스위트 실행에서 재현/확인함 — Webhook->WatchServiceSpec,
        // Watch->OrganizationServiceSpec 등 무관한 스펙에서 FK 위반으로 튀었었다). FK 의존 순서대로
        // 지운다(참조하는 쪽 먼저).
        afterSpec {
            watchRepository.deleteAll()
            webhookRepository.deleteAll()
            notificationEventRepository.deleteAll()
            apiTokenRepository.deleteAll()
            projectUserRepository.deleteAll()
            projectRepository.deleteAll()
            userRepository.deleteAll()
        }

        fun tokenFor(
            owner: User,
            raw: String,
            allRepositories: Boolean,
            scopeGroup: ApiTokenScopeGroup?,
            permission: ApiTokenPermission,
            scopedProjects: Set<Project> = emptySet()
        ): ApiToken {
            val token = ApiToken(
                owner = owner,
                tokenHash = hashApiToken(raw),
                allRepositories = allRepositories,
                expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
            )
            token.scopedProjects = scopedProjects.toMutableSet()
            if (scopeGroup != null) {
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = scopeGroup, permission = permission))
            }
            return apiTokenRepository.save(token)
        }

        describe("계정 수준 URL(owner 세그먼트 없음)의 스코프 인가") {
            it("POST /api/v1/projects는 allRepositories=false 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-create-owner1", name = "생성권한없음", email = "acct-create-owner1@example.com")
                )
                val raw = "project-create-no-allrepos"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"owner":"${owner.loginId}","name":"acct-create-proj1"}""")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("POST /api/v1/projects는 allRepositories=true + ADMINISTRATION:WRITE 토큰을 201로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-create-owner2", name = "생성권한있음", email = "acct-create-owner2@example.com")
                )
                val raw = "project-create-allrepos-admin"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects")
                        .header("Yona-Token", raw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"owner":"${owner.loginId}","name":"acct-create-proj2"}""")
                ).andReturn()

                result.response.status shouldBe 201
            }

            it("GET /api/v1/user/issues/status는 ISSUES 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-issues-owner1", name = "이슈권한없음", email = "acct-issues-owner1@example.com")
                )
                val raw = "user-issues-status-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/user/issues/status").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/user/issues/status는 ISSUES:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-issues-owner2", name = "이슈권한있음", email = "acct-issues-owner2@example.com")
                )
                val raw = "user-issues-status-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/user/issues/status").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            // yona-wiki P3-02 16라운드(TASK-0440) — `GET /api/v1/user/status`(`gh status` 대응)는
            // ISSUES+PULL_REQUESTS 두 스코프를 AND로 요구한다(둘 중 하나만 있으면 403).
            it("GET /api/v1/user/status는 ISSUES 스코프만 있고 PULL_REQUESTS 스코프가 없으면 403이어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-status-owner1", name = "이슈만있음", email = "acct-status-owner1@example.com")
                )
                val raw = "user-status-issues-only"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/user/status").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/user/status는 PULL_REQUESTS 스코프만 있고 ISSUES 스코프가 없으면 403이어야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-status-owner2", name = "PR만있음", email = "acct-status-owner2@example.com")
                )
                val raw = "user-status-prs-only"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.PULL_REQUESTS, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/user/status").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/user/status는 ISSUES:READ + PULL_REQUESTS:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "acct-status-owner3", name = "둘다있음", email = "acct-status-owner3@example.com")
                )
                val raw = "user-status-both-scopes"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(raw),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ))
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.PULL_REQUESTS, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/user/status").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /site/export는 ADMINISTRATION 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(
                        loginId = "acct-export-owner1", name = "백업권한없음", email = "acct-export-owner1@example.com",
                        state = UserState.SITE_ADMIN
                    )
                )
                val raw = "site-export-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/site/export").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /site/export는 allRepositories=true + ADMINISTRATION:READ 토큰(사이트 관리자)을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(
                        loginId = "acct-export-owner2", name = "백업권한있음", email = "acct-export-owner2@example.com",
                        state = UserState.SITE_ADMIN
                    )
                )
                val raw = "site-export-allrepos-admin"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/site/export").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /site/export는 allRepositories=false 토큰(사이트 관리자라도)을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(
                        loginId = "acct-export-owner3", name = "부분스코프", email = "acct-export-owner3@example.com",
                        state = UserState.SITE_ADMIN
                    )
                )
                val raw = "site-export-scoped-only"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/site/export").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }
        }

        // 사용자 요청(2026-09-09)으로 P3-02 계획 문서의 "미해결" 리스크 항목(search/organizations는
        // 저장소 단위 3세그먼트 모델에 맞지 않아 Fine-grained PAT이 인증되지 않는다)을 재조사한 결과,
        // 그 항목이 기록된 이후(16라운드) 정확히 이 문제를 풀기 위한 AccountLevelTarget 메커니즘이
        // 이미 만들어져 있었음을 확인 — 새 설계 없이 그 메커니즘을 확장하는 것만으로 해소 가능하다.
        // 현재는 이 URL들이 ApiTokenAuthenticationFilter의 어떤 패턴과도 매칭되지 않아 PAT 헤더가
        // 있어도 인증되지 않고(레거시 findByToken이 스코프 토큰의 원문을 모름), 그 결과 익명
        // 취급되어 apiResourceServerSecurityFilterChain의 anyRequest().authenticated()에 막혀
        // 401이 난다(스코프 부족의 403이 아니라 아예 인증 자체가 안 되는 401).
        describe("search/organizations 네임스페이스(P3-36)의 스코프 인가") {
            it("GET /api/v1/search/issues는 ISSUES 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-issues-owner2", name = "검색이슈권한없음2", email = "search-issues-owner2@example.com")
                )
                val raw = "search-issues-no-issues-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/issues").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/search/issues는 ISSUES:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-issues-owner3", name = "검색이슈권한있음", email = "search-issues-owner3@example.com")
                )
                val raw = "search-issues-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/issues").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /api/v1/search/prs는 PULL_REQUESTS 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-prs-owner1", name = "검색PR권한없음", email = "search-prs-owner1@example.com")
                )
                val raw = "search-prs-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/prs").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/search/prs는 PULL_REQUESTS:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-prs-owner2", name = "검색PR권한있음", email = "search-prs-owner2@example.com")
                )
                val raw = "search-prs-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.PULL_REQUESTS, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/prs").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /api/v1/search/projects는 ADMINISTRATION 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-projects-owner1", name = "검색프로젝트권한없음", email = "search-projects-owner1@example.com")
                )
                val raw = "search-projects-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/projects").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/search/projects는 ADMINISTRATION:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "search-projects-owner2", name = "검색프로젝트권한있음", email = "search-projects-owner2@example.com")
                )
                val raw = "search-projects-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/search/projects").param("q", "hello").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /api/v1/organizations는 ADMINISTRATION 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "org-list-owner1", name = "조직목록권한없음", email = "org-list-owner1@example.com")
                )
                val raw = "org-list-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/organizations").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/organizations는 ADMINISTRATION:READ 토큰을 200으로 통과시켜야 한다") {
                val owner = userRepository.save(
                    User(loginId = "org-list-owner2", name = "조직목록권한있음", email = "org-list-owner2@example.com")
                )
                val raw = "org-list-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/organizations").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 200
            }

            it("GET /api/v1/organizations/{name}도 동일하게 ADMINISTRATION 스코프로 인가돼야 한다(스코프 없으면 403)") {
                val owner = userRepository.save(
                    User(loginId = "org-view-owner1", name = "조직조회권한없음", email = "org-view-owner1@example.com")
                )
                val raw = "org-view-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/organizations/no-such-org").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("GET /api/v1/organizations/{name}은 ADMINISTRATION:READ 토큰이면 필터를 통과해 컨트롤러까지 도달해야 한다(존재하지 않는 조직이면 404)") {
                val owner = userRepository.save(
                    User(loginId = "org-view-owner2", name = "조직조회권한있음", email = "org-view-owner2@example.com")
                )
                val raw = "org-view-read"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/organizations/no-such-org").header("Yona-Token", raw)
                ).andReturn()

                // 필터를 통과해 컨트롤러(get())까지 도달하면, 존재하지 않는 조직이라 404를 응답한다
                // — 401/403이 아니라는 것으로 스코프 인가 필터를 실제로 통과했음을 확인한다.
                result.response.status shouldBe 404
            }
        }

        describe("레거시 세션 기반 웹 MVC 프로젝트 리소스(/projects/{owner}/{project}/{resource})의 스코프 인가") {
            it("POST /projects/{owner}/{project}/webhooks는 WEBHOOKS 스코프가 없는 토큰을 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "legacy-webhook-owner1", name = "웹훅권한없음1", email = "legacy-webhook-owner1@example.com")
                )
                var project = projectRepository.save(Project(owner = owner.loginId, name = "legacy-webhook-repo1"))
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
                project = projectRepository.save(project)

                val raw = "legacy-webhook-no-scope"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/projects/${owner.loginId}/${project.name}/webhooks")
                        .header("Yona-Token", raw)
                        .param("payloadUrl", "https://example.com/hook")
                        .param("webhookType", "SIMPLE")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("POST /projects/{owner}/{project}/webhooks는 WEBHOOKS:WRITE 토큰(매니저)을 정상 처리해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "legacy-webhook-owner2", name = "웹훅권한있음", email = "legacy-webhook-owner2@example.com")
                )
                var project = projectRepository.save(Project(owner = owner.loginId, name = "legacy-webhook-repo2"))
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
                project = projectRepository.save(project)

                val raw = "legacy-webhook-write"
                tokenFor(owner, raw, allRepositories = true, scopeGroup = ApiTokenScopeGroup.WEBHOOKS, permission = ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/projects/${owner.loginId}/${project.name}/webhooks")
                        .header("Yona-Token", raw)
                        .param("payloadUrl", "https://example.com/hook")
                        .param("webhookType", "SIMPLE")
                ).andReturn()

                // 필터를 통과해 컨트롤러(newWebhook)까지 도달하면 성공 시 "redirect:..." 뷰로
                // 3xx를 응답한다 — 이 스펙은 스코프 인가 필터 통과 여부만 검증하므로 401/403이
                // 아니라는 것으로 충분하다.
                (result.response.status == 401 || result.response.status == 403) shouldBe false
            }
        }

        describe("레거시 숫자 프로젝트 ID API(/api/projects/{id}/members)") {
            it("인증 정보가 전혀 없으면 500이 아니라 401을 응답해야 한다(ProjectMemberController.getLoginUserId)") {
                val owner = userRepository.save(
                    User(loginId = "legacy-member-owner1", name = "멤버추가무인증", email = "legacy-member-owner1@example.com")
                )
                val project = projectRepository.save(Project(owner = owner.loginId, name = "legacy-member-repo1"))

                val result = mockMvc.perform(
                    post("/api/projects/${project.id}/members").param("loginId", "someone")
                ).andReturn()

                result.response.status shouldBe 401
            }

            it("ADMINISTRATION 스코프가 없는 토큰은 403으로 거부해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "legacy-member-owner2", name = "멤버추가스코프없음", email = "legacy-member-owner2@example.com")
                )
                val project = projectRepository.save(Project(owner = owner.loginId, name = "legacy-member-repo2"))
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
                projectRepository.save(project)

                val raw = "legacy-member-no-scope"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.CODE, permission = ApiTokenPermission.WRITE, scopedProjects = setOf(project))

                val result = mockMvc.perform(
                    post("/api/projects/${project.id}/members").param("loginId", "someone").header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("ADMINISTRATION:WRITE 스코프 토큰(매니저)은 필터를 통과해 컨트롤러까지 도달해야 한다") {
                val owner = userRepository.save(
                    User(loginId = "legacy-member-owner3", name = "멤버추가권한있음", email = "legacy-member-owner3@example.com")
                )
                val newMember = userRepository.save(
                    User(loginId = "legacy-member-newbie", name = "새멤버", email = "legacy-member-newbie@example.com")
                )
                val project = projectRepository.save(Project(owner = owner.loginId, name = "legacy-member-repo3"))
                val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                    roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
                }
                project.projectUsers.add(ProjectUser(project = project, user = owner, role = managerRole))
                projectRepository.save(project)

                val raw = "legacy-member-write"
                tokenFor(owner, raw, allRepositories = false, scopeGroup = ApiTokenScopeGroup.ADMINISTRATION, permission = ApiTokenPermission.WRITE, scopedProjects = setOf(project))

                val result = mockMvc.perform(
                    post("/api/projects/${project.id}/members").param("loginId", newMember.loginId).header("Yona-Token", raw)
                ).andReturn()

                // 필터를 통과해 addMember 컨트롤러까지 도달하면(매니저 신원도 확인됨) 200이어야 한다.
                result.response.status shouldBe 200
            }
        }
    }
}
