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

// yona-wiki P3-02 Step6.6 — "토큰 발급/관리 웹 UI 설계" 대응 신규 화면(user/edit_tokens.html)이
// 실제로 Thymeleaf 렌더링까지 통과하는지 검증한다(PostingHistoryTemplateRenderingSpec 패턴 —
// standaloneSetup MockMvc는 실제 ViewResolver를 태우지 않아 템플릿 문법 오류를 못 잡으므로, 이
// 스펙처럼 webAppContextSetup + 실제 시큐리티로 렌더링까지 확인해야 한다).
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
            it("로그인 사용자에게 200과 발급 폼/탭메뉴를 렌더링해야 한다") {
                val body = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "발급된 토큰"
                body shouldContain "새 토큰 발급"
                body shouldContain "/user/editform/tokens"
            }
        }

        describe("POST /user/editform/tokens -> GET /user/editform/tokens") {
            it("토큰을 발급하면 발급 직후 화면에 원문 값이 노출되고, 목록에도 새 토큰이 나타나야 한다") {
                val issueResponse = mockMvc.perform(
                    post("/user/editform/tokens").with(authOf(owner))
                        .param("name", "렌더링테스트토큰")
                        .param("allRepositories", "true")
                        .param("expiresInDays", "30")
                        .param("scope_ISSUES", "WRITE")
                ).andExpect(status().isOk).andReturn().response.contentAsString

                issueResponse shouldContain "토큰이 발급되었습니다"
                issueResponse shouldContain "렌더링테스트토큰"

                val listBody = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldContain "렌더링테스트토큰"
                listBody shouldContain "issues:write"
            }

            it("폐기하면 목록에서 사라져야 한다") {
                mockMvc.perform(
                    post("/user/editform/tokens").with(authOf(owner))
                        .param("name", "폐기될토큰")
                        .param("allRepositories", "true")
                        .param("expiresInDays", "30")
                ).andExpect(status().isOk)

                val issued = apiTokenRepository.findByOwner(owner).first { it.name == "폐기될토큰" }

                mockMvc.perform(post("/user/editform/tokens/${issued.id}/revoke").with(authOf(owner)))
                    .andExpect(status().is3xxRedirection)

                val listBody = mockMvc.perform(get("/user/editform/tokens").with(authOf(owner)))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                listBody shouldNotContain "폐기될토큰"
            }
        }
    }
}
