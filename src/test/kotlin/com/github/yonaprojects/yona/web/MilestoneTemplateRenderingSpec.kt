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
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit

// yona milestone/list.scala.html, milestone/view.scala.html, milestone/partial_status.scala.html
// (그룹9 #149~153) 대응. 단위 테스트(mockk)는 모델 데이터 조립만 검증하고 실제 Thymeleaf 렌더링은
// 거치지 않으므로, 이 스펙은 실제 ViewResolver를 태워 목록/상세 화면과 프로젝트 홈 사이드바 위젯이
// 문법 오류 없이 렌더링되는지까지 확인한다.
@Transactional
class MilestoneTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueRepository: IssueRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("마일스톤 목록/상세/사이드바 위젯 렌더링") {
            it("마일스톤 목록 화면이 정렬 필터와 이슈 진행률을 실제로 렌더링해야 한다") {
                val project = projectRepository.save(Project(name = "tmpl-milestone-proj", owner = "tmpl-milestone-owner", projectScope = ProjectScope.PUBLIC))
                val author = userRepository.save(User(loginId = "tmpl-milestone-author", name = "마일스톤작성자", email = "tmpl-milestone-author@yona.io"))
                val milestone = milestoneRepository.save(
                    Milestone(title = "템플릿 렌더링 마일스톤", project = project, state = State.OPEN, dueDate = Instant.now().plus(5, ChronoUnit.DAYS))
                )
                issueRepository.save(Issue(title = "열린 이슈", body = "본문", project = project, number = 1L, authorId = author.id, milestone = milestone, state = State.OPEN))
                issueRepository.save(Issue(title = "닫힌 이슈", body = "본문", project = project, number = 2L, authorId = author.id, milestone = milestone, state = State.CLOSED))

                val result = mockMvc.perform(get("/${project.owner}/${project.name}/milestones").param("state", "open"))
                    .andExpect(status().isOk)
                    .andReturn()

                val body = result.response.contentAsString
                body shouldContain "템플릿 렌더링 마일스톤"
                body shouldContain "열린 이슈"
                body shouldContain "닫힌 이슈"
                body shouldContain "1 / 2"
            }

            it("마일스톤 상세 화면이 진행률 바와 남은 기한을 실제로 렌더링해야 한다") {
                // BootstrapSetupInterceptor가 DB에 유저가 1명도 없으면 무조건 /bootstrap-setup으로
                // 리다이렉트하므로(초기 관리자 설정 유도), @Transactional로 매 테스트마다 롤백되는 이
                // 스펙에서는 항상 유저를 최소 1명 만들어둬야 한다.
                userRepository.save(User(loginId = "tmpl-milestone-view-user", name = "뷰테스트유저", email = "tmpl-milestone-view-user@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-milestone-view-proj", owner = "tmpl-milestone-view-owner", projectScope = ProjectScope.PUBLIC))
                val milestone = milestoneRepository.save(
                    Milestone(title = "상세화면 마일스톤", project = project, state = State.OPEN, contents = "마일스톤 설명", dueDate = Instant.now().plus(2, ChronoUnit.DAYS))
                )

                val result = mockMvc.perform(get("/${project.owner}/${project.name}/milestone/${milestone.id}"))
                    .andExpect(status().isOk)
                    .andReturn()

                result.response.contentAsString shouldContain "상세화면 마일스톤"
            }

            it("열린 마일스톤이 있을 때 프로젝트 홈 사이드바에 milestone/partial_status 진행 상황 카드가 렌더링되어야 한다") {
                userRepository.save(User(loginId = "tmpl-milestone-home-user", name = "홈테스트유저", email = "tmpl-milestone-home-user@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-milestone-home-proj", owner = "tmpl-milestone-home-owner", projectScope = ProjectScope.PUBLIC))
                milestoneRepository.save(
                    Milestone(title = "사이드바 마일스톤", project = project, state = State.OPEN, dueDate = Instant.now().plus(7, ChronoUnit.DAYS))
                )

                val result = mockMvc.perform(get("/${project.owner}/${project.name}"))
                    .andExpect(status().isOk)
                    .andReturn()

                val body = result.response.contentAsString
                body shouldContain "milestone-info"
                body shouldContain "사이드바 마일스톤"
            }
        }
    }
}
