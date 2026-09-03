package com.github.yonaprojects.yona.web

import com.github.yonaprojects.yona.AbstractIntegrationTest
import com.github.yonaprojects.yona.domain.enumeration.State
import com.github.yonaprojects.yona.domain.issue.Assignee
import com.github.yonaprojects.yona.domain.issue.AssigneeRepository
import com.github.yonaprojects.yona.domain.issue.Issue
import com.github.yonaprojects.yona.domain.issue.IssueLabel
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategory
import com.github.yonaprojects.yona.domain.issue.IssueLabelCategoryRepository
import com.github.yonaprojects.yona.domain.issue.IssueLabelRepository
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

// yona issue/partial_list.scala.html(그룹7 #117, issue/list.html에 인라인으로 확인 완료 처리됐던
// 항목) 대응. 이번에 milestone/view.html(#149~153)이 동일 파샬을 공유해야 함이 드러나 issue/list.html
// 인라인 마크업을 issue/partial_list.html 공용 조각으로 추출했는데, 이 파샬 자체를 실제 Thymeleaf로
// 렌더링 검증한 테스트가 이전에 하나도 없었다(#117은 mockk 단위테스트로만 확인됨) — 추출 리팩터링의
// 안전망 겸 실제 렌더링 검증으로 신설.
@Transactional
class IssueListTemplateRenderingSpec @Autowired constructor(
    private val webApplicationContext: WebApplicationContext,
    private val projectRepository: ProjectRepository,
    private val userRepository: UserRepository,
    private val issueRepository: IssueRepository,
    private val milestoneRepository: MilestoneRepository,
    private val issueLabelCategoryRepository: IssueLabelCategoryRepository,
    private val issueLabelRepository: IssueLabelRepository,
    private val assigneeRepository: AssigneeRepository
) : AbstractIntegrationTest() {

    private val mockMvc: MockMvc by lazy { MockMvcBuilders.webAppContextSetup(webApplicationContext).build() }

    init {
        describe("이슈 목록 화면 행 마크업 렌더링") {
            it("라벨/담당자/마일스톤/서브태스크가 있는 이슈 목록이 issue/partial_list 공용 조각으로 실제 렌더링돼야 한다") {
                val author = userRepository.save(User(loginId = "tmpl-issuelist-author", name = "이슈작성자", email = "tmpl-issuelist-author@yona.io"))
                val assigneeUser = userRepository.save(User(loginId = "tmpl-issuelist-assignee", name = "담당자", email = "tmpl-issuelist-assignee@yona.io"))
                val project = projectRepository.save(Project(name = "tmpl-issuelist-proj", owner = "tmpl-issuelist-owner", projectScope = ProjectScope.PUBLIC))
                val milestone = milestoneRepository.save(Milestone(title = "이슈목록 마일스톤", project = project, state = State.OPEN))
                val labelCategory = issueLabelCategoryRepository.save(IssueLabelCategory(name = "종류", project = project))
                val label = issueLabelRepository.save(IssueLabel(category = labelCategory, color = "#333333", name = "버그", project = project))
                val assignee = assigneeRepository.save(Assignee(user = assigneeUser, project = project))

                val parentIssue = issueRepository.save(
                    Issue(
                        title = "부모 이슈", body = "본문", project = project, number = 1L, authorId = author.id,
                        authorLoginId = author.loginId, authorName = author.name, state = State.OPEN,
                        milestone = milestone, assignee = assignee,
                        dueDate = Instant.now().plus(3, ChronoUnit.DAYS)
                    )
                )
                parentIssue.labels.add(label)
                issueRepository.save(parentIssue)

                issueRepository.save(
                    Issue(
                        title = "자식 이슈", body = "본문", project = project, number = 2L, authorId = author.id,
                        authorLoginId = author.loginId, authorName = author.name, state = State.OPEN,
                        parent = parentIssue
                    )
                )

                val result = mockMvc.perform(get("/${project.owner}/${project.name}/issues").param("state", "OPEN"))
                    .andExpect(status().isOk)
                    .andReturn()

                val body = result.response.contentAsString
                body shouldContain "post-item"
                body shouldContain "부모 이슈"
                body shouldContain "버그"
                body shouldContain "담당자"
                body shouldContain "이슈목록 마일스톤"
                body shouldContain "자식 이슈"
            }
        }
    }
}
