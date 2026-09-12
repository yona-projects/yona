package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.board.Posting
import com.github.yonaprojects.yona.domain.board.PostingRepository
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

// P3-64: legacy board/view.scala.html(208·211·294행)·issue/view.scala.html(487·491행)이 로드하는
// jQuery elevator("맨 위로 스크롤" 버튼) 플러그인이 yona board/view.html·issue/view.html에는
// 로드되지 않고 있었다. lib/elevator/* 파일 자체는 이미 vendoring돼 있으므로(정적 리소스 존재
// 확인됨), 이 스펙은 두 화면이 CSS/JS를 로드하고 legacy와 동일한 옵션으로 $.elevator(...)를
// 호출하는지만 검증한다(마크업 추가는 필요 없음 - 플러그인이 스스로 버튼을 주입).
class ScrollToTopElevatorTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val postingRepository: PostingRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
        }

        describe("board/issue 상세 화면의 jQuery elevator(맨 위로 스크롤) 배선") {
            val author = userRepository.findByLoginId("p364-author").orElseGet {
                userRepository.save(User(loginId = "p364-author", name = "P364작성자", email = "p364-author@yona.io"))
            }
            val project = projectRepository.findAll().find { it.name == "p364-proj" && it.owner == "p364-author" }
                ?: projectRepository.save(Project(name = "p364-proj", owner = "p364-author", projectScope = ProjectScope.PUBLIC))

            it("issue/view.html에 jquery.elevator.css/js 로드와 \$.elevator({shape:'rounded', tooltips:true}) 호출이 있어야 한다") {
                val issue = issueRepository.findAll().find { it.project?.id == project.id && it.number == 1L }
                    ?: issueRepository.save(
                        Issue(title = "엘리베이터 테스트 이슈", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                    )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issue/${issue.number}"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "/javascripts/lib/elevator/jquery.elevator.css"
                body shouldContain "/javascripts/lib/elevator/jquery.elevator.js"
                body shouldContain "\$.elevator("
                body shouldContain "shape: 'rounded'"
                // legacy issue/view.scala.html(614행)은 board와 달리 glass가 아니라 tooltips 옵션을 쓴다.
                body shouldContain "tooltips: true"
            }

            it("board/view.html에 jquery.elevator.css/js 로드와 \$.elevator({shape:'rounded', glass:true}) 호출이 있어야 한다") {
                val post = postingRepository.findAll().find { it.project?.id == project.id && it.number == 1L }
                    ?: postingRepository.save(
                        Posting(title = "엘리베이터 테스트 게시글", body = "본문", project = project, number = 1L, authorId = author.id, authorLoginId = author.loginId)
                    )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/post/${post.number}"))
                    .andExpect(status().isOk).andReturn().response.contentAsString

                body shouldContain "/javascripts/lib/elevator/jquery.elevator.css"
                body shouldContain "/javascripts/lib/elevator/jquery.elevator.js"
                body shouldContain "\$.elevator("
                body shouldContain "shape: 'rounded'"
                body shouldContain "glass: true"
            }
        }
    }
}
