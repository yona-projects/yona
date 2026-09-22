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
                User(loginId = "board-owner", name = "게시판소유자", email = "board-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "board-repo"))
            // project.owner는 표시용 문자열일 뿐 AccessControl.isAllowed()의 실제 권한 판정은
            // ProjectUser 관계(user.isMemberOf(project))를 본다 — 직접 저장만으로는 소유자가
            // 멤버로 등록되지 않는다.
            projectUserRepository.save(
                ProjectUser(user = owner, project = project, role = Role(id = RoleType.MANAGER.roleType))
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
