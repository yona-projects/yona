package com.github.yonaprojects.yona.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleType
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// yona-wiki P3-02 Step8.7 1번(최우선 실제 버그) — LabelRestApiController.list()는
// ProjectController.getProjectLabels()(domain/project/Label, 프로젝트 홈 화면 토픽 태그)를
// 반환하는데 create()/update()/delete()는 IssueLabel(카테고리 기반 이슈 라벨링, 실제 CLI/이슈
// 화면이 쓰는 라벨)을 조작한다 - `yona label create`로 만든 라벨이 `yona label list`엔 절대 안
// 뜬다. 이 스펙은 실제 REST 엔드포인트를 통해 라벨을 생성한 뒤 목록 조회로 그 라벨이 보이는지
// end-to-end로 검증한다(수정 전에는 반드시 실패해야 하는 RED 테스트).
class LabelRestApiControllerIntegrationSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()
    private val ownerName = "label-bug-owner"
    private val projName = "label-bug-repo"

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        beforeTest {
            // 다른 스펙과 테스트 컨테이너 DB를 공유하므로 이 스펙 소유 데이터만 매회 정리한다.
            projectRepository.findByOwnerAndName(ownerName, projName).ifPresent { existing ->
                issueLabelRepository.findByProject(existing).forEach { issueLabelRepository.delete(it) }
                issueLabelCategoryRepository.findByProject(existing).forEach { issueLabelCategoryRepository.delete(it) }
                projectUserRepository.findByProjectId(existing.id!!).forEach { projectUserRepository.delete(it) }
                projectRepository.delete(existing)
            }
            userRepository.findByLoginId(ownerName).ifPresent { userRepository.delete(it) }

            val owner = userRepository.save(User(loginId = ownerName, name = "라벨버그소유자", email = "$ownerName@example.com"))
            val project = projectRepository.save(Project(owner = ownerName, name = projName))
            projectUserRepository.save(ProjectUser(user = owner, project = project, role = Role(id = RoleType.MANAGER.roleType)))
        }

        describe("GET /api/v1/projects/{owner}/{project}/labels") {
            it("방금 POST로 생성한 라벨이 목록 조회 결과에 보여야 한다") {
                val ownerObj = userRepository.findByLoginId(ownerName).get()
                val ownerDetails = YonaUserDetails(
                    id = ownerObj.id!!,
                    loginId = ownerObj.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )

                val createResult = mockMvc.perform(
                    post("/api/v1/projects/$ownerName/$projName/labels")
                        .with(user(ownerDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"버그","color":"ff0000","category":"타입"}""")
                ).andExpect(status().isCreated).andReturn()

                @Suppress("UNCHECKED_CAST")
                val created = objectMapper.readValue(createResult.response.contentAsString, Map::class.java) as Map<String, Any?>
                val createdId = (created["id"] as Number).toLong()

                val listResult = mockMvc.perform(
                    get("/api/v1/projects/$ownerName/$projName/labels")
                        .with(user(ownerDetails))
                ).andExpect(status().isOk).andReturn()

                val listedLabels = objectMapper.readValue(listResult.response.contentAsString, List::class.java)
                @Suppress("UNCHECKED_CAST")
                val matched = listedLabels.map { it as Map<String, Any?> }
                    .find { (it["id"] as Number).toLong() == createdId }

                matched shouldNotBe null
                (matched!!["name"]) shouldBe "버그"
            }
        }
    }
}
