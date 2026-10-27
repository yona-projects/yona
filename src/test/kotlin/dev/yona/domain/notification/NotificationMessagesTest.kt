package dev.yona.domain.notification

import dev.yona.domain.board.Posting
import dev.yona.domain.board.PostingRepository
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import java.util.Locale
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 알림 이벤트가 메일에 실리는 문구와 이동 주소가 되는 규칙을 검증한다.
 *
 * 원본: `NotificationEvent.getMessage`, `getPlainMessage`, `getUrlToView`(`app/models/NotificationEvent.java:123-273, 535-575`).
 */
@Transactional
class NotificationMessagesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var notificationMessages: NotificationMessages
    @Autowired private lateinit var messages: MessageSource
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var postings: PostingRepository
    @Autowired private lateinit var milestones: MilestoneRepository

    private var sequence = 0
    private val en = "en"

    private fun msg(key: String, vararg args: Any) = messages.getMessage(key, args, Locale.forLanguageTag(en))

    private fun event(type: EventType, old: String? = null, new: String? = null, resourceType: ResourceType = ResourceType.ISSUE_POST, resourceId: String = "1", sender: Long = 1) =
        NotificationEvent(title = "t", senderId = sender, created = Date(), resourceType = resourceType, resourceId = resourceId, eventType = type, oldValue = old, newValue = new)

    private fun user(name: String = "msg${++sequence}"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = name, name = name, email = "$name@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun project(): Project = projects.save(Project(owner = "mo${++sequence}", name = "mp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    @Test
    fun `상태 변경은 닫힘과 다시 열림을 구분한다`() {
        assertEquals(msg("notification.issue.closed"), notificationMessages.message(event(EventType.ISSUE_STATE_CHANGED, "open", "closed"), en))
        assertEquals(msg("notification.issue.reopened"), notificationMessages.message(event(EventType.ISSUE_STATE_CHANGED, "closed", "open"), en))
    }

    @Test
    fun `담당자 변경은 지정과 해제를 구분한다`() {
        assertEquals(msg("notification.issue.assigned", "kim"), notificationMessages.message(event(EventType.ISSUE_ASSIGNEE_CHANGED, null, "kim"), en))
        assertEquals(msg("notification.issue.unassigned"), notificationMessages.message(event(EventType.ISSUE_ASSIGNEE_CHANGED, "kim", null), en))
    }

    @Test
    fun `마일스톤 변경은 제목을, 없으면 마일스톤 없음을 알린다`() {
        val p = project()
        val m = milestones.save(Milestone(project = p, title = "1.0 출시", contents = "", state = State.OPEN))
        assertEquals(msg("notification.milestone.changed", "1.0 출시"), notificationMessages.message(event(EventType.ISSUE_MILESTONE_CHANGED, "-1", m.id.toString()), en))
        assertEquals(msg("notification.milestone.changed", messages.getMessage("issue.noMilestone", null, Locale.forLanguageTag("en-US"))), notificationMessages.message(event(EventType.ISSUE_MILESTONE_CHANGED, "1", "-1"), en))
    }

    @Test
    fun `마일스톤 없음 문구는 받는 사람의 언어와 JVM 로캘이 아니라 application_langs의 첫 언어이다 (v1_6 Lang_defaultLang)`() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.KOREA)
        try {
            val korean = notificationMessages.message(event(EventType.ISSUE_MILESTONE_CHANGED, "1", "-1"), "ko-KR")
            assertTrue(korean!!.contains(messages.getMessage("issue.noMilestone", null, Locale.forLanguageTag("en-US"))), korean)
        } finally { Locale.setDefault(before) }
    }

    @Test
    fun `새 글, 새 이슈, 댓글 수정은 새 값을 그대로 쓰고 새 댓글은 이전 내용을 이어 붙인다`() {
        assertEquals("본문", notificationMessages.message(event(EventType.NEW_ISSUE, null, "본문"), en))
        assertEquals("본문", notificationMessages.message(event(EventType.NEW_POSTING, null, "본문"), en))
        assertEquals("고침", notificationMessages.message(event(EventType.COMMENT_UPDATED, "x", "고침"), en))
        assertEquals("댓글\n\n이전", notificationMessages.message(event(EventType.NEW_COMMENT, "\n\n이전", "댓글"), en))
        assertEquals("댓글null", notificationMessages.message(event(EventType.NEW_COMMENT, null, "댓글"), en))
    }

    @Test
    fun `본문 변경은 변경 차이를 보여 주고 일반 텍스트는 접두어를 쓴다`() {
        val e = event(EventType.ISSUE_BODY_CHANGED, "c", "d")
        assertEquals(MailDiff.html("c", "d"), notificationMessages.message(e, en))
        assertEquals("--- c\n+++ d\n", notificationMessages.plainMessage(e, en))
        assertEquals(MailDiff.html("c", "d"), notificationMessages.message(event(EventType.POSTING_BODY_CHANGED, "c", "d"), en))
    }

    @Test
    fun `일반 텍스트 메시지는 문단 사이의 br 줄을 걷어 낸다`() {
        assertEquals("댓글\n\n이전", notificationMessages.plainMessage(event(EventType.NEW_COMMENT, "\n\n<br />\n이전", "댓글"), en))
    }

    @Test
    fun `가입 신청은 상태에 따라 문구가 다르다`() {
        assertEquals(msg("notification.member.enroll.request"), notificationMessages.message(event(EventType.MEMBER_ENROLL_REQUEST, "CANCEL", "REQUEST"), en))
        assertEquals(msg("notification.member.enroll.accept"), notificationMessages.message(event(EventType.MEMBER_ENROLL_REQUEST, "REQUEST", "ACCEPT"), en))
        assertEquals(msg("notification.member.enroll.cancel"), notificationMessages.message(event(EventType.MEMBER_ENROLL_REQUEST, "REQUEST", "CANCEL"), en))
        assertEquals(msg("notification.organization.member.enroll.request"), notificationMessages.message(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, "CANCEL", "REQUEST"), en))
        assertEquals(msg("notification.organization.member.enroll.accept"), notificationMessages.message(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, "REQUEST", "ACCEPT"), en))
        assertEquals(msg("notification.organization.member.enroll.cancel"), notificationMessages.message(event(EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST, "REQUEST", "CANCEL"), en))
    }

    @Test
    fun `이동, 공유자, 삭제 문구`() {
        assertEquals(msg("notification.type.issue.moved", "a/b", "c/d"), notificationMessages.message(event(EventType.ISSUE_MOVED, "a/b", "c/d"), en))
        val sharer = user()
        assertEquals(msg("notification.issue.sharer.added", sharer.name!!), notificationMessages.message(event(EventType.ISSUE_SHARER_CHANGED, "", sharer.loginId), en))
        assertEquals(msg("notification.issue.sharer.deleted"), notificationMessages.message(event(EventType.ISSUE_SHARER_CHANGED, sharer.loginId, ""), en))
        val deleter = user()
        assertEquals(msg("notification.resource.deleted", deleter.name!!), notificationMessages.message(event(EventType.RESOURCE_DELETED, "본문", deleter.loginId, ResourceType.PROJECT), en))
    }

    @Test
    fun `이동 주소는 리소스 종류에 따라 정해진다`() {
        val p = project(); val a = user()
        val issue = issues.save(Issue(project = p, number = 7, title = "제목", body = "b", state = State.OPEN, authorId = a.id, authorLoginId = a.loginId, authorName = a.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        val post = postings.save(Posting(project = p, number = 3, title = "글", body = "b", authorId = a.id, authorLoginId = a.loginId, authorName = a.name, notice = false, readme = false, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        assertEquals("/${p.owner}/${p.name}/issue/7", notificationMessages.urlToView(event(EventType.ISSUE_STATE_CHANGED, resourceId = issue.id.toString())))
        assertEquals("/${p.owner}/${p.name}/post/3", notificationMessages.urlToView(event(EventType.NEW_POSTING, resourceType = ResourceType.BOARD_POST, resourceId = post.id.toString())))
        assertEquals("/${p.owner}/${p.name}", notificationMessages.urlToView(event(EventType.RESOURCE_DELETED, resourceType = ResourceType.PROJECT, resourceId = p.id.toString())))
        assertEquals("/${p.owner}/${p.name}/members", notificationMessages.urlToView(event(EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = p.id.toString())))
        assertEquals("/${p.owner}/${p.name}", notificationMessages.urlToView(event(EventType.MEMBER_ENROLL_ACCEPT, resourceType = ResourceType.PROJECT, resourceId = p.id.toString())))
        assertNull(notificationMessages.urlToView(event(EventType.MEMBER_ENROLL_REQUEST, resourceType = ResourceType.PROJECT, resourceId = "999999")))
        assertNotNull(p.id)
    }
}
