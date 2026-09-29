package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.apitoken.ApiToken
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenPermission
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScope
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenScopeGroup
import com.github.yonaprojects.yona.domain.apitoken.hashApiToken
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

// 게시판(Board)에는 Issue/PR/Wiki와 달리 `/api/v1/projects/{owner}/{project}/...`
// 네임스페이스에 대응하는 v1 REST 어댑터가 없었다. 유일한 JSON 경로(`/api/projects/{id}/posts`,
// BoardController)는 ApiTokenAuthenticationFilter의 레거시 숫자ID 패턴에 걸려 무조건
// ADMINISTRATION 스코프를 요구했다(ProjectMemberController 전용으로 의도된 패턴이 너무 넓게 잡혀
// 있었던 버그, 그 패턴 자체는 ApiTokenAuthenticationFilterSpec에서 좁혔다) — 결과적으로
// BOARD_POST 스코프만 가진 fine-grained 토큰으로는 게시판을 읽을 방법이 전혀 없었다.
// BoardRestApiController가 그 v1 어댑터다(PullRequestApiController/WikiRestApiController와 동일한
// "얇은 위임" 패턴).
class BoardRestApiControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository,
    private val postingRepository: PostingRepository,
    private val projectUserRepository: ProjectUserRepository,
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
                User(loginId = "board-owner", name = "게시판소유자", email = "board-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "board-repo"))
            // project.owner는 표시용 문자열일 뿐 AccessControl.isAllowed()의 실제 권한 판정은
            // ProjectUser 관계(user.isMemberOf(project))를 본다 — 직접 저장만으로는 소유자가
            // 멤버로 등록되지 않는다.
            // Role(id = ...)을 DB 조회 없이 transient 객체로 바로 참조하지 않는다 - P3-74 후속
            // 조사에서, AbstractIntegrationTest의 @DynamicPropertySource가 매 통합테스트 클래스마다
            // System.nanoTime() 기반 H2 URL을 등록해도 Spring의
            // DynamicPropertiesContextCustomizer.equals()/hashCode()가 "등록된 메서드 시그니처"만
            // 비교하고 그 반환값(URL)은 비교하지 않아, 서로 다른 통합테스트 클래스가 우연히 같은
            // ApplicationContext를 캐시 히트로 공유하는 경우가 실측으로 확인됐다(예: 이 스펙이
            // YonaApplicationTests의 컨텍스트를 그대로 재사용). 그 경우 DatabaseInitializer(앱 부팅
            // 시 1회만 실행되는 CommandLineRunner)가 채워 넣었어야 할 role 기본 데이터가 이 스펙
            // 실행 시점에는 아직 반영되지 않은 것처럼 보일 수 있어, transient Role 참조가
            // TransientPropertyValueException으로 이어졌다(전체 스위트 실행에서만 간헐적으로
            // 재현, 단독 실행에서는 재현 안 됨). LegacyIssueResponseIntegrationSpec이 이미 쓰는
            // 대로 실제 저장된 Role을 조회하고 없으면 그때 채워 넣는 방어적 패턴으로 캐시 히트
            // 여부와 무관하게 항상 안전하게 만든다.
            val managerRole = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            projectUserRepository.save(
                ProjectUser(user = owner, project = project, role = managerRole)
            )
        }

        afterSpec {
            postingRepository.deleteAll()
            apiTokenRepository.deleteAll()
            projectUserRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
        }

        describe("GET /api/v1/projects/{owner}/{project}/board") {
            it("BOARD 스코프가 없는 토큰은 403이어야 한다") {
                val rawToken = "board-scope-none"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.ISSUES, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/board")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("BOARD:READ 스코프가 있는 토큰은 실제 게시글 목록을 200으로 돌려줘야 한다") {
                val posting = postingRepository.save(
                    Posting(title = "공지사항", body = "본문", project = project)
                )

                val rawToken = "board-scope-read"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.BOARD, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/board")
                        .header("Yona-Token", rawToken)
                ).andReturn()

                result.response.status shouldBe 200
                result.response.contentAsString shouldContain "공지사항"
                postingRepository.delete(posting)
            }
        }

        describe("POST /api/v1/projects/{owner}/{project}/board") {
            it("BOARD:WRITE 스코프가 있는 토큰은 게시글을 생성하고 201을 반환해야 한다") {
                val rawToken = "board-scope-write"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.BOARD, permission = ApiTokenPermission.WRITE))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/board")
                        .header("Yona-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title": "새 글", "body": "내용", "notice": false, "readme": false}""")
                ).andReturn()

                result.response.status shouldBe 201
                result.response.contentAsString shouldContain "새 글"
            }

            it("BOARD:READ 스코프만 있는 토큰은 쓰기 요청을 403으로 거부해야 한다") {
                val rawToken = "board-scope-read-only-write-attempt"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(rawToken),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                token.scopes.add(ApiTokenScope(apiToken = token, scopeGroup = ApiTokenScopeGroup.BOARD, permission = ApiTokenPermission.READ))
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/board")
                        .header("Yona-Token", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title": "새 글", "body": "내용", "notice": false, "readme": false}""")
                ).andReturn()

                result.response.status shouldBe 403
            }
        }
    }
}
