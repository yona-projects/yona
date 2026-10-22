package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.MentionRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.TitleHeadRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 만들기(`IssueService.create`)를 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:562-660`(`newIssue`), `app/models/AbstractPosting.java`(`save`),
 * `app/models/Assignee.java`(`add`).
 */
@Transactional
@RecordApplicationEvents
class IssueServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: IssueService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var titleHeads: TitleHeadRepository
    @Autowired private lateinit var mentions: MentionRepository
    @Autowired private lateinit var events: ApplicationEvents

    private var sequence = 0

    private fun user(name: String = "u${++sequence}"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = name, name = "이름$name", email = "$name@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project = projects.save(Project(owner = "so", name = "sp${++sequence}", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 0))

    private fun input(title: String = "제목", body: String? = "본문", draft: Boolean = false, assignee: String? = null, milestoneId: Long? = null, labelIds: Set<Long> = emptySet(),
                      parent: Long? = null, dueDate: String? = null) =
        NewIssue(title, body, dueDate?.let { SimpleDateFormat("yyyy-MM-dd").parse(it) }, draft, parent, assignee, milestoneId, labelIds)

    @Test
    fun `이슈를 만들면 번호가 차례로 매겨지고 작성자와 시각이 정해진다`() {
        val project = project(); val author = user()
        val first = service.create(project, author, input())
        val second = service.create(project, author, input("둘째"))
        assertEquals(1L, first.number); assertEquals(2L, second.number)
        assertEquals(2L, projects.findById(project.id!!).get().lastIssueNumber)
        assertEquals(State.OPEN, first.state)
        assertEquals(author.id, first.authorId); assertEquals(author.loginId, first.authorLoginId); assertEquals(author.name, first.authorName)
        assertNotNull(first.createdDate); assertEquals(first.createdDate, first.updatedDate)
        assertEquals(0, first.numOfComments)
        assertEquals(false, first.isDraft)
        assertEquals(0, first.weight)
    }

    @Test
    fun `초안은 초안 상태로 만들고 알림을 보내지 않는다`() {
        val project = project(); val author = user()
        val draft = service.create(project, author, input(draft = true))
        assertEquals(State.DRAFT, draft.state); assertEquals(true, draft.isDraft)
        assertEquals(0, events.stream(IssueCreatedActivity::class.java).count())
        service.create(project, author, input())
        assertEquals(1, events.stream(IssueCreatedActivity::class.java).count())
    }

    @Test
    fun `담당자는 프로젝트의 담당자 행을 찾아 쓰거나 만들고 없는 아이디는 담당자 없음이다`() {
        val project = project(); val author = user(); val assignee = user()
        val a = service.create(project, author, input(assignee = assignee.loginId))
        val b = service.create(project, author, input(assignee = assignee.loginId))
        assertEquals(assignee.id, a.assignee?.user?.id)
        assertEquals(a.assignee?.id, b.assignee?.id)
        assertEquals(1, assignees.findAll().count { it.project?.id == project.id })
        assertNull(service.create(project, author, input(assignee = "nobody")).assignee)
        assertNull(service.create(project, author, input(assignee = "")).assignee)
        assertNull(service.create(project, author, input(assignee = null)).assignee)
    }

    @Test
    fun `마일스톤과 마감일은 그날의 마지막 초로 정해지고 -1은 마일스톤 없음이다`() {
        val project = project(); val author = user()
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val issue = service.create(project, author, input(milestoneId = milestone.id, dueDate = "2030-05-06"))
        assertEquals(milestone.id, issue.milestone?.id)
        assertEquals("2030-05-06 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(issue.dueDate!!))
        assertNull(service.create(project, author, input(milestoneId = -1L)).milestone)
        assertNull(service.create(project, author, input()).dueDate)
    }

    @Test
    fun `라벨은 이 프로젝트의 라벨만 붙인다`() {
        val project = project(); val other = project(); val author = user()
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val mine = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        val otherCategory = categories.save(IssueLabelCategory(project = other, name = "c", isExclusive = false))
        val foreign = labels.save(IssueLabel(project = other, category = otherCategory, name = "b", color = "#fff"))
        val issue = service.create(project, author, input(labelIds = setOf(mine.id!!, foreign.id!!, 999_999L)))
        assertEquals(setOf(mine.id), issue.labels.map { it.id }.toSet())
    }

    @Test
    fun `부모 이슈를 지정하면 하위 이슈가 된다`() {
        val project = project(); val author = user()
        val parent = service.create(project, author, input())
        val child = service.create(project, author, input("자식", parent = parent.id))
        assertEquals(parent.id, child.parent?.id)
    }

    @Test
    fun `제목 머리말과 멘션을 기록한다`() {
        val project = project(); val author = user(); val mentioned = user("mentioned1")
        val issue = service.create(project, author, input("[버그] 제목", "확인 부탁 @mentioned1"))
        assertEquals(1, titleHeads.findAll().count { it.project?.id == project.id && it.headKeyword == "버그" })
        assertEquals(listOf(mentioned.loginId), mentions.findAll().filter { it.resourceId == issue.id.toString() && it.resourceType == ResourceType.ISSUE_POST }.map { it.user?.loginId })
    }

    @Test
    fun `본문이 없는 이슈도 만들 수 있다`() {
        val project = project(); val author = user()
        assertNull(service.create(project, author, input(body = null)).body)
    }

    @Test
    fun `만들면 이슈 생성 활동이 발행된다`() {
        val project = project(); val author = user()
        val issue = service.create(project, author, input())
        val published = events.stream(IssueCreatedActivity::class.java).toList().single()
        assertEquals(issue.id, published.issue.id); assertEquals(author.id, published.actor.id)
        assertTrue(EventType.NEW_ISSUE.name.isNotEmpty())
    }
}
