package dev.yona.web.home

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.notification.NotificationEvent
import dev.yona.domain.notification.NotificationEventRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 홈의 알림 목록(`GET /notifications`)과 더 보기(`GET /notification`)를 검증한다.
 *
 * 원본: `app/views/index/partial_notifications.scala.html`, `app/controllers/NotificationApp.java`,
 * `NotificationEvent.findByReceiver`(`app/models/NotificationEvent.java:1612`).
 */
@AutoConfigureMockMvc
@Transactional
class NotificationListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var events: NotificationEventRepository

    private var sequence = 0

    private fun user(loginId: String = "nl${++sequence}"): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = loginId, name = "이름$loginId", email = "$loginId@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession {
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
        return session
    }

    private fun issue(author: User): Issue {
        val p = projects.save(Project(owner = "nlo${++sequence}", name = "nlp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = p, number = 3, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
    }

    private fun event(sender: User, receiver: User, issue: Issue, type: EventType, old: String?, new: String?, ago: Long = 0, title: String = "[p] 제목 (#3)") =
        events.save(NotificationEvent(title = title, senderId = sender.id, created = Date(System.currentTimeMillis() - ago), resourceType = ResourceType.ISSUE_POST, resourceId = issue.id.toString(), eventType = type, oldValue = old, newValue = new, receivers = mutableSetOf(receiver)))

    private fun page(path: String, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `알림이 없으면 없음 안내를 보여 주고 더 보기는 없다`() {
        val me = user()
        val doc = page("/notifications", login(me))
        assertNotNull(doc.selectFirst("ul.notification-wrap .warning-none"))
        assertNull(doc.selectFirst("#notification-more"))
    }

    @Test
    fun `알림 항목은 아이콘, 제목 링크, 본문, 보낸 사람을 보여 준다`() {
        val me = user(); val sender = user("sender1"); val i = issue(sender)
        event(sender, me, i, EventType.NEW_ISSUE, null, "첫 줄\n둘째 줄")
        val doc = page("/notifications", login(me))
        val item = doc.selectFirst("ul.notification-wrap li.notification-stream")!!
        assertEquals("yobicon-list-alt", item.selectFirst(".stream-type i")?.className())
        assertTrue(item.selectFirst(".stream-type")!!.hasClass("list-alt"))
        val link = item.selectFirst(".stream-info .title a")!!
        assertEquals("[p] 제목 (#3)", link.text())
        assertEquals("/${i.project!!.owner}/${i.project!!.name}/issue/3", link.attr("href"))
        assertEquals(1, item.selectFirst(".message")!!.select("br").size)
        assertEquals("첫 줄 둘째 줄", item.selectFirst(".message")!!.text())
        assertEquals("/sender1", item.selectFirst(".meta a.author")?.attr("href"))
        assertEquals("이름sender1", item.selectFirst(".meta a.author")?.text())
        assertTrue(item.selectFirst(".meta")!!.text().contains("@sender1"))
        assertNotNull(item.selectFirst(".meta .ago"))
    }

    @Test
    fun `종류와 새 값에 따라 아이콘이 달라진다`() {
        val me = user(); val s = user(); val i = issue(s)
        event(s, me, i, EventType.ISSUE_STATE_CHANGED, "open", "closed", 5000)
        event(s, me, i, EventType.NEW_COMMENT, "", "댓글", 4000)
        event(s, me, i, EventType.ISSUE_ASSIGNEE_CHANGED, null, "x", 3000)
        event(s, me, i, EventType.RESOURCE_DELETED, "본문", "x", 2000)
        val types = page("/notifications", login(me)).select("li.notification-stream .stream-type").map { it.className() }
        assertEquals(listOf("stream-type megaphone", "stream-type friends changed", "stream-type comment2", "stream-type list-alt closed"), types)
    }

    @Test
    fun `본문 변경과 댓글 수정은 Edit 표시를 쓴다`() {
        val me = user(); val s = user(); val i = issue(s)
        event(s, me, i, EventType.ISSUE_BODY_CHANGED, "a", "b")
        val type = page("/notifications", login(me)).selectFirst("li.notification-stream .stream-type")!!
        assertEquals("stream-type updated", type.className())
        assertEquals("Edit", type.text())
    }

    @Test
    fun `이동할 곳이 없으면 제목만 보여 준다`() {
        val me = user(); val s = user(); val i = issue(s)
        val e = event(s, me, i, EventType.NEW_ISSUE, null, "x")
        e.resourceId = "9999999"; events.save(e)
        val title = page("/notifications", login(me)).selectFirst("li.notification-stream .title")!!
        assertEquals("[p] 제목 (#3)", title.text())
        assertNull(title.selectFirst("a"))
    }

    @Test
    fun `알림은 최신순이고 스무 개를 넘으면 더 보기가 나온다`() {
        val me = user(); val s = user(); val i = issue(s)
        (1..25).forEach { n -> event(s, me, i, EventType.NEW_ISSUE, null, "n$n", ago = (30 - n) * 1000L, title = "알림$n") }
        val session = login(me)
        val doc = page("/notifications", session)
        val titles = doc.select("li.notification-stream .title").map { it.text() }
        assertEquals(20, titles.size)
        assertEquals("알림25", titles.first())
        assertNotNull(doc.selectFirst("#notification-more"))

        val more = Jsoup.parse(mockMvc.perform(get("/notification").param("from", "20").param("limit", "20").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(listOf("알림5", "알림4", "알림3", "알림2", "알림1"), more.select("li.notification-stream .title").map { it.text() })
    }

    @Test
    fun `다른 사람의 알림은 보이지 않는다`() {
        val me = user(); val other = user(); val s = user(); val i = issue(s)
        event(s, other, i, EventType.NEW_ISSUE, null, "x")
        assertNotNull(page("/notifications", login(me)).selectFirst(".warning-none"))
    }

    @Test
    fun `로그인하지 않아도 더 보기는 오류 없이 빈 목록을 돌려준다`() {
        val result = mockMvc.perform(get("/notification").param("from", "0").param("limit", "20")).andExpect(status().isOk).andReturn().response
        assertTrue(Jsoup.parse(result.contentAsString).select("li.notification-stream").isEmpty())
    }
}
