package dev.yona.domain.notification

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Unwatch
import dev.yona.domain.misc.UnwatchRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 알림 이벤트를 저장하는 규칙을 검증한다.
 *
 * 원본: `app/models/NotificationEvent.java:400-512`(`add`, `addWithoutSkipEvent`, `filterReceivers`, `deleteBy`).
 */
@Transactional
class NotificationEventsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var events: NotificationEvents
    @Autowired private lateinit var eventRepository: NotificationEventRepository
    @Autowired private lateinit var mailRepository: NotificationMailRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var unwatches: UnwatchRepository
    @Autowired private lateinit var projectNotifications: UserProjectNotificationRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ne$n", name = "ne$n", email = "ne$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(scope: ProjectScope = ProjectScope.PUBLIC): Project = projects.save(Project(owner = "neo${++sequence}", name = "nep$sequence", vcs = "GIT", projectScope = scope, lastIssueNumber = 1))

    private fun issue(project: Project, author: User): Issue = issues.save(
        Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date())
    )

    private fun note(sender: User, issue: Issue, receivers: Collection<User>, old: String?, new: String?, type: EventType = EventType.ISSUE_STATE_CHANGED) =
        NewNotification(sender, ResourceType.ISSUE_POST, issue.id.toString(), type, "[p] t (#1)", old, new, receivers)

    @Test
    fun `이벤트와 수신자와 메일 대기열을 저장한다`() {
        val p = project(); val sender = user(); val receiver = user(); val i = issue(p, sender)
        val saved = events.add(note(sender, i, listOf(receiver), "open", "closed"))
        assertNotNull(saved)
        entityManager.flush(); entityManager.clear()
        val loaded = eventRepository.findById(saved.id!!).get()
        assertEquals(setOf(receiver.id), loaded.receivers.map { it.id }.toSet()); assertEquals("[p] t (#1)", loaded.title); assertEquals(sender.id, loaded.senderId)
        assertEquals(1, mailRepository.findAll().count { it.notificationEvent?.id == saved.id })
    }

    @Test
    fun `읽을 수 없는 사용자는 수신자에서 뺀다`() {
        val secret = project(ProjectScope.PRIVATE); val sender = user(); val member = user(); val outsider = user(); val i = issue(secret, sender)
        projectUsers.save(ProjectUser(user = member, project = secret, role = roles.findById(RoleType.MEMBER.id).get()))
        val saved = events.add(note(sender, i, listOf(member, outsider), "open", "closed"))!!
        assertEquals(setOf(member.id), saved.receivers.map { it.id }.toSet())
    }

    @Test
    fun `프로젝트를 구독 중인 사용자는 이벤트별 설정을 따르고 구독하지 않으면 그대로 받는다`() {
        val p = project(); val sender = user(); val i = issue(p, sender)
        val default = user(); val optedOut = user(); val optedIn = user(); val notWatching = user()
        listOf(default, optedOut, optedIn).forEach { watches.save(Watch(user = it, resourceType = ResourceType.PROJECT, resourceId = p.id.toString())) }
        projectNotifications.save(UserProjectNotification(user = optedOut, project = p, notificationType = EventType.ISSUE_STATE_CHANGED, allowed = false))
        projectNotifications.save(UserProjectNotification(user = optedIn, project = p, notificationType = EventType.ISSUE_STATE_CHANGED, allowed = true))
        val saved = events.add(note(sender, i, listOf(default, optedOut, optedIn, notWatching), "open", "closed"))!!
        assertEquals(setOf(default.id, optedIn.id, notWatching.id), saved.receivers.map { it.id }.toSet())
    }

    @Test
    fun `프로젝트 구독을 해제한 사용자는 구독하지 않은 것으로 본다`() {
        val p = project(); val sender = user(); val i = issue(p, sender); val muted = user()
        watches.save(Watch(user = muted, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        unwatches.save(Unwatch(user = muted, resourceType = ResourceType.PROJECT, resourceId = p.id.toString()))
        projectNotifications.save(UserProjectNotification(user = muted, project = p, notificationType = EventType.ISSUE_STATE_CHANGED, allowed = false))
        assertEquals(setOf(muted.id), events.add(note(sender, i, listOf(muted), "open", "closed"))!!.receivers.map { it.id }.toSet())
    }

    @Test
    fun `수신자가 없으면 저장하지 않는다`() {
        val p = project(); val sender = user(); val i = issue(p, sender)
        assertNull(events.add(note(sender, i, emptyList(), "open", "closed")))
        assertEquals(0, eventRepository.count())
    }

    @Test
    fun `같은 사용자의 연이은 같은 종류 이벤트는 처음 값에서 마지막 값으로 합친다`() {
        val p = project(); val sender = user(); val receiver = user(); val i = issue(p, sender)
        events.add(note(sender, i, listOf(receiver), "open", "closed"))
        val merged = events.add(note(sender, i, listOf(receiver), "closed", "draft"))!!
        assertEquals("open", merged.oldValue); assertEquals("draft", merged.newValue)
        assertEquals(1, eventRepository.count()); assertEquals(1, mailRepository.count())
    }

    @Test
    fun `되돌리면 둘 다 지운다`() {
        val p = project(); val sender = user(); val receiver = user(); val i = issue(p, sender)
        events.add(note(sender, i, listOf(receiver), "open", "closed"))
        assertNull(events.add(note(sender, i, listOf(receiver), "closed", "open")))
        assertEquals(0, eventRepository.count()); assertEquals(0, mailRepository.count())
    }

    @Test
    fun `보낸 사람이나 종류가 다르면 합치지 않는다`() {
        val p = project(); val sender = user(); val other = user(); val receiver = user(); val i = issue(p, sender)
        events.add(note(sender, i, listOf(receiver), "open", "closed"))
        events.add(note(other, i, listOf(receiver), "closed", "open"))
        events.add(note(sender, i, listOf(receiver), "a", "b", EventType.ISSUE_ASSIGNEE_CHANGED))
        assertEquals(3, eventRepository.count())
    }

    @Test
    fun `합치지 않는 방식은 값을 되돌릴 때만 지운다`() {
        val p = project(); val sender = user(); val receiver = user(); val i = issue(p, sender)
        val type = EventType.ISSUE_LABEL_CHANGED
        events.addWithoutSkip(note(sender, i, listOf(receiver), "", "a", type))
        events.addWithoutSkip(note(sender, i, listOf(receiver), "", "b", type))
        assertEquals(2, eventRepository.count())
        assertNull(events.addWithoutSkip(note(sender, i, listOf(receiver), "b", "", type)))
        assertEquals(1, eventRepository.count())
    }

    @Test
    fun `합치지 않는 방식은 공유자를 추가할 때만 수신자를 거른다`() {
        val secret = project(ProjectScope.PRIVATE); val sender = user(); val outsider = user(); val i = issue(secret, sender)
        val added = events.addWithoutSkip(note(sender, i, listOf(outsider), "", outsider.loginId, EventType.ISSUE_SHARER_CHANGED))
        assertNull(added)
        val removed = events.addWithoutSkip(note(sender, i, listOf(outsider), outsider.loginId, "", EventType.ISSUE_SHARER_CHANGED))
        assertNotNull(removed); assertEquals(setOf(outsider.id), removed.receivers.map { it.id }.toSet())
    }

    @Test
    fun `리소스에 걸린 알림 이벤트를 모두 지운다`() {
        val p = project(); val sender = user(); val receiver = user(); val i = issue(p, sender)
        events.add(note(sender, i, listOf(receiver), "open", "closed"))
        events.deleteBy(ResourceType.ISSUE_POST, i.id.toString())
        entityManager.flush()
        assertEquals(0, eventRepository.count()); assertTrue(mailRepository.findAll().isEmpty())
    }
}
