package dev.yona.web.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 일괄 변경(`POST /:user/:project/issues`)을 검증한다. 목록의 일괄 변경 막대와 이슈 보기의 오른쪽 창이 같은 주소를 쓴다.
 *
 * 원본: `app/controllers/IssueApp.java:389-560`, `app/models/IssueMassUpdate.java`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueMassUpdateTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "im$n", name = "im$n", email = "im$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "mo$n", name = "mp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private fun issue(project: Project, number: Long, author: User? = null, draft: Boolean = false, state: State = State.OPEN, assignee: User? = null, milestone: Milestone? = null, issueLabels: Set<IssueLabel> = emptySet()): Issue {
        project.lastIssueNumber = maxOf(project.lastIssueNumber ?: 0, number); projects.save(project)
        return issues.save(
            Issue(
                project = project, number = number, title = "제목 $number", body = "본문", state = if (draft) State.DRAFT else state, isDraft = draft, authorId = author?.id, authorLoginId = author?.loginId,
                authorName = author?.name, assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, labels = issueLabels.toMutableSet(),
                numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
    }

    private fun post(project: Project, session: MockHttpSession?, vararg params: Pair<String, String>, json: Boolean = true) =
        mockMvc.perform(
            post("/${project.owner}/${project.name}/issues").apply { session?.let { session(it) } }
                .header("Accept", if (json) "application/json" else "text/html").header("Referer", "http://localhost/back")
                .apply { params.forEach { (k, v) -> param(k, v) } }
        )

    private fun reload(i: Issue): Issue { entityManager.flush(); entityManager.clear(); return issues.findById(i.id!!).get() }

    @Test
    fun `상태를 한꺼번에 바꾸고 이벤트를 남긴다`() {
        val project = newProject(); val member = join(newUser(), project)
        val a = issue(project, 1); val b = issue(project, 2)
        post(project, login(member), "issues[0].id" to a.id.toString(), "issues[1].id" to b.id.toString(), "state" to "CLOSED").andExpect(status().isOk).andExpect(content().string("{}"))
        assertEquals(State.CLOSED, reload(a).state); assertEquals(State.CLOSED, reload(b).state)
        val types = entityManager.createQuery("select e.eventType from IssueEvent e", EventType::class.java).resultList
        assertEquals(listOf(EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_STATE_CHANGED), types)
    }

    @Test
    fun `담당자를 지정하고 -1이면 비운다`() {
        val project = newProject(); val member = join(newUser(), project); val target = join(newUser(), project)
        val a = issue(project, 1)
        val session = login(member)
        post(project, session, "issues[0].id" to a.id.toString(), "assignee.id" to target.id.toString()).andExpect(status().isOk)
        assertEquals(target.id, reload(a).assignee?.user?.id)
        post(project, session, "issues[0].id" to a.id.toString(), "assignee.id" to "-1").andExpect(status().isOk)
        assertNull(reload(a).assignee)
        val types = entityManager.createQuery("select e.eventType from IssueEvent e", EventType::class.java).resultList
        assertTrue(types.all { it == EventType.ISSUE_ASSIGNEE_CHANGED })
    }

    @Test
    fun `마일스톤을 지정하고 -1이면 비운다`() {
        val project = newProject(); val member = join(newUser(), project)
        val m = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val a = issue(project, 1)
        val session = login(member)
        post(project, session, "issues[0].id" to a.id.toString(), "milestone.id" to m.id.toString()).andExpect(status().isOk)
        assertEquals(m.id, reload(a).milestone?.id)
        val events = entityManager.createQuery("select e from IssueEvent e order by e.id", IssueEvent::class.java).resultList
        assertEquals(listOf("-1" to m.id.toString()), events.map { it.oldValue to it.newValue })
        post(project, session, "issues[0].id" to a.id.toString(), "milestone.id" to "-1").andExpect(status().isOk)
        assertNull(reload(a).milestone)
        // 곧바로 되돌리면 연달아 바꾼 이벤트는 서로 지워진다.
        assertEquals(0, entityManager.createQuery("select e from IssueEvent e", IssueEvent::class.java).resultList.size)
    }

    @Test
    fun `라벨을 붙이고 뗀다`() {
        val project = newProject(); val member = join(newUser(), project)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val x = labels.save(IssueLabel(project = project, category = category, name = "x", color = "#fff"))
        val y = labels.save(IssueLabel(project = project, category = category, name = "y", color = "#000"))
        val a = issue(project, 1, issueLabels = setOf(y))
        post(project, login(member), "issues[0].id" to a.id.toString(), "attachingLabelIds[]" to x.id.toString(), "detachingLabelIds" to y.id.toString()).andExpect(status().isOk)
        assertEquals(setOf(x.id), reload(a).labels.map { it.id }.toSet())
        val event = entityManager.createQuery("select e from IssueEvent e", IssueEvent::class.java).singleResult
        assertEquals(EventType.ISSUE_LABEL_CHANGED, event.eventType)
        assertEquals("c - x #fff", event.newValue); assertEquals("c - y #000", event.oldValue)
    }

    @Test
    fun `마감일을 바꾸면 마감 문구를 JSON으로 돌려준다`() {
        val project = newProject(); val member = join(newUser(), project)
        val a = issue(project, 1)
        post(project, login(member), "issues[0].id" to a.id.toString(), "dueDate" to "2001-02-03", "isDueDateChanged" to "true")
            .andExpect(status().isOk).andExpect(jsonPath("$.isOverDue").value(true)).andExpect(jsonPath("$.dueDateMsg").isString)
        assertEquals("2001-02-03 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(reload(a).dueDate!!))
        post(project, login(member), "issues[0].id" to a.id.toString(), "dueDate" to "", "isDueDateChanged" to "true").andExpect(status().isOk)
        assertNull(reload(a).dueDate)
    }

    @Test
    fun `초안은 건너뛴다`() {
        val project = newProject(); val member = join(newUser(), project)
        val d = issue(project, 1, draft = true)
        post(project, login(member), "issues[0].id" to d.id.toString(), "state" to "CLOSED").andExpect(status().isOk)
        assertEquals(State.DRAFT, reload(d).state)
    }

    @Test
    fun `삭제를 함께 보내면 지운다`() {
        val project = newProject(); val member = join(newUser(), project)
        val a = issue(project, 1, author = member)
        post(project, login(member), "issues[0].id" to a.id.toString(), "delete" to "true").andExpect(status().isOk)
        entityManager.flush(); entityManager.clear()
        assertTrue(issues.findById(a.id!!).isEmpty)
    }

    @Test
    fun `권한이 없으면 403이다`() {
        val project = newProject()
        val a = issue(project, 1)
        post(project, login(newUser()), "issues[0].id" to a.id.toString(), "state" to "CLOSED").andExpect(status().isForbidden)
        post(project, null, "issues[0].id" to a.id.toString(), "state" to "CLOSED").andExpect(status().isForbidden)
        assertEquals(State.OPEN, reload(a).state)
    }

    @Test
    fun `이슈를 보내지 않으면 400이다`() {
        val project = newProject(); val member = join(newUser(), project)
        post(project, login(member), "state" to "CLOSED").andExpect(status().isBadRequest)
    }

    @Test
    fun `JSON을 원하지 않으면 보낸 곳으로 되돌린다`() {
        val project = newProject(); val member = join(newUser(), project)
        val a = issue(project, 1)
        post(project, login(member), "issues[0].id" to a.id.toString(), "state" to "CLOSED", json = false).andExpect(status().isSeeOther).andExpect(header().string("Location", "http://localhost/back"))
    }
}
