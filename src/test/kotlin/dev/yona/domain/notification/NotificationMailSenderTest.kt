package dev.yona.domain.notification

import dev.yona.domain.comment.IssueComment
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
import dev.yona.markdown.SiteHostname
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.Message
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import dev.yona.mailbox.MailboxAddress
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 알림 한 건이 메일 한 통으로 조립되어 나가는 모습을 검증한다.
 *
 * 원본: `NotificationMail.sendNotification`, `sendMail`, `EventEmail`(`app/models/NotificationMail.java:290-540`).
 */
@Transactional
class NotificationMailSenderTest : AbstractIntegrationTest() {

    @Autowired private lateinit var sender: NotificationMailSender
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager
    @Autowired private lateinit var hostname: SiteHostname
    @MockitoBean private lateinit var mailSender: JavaMailSender
    @MockitoSpyBean private lateinit var mailboxAddress: MailboxAddress

    private var sequence = 0

    @BeforeEach
    fun clear() { Mockito.clearInvocations(mailSender) }

    private fun user(lang: String? = null, state: UserState = UserState.ACTIVE, name: String = "ms${++sequence}"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = name, name = name, email = "$name@example.com", lang = lang, state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun issue(author: User): Issue {
        val p = projects.save(Project(owner = "mso${++sequence}", name = "msp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = p, number = 1, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    private fun event(sender: User, issue: Issue, type: EventType, old: String?, new: String?, vararg receivers: User, title: String = "[p] 제목 (#1)", resourceType: ResourceType = ResourceType.ISSUE_POST, resourceId: Long = issue.id!!) =
        NotificationEvent(title = title, senderId = sender.id, created = Date(), resourceType = resourceType, resourceId = resourceId.toString(), eventType = type, oldValue = old, newValue = new, receivers = receivers.toMutableSet())
            .also { entityManager.persist(it); entityManager.flush() }

    /** 보낸 메일을 모두 모은다. 메시지 식별자와 헤더가 정해지도록 저장 과정을 거친다. */
    private fun sent(): List<MimeMessage> {
        val invocations = Mockito.mockingDetails(mailSender).invocations.filter { it.method.name == "send" }
        return invocations.flatMap { inv -> inv.arguments.flatMap { a -> if (a is Array<*>) a.toList() else listOf(a) } }.filterIsInstance<MimeMessage>().onEach { it.saveChanges() }
    }

    private fun bodyParts(message: MimeMessage): List<String> {
        val parts = mutableListOf<String>()
        fun walk(p: jakarta.mail.Part) {
            if (p.isMimeType("multipart/*")) { val m = p.content as MimeMultipart; for (i in 0 until m.count) walk(m.getBodyPart(i)) }
            else if (p.isMimeType("text/*")) parts += p.content as String
        }
        walk(message)
        return parts
    }

    @Test
    fun `새 이슈 알림은 주소를 숨겨 숨은참조로 보내고 제목, 보낸 사람, 메시지 식별자를 정한다`() {
        val author = user(name = "writer"); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.NEW_ISSUE, null, "**굵게** 본문", receiver)))
        val mail = sent().single()
        assertEquals("[p] 제목 (#1)", mail.subject)
        assertEquals(listOf(receiver.email), mail.getRecipients(Message.RecipientType.BCC).map { (it as jakarta.mail.internet.InternetAddress).address })
        assertEquals(1, mail.getRecipients(Message.RecipientType.TO).size)
        assertEquals("writer", (mail.from.single() as jakarta.mail.internet.InternetAddress).personal)
        assertEquals("<issue_post/${i.id}@${hostname.name()}>", mail.messageID)
        assertNull(mail.getHeader("References"))
    }

    @Test
    fun `메일 본문은 마크다운을 렌더링하고 보기 링크와 구독 해제 링크를 덧붙인다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.NEW_ISSUE, null, "**굵게** 본문", receiver)))
        val parts = bodyParts(sent().single())
        val text = parts.first { !it.contains("<") }
        val html = parts.first { it.contains("<") }
        assertTrue(html.contains("<strong>굵게</strong>"), html)
        assertTrue(html.contains("/${i.project!!.owner}/${i.project!!.name}/issue/1\""), html)
        assertTrue(html.contains("/unwatch?resource.type=issue_post&amp;resource.id=${i.id}"), html)
        assertTrue(html.contains("/user/editform/notifications"), html)
        assertTrue(text.startsWith("**굵게** 본문"), text)
    }

    @Test
    fun `댓글 알림의 메시지 식별자는 댓글이고 참조 헤더는 이슈이다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        val comment = IssueComment(contents = "댓글", createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = i, projectId = i.project!!.id!!).also { entityManager.persist(it); entityManager.flush() }
        sender.send(MergedNotification(event(author, i, EventType.NEW_COMMENT, "\n\n이전", "댓글", receiver, resourceType = ResourceType.ISSUE_COMMENT, resourceId = comment.id!!)))
        val mail = sent().single()
        assertEquals("<issue_comment/${comment.id}@${hostname.name()}>", mail.messageID)
        assertEquals("<issue_post/${i.id}@${hostname.name()}>", mail.getHeader("References")!!.single())
    }

    @Test
    fun `상태 변경은 새 메시지 식별자를 받는다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.ISSUE_STATE_CHANGED, "open", "closed", receiver)))
        assertTrue(!sent().single().messageID.startsWith("<issue_post/"))
    }

    @Test
    fun `본문 변경은 마크다운이 아니라 변경 차이를 HTML 그대로 보낸다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.ISSUE_BODY_CHANGED, "a", "b", receiver)))
        val parts = bodyParts(sent().single())
        assertTrue(parts.first { it.contains("<") }.contains("background-color: #abdd52"))
        assertTrue(parts.first { !it.contains("<") }.startsWith("--- a\n+++ b\n"))
    }

    @Test
    fun `활성 사용자가 아닌 받는 사람은 제외하고 아무도 남지 않으면 보내지 않는다`() {
        val author = user(); val locked = user(state = UserState.LOCKED); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.NEW_ISSUE, null, "x", locked)))
        assertEquals(0, sent().size)
    }

    @Test
    fun `선호 언어가 다른 받는 사람은 언어별로 따로 보낸다`() {
        val author = user(); val ko = user("ko"); val en = user("en"); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.ISSUE_STATE_CHANGED, "open", "closed", ko, en)))
        val mails = sent()
        assertEquals(2, mails.size)
        assertEquals(setOf(ko.email, en.email), mails.flatMap { m -> m.getRecipients(Message.RecipientType.BCC).map { (it as jakarta.mail.internet.InternetAddress).address } }.toSet())
        assertNotNull(mails.first().getHeader("Message-ID"))
    }

    @Test
    fun `묶은 알림은 문구를 구분선으로 이어 한 통으로 보낸다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        val first = event(author, i, EventType.NEW_COMMENT, "", "처음 댓글", receiver, resourceType = ResourceType.ISSUE_COMMENT,
            resourceId = IssueComment(contents = "처음 댓글", createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = i, projectId = i.project!!.id!!).also { entityManager.persist(it); entityManager.flush() }.id!!)
        val second = event(author, i, EventType.ISSUE_STATE_CHANGED, "open", "closed", receiver)
        sender.send(MergedNotification(second, mutableListOf(first, second)))
        val text = bodyParts(sent().single()).first { !it.contains("<") }
        assertTrue(text.contains("처음 댓글\n\n---\n\n"), text)
    }

    @Test
    fun `수신 주소가 있으면 Reply-To에 리소스의 세부 주소를 달고 본문 안내가 회신 문구가 된다`() {
        Mockito.doReturn("yona@yona.local").`when`(mailboxAddress).address()
        val author = user(); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.NEW_ISSUE, null, "본문", receiver)))
        val mail = sent().single()
        val expected = "yona+${i.project!!.owner}/${i.project!!.name}/issue_post/${i.id}@yona.local"
        assertEquals(expected, (mail.replyTo.single() as jakarta.mail.internet.InternetAddress).address)
        val html = bodyParts(mail).first { it.contains("<") }
        assertTrue(html.contains("Reply to this email directly"), html)
    }

    @Test
    fun `댓글 알림의 Reply-To는 댓글이 아니라 이슈를 가리킨다`() {
        Mockito.doReturn("yona@yona.local").`when`(mailboxAddress).address()
        val author = user(); val receiver = user(); val i = issue(author)
        val comment = IssueComment(contents = "댓글", createdDate = Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = i, projectId = i.project!!.id!!).also { entityManager.persist(it); entityManager.flush() }
        sender.send(MergedNotification(event(author, i, EventType.NEW_COMMENT, "\n\n이전", "댓글", receiver, resourceType = ResourceType.ISSUE_COMMENT, resourceId = comment.id!!)))
        val mail = sent().single()
        assertEquals("yona+${i.project!!.owner}/${i.project!!.name}/issue_post/${i.id}@yona.local", (mail.replyTo.single() as jakarta.mail.internet.InternetAddress).address)
    }

    @Test
    fun `수신 주소가 없으면 Reply-To가 없고 본문은 보기 링크만 안내한다`() {
        val author = user(); val receiver = user(); val i = issue(author)
        sender.send(MergedNotification(event(author, i, EventType.NEW_ISSUE, null, "본문", receiver)))
        val mail = sent().single()
        assertNull(mail.getHeader("Reply-To"))
        assertTrue(!bodyParts(mail).first { it.contains("<") }.contains("Reply to this email directly"))
    }
}
