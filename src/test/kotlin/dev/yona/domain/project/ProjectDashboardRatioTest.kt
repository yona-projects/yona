package dev.yona.domain.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** 대시보드의 담당자별 비율을 검증한다(`architecture/v16-safe-fix-candidates.md` R-68-1). */
@Transactional
class ProjectDashboardRatioTest : AbstractIntegrationTest() {

    @Autowired private lateinit var dashboard: ProjectDashboardService
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var issues: IssueRepository

    private fun newUser(): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val id = "dash${System.nanoTime()}"
        return users.save(User(loginId = id, name = id, email = "$id@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun issue(project: Project, number: Long, assignee: Assignee?, parent: Issue? = null) = issues.save(
        Issue(project = project, number = number, title = "이슈 $number", state = State.OPEN, assignee = assignee, parent = parent, createdDate = Date(), updatedDate = Date(), numOfComments = 0, weight = 0)
    )

    @Test
    fun `담당자별 비율은 하위 이슈를 센 수와 같은 집합을 분모로 해서 100을 넘지 않는다 (R-68-1)`() {
        val project = projects.save(Project(owner = "dashratio${System.nanoTime()}", name = "p", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val assignee = assignees.save(Assignee(user = newUser(), project = project))
        val parent = issue(project, 1, assignee)
        issue(project, 2, assignee, parent)
        val result = dashboard.byAssignee(project)
        val row = result.rows.single()
        // 담당자의 수는 부모와 하위 이슈를 모두 센 2이다. v1.6은 분모를 최상위 이슈 수 1로 해서 200%가 되었다
        assertEquals(2, row.count)
        assertEquals(100, row.percent)
        assertFalse(result.rows.any { it.percent > 100 })
    }

    @Test
    fun `최상위 이슈가 모두 닫히고 하위 이슈만 열려 있어도 담당자별 목록이 비지 않는다 (R-68-1)`() {
        val project = projects.save(Project(owner = "dashratio2${System.nanoTime()}", name = "p", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        val assignee = assignees.save(Assignee(user = newUser(), project = project))
        val closedParent = issues.save(Issue(project = project, number = 1, title = "닫힌 부모", state = State.CLOSED, createdDate = Date(), updatedDate = Date(), numOfComments = 0, weight = 0))
        issue(project, 2, assignee, closedParent)
        val result = dashboard.byAssignee(project)
        assertEquals(1, result.rows.size)
        assertEquals(100, result.rows.single().percent)
    }
}
