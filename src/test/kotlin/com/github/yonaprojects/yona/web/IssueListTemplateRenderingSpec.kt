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
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
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
    private val assigneeRepository: AssigneeRepository,
    private val entityManagerFactory: EntityManagerFactory
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

            it("renders a selected public number with its detail even when the list filter excludes it") {
                val author = userRepository.save(User(loginId = "selected-author", name = "Selected author", email = "selected-author@yona.io"))
                val project = projectRepository.save(Project(name = "selected-project", owner = "selected-owner", projectScope = ProjectScope.PUBLIC))
                issueRepository.save(
                    Issue(title = "Listed open issue", body = "Open body", project = project, number = 31L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "Selected closed issue", body = "Selected detail body outside the list filter",
                        project = project, number = 47L, authorId = author.id, authorLoginId = author.loginId,
                        authorName = author.name, state = State.CLOSED)
                )

                val body = mockMvc.perform(get("/${project.owner}/${project.name}/issues")
                    .queryParam("state", "open").queryParam("selected", "47"))
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "<!DOCTYPE html>"
                body shouldContain "id=\"issue-list\""
                body shouldContain "id=\"issue-detail\""
                body shouldContain "Listed open issue"
                body shouldContain "Selected closed issue"
                body shouldContain "Selected detail body outside the list filter"
            }

            // P3-74 — Turbo가 `Turbo-Frame: issue-detail` 헤더로 "#issue-detail 프레임만 필요하다"고
            // 명시하는데도 IssueViewController.listIssues()가 이를 무시하고 목록 전체를 다시 그려서
            // (23 SQL) 상세 조회(18 SQL)에 15 SQL을 더 낭비하던 문제(docs/TURBO_THYMELEAF_POC.md
            // "SQL" 절, PR #834가 알려진 한계로 명시). 위 테스트(헤더 없는 요청)는 그대로 전체
            // 페이지가 렌더링됨을 계속 보장하고, 이 테스트는 헤더가 있을 때 상세 fragment만
            // 돌아오는지를 검증한다.
            it("a Turbo-Frame: issue-detail request for a selected issue returns only the detail fragment, not the full list page") {
                val author = userRepository.save(User(loginId = "turbo-frame-author", name = "Turbo Frame Author", email = "turbo-frame-author@yona.io"))
                val project = projectRepository.save(Project(name = "turbo-frame-project", owner = "turbo-frame-owner", projectScope = ProjectScope.PUBLIC))
                issueRepository.save(
                    Issue(title = "Other listed issue", body = "Other body", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "Turbo selected issue", body = "Turbo selected detail body",
                        project = project, number = 2L, authorId = author.id, authorLoginId = author.loginId,
                        authorName = author.name, state = State.OPEN)
                )

                val body = mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .queryParam("selected", "2")
                        .header("Turbo-Frame", "issue-detail")
                )
                    .andExpect(status().isOk)
                    .andReturn().response.contentAsString

                body shouldContain "id=\"issue-detail\""
                body shouldContain "Turbo selected issue"
                body shouldContain "Turbo selected detail body"
                // 프레임 전용 응답은 목록/전체 문서를 함께 그리지 않는다 — 그렸다면 아래 문자열이
                // 섞여 들어온다(목록 프레임 id, 목록에만 있는 다른 이슈 제목, 전체 문서 선언).
                body shouldNotContain "<!DOCTYPE html>"
                body shouldNotContain "id=\"issue-list\""
                body shouldNotContain "Other listed issue"
            }

            it("a Turbo-Frame: issue-detail request executes no more SQL than a plain single-issue detail view") {
                val author = userRepository.save(User(loginId = "turbo-sql-author", name = "Turbo SQL Author", email = "turbo-sql-author@yona.io"))
                val project = projectRepository.save(Project(name = "turbo-sql-project", owner = "turbo-sql-owner", projectScope = ProjectScope.PUBLIC))
                issueRepository.save(
                    Issue(title = "Other listed issue", body = "Other body", project = project, number = 1L,
                        authorId = author.id, authorLoginId = author.loginId, authorName = author.name, state = State.OPEN)
                )
                issueRepository.save(
                    Issue(title = "Turbo selected issue", body = "Turbo selected detail body",
                        project = project, number = 2L, authorId = author.id, authorLoginId = author.loginId,
                        authorName = author.name, state = State.OPEN)
                )

                val statistics = entityManagerFactory.unwrap(SessionFactory::class.java).statistics
                statistics.setStatisticsEnabled(true)

                statistics.clear()
                mockMvc.perform(get("/${project.owner}/${project.name}/issue/2")).andExpect(status().isOk)
                val normalDetailSqlCount = statistics.prepareStatementCount

                statistics.clear()
                mockMvc.perform(
                    get("/${project.owner}/${project.name}/issues")
                        .queryParam("selected", "2")
                        .header("Turbo-Frame", "issue-detail")
                ).andExpect(status().isOk)
                val turboFrameSqlCount = statistics.prepareStatementCount

                // 정상 상세 조회(viewIssue() 단독)와 동일한 쿼리만 실행되어야 한다 — 목록 조회
                // (필터/페이지네이션/카운트/마일스톤/멤버/라벨 등)가 함께 실행되면 이 값을 넘어선다.
                turboFrameSqlCount shouldBeLessThanOrEqualTo normalDetailSqlCount
            }
        }
    }
}
