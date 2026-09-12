package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.milestone.Milestone
import com.github.yonaprojects.yona.domain.milestone.MilestoneRepository
import com.github.yonaprojects.yona.domain.project.Project
import com.github.yonaprojects.yona.domain.project.ProjectRepository
import com.github.yonaprojects.yona.domain.project.ProjectScope
import com.github.yonaprojects.yona.domain.user.User
import com.github.yonaprojects.yona.domain.user.UserRepository
import com.github.yonaprojects.yona.domain.user.YonaUserDetails
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.jsoup.Jsoup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

// P3-62: legacy IssueApp.userIssues() cross-project "내 이슈" 화면(my_partial_search.scala.html:
// 47-54)은 milestoneId가 URL 파라미터로 넘어오면(이슈 목록 필터링 목적이 아니라) 좌측 사이드바에
// 그 마일스톤의 진행률 카드(milestone/partial_status)만 순수 표시 목적으로 보여준다. yona의
// /user/issues에는 이 배선이 전혀 없었다(milestoneId RequestParam 자체가 없었음) — 이 스펙으로
// 정정 여부를 검증한다.
class UserIssuesMilestoneProgressCardTemplateRenderingSpec @Autowired constructor(
    private val wac: WebApplicationContext,
    private val userRepository: UserRepository,
    private val projectRepository: ProjectRepository,
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

        describe("P3-62: 내 이슈(cross-project) 화면에서 milestoneId로 진입하면 진행률 카드가 보여야 한다") {
            val suffix = System.currentTimeMillis().toString()
            val author = userRepository.findByLoginId("myissue-mstone-$suffix").orElseGet {
                userRepository.save(User(loginId = "myissue-mstone-$suffix", name = "작성자", email = "myissue-mstone-$suffix@yona.io"))
            }
            val project = projectRepository.findAll().find { it.name == "myissue-mstone-proj-$suffix" && it.owner == author.loginId }
                ?: projectRepository.save(Project(name = "myissue-mstone-proj-$suffix", owner = author.loginId!!, projectScope = ProjectScope.PUBLIC, vcs = "GIT"))

            val milestone = milestoneRepository.findAll().find { it.project?.id == project.id && it.title == "내이슈 진행률 마일스톤" }
                ?: milestoneRepository.save(Milestone(title = "내이슈 진행률 마일스톤", project = project, state = State.OPEN))

            fun authOf(u: User) = user(
                YonaUserDetails(
                    id = u.id ?: 0L,
                    loginId = u.loginId,
                    passwordVal = "hashed",
                    passwordSalt = "salt",
                    authoritiesVal = AuthorityUtils.createAuthorityList("ROLE_ACTIVE")
                )
            )

            it("milestoneId 파라미터가 없으면 진행률 카드가 안 보여야 한다") {
                val html = mockMvc.perform(get("/user/issues").with(authOf(author)))
                    .andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".milestone-info").size shouldBe 0
            }

            it("실제 존재하는 milestoneId로 진입하면 milestone/partial_status 진행률 카드가 렌더링돼야 한다") {
                val html = mockMvc.perform(
                    get("/user/issues").param("milestoneId", milestone.id.toString()).with(authOf(author))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                val card = doc.select(".milestone-info")
                card.size shouldBe 1
                card.select(".title").text() shouldBe "내이슈 진행률 마일스톤"
                card.select(".progress-wrap").size shouldBe 1
            }

            it("존재하지 않는 milestoneId면 진행률 카드가 보이면 안 된다") {
                val html = mockMvc.perform(
                    get("/user/issues").param("milestoneId", "999999999").with(authOf(author))
                ).andExpect(status().isOk).andReturn().response.contentAsString
                val doc = Jsoup.parse(html)

                doc.select(".milestone-info").size shouldBe 0
            }
        }
    }
}
