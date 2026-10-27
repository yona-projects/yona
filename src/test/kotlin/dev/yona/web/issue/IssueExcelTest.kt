package dev.yona.web.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jxl.Workbook
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이슈 목록의 엑셀 내려받기(`format=xls`)를 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:174-184`(`issuesAsExcel`), `app/models/Issue.java:290-400`(`excelFrom`).
 */
@AutoConfigureMockMvc
@Transactional
class IssueExcelTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var entityManager: EntityManager

    private fun fixture(): Triple<Project, Issue, User> {
        val salt = LegacyPasswordHasher.generateSalt()
        val user = users.save(User(loginId = "ex1", name = "담당자", email = "ex1@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        val project = projects.save(Project(owner = "exo", name = "exp", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "종류", isExclusive = false))
        val a = labels.save(IssueLabel(project = project, category = category, name = "나", color = "#fff"))
        val b = labels.save(IssueLabel(project = project, category = category, name = "가", color = "#fff"))
        val issue = issues.save(
            Issue(
                project = project, number = 1, title = "엑셀 이슈", body = "본문", state = State.OPEN, assignee = assignees.save(Assignee(user = user, project = project)),
                milestone = milestone, labels = mutableSetOf(a, b), dueDate = SimpleDateFormat("yyyy-MM-dd").parse("2030-05-06"),
                createdDate = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-01 09:30"), updatedDate = Date()
            )
        )
        entityManager.persist(IssueComment(contents = "첫 댓글", authorName = "갑", createdDate = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-02 10:00"), issue = issue, projectId = project.id!!))
        entityManager.persist(IssueComment(contents = "둘째 댓글", authorName = "을", createdDate = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-10-03 11:00"), issue = issue, projectId = project.id!!))
        entityManager.flush()
        return Triple(project, issue, user)
    }

    private fun workbook(project: Project): jxl.Sheet {
        val result = mockMvc.perform(get("/${project.owner}/${project.name}/issues?format=xls"))
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Type", "application/vnd.ms-excel"))
            .andReturn()
        val disposition = result.response.getHeader("Content-Disposition")!!
        assertTrue(Regex("attachment; filename\\*=UTF-8''exp_issues_\\d{12}\\.xls").matches(disposition), disposition)
        return Workbook.getWorkbook(ByteArrayInputStream(result.response.contentAsByteArray)).getSheet(0)
    }

    @Test
    fun `엑셀 파일로 내려받고 머리글은 열세 칸이다`() {
        val (project, _, _) = fixture()
        val sheet = workbook(project)
        assertEquals(
            listOf("No", "Status", "Title", "Assignee", "Issue Content", "Issue Label", "Created date", "Due date", "Milestone", "URL", "Comment", "Comment Author", "common.comment.created"),
            (0 until 13).map { sheet.getCell(it, 0).contents }
        )
    }

    @Test
    fun `이슈 한 줄과 댓글은 아래로 이어진다`() {
        val (project, _, _) = fixture()
        val sheet = workbook(project)
        val row = (0 until 10).map { sheet.getCell(it, 1).contents }
        // 라벨은 이름순이 아니라 번호순이다(v1.6 getIssueLabels는 issue.getLabels()를 그대로 돈다)
        assertEquals(listOf("1", "OPEN", "엑셀 이슈", "담당자", "본문", "나, 가"), row.take(6))
        assertEquals("2026-10-01 09:30", row[6])
        assertEquals(listOf("2030-05-06", "v1", "/exo/exp/issue/1"), row.drop(7))
        assertEquals(listOf("첫 댓글", "갑"), listOf(sheet.getCell(10, 1).contents, sheet.getCell(11, 1).contents))
        assertEquals("2026-10-02 10:00", sheet.getCell(12, 1).contents)
        assertEquals(listOf("둘째 댓글", "을"), listOf(sheet.getCell(10, 2).contents, sheet.getCell(11, 2).contents))
        assertEquals("2026-10-03 11:00", sheet.getCell(12, 2).contents)
        assertEquals(3, sheet.rows)
    }

    @Test
    fun `조건에 맞는 이슈만 담고 쪽 나누기는 하지 않는다`() {
        val (project, _, user) = fixture()
        (2L..20L).forEach { issues.save(Issue(project = project, number = it, title = "이슈 $it", state = State.OPEN, createdDate = Date(), updatedDate = Date())) }
        issues.save(Issue(project = project, number = 99, title = "닫힘", state = State.CLOSED, createdDate = Date(), updatedDate = Date(), authorId = user.id))
        entityManager.flush()
        val sheet = workbook(project)
        // 머리글 1줄 + 첫 이슈(댓글 둘이라 2줄) + 나머지 열린 이슈 19줄. 닫힌 이슈는 빠지고 15개를 넘어도 모두 담는다.
        assertEquals(22, sheet.rows)
    }
}
