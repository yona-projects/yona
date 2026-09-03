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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona-wiki P3-02 Step4~5 — ApiTokenScopedAuthorizationIntegrationSpec(Step3)은 필터의 스코프 판정
// 로직 자체(만료/repo scope/권한크기)를 이슈 리소스 그룹의 베이스 경로(`/issues`)만으로 이미
// 충분히 검증했다. 이 스펙은 Step4~5에서 새로 생긴 하위 경로(`/issues/{number}/comments`,
// `/issues/{number}/close`, `/pull-requests/{number}/merge`,
// `/pull-requests/{number}/reviewers`)가 필터의 scopedApiPattern(`(?:/.*)?` 접미부)에 여전히
// 올바르게 매칭되어 같은 리소스 그룹(ISSUES/PULL_REQUESTS)으로 인가되는지만 확인한다 — 컨트롤러가
// 이제 실제로 존재하므로, "403이 아니다"는 필터를 통과해 실제 컨트롤러 디스패치까지 갔다는 뜻이다.
class ApiTokenScopedIssueAndPullRequestSubpathAuthorizationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val roleRepository: RoleRepository,
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

            owner = userRepository.save(
                User(loginId = "subpath-owner", name = "서브패스소유자", email = "subpath-owner@example.com")
            )
            project = projectRepository.save(Project(owner = owner.loginId, name = "subpath-repo"))

            // PullRequestController.mergePullRequest/addReviewer는 스코프 필터를 통과한 뒤에도
            // checkWritePermission(프로젝트 멤버 여부)을 PR 조회보다 먼저 확인한다 — 이 스펙의
            // 목적은 필터의 스코프 판정만 검증하는 것이라, 그 뒤 컨트롤러 단의 프로젝트 멤버십
            // 체크에서 우연히 403이 나 "필터 통과 여부" 판정과 뒤섞이지 않도록 owner를 프로젝트
            // 멤버로 미리 등록해둔다.
            val memberRole = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }
            // ProjectApiController.newProject()와 동일한 패턴 — projectUserRepository.save()를 직접
            // 호출하는 대신 project.projectUsers(cascade=ALL)에 추가해 저장한다. 직접 저장하면
            // project의 인메모리 projectUsers 컬렉션이 비어있는 채로 남아, afterSpec의
            // projectRepository.delete(project)가 그 stale 컬렉션 기준으로 cascade를 계산하면서
            // "Persistent instance of ProjectUser references an unsaved transient instance of
            // Project" 예외를 던진다(실제로 재현 확인).
            project.projectUsers.add(ProjectUser(project = project, user = owner, role = memberRole))
            project = projectRepository.save(project)
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

        describe("이슈 하위 경로(comments/close)의 스코프 기반 인가") {
            it("issues 쓰기 권한이 없는 토큰은 댓글 작성 요청을 403으로 거부해야 한다") {
                val raw = "subpath-issues-comments-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/comments")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 쓰기 권한이 있는 토큰은 댓글 작성 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-issues-comments-write"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/comments")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }

            it("issues 쓰기 권한이 없는 토큰은 이슈 닫기 요청을 403으로 거부해야 한다") {
                val raw = "subpath-issues-close-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/close")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 쓰기 권한이 있는 토큰은 이슈 닫기 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-issues-close-write"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/close")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }

        describe("PR 하위 경로(merge/reviewers)의 스코프 기반 인가") {
            it("pull-requests 쓰기 권한이 없는 토큰은 머지 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-merge-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/merge")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 머지 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-merge-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/merge")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }

            it("pull-requests 쓰기 권한이 없는 토큰은 리뷰어 등록 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-reviewers-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/reviewers")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 리뷰어 등록 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-reviewers-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/reviewers")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }

        // yona-wiki P3-02 4라운드(Step8.5 서버 보강) — 이슈 reopen/transfer, PR edit/close/reopen/
        // diff/comment 하위 경로도 동일한 scopedApiPattern 접미부((?:/.*)?)로 매칭되는지 확인한다.
        describe("이슈 하위 경로(reopen/transfer)의 스코프 기반 인가") {
            it("issues 쓰기 권한이 없는 토큰은 이슈 재오픈 요청을 403으로 거부해야 한다") {
                val raw = "subpath-issues-reopen-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/reopen")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 쓰기 권한이 있는 토큰은 이슈 재오픈 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-issues-reopen-write"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/reopen")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }

            it("issues 쓰기 권한이 없는 토큰은 이슈 이관 요청을 403으로 거부해야 한다") {
                val raw = "subpath-issues-transfer-readonly"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/transfer")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("issues 쓰기 권한이 있는 토큰은 이슈 이관 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-issues-transfer-write"
                tokenWith(raw, ApiTokenScopeGroup.ISSUES, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/issues/1/transfer")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }

        describe("PR 하위 경로(edit/close/reopen/diff/comments)의 스코프 기반 인가") {
            it("pull-requests 쓰기 권한이 없는 토큰은 PR 수정 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-edit-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 PR 수정 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-edit-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }

            it("pull-requests 읽기 권한만 있는 토큰도 diff 조회는 허용해야 한다") {
                val raw = "subpath-pr-diff-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/diff")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }

            it("pull-requests 그룹에 스코프가 전혀 없으면 diff 조회도 403이어야 한다") {
                val raw = "subpath-pr-diff-no-scope"
                val token = ApiToken(
                    owner = owner,
                    tokenHash = hashApiToken(raw),
                    allRepositories = true,
                    expiresAt = Instant.now().plus(30, ChronoUnit.DAYS)
                )
                apiTokenRepository.save(token)

                val result = mockMvc.perform(
                    get("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/diff")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 없는 토큰은 PR 댓글 작성 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-comments-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/comments")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 PR close/reopen 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-close-reopen-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val closeResult = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/close")
                        .header("Yona-Token", raw)
                ).andReturn()
                closeResult.response.status shouldNotBe 403

                val reopenResult = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/reopen")
                        .header("Yona-Token", raw)
                ).andReturn()
                reopenResult.response.status shouldNotBe 403
            }

            // yona-wiki P3-02 Step8.6 항목4(2026-09-01, 우선순위 4위) — PR 담당자/라벨 하위 경로도
            // 기존 scopedApiPattern에 그대로 매칭돼 PULL_REQUESTS 그룹으로 인가되는지 확인한다.
            it("pull-requests 쓰기 권한이 없는 토큰은 담당자 지정 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-assignee-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/assignee")
                        .header("Yona-Token", raw)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""{"userId":1}""")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 담당자 지정/해제 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-assignee-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val setResult = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/assignee")
                        .header("Yona-Token", raw)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""{"userId":${owner.id}}""")
                ).andReturn()
                setResult.response.status shouldNotBe 403

                val removeResult = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/assignee")
                        .header("Yona-Token", raw)
                ).andReturn()
                removeResult.response.status shouldNotBe 403
            }

            it("pull-requests 쓰기 권한이 없는 토큰은 라벨 추가 요청을 403으로 거부해야 한다") {
                val raw = "subpath-pr-labels-readonly"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.READ)

                val result = mockMvc.perform(
                    post("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/labels")
                        .header("Yona-Token", raw)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""{"labelId":1}""")
                ).andReturn()

                result.response.status shouldBe 403
            }

            it("pull-requests 쓰기 권한이 있는 토큰은 라벨 제거 요청에서 필터를 통과해야 한다") {
                val raw = "subpath-pr-labels-remove-write"
                tokenWith(raw, ApiTokenScopeGroup.PULL_REQUESTS, ApiTokenPermission.WRITE)

                val result = mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/projects/${owner.loginId}/${project.name}/pull-requests/1/labels/1")
                        .header("Yona-Token", raw)
                ).andReturn()

                result.response.status shouldNotBe 403
            }
        }
    }
}
