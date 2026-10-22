package dev.yona.domain.notification

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * v1.6 `NotificationEvent.findByReceiver`/`getNotificationsCount`(`NotificationEvent.java:1612-1640`)를 검증한다.
 *
 * 받는 사람(`notification_event_n4user`)이 일치하는 알림을 최신순으로, `from`번째부터 `size`개만 돌려준다.
 */
@Transactional
class NotificationEventQueryTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var events: NotificationEventRepository

    @Autowired
    private lateinit var users: UserRepository

    private fun user(loginId: String) = users.save(User(loginId = loginId, email = "$loginId@example.com", state = UserState.ACTIVE))

    private fun event(title: String, minutesAgo: Int, vararg receivers: User) = events.save(
        NotificationEvent(
            title = title, eventType = EventType.NEW_ISSUE,
            created = Date(System.currentTimeMillis() - minutesAgo * 60_000L),
            receivers = receivers.toMutableSet()
        )
    )

    @Test
    fun `받는 사람의 알림만 최신순으로 돌려준다`() {
        val alice = user("alice")
        val bob = user("bob")
        event("old", 30, alice)
        event("new", 1, alice)
        event("middle", 10, alice, bob)
        event("bob only", 5, bob)

        assertEquals(listOf("new", "middle", "old"), events.findByReceiver(alice, 0, 20).map { it.title })
        assertEquals(listOf("bob only", "middle"), events.findByReceiver(bob, 0, 20).map { it.title })
    }

    @Test
    fun `시작 위치와 개수로 잘라서 돌려준다`() {
        val alice = user("alice2")
        (1..5).forEach { event("e$it", it, alice) } // e1이 가장 최신
        assertEquals(listOf("e2", "e3"), events.findByReceiver(alice, 1, 2).map { it.title })
        assertEquals(listOf("e5"), events.findByReceiver(alice, 4, 20).map { it.title })
        assertEquals(emptyList(), events.findByReceiver(alice, 5, 20).map { it.title })
    }

    @Test
    fun `알림이 없으면 빈 목록이다`() {
        assertEquals(emptyList(), events.findByReceiver(user("nobody"), 0, 20))
    }
}
