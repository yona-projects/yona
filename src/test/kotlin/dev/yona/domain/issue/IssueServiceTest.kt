package dev.yona.domain.issue

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.notification.MentionRepository
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
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
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository

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

    // --- 다음 상태 ---

    @Autowired private lateinit var entityManager: jakarta.persistence.EntityManager

    private fun events(issue: Issue) = entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id order by e.id", IssueEvent::class.java).setParameter("id", issue.id).resultList

    @Test
    fun `열림은 닫힘으로, 닫힘은 열림으로, 초안은 닫힘으로 바뀐다`() {
        val project = project(); val actor = user()
        val issue = service.create(project, actor, input())
        assertEquals(State.CLOSED, service.toNextState(issue, actor))
        assertEquals(State.CLOSED, issue.state)
        assertEquals(State.OPEN, service.toNextState(issue, actor))
        val draft = service.create(project, actor, input(draft = true))
        assertEquals(State.CLOSED, service.toNextState(draft, actor))
    }

    @Test
    fun `상태를 바꾸면 수정 시각과 타임라인 이벤트와 활동이 남는다`() {
        val project = project(); val actor = user()
        val issue = service.create(project, actor, input())
        val before = issue.updatedDate!!
        Thread.sleep(5)
        service.toNextState(issue, actor)
        assertTrue(issue.updatedDate!!.after(before))
        val event = events(issue).single()
        assertEquals(EventType.ISSUE_STATE_CHANGED, event.eventType); assertEquals("open", event.oldValue); assertEquals("closed", event.newValue); assertEquals(actor.loginId, event.senderLoginId)
        val activity = events.stream(IssueChangedActivity::class.java).toList().single()
        assertEquals(EventType.ISSUE_STATE_CHANGED, activity.type); assertEquals("closed", activity.newValue)
    }

    @Test
    fun `하위 이슈를 다시 열면 닫힌 부모 이슈도 다시 열린다`() {
        val project = project(); val actor = user()
        val parent = service.create(project, actor, input("부모"))
        val child = service.create(project, actor, input("자식", parent = parent.id))
        service.toNextState(parent, actor); service.toNextState(child, actor)
        assertEquals(State.CLOSED, parent.state)
        service.toNextState(child, actor)
        assertEquals(State.OPEN, child.state); assertEquals(State.OPEN, parent.state)
    }

    // --- 삭제 ---

    @Test
    fun `댓글이 없거나 작성자만 댓글을 썼으면 지울 수 있다`() {
        val project = project(); val author = user(); val other = user()
        val issue = service.create(project, author, input())
        assertTrue(service.canBeDeleted(issue))
        entityManager.persist(dev.yona.domain.comment.IssueComment(contents = "c", createdDate = java.util.Date(), authorLoginId = author.loginId, issue = issue, projectId = project.id!!)); entityManager.flush()
        assertTrue(service.canBeDeleted(issue))
        entityManager.persist(dev.yona.domain.comment.IssueComment(contents = "c", createdDate = java.util.Date(), authorLoginId = other.loginId, issue = issue, projectId = project.id!!)); entityManager.flush()
        assertEquals(false, service.canBeDeleted(issue))
    }

    @Test
    fun `이슈를 지우면 댓글, 이벤트, 머리말 개수와 하위 이슈의 부모 연결이 정리된다`() {
        val project = project(); val author = user()
        val parent = service.create(project, author, input("[버그] 부모"))
        val child = service.create(project, author, input("자식", parent = parent.id))
        entityManager.persist(dev.yona.domain.comment.IssueComment(contents = "c", createdDate = java.util.Date(), authorLoginId = author.loginId, issue = parent, projectId = project.id!!))
        service.toNextState(parent, author)
        entityManager.flush()
        val parentId = parent.id!!
        service.delete(parent, author)
        entityManager.flush(); entityManager.clear()
        assertNull(entityManager.find(Issue::class.java, parentId))
        assertEquals(0, entityManager.createQuery("select c from IssueComment c where c.issue.id = :id").setParameter("id", parentId).resultList.size)
        assertEquals(0, entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id").setParameter("id", parentId).resultList.size)
        assertNull(entityManager.find(Issue::class.java, child.id!!)!!.parent)
        assertEquals(0, titleHeads.findAll().count { it.project?.id == project.id && it.headKeyword == "버그" })
        val deleted = events.stream(IssueDeletedActivity::class.java).toList().single()
        assertEquals(parent.number, deleted.issueNumber); assertEquals("[버그] 부모", deleted.title)
    }

    @Test
    fun `초안을 지울 때는 삭제 활동을 발행하지 않는다`() {
        val project = project(); val author = user()
        val draft = service.create(project, author, input(draft = true))
        service.delete(draft, author)
        assertEquals(0, events.stream(IssueDeletedActivity::class.java).count())
    }

    // --- 수정 ---

    private fun edit(
        title: String = "제목", body: String = "본문", dueDate: String? = null, state: State? = null, draft: Boolean = false, publish: Boolean = false, notify: Boolean = true,
        parent: Long? = null, changeParent: Boolean = false, assignee: String? = null, milestoneId: Long? = null, labelIds: Set<Long> = emptySet()
    ) = IssueEdit(title, body, dueDate?.let { SimpleDateFormat("yyyy-MM-dd").parse(it) }, state, draft, publish, notify, parent, changeParent, assignee, milestoneId, labelIds)

    @Test
    fun `수정하면 폼의 값이 모두 반영되고 작성 정보는 그대로이다`() {
        val project = project(); val author = user(); val editor = user(); val assignee = user()
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        val issue = service.create(project, author, input("처음", "처음 본문", dueDate = "2030-01-01"))
        val created = issue.createdDate
        Thread.sleep(5)
        service.update(issue, editor, edit("고친 제목", "고친 본문", dueDate = "2031-02-03", state = State.CLOSED, assignee = assignee.loginId, milestoneId = milestone.id, labelIds = setOf(label.id!!)))
        assertEquals("고친 제목", issue.title); assertEquals("고친 본문", issue.body); assertEquals(State.CLOSED, issue.state)
        assertEquals("2031-02-03 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(issue.dueDate!!))
        assertEquals(assignee.id, issue.assignee?.user?.id); assertEquals(milestone.id, issue.milestone?.id); assertEquals(setOf(label.id), issue.labels.map { it.id }.toSet())
        assertEquals(created, issue.createdDate); assertTrue(issue.updatedDate!!.after(created))
        assertEquals(author.loginId, issue.authorLoginId); assertEquals(editor.id, issue.updatedByAuthorId)
    }

    @Test
    fun `마감일, 담당자, 마일스톤, 라벨을 비우면 지워진다`() {
        val project = project(); val author = user(); val assignee = user()
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val issue = service.create(project, author, input(assignee = assignee.loginId, milestoneId = milestone.id, dueDate = "2030-01-01"))
        service.update(issue, author, edit())
        assertNull(issue.dueDate); assertNull(issue.assignee); assertNull(issue.milestone); assertTrue(issue.labels.isEmpty())
    }

    @Test
    fun `상태를 보내지 않으면 현재 상태를 유지한다`() {
        val project = project(); val author = user()
        val issue = service.create(project, author, input())
        service.toNextState(issue, author)
        service.update(issue, author, edit(state = null))
        assertEquals(State.CLOSED, issue.state)
    }

    @Test
    fun `본문이 바뀌면 변경 이력이 앞에 쌓이고 같으면 그대로이다`() {
        val project = project(); val author = user(); val editor = user()
        val issue = service.create(project, author, input("t", "첫 본문"))
        service.update(issue, editor, edit("t", "둘째 본문"))
        val first = issue.history!!
        assertTrue(first.startsWith("<div class='history-made-by'>${editor.name}(${editor.loginId}) <span class='added'>"), first)
        assertTrue("<span class='diff-deleted'>" in first && "<span class='diff-added'>" in first && "</br>" in first)
        service.update(issue, editor, edit("t", "둘째 본문"))
        assertEquals(first, issue.history)
        service.update(issue, editor, edit("t", "셋째 본문"))
        assertTrue(issue.history!!.endsWith(first))
        assertTrue(issue.history!!.length > first.length)
    }

    @Test
    fun `변경 이력의 본문은 이스케이프한다`() {
        val project = project(); val author = user()
        val issue = service.create(project, author, input("t", "a"))
        service.update(issue, author, edit("t", "<script>x</script>"))
        assertTrue("&lt;script&gt;" in issue.history!!)
        assertEquals(false, "<script>" in issue.history!!)
    }

    @Test
    fun `제목 머리말 개수와 멘션이 고친 값으로 바뀐다`() {
        val project = project(); val author = user(); val mentioned = user("who1")
        val issue = service.create(project, author, input("[버그] 하나", "본문"))
        service.update(issue, author, edit("[기능] 하나", "다시 @who1"))
        assertEquals(0, titleHeads.findAll().count { it.project?.id == project.id && it.headKeyword == "버그" })
        assertEquals(1, titleHeads.findAll().count { it.project?.id == project.id && it.headKeyword == "기능" })
        assertEquals(listOf(mentioned.loginId), mentions.findAll().filter { it.resourceId == issue.id.toString() }.map { it.user?.loginId })
    }

    @Test
    fun `작성자가 알림을 끄고 고치면 이벤트를 남기지 않고 다른 사람이 고치면 남긴다`() {
        val project = project(); val author = user(); val other = user(); val assignee = user()
        val issue = service.create(project, author, input("t", "본문"))
        service.update(issue, author, edit("t", "고침1", state = State.CLOSED, assignee = assignee.loginId, notify = false))
        assertEquals(0, events(issue).size)
        service.update(issue, other, edit("t", "고침2", state = State.OPEN, assignee = null, notify = false))
        assertEquals(setOf(EventType.ISSUE_STATE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED, EventType.ISSUE_BODY_CHANGED), events(issue).map { it.eventType }.toSet())
        val state = events(issue).first { it.eventType == EventType.ISSUE_STATE_CHANGED }
        assertEquals("closed", state.oldValue); assertEquals("open", state.newValue)
        val changed = events.stream(IssueChangedActivity::class.java).toList().map { it.type }
        assertTrue(EventType.ISSUE_BODY_CHANGED in changed)
    }

    @Test
    fun `알림을 켜고 고치면 작성자의 변경도 이벤트로 남는다`() {
        val project = project(); val author = user()
        val issue = service.create(project, author, input("t", "본문"))
        service.update(issue, author, edit("t", "고침", notify = true))
        assertEquals(listOf(EventType.ISSUE_BODY_CHANGED), events(issue).map { it.eventType })
    }

    @Test
    fun `초안은 알림 이벤트를 남기지 않는다`() {
        val project = project(); val author = user(); val other = user()
        val issue = service.create(project, author, input("t", "본문", draft = true))
        service.update(issue, other, edit("t", "고침", draft = true))
        assertEquals(0, events(issue).size)
        assertEquals(State.DRAFT, issue.state)
    }

    @Test
    fun `초안을 게시하면 열린 이슈가 되고 새 번호와 시각을 받는다`() {
        val project = project(); val author = user()
        val first = service.create(project, author, input("첫째"))
        val draft = service.create(project, author, input("초안", "본문", draft = true))
        val oldCreated = draft.createdDate!!
        Thread.sleep(5)
        service.update(draft, author, edit("초안", "본문", publish = true))
        assertEquals(State.OPEN, draft.state); assertEquals(false, draft.isDraft)
        assertEquals(3L, draft.number)
        assertTrue(draft.createdDate!!.after(oldCreated))
        assertEquals("", draft.history)
        assertEquals(1, events.stream(IssueCreatedActivity::class.java).toList().count { it.issue.id == draft.id })
        assertEquals(1L, first.number)
    }

    @Test
    fun `부모를 바꾸거나 지운다`() {
        val project = project(); val author = user()
        val parent = service.create(project, author, input("부모")); val other = service.create(project, author, input("다른 부모"))
        val child = service.create(project, author, input("자식", parent = parent.id))
        service.update(child, author, edit("자식", changeParent = false))
        assertEquals(parent.id, child.parent?.id)
        service.update(child, author, edit("자식", changeParent = true, parent = other.id))
        assertEquals(other.id, child.parent?.id)
        service.update(child, author, edit("자식", changeParent = true, parent = null))
        assertNull(child.parent)
    }

    @Test
    fun `다른 프로젝트로 옮기면 새 번호와 라벨 이전, 하위 이슈 이동, 이동 이벤트가 따른다`() {
        val from = project(); val to = project(); val author = user(); val mover = user()
        projectUsers.save(dev.yona.domain.project.ProjectUser(user = mover, project = to, role = roles.findById(dev.yona.domain.enumeration.RoleType.MEMBER.id).get()))
        val category = categories.save(IssueLabelCategory(project = from, name = "종류", isExclusive = true))
        val label = labels.save(IssueLabel(project = from, category = category, name = "버그", color = "#f00"))
        val milestone = milestones.save(Milestone(project = from, title = "v1", state = State.OPEN))
        val issue = service.create(from, author, input("옮길 이슈", labelIds = setOf(label.id!!), milestoneId = milestone.id))
        val child = service.create(from, author, input("자식", parent = issue.id))
        service.create(to, author, input("먼저 있던 이슈"))
        entityManager.persist(dev.yona.domain.comment.IssueComment(contents = "c", createdDate = java.util.Date(), authorLoginId = author.loginId, issue = issue, projectId = from.id!!))
        entityManager.flush()
        service.update(issue, mover, edit("옮길 이슈", labelIds = setOf(label.id!!), milestoneId = milestone.id), target = to)
        assertEquals(to.id, issue.project?.id); assertEquals(2L, issue.number)
        assertNull(issue.milestone)
        assertEquals(1, issue.labels.size)
        val moved = issue.labels.single()
        assertEquals(to.id, moved.project?.id); assertEquals("버그", moved.name); assertEquals("종류", moved.category?.name); assertEquals(true, moved.category?.isExclusive)
        assertEquals(to.id, child.project?.id); assertEquals(3L, child.number)
        assertEquals(to.id, entityManager.createQuery("select c.projectId from IssueComment c where c.issue.id = :id", java.lang.Long::class.java).setParameter("id", issue.id).singleResult.toLong())
        val moveEvent = events(issue).single { it.eventType == EventType.ISSUE_MOVED }
        assertEquals("${from.owner}/${from.name}", moveEvent.oldValue); assertEquals("${to.owner}/${to.name}", moveEvent.newValue)
    }

    @Test
    fun `옮기는 사람이 대상 프로젝트의 구성원이 아니면 라벨을 이전하지 않는다`() {
        val from = project(); val to = project(); val author = user()
        val category = categories.save(IssueLabelCategory(project = from, name = "종류", isExclusive = false))
        val label = labels.save(IssueLabel(project = from, category = category, name = "버그", color = "#f00"))
        val issue = service.create(from, author, input(labelIds = setOf(label.id!!)))
        service.update(issue, author, edit(labelIds = setOf(label.id!!)), target = to)
        assertTrue(issue.labels.isEmpty())
    }
}
