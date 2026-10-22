package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ResourceType
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 보관 기간이 지난 알림 이벤트를 지우는 동작을 검증한다.
 *
 * 원본: `NotificationEvent.scheduleDeleteOldNotifications`(`app/models/NotificationEvent.java:1577-1597`).
 */
@Transactional
class NotificationCleanerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var events: NotificationEventRepository

    private fun event(daysAgo: Int) = events.save(
        NotificationEvent(title = "t$daysAgo", senderId = 1, created = Date(System.currentTimeMillis() - daysAgo * 86_400_000L), resourceType = ResourceType.ISSUE_POST, resourceId = "1", eventType = EventType.NEW_ISSUE)
    )

    @Test
    fun `보관 일수보다 오래된 알림만 지운다`() {
        val old = event(11); val recent = event(9)
        val deleted = NotificationCleaner(events, YonaProperties(notificationKeepTime = 10)).deleteOld()
        assertEquals(1, deleted)
        assertEquals(listOf(recent.id), events.findAll().map { it.id })
        assertEquals(false, events.existsById(old.id!!))
    }

    @Test
    fun `보관 일수가 0 이하이면 아무것도 지우지 않는다`() {
        event(1000)
        assertEquals(0, NotificationCleaner(events, YonaProperties(notificationKeepTime = -1)).deleteOld())
        assertEquals(0, NotificationCleaner(events, YonaProperties(notificationKeepTime = 0)).deleteOld())
        assertEquals(1, events.count())
    }
}
