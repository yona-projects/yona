package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueRepository
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
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

// P3-61: 프로젝트 범위 이슈 목록(issue/list.html)에서 마일스톤으로 필터링해도 진행률 카드
// (milestone/partial_status, 이미 project/home.html 사이드바에서 쓰이는 fragment)가 보이지
// 않았다. TASK-0253이 "cross-project 전용이라 범위 밖"이라 잘못 내린 제외 판단을 정정하고
// 실제로 배선했는지 검증한다.
class IssueListMilestoneProgressCardTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val milestoneRepository: MilestoneRepository
) : AbstractIntegrationTest() {

    override fun extensions() = listOf(SpringExtension)

    private lateinit var mockMvc: MockMvc

    init {
        beforeSpec {
            mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()
        }

        describe("P3-61: 이슈 목록에서 마일스톤 필터 시 진행률 카드가 보여야 한다") {
            val author = userRepository.findByLoginId("mstoneprog-author").orElseGet {
                userRepository.save(User(loginId = "mstoneprog-author", name = "작성자", email = "mstoneprog-author@yona.io"))
            }
            val project = projectRepository.findAll().find { it.name == "mstoneprog-proj" && it.owner == "mstoneprog-author" }
                ?: projectRepository.save(Project(name = "mstoneprog-proj", owner = "mstoneprog-author", projectScope = ProjectScope.PUBLIC, vcs = "GIT"))

            val milestone = milestoneRepository.findAll().find { it.project?.id == project.id && it.title == "진행률 마일스톤" }
                ?: milestoneRepository.save(Milestone(title = "진행률 마일스톤", project = project, state = State.OPEN))

            issueRepository.findAll().find { it.project.id == project.id && it.milestone?.id == milestone.id }
                ?: issueRepository.save(
                    Issue(
                        title = "마일스톤 이슈", body = "본문", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, state = State.OPEN, milestone = milestone
                    )
                )

            it("milestoneId 파라미터가 없으면 진행률 카드가 안 보여야 한다") {
                val html = mockMvc.perform(get("/${project.owner}/${project.name}/issues").param("state", "OPEN"))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".milestone-info").size shouldBe 0
            }

            it("milestoneId로 필터링하면 milestone/partial_status 진행률 카드가 렌더링돼야 한다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .param("state", "OPEN")
                        .param("milestoneId", milestone.id.toString())
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val card = doc.select(".milestone-info")
                card.size shouldBe 1
                card.select(".title").text() shouldBe "진행률 마일스톤"
                card.select(".progress-wrap").size shouldBe 1
            }

            it("milestoneId=-1(마일스톤 없음)이면 진행률 카드가 보이면 안 된다") {
                val html = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .param("state", "OPEN")
                        .param("milestoneId", "-1")
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".milestone-info").size shouldBe 0
            }
        }
    }
}
