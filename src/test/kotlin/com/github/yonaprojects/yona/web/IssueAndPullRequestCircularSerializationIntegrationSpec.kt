package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.pullrequest.PullRequest
import com.github.yonaprojects.yona.domain.pullrequest.PullRequestRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-02 Step8.7 2번 — 실제 서버로 골든패스 수동검증 중 발견된 심각한 버그를 재현하는
// RED 테스트. IssueRestApiController/PullRequestApiController/SearchRestApiController가
// JPA 엔티티(Issue/PullRequest/Project)를 가공 없이 그대로 반환하는데, User.projectUsers
// (@OneToMany mappedBy="user") <-> ProjectUser.user(@ManyToOne)가 양방향 연관관계라
// Jackson이 "이슈->project->projectUsers[]->user->projectUsers[]->user->..."로 무한
// 순환 직렬화한다(실측: curl로 60KB 넘는 깨진 채로 끊긴 JSON 확인).
//
// mockk로 서비스 계층을 목킹한 기존 *RestApiControllerSpec.kt들은 순환이 실제로 발생할 실제
// 연관관계 그래프가 없어 이 버그를 놓쳤다 — 이 스펙은 AbstractIntegrationTest(실제 DB) +
// MockMvc(webAppContextSetup, 실제 Jackson HttpMessageConverter)로 실제 엔티티 그래프를
// 직렬화해 재현한다. spring.jpa.open-in-view가 기본 true라 응답 작성 시점까지 Hibernate
// 세션이 열려있어, lazy 컬렉션이 실제로 초기화되며 순환이 발생한다.
class IssueAndPullRequestCircularSerializationIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val issueRepository: IssueRepository,
    private val pullRequestRepository: PullRequestRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private val ownerName = "circular-bug-owner"
    private val memberName = "circular-bug-member"
    private val projName = "circular-bug-repo"

    // 응답이 순환 직렬화에 빠지면 실측(curl)에서 60KB를 넘겼다 — DTO로 정상 변환되면 이 정도로
    // 작은 응답(수백 바이트~수 KB)이어야 한다. 정상 응답도 여유를 두기 위해 10KB로 잡는다.
    private val maxSaneResponseLength = 10_000

    private lateinit var owner: User
    private lateinit var member: User
    private lateinit var project: Project

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            // 다른 스펙과 테스트 컨테이너 DB를 공유하므로 이 스펙 소유 데이터만 매회 정리한다.
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                pullRequestRepository.findByToProject(existing).forEach { pullRequestRepository.delete(it) }
                issueRepository.findByProject(existing).forEach { issueRepository.delete(it) }
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            userRepository.findByLoginId(memberName).ifPresent { userRepository.delete(it) }

            owner = userRepository.save(User(loginId = ownerName, name = "순환버그소유자", email = "$ownerName@example.com"))
            member = userRepository.save(User(loginId = memberName, name = "순환버그멤버", email = "$memberName@example.com"))
            project = projectRepository.save(Project(owner = ownerName, name = projName))

            // User.projectUsers <-> ProjectUser.user <-> Project.projectUsers의 양방향 순환이
            // 실제로 발생하려면 프로젝트에 멤버가 최소 1명 있어야 한다(각 방향 모두 실제 FK로
            // DB에 저장돼 있어야 재조회 시 Hibernate가 lazy 컬렉션을 채운다).
            val role = roleRepository.findById(RoleType.MEMBER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MEMBER.roleType, name = "MEMBER"))
            }
            projectUserRepository.save(ProjectUser(user = owner, project = project, role = role))
            projectUserRepository.save(ProjectUser(user = member, project = project, role = role))
        }

        fun ownerDetails() = YonaUserDetails(
            id = owner.id!!,
            loginId = owner.loginId,
            passwordVal = "hashed",
            passwordSalt = "salt",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        describe("GET /api/v1/projects/{owner}/{project}/issues/{number} — 이슈 단건 조회") {
            it("순환 직렬화 없이 작은 JSON으로 응답해야 한다") {
                val issue = issueRepository.save(
                    Issue(
                        title = "순환 버그 재현용 이슈",
                        body = "본문",
                        project = project,
                        authorId = owner.id,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 1
                    )
                )

                val result = mockMvc.perform(
                    get("/api/v1/projects/$ownerName/$projName/issues/${issue.number}")
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                // 순환 직렬화에 빠지면 이 파싱 자체가 실패하거나(끊긴 JSON) 매우 커진다.
                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "projectUsers"
                body shouldNotContain "enrolledProjects"
            }
        }

        describe("GET /api/v1/projects/{owner}/{project}/issues — 이슈 목록 조회") {
            it("순환 직렬화 없이 작은 JSON으로 응답해야 한다") {
                issueRepository.save(
                    Issue(
                        title = "순환 버그 재현용 이슈 목록",
                        body = "본문",
                        project = project,
                        authorId = owner.id,
                        authorLoginId = owner.loginId,
                        authorName = owner.name,
                        number = 2
                    )
                )

                val result = mockMvc.perform(
                    get("/api/v1/projects/$ownerName/$projName/issues")
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "projectUsers"
            }
        }

        describe("GET /api/v1/projects/{owner}/{project}/pull-requests/{number} — PR 단건 조회") {
            it("순환 직렬화 없이 작은 JSON으로 응답하고 fromProject.owner/name을 포함해야 한다") {
                val pr = pullRequestRepository.save(
                    PullRequest(
                        title = "순환 버그 재현용 PR",
                        body = "본문",
                        toProject = project,
                        fromProject = project,
                        toBranch = "main",
                        fromBranch = "feature",
                        contributor = owner,
                        number = 1
                    )
                )

                val result = mockMvc.perform(
                    get("/api/v1/projects/$ownerName/$projName/pull-requests/${pr.number}")
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "projectUsers"

                // yona-cli cmd/pr.go의 planCheckout()이 pr["fromProject"]["owner"]/["name"]을
                // 그대로 읽는다("yona pr checkout") — DTO로 바꾸더라도 이 중첩 필드는 유지해야 한다.
                val node = objectMapper.readTree(body)
                node.path("fromProject").path("owner").asText() shouldBe ownerName
                node.path("fromProject").path("name").asText() shouldBe projName
            }
        }

        describe("GET /api/v1/projects/{owner}/{project}/pull-requests — PR 목록 조회") {
            it("순환 직렬화 없이 작은 JSON으로 응답해야 한다") {
                pullRequestRepository.save(
                    PullRequest(
                        title = "순환 버그 재현용 PR 목록",
                        body = "본문",
                        toProject = project,
                        fromProject = project,
                        toBranch = "main",
                        fromBranch = "feature2",
                        contributor = owner,
                        number = 2
                    )
                )

                val result = mockMvc.perform(
                    get("/api/v1/projects/$ownerName/$projName/pull-requests")
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "projectUsers"
            }
        }

        describe("GET /api/v1/search/projects — 프로젝트 검색") {
            it("순환 직렬화 없이 작은 JSON으로 응답해야 한다") {
                val result = mockMvc.perform(
                    get("/api/v1/search/projects")
                        .param("q", projName)
                        .with(user(ownerDetails()))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                val node = objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "projectUsers"
                node.path("content").size() shouldBe 1
            }
        }
    }
}
