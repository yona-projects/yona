package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-59: board/list.html에 "2단 모드" 체크박스 마크업이 없어 yona.twoColumnMode.js가 로드는
// 되지만 조작할 대상(#two-column-mode-checkbox / #two-column-mode)이 없는 죽은 include였다.
// 이미 정상 이식된 issue/list.html 등 6개 화면과 동일한 마크업이 board/list.html에도 렌더링되고,
// yona.twoColumnMode.js 스크립트도 여전히 로드되는지 검증한다.
class BoardListTwoColumnModeTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-59: board/list.html 2단 모드 체크박스 마크업 회귀 검증") {
            userRepository.findByLoginId("boardlist2col-seed").orElseGet {
                userRepository.save(User(loginId = "boardlist2col-seed", name = "시드유저", email = "boardlist2col-seed@yona.io"))
            }
            val project = projectRepository.findAll().find { it.name == "boardlist2col-proj" && it.owner == "boardlist2col-seed" }
                ?: projectRepository.save(Project(name = "boardlist2col-proj", owner = "boardlist2col-seed", projectScope = ProjectScope.PUBLIC, vcs = "GIT"))

            it("검색 폼 영역에 #two-column-mode-checkbox / #two-column-mode 체크박스 마크업이 렌더링돼야 한다") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/posts"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val optionForm = doc.select("#option_form")
                optionForm.size shouldBe 1

                val checkboxArea = optionForm.select("#two-column-mode-checkbox")
                checkboxArea.size shouldBe 1
                checkboxArea.select("input#two-column-mode[type=checkbox]").size shouldBe 1
            }

            it("yona.twoColumnMode.js 스크립트도 여전히 로드돼야 한다(마크업 추가로 include가 죽지 않았는지 확인)") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/posts"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                html.contains("/javascripts/service/yona.twoColumnMode.js") shouldBe true
            }
        }
    }
}
