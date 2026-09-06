package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.deploykey.DeployKeyRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.project.ProjectUser
import com.github.yonaprojects.yona.domain.project.ProjectUserRepository
import com.github.yonaprojects.yona.domain.role.Role
import com.github.yonaprojects.yona.domain.role.RoleRepository
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

private const val DEPLOY_KEY_1 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHFAhHu7wL23JgqJtpP8u/JUqCaLm1vcYoohMQFAdpXS deployform-test@example.com"
private const val DEPLOY_KEY_2 =
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAILRyWi6jud2ngJsCWbqDTigEMGZ6zxc+j8wSz5iQL6Bx deployform-test2@example.com"

// yona-wiki P3-03 Step1/Step2 — 새 화면(project/setting_deploykeys.html)이 실제로 Thymeleaf
// 렌더링까지 통과하는지 검증.
class DeployKeyEditFormTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val projectUserRepository: ProjectUserRepository,
    private val roleRepository: RoleRepository,
    private val deployKeyRepository: DeployKeyRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc
    private lateinit var manager: User
    private lateinit var project: Project

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

            manager = userRepository.save(
                User(loginId = "deploykeyform-owner", name = "DK폼소유자", email = "deploykeyform-owner@example.com")
            )
            project = projectRepository.save(
                Project(owner = manager.loginId, name = "deploykeyform-repo", projectScope = ProjectScope.PUBLIC)
            )
            val role = roleRepository.findById(1L).orElseGet { roleRepository.save(Role(id = 1L, name = "manager", active = true)) }
            projectUserRepository.save(ProjectUser(user = manager, project = project, role = role))
        }

        afterSpec {
            deployKeyRepository.deleteAll()
            projectUserRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(manager)
        }

        fun authOf(u: User) = user(
            YonaUserDetails(
                id = u.id ?: 0L,
                loginId = u.loginId,
                passwordVal = "hashed",
                passwordSalt = "salt",
                authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
            )
        )

        describe("GET /projects/{owner}/{name}/deploy-keys") {
            it("매니저에게 200과 등록 폼/탭메뉴를 렌더링해야 한다") {
                val body = mockMvc.perform(get("/projects/${project.owner}/${project.name}/deploy-keys").with(authOf(manager)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "새 Deploy Key 추가"
                body shouldContain "/projects/${project.owner}/${project.name}/deploy-keys"
            }
        }

        // yona-wiki P3-03 Step2 — 등록(POST)은 Post/Redirect/Get 패턴이라(DeployKeyController 주석
        // 참고) 같은 세션으로 리다이렉트를 따라가야 플래시 속성(issuedHttpsToken)이 보인다.
        describe("POST /projects/{owner}/{name}/deploy-keys -> GET") {
            it("Deploy Key를 등록하면 리다이렉트 후 화면에 HTTPS 토큰이 노출되고 목록에도 나타나야 한다") {
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/projects/${project.owner}/${project.name}/deploy-keys").with(authOf(manager)).session(session)
                        .param("title", "렌더링테스트DK")
                        .param("publicKey", DEPLOY_KEY_1)
                        .param("readOnly", "true")
                ).andExpect(status().is3xxRedirection)

                val listBody = mockMvc.perform(get("/projects/${project.owner}/${project.name}/deploy-keys").with(authOf(manager)).session(session))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldContain "yona_dk_"
                listBody shouldContain "렌더링테스트DK"
            }

            it("삭제하면 목록에서 사라져야 한다") {
                mockMvc.perform(
                    post("/projects/${project.owner}/${project.name}/deploy-keys").with(authOf(manager))
                        .param("title", "삭제될DK")
                        .param("publicKey", DEPLOY_KEY_2)
                        .param("readOnly", "true")
                ).andExpect(status().is3xxRedirection)

                val issued = deployKeyRepository.findByProjectId(project.id!!).first { it.title == "삭제될DK" }

                mockMvc.perform(post("/projects/${project.owner}/${project.name}/deploy-keys/${issued.id}/delete").with(authOf(manager)))
                    .andExpect(status().is3xxRedirection)

                val listBody = mockMvc.perform(get("/projects/${project.owner}/${project.name}/deploy-keys").with(authOf(manager)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldNotContain "삭제될DK"
            }
        }
    }
}
