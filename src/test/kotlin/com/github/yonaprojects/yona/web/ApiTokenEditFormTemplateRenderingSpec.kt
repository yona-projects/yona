package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.apitoken.ApiTokenRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// 신규 화면(user/edit_tokens.html)이 실제로 Thymeleaf 렌더링까지 통과하는지 검증한다
// (PostingHistoryTemplateRenderingSpec 패턴 —
// standaloneSetup MockMvc는 실제 ViewResolver를 태우지 않아 템플릿 문법 오류를 못 잡으므로, 이
// 스펙처럼 webAppContextSetup + 실제 시큐리티로 렌더링까지 확인해야 한다).
//
// GitHub의 "Settings > Developer settings > Personal access tokens" 컨벤션대로 목록
// (edit_tokens.html)과 발급 폼(edit_tokens_new.html)을 별개 페이지로 분리했고, 발급(POST)은
// Post/Redirect/Get 패턴이라(DeployKeyController와 동일한 컨벤션) 같은 세션으로 리다이렉트를
// 따라가야 플래시 속성(issuedRawToken)이 보인다.
class ApiTokenEditFormTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val apiTokenRepository: ApiTokenRepository
) : AbstractIntegrationTest() {

    private lateinit var mockMvc: MockMvc
    private lateinit var owner: User
    private lateinit var project: Project

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

            owner = userRepository.save(
                User(loginId = "tokenform-owner", name = "토큰폼소유자", email = "tokenform-owner@example.com")
            )
            project = projectRepository.save(
                Project(owner = owner.loginId, name = "tokenform-repo", projectScope = ProjectScope.PUBLIC)
            )
        }

        afterSpec {
            apiTokenRepository.deleteAll()
            projectRepository.delete(project)
            userRepository.delete(owner)
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

        describe("GET /user/editform/tokens") {
            it("로그인 사용자에게 200과 목록/탭메뉴를 렌더링해야 하고, 발급 폼 필드는 없어야 한다") {
                val body = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "발급된 토큰"
                body shouldContain "새 토큰 발급"
                body shouldContain "/user/editform/tokens/new"
                body shouldNotContain "frmApiTokenIssue"
            }
        }

        describe("GET /user/editform/tokens/new") {
            it("로그인 사용자에게 200과 발급 폼을 렌더링해야 한다") {
                val body = mockMvc.perform(get("/user/editform/tokens/new").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "frmApiTokenIssue"
                body shouldContain "새 토큰 발급"
                body shouldContain "/user/editform/tokens"
            }
        }

        describe("POST /user/editform/tokens -> GET /user/editform/tokens") {
            it("토큰을 발급하면 목록 화면으로 리다이렉트되고, 그 화면에 원문 값이 노출되며 목록에도 새 토큰이 나타나야 한다") {
                val session = MockHttpSession()

                mockMvc.perform(
                    post("/user/editform/tokens").with(authOf(owner)).with(csrf()).session(session)
                        .param("name", "렌더링테스트토큰")
                        .param("allRepositories", "true")
                        .param("expiresInDays", "30")
                        .param("scope_ISSUES", "WRITE")
                ).andExpect(status().is3xxRedirection)
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/user/editform/tokens"))

                val listBody = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)).session(session))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldContain "토큰이 발급되었습니다"
                listBody shouldContain "렌더링테스트토큰"
                listBody shouldContain "issues:write"
            }

            it("발급이 거부되면(만료일 범위 초과) 목록이 아니라 발급 폼으로 되돌아가 오류와 입력값을 보여줘야 한다") {
                val body = mockMvc.perform(
                    post("/user/editform/tokens").with(authOf(owner)).with(csrf())
                        .param("name", "잘못된만료일토큰")
                        .param("allRepositories", "true")
                        .param("expiresInDays", "9999")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "frmApiTokenIssue"
                body shouldContain "alert-error"
                body shouldContain "잘못된만료일토큰"
            }

            it("폐기하면 목록에서 사라져야 한다") {
                mockMvc.perform(
                    post("/user/editform/tokens").with(authOf(owner)).with(csrf())
                        .param("name", "폐기될토큰")
                        .param("allRepositories", "true")
                        .param("expiresInDays", "30")
                ).andExpect(status().is3xxRedirection)

                val issued = apiTokenRepository.findByOwner(owner).first { it.name == "폐기될토큰" }

                mockMvc.perform(post("/user/editform/tokens/${issued.id}/revoke").with(authOf(owner)).with(csrf()))
                    .andExpect(status().is3xxRedirection)

                val listBody = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldNotContain "폐기될토큰"
            }
        }
    }
}
