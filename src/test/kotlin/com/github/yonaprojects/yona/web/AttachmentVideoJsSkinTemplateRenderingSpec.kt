package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-65: legacy issue/view.scala.html(488·492행)이 로드하는 Video.js(첨부 동영상 스킨)가 yona
// issue/view.html에는 로드되지 않아, common/yobi.Attachments.js가 생성하는
// class="video-js" data-setup='{}' 마크업이 Video.js 없이는 무시되고 브라우저 기본
// <video controls> UI로만 재생되고 있었다(기능 손실은 없지만 순수 시각적 스킨 격차).
// 이 스펙은 이슈 상세 화면(board 쪽은 legacy도 videojs를 로드하지 않음)에 Video.js
// 라이브러리(css+js)가 로드되는지만 확인한다(마크업은 이미 Attachments.js가 생성).
class AttachmentVideoJsSkinTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        }

        describe("이슈 상세 화면의 Video.js(첨부 동영상 스킨) 라이브러리 배선") {
            it("issue/view.html에 lib/videojs/video-js.min.css + video.min.js가 로드돼야 한다") {
                val author = userRepository.findByLoginId("p365-author").orElseGet {
                    userRepository.save(User(loginId = "p365-author", name = "P365작성자", email = "p365-author@yona.io"))
                }
                val project = projectRepository.findAll().find { it.name == "p365-proj" && it.owner == "p365-author" }
                    ?: projectRepository.save(Project(name = "p365-proj", owner = "p365-author", projectScope = ProjectScope.PUBLIC))
                val issue = issueRepository.findAll().find { it.project?.id == project.id && it.number == 1L }
                    ?: issueRepository.save(
                        Issue(title = "Video.js 테스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                    )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "/javascripts/lib/videojs/video-js.min.css"
                body shouldContain "/javascripts/lib/videojs/video.min.js"
            }
        }
    }
}
