package dev.yona.domain.notification

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.internet.MimeMessage
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 지연 시간이 지난 알림 메일 대기열을 비우며 보내는 동작을 검증한다.
 *
 * 원본: `NotificationMail.startSchedule`의 `sendMail`(`app/models/NotificationMail.java:100-170`).
 */
@Transactional
class NotificationMailSchedulerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var scheduler: NotificationMailScheduler
    @Autowired private lateinit var mails: NotificationMailRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager
    @MockitoBean private lateinit var mailSender: JavaMailSender

    private var sequence = 0

    @BeforeEach
    fun clear() { Mockito.clearInvocations(mailSender) }

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sc$n", name = "sc$n", email = "sc$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun issue(author: User): Issue {
        val p = projects.save(Project(owner = "sco${++sequence}", name = "scp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = p, number = 1, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    private fun queued(sender: User, issue: Issue, receiver: User, createdAgoMillis: Long, type: EventType = EventType.NEW_ISSUE, resourceId: String = issue.id.toString()): NotificationMail {
        val event = NotificationEvent(title = "[p] 제목 (#1)", senderId = sender.id, created = Date(System.currentTimeMillis() - createdAgoMillis), resourceType = ResourceType.ISSUE_POST, resourceId = resourceId, eventType = type, newValue = "본문", receivers = mutableSetOf(receiver))
        entityManager.persist(event)
        return mails.save(NotificationMail(notificationEvent = event)).also { entityManager.flush() }
    }

    private fun sentCount() = Mockito.mockingDetails(mailSender).invocations.count { inv -> inv.method.name == "send" && inv.arguments.any { it is MimeMessage } }

    @Test
    fun `지연 시간이 지난 알림만 메일로 보내고 대기열에서 지운다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        val old = queued(author, i, receiver, 200_000)
        val recent = queued(author, i, receiver, 10_000, EventType.ISSUE_STATE_CHANGED)
        scheduler.sendDue()
        assertEquals(1, sentCount())
        assertEquals(listOf(recent.id), mails.findAll().map { it.id })
        assertEquals(false, mails.existsById(old.id!!))
    }

    @Test
    fun `리소스가 지워졌으면 보내지 않지만 대기열에서는 지운다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        val mail = queued(author, i, receiver, 200_000)
        issues.delete(i); entityManager.flush()
        scheduler.sendDue()
        assertEquals(0, sentCount())
        assertEquals(false, mails.existsById(mail.id!!))
    }

    @Test
    fun `알림마다 메일이 나간다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        queued(author, i, receiver, 300_000); queued(author, i, receiver, 250_000, EventType.ISSUE_STATE_CHANGED)
        scheduler.sendDue()
        assertEquals(2, sentCount())
        assertEquals(0, mails.count())
    }

    @Test
    fun `보낼 것이 없어도 오류 없이 끝난다`() {
        scheduler.sendDue()
        assertEquals(0, sentCount())
    }
}
