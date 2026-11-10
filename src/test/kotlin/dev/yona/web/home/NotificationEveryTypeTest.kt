package dev.yona.web.home

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 알림의 **모든 종류**(`EventType`의 모든 값)가 값 조합(옛 값, 새 값)마다 홈의 알림 목록에서 오류 없이 그려지는지 확인한다(새 UI의 알림 화면 설계 확인, `ui-ia-gitlab.md`의 4.2.2절).
 * 항목마다 아이콘이 있고, 본문에 풀리지 않은 자리 표시(`{0}`), 번역 키 원문(`notification.`), `null`이 나오지 않아야 하며, 한국어와 영어 두 언어에서 같다.
 * 이동할 곳을 만들 수 없는 값이어도 제목만 보여 주며 오류가 나지 않아야 한다(기존 시험 `NotificationListTest`가 하나의 경우를 확인한다).
 */
@AutoConfigureMockMvc
@Transactional
class NotificationEveryTypeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var events: NotificationEventRepository

    private var sequence = 0

    private fun user(lang: String?): User {
        val salt = LegacyPasswordHasher.generateSalt()
        val id = "et${++sequence}-${System.nanoTime() % 100000}"
        return users.save(User(loginId = id, name = "이름$id", email = "$id@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), lang = lang))
    }

    private fun login(user: User): MockHttpSession {
        val session = MockHttpSession()
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
        return session
    }

    private fun issue(author: User): Issue {
        val p = projects.save(Project(owner = "eto${++sequence}", name = "etp$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        return issues.save(Issue(project = p, number = 3, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0))
    }

    /** 종류에 맞는 자원 종류를 고른다. 자원이 실제로는 없어도 이동할 곳이 없을 뿐 오류가 나면 안 된다. */
    private fun resourceTypeOf(type: EventType): ResourceType = when {
        type.name.startsWith("PULL_REQUEST") || type == EventType.NEW_PULL_REQUEST || type == EventType.ISSUE_REFERRED_FROM_PULL_REQUEST -> ResourceType.PULL_REQUEST
        type == EventType.NEW_COMMENT || type == EventType.COMMENT_UPDATED -> ResourceType.ISSUE_COMMENT
        type == EventType.NEW_POSTING || type == EventType.POSTING_BODY_CHANGED -> ResourceType.BOARD_POST
        type == EventType.MEMBER_ENROLL_REQUEST || type == EventType.MEMBER_ENROLL_ACCEPT -> ResourceType.PROJECT
        type == EventType.ORGANIZATION_MEMBER_ENROLL_REQUEST || type == EventType.ORGANIZATION_MEMBER_ENROLL_ACCEPT -> ResourceType.ORGANIZATION
        type == EventType.NEW_REVIEW_COMMENT -> ResourceType.REVIEW_COMMENT
        type == EventType.REVIEW_THREAD_STATE_CHANGED -> ResourceType.COMMENT_THREAD
        type == EventType.NEW_COMMIT || type == EventType.ISSUE_REFERRED_FROM_COMMIT -> ResourceType.COMMIT
        else -> ResourceType.ISSUE_POST
    }

    /** (옛 값, 새 값) 조합. 알림 종류마다 쓰는 값이 달라 흔한 값을 모두 시험한다. */
    private val values: List<Pair<String?, String?>> = listOf(
        null to null, null to "open", "open" to "closed", null to "closed", null to "merged", null to "ACCEPT", null to "CANCEL", null to "DONE", null to "CLOSED",
        "APPROVED" to "본문", "COMMENTED" to "본문", "CHANGES_REQUESTED" to "본문", "이전 본문" to "새 본문", null to "123", "a" to "b"
    )

    private fun page(path: String, session: MockHttpSession): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun check(lang: String?) {
        val me = user(lang); val sender = user(lang); val i = issue(sender)
        val problems = mutableListOf<String>()
        for (type in EventType.entries) {
            // 종류마다 따로 저장하고 따로 그린다(한 목록은 20개까지 보이므로)
            events.deleteAll(events.findAll().filter { e -> e.receivers.any { it.id == me.id } })
            // 종류마다 실제 생성 코드(`NotificationListener`)가 넣는 값만 시험한다. 현실에서 만들어지지 않는 조합은 제외한다
            val values = when (type) {
                EventType.PULL_REQUEST_STATE_CHANGED -> values.filter { it.second in setOf("open", "closed", "merged") }
                EventType.PULL_REQUEST_MERGED -> listOf(null to "conflict", null to "resolved")
                EventType.NEW_COMMENT, EventType.COMMENT_UPDATED -> listOf("" to "본문", "이전 본문" to "새 본문")
                EventType.ISSUE_MOVED -> listOf("a/b" to "c/d")
                EventType.PULL_REQUEST_REVIEW_SUBMITTED -> listOf("APPROVED" to "본문", "COMMENTED" to null, "CHANGES_REQUESTED" to "본문")
                EventType.PULL_REQUEST_REVIEW_DISMISSED -> listOf("someone" to null, "someone" to "사유")
                EventType.PULL_REQUEST_REVIEW_REQUESTED, EventType.PULL_REQUEST_REVIEW_UNREQUESTED -> listOf(null to "someone")
                else -> values
            }
            val created = values.mapIndexed { n, (old, new) ->
                events.save(NotificationEvent(title = "[p] 제목 (#3)", senderId = sender.id, created = Date(System.currentTimeMillis() - n * 1000L), resourceType = resourceTypeOf(type), resourceId = i.id.toString(), eventType = type, oldValue = old, newValue = new, receivers = mutableSetOf(me)))
            }
            val doc = try { page("/notifications", login(me)) } catch (e: Throwable) { problems += "$type: 화면이 오류로 끝났다: ${e.message?.take(120)}"; continue }
            val items = doc.select("ul.notification-wrap li.notification-stream")
            if (items.size != created.size) problems += "$type: 항목 ${items.size}개(기대 ${created.size}개)"
            for ((n, item) in items.withIndex()) {
                val icon = item.selectFirst(".stream-type i")?.className().orEmpty()
                val text = item.text()
                val pair = values.getOrNull(n)
                when {
                    !icon.startsWith("yobicon-") && item.selectFirst(".stream-type.updated") == null -> problems += "$type $pair: 아이콘이 없다"
                    Regex("\\{\\d+\\}").containsMatchIn(text) -> problems += "$type $pair: 풀리지 않은 자리 표시 `${text.take(80)}`"
                    text.contains("notification.") || text.contains("pullRequest.event.") -> problems += "$type $pair: 번역 키 원문 `${text.take(80)}`"
                    item.selectFirst(".message")?.text()?.contains("null") == true -> problems += "$type $pair: 본문에 null `${item.selectFirst(".message")?.text()?.take(80)}`"
                }
            }
        }
        assertEquals(emptyList(), problems.distinct().take(80), "알림 종류 ${EventType.entries.size}개 중 문제가 있는 항목($lang)")
    }

    @Test
    fun `모든 알림 종류가 한국어로 오류 없이 그려진다`() = check("ko-KR")

    @Test
    fun `모든 알림 종류가 영어로 오류 없이 그려진다`() = check("en-US")

    @Test
    fun `알림 종류는 서른 개 넘게 있고 이 시험이 전부를 돈다`() {
        assertTrue(EventType.entries.size >= 30, "종류 수 ${EventType.entries.size}")
        assertFalse(values.isEmpty())
    }
}
