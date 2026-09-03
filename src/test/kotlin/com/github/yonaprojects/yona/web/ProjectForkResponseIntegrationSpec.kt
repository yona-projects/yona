package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// TASK-0421(P3-02 11라운드, 버그8) — 실서버(H2 프로파일) + 실제 yona-cli 바이너리로
// `yona project fork admin/<proj>`를 반복 검증하던 중 발견. ProjectController.forkProject()
// (`/api/{owner}/{projectName}/fork`)와 이를 그대로 위임 호출하는 ProjectRestApiController.fork()
// (`/api/v1/projects/{owner}/{project}/fork`)가 성공 시 forkedProject(JPA Project 엔티티)를
// 가공 없이 그대로 반환했다. Project.projectUsers[].user(User.projectUsers와의 양방향 연관)를
// 따라가며 Jackson이 순환 직렬화를 시도하는 과정에서 User.password/passwordSalt 해시값까지
// 응답 바이트에 그대로 노출된다(실측: curl로 90KB 응답에서 "password" 키 확인) — 단순 파싱
// 실패가 아니라 보안 문제. IssueAndPullRequestCircularSerializationIntegrationSpec.kt와 동일하게
// mockk가 아닌 실제 DB + MockMvc로 실제 연관관계 그래프를 직렬화해 재현한다.
class ProjectForkResponseIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private val ownerName = "fork-resp-owner"
    private val forkerName = "fork-resp-forker"
    private val projName = "fork-resp-repo"

    // 순환 직렬화에 빠지면 실측(curl)에서 90KB를 넘겼다 — DTO로 정상 변환되면 수백 바이트~수 KB
    // 수준이어야 한다. 정상 응답도 여유를 두기 위해 10KB로 잡는다.
    private val maxSaneResponseLength = 10_000

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            // 다른 스펙과 테스트 DB를 공유하므로 이 스펙 소유 데이터만 매회 정리한다.
            projectRepository.findByOwnerAndName(forkerName, projName).ifPresent { existing ->
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }
            userRepository.findByLoginId(forkerName).ifPresent { userRepository.delete(it) }

            // password/passwordSalt를 실제로 채워야(빈 문자열이면 leak 검증이 무의미) 순환
            // 직렬화가 실제로 이 값을 응답에 실어 나르는지 의미 있게 검증할 수 있다.
            userRepository.save(
                User(
                    loginId = ownerName,
                    name = "포크응답소유자",
                    email = "$ownerName@example.com",
                    password = "super-secret-hash",
                    passwordSalt = "super-secret-salt"
                )
            )
            val forker = userRepository.save(
                User(
                    loginId = forkerName,
                    name = "포크응답실행자",
                    email = "$forkerName@example.com",
                    password = "forker-secret-hash",
                    passwordSalt = "forker-secret-salt"
                )
            )
            val owner = userRepository.findByLoginId(ownerName).orElseThrow()
            val project = projectRepository.save(Project(owner = ownerName, name = projName, vcs = "GIT"))

            // 양방향 순환(User.projectUsers <-> ProjectUser.user <-> Project.projectUsers)이 실제로
            // 발생하려면 원본 프로젝트에도 최소 1명의 멤버가 있어야 한다.
            val role = roleRepository.findById(RoleType.MANAGER.roleType).orElseGet {
                roleRepository.save(Role(id = RoleType.MANAGER.roleType, name = "MANAGER"))
            }
            projectUserRepository.save(
                com.github.yonaprojects.yona.domain.project.ProjectUser(user = owner, project = project, role = role)
            )
        }

        fun userDetails(loginId: String, id: Long) = YonaUserDetails(
            id = id,
            loginId = loginId,
            passwordVal = "hashed",
            passwordSalt = "salt",
            authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
        )

        describe("POST /api/v1/projects/{owner}/{project}/fork") {
            it("순환 직렬화 없이 작은 JSON으로 응답하고 password/passwordSalt를 노출하지 않아야 한다") {
                val forker = userRepository.findByLoginId(forkerName).orElseThrow()

                val result = mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/fork")
                        .with(user(userDetails(forkerName, forker.id!!)))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "password"
                body shouldNotContain "passwordSalt"
                body shouldNotContain "super-secret-hash"
                body shouldNotContain "forker-secret-hash"
                body shouldNotContain "projectUsers"

                val node = objectMapper.readTree(body)
                node.path("owner").asText() shouldBe forkerName
                node.path("name").asText() shouldBe projName
            }
        }

        describe("POST /api/{owner}/{projectName}/fork (legacy)") {
            it("순환 직렬화 없이 작은 JSON으로 응답하고 password/passwordSalt를 노출하지 않아야 한다") {
                val forker = userRepository.findByLoginId(forkerName).orElseThrow()

                val result = mockMvc.perform(
                    post("/api/$ownerName/$projName/fork")
                        .with(user(userDetails(forkerName, forker.id!!)))
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "password"
                body shouldNotContain "super-secret-hash"
            }
        }

        // TASK-0424(P3-02 11라운드, 버그8과 동일 근본원인의 별도 발생 지점) — 실서버+실 yona-cli로
        // `project edit`를 실측하다가 발견. ProjectController.updateProject()
        // (PATCH .../settings가 위임하는 대상)도 성공 시 raw Project 엔티티를 그대로 반환해 동일한
        // 순환 직렬화로 password/passwordSalt가 노출됐다(실측: curl로 60KB 응답에서 "password" 값
        // 수백 회 반복 확인).
        describe("PATCH /api/v1/projects/{owner}/{project}/settings") {
            it("순환 직렬화 없이 작은 JSON으로 응답하고 password/passwordSalt를 노출하지 않아야 한다") {
                val owner = userRepository.findByLoginId(ownerName).orElseThrow()

                val result = mockMvc.perform(
                    patch("/api/v1/projects/$ownerName/$projName/settings")
                        .with(user(userDetails(ownerName, owner.id!!)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"overview":"수정된 설명","projectScope":"PUBLIC"}"""
                        )
                ).andReturn()

                result.response.status shouldBe 200
                val body = result.response.contentAsString

                objectMapper.readTree(body)
                body.length shouldBeLessThan maxSaneResponseLength
                body shouldNotContain "password"
                body shouldNotContain "passwordSalt"
                body shouldNotContain "super-secret-hash"
                body shouldNotContain "projectUsers"

                val node = objectMapper.readTree(body)
                node.path("owner").asText() shouldBe ownerName
                node.path("overview").asText() shouldBe "수정된 설명"
            }
        }
    }
}
