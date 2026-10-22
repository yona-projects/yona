package dev.yona.domain.notification

import dev.yona.config.YonaProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.util.Date

/**
 * 보관 기간이 지난 알림 이벤트를 지운다. v1.6 `NotificationEvent.scheduleDeleteOldNotifications`
 * (`app/models/NotificationEvent.java:1577-1597`)에 해당한다.
 *
 * `yona.notification-keep-time`(일)이 0보다 클 때만 동작한다. 기동 1분 뒤부터 하루에 한 번 실행한다.
 */
@Component
class NotificationCleaner(
    private val events: NotificationEventRepository,
    private val properties: YonaProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 하루에 한 번 정리한다. 보관 기간을 정하지 않았으면 아무것도 하지 않는다. */
    @Scheduled(initialDelay = 60_000, fixedRate = 86_400_000)
    fun scheduled() {
        try {
            val deleted = deleteOld()
            if (deleted > 0) log.info("보관 기간이 지난 알림 이벤트를 {}건 지웠다", deleted)
        } catch (e: Exception) {
            log.warn("오래된 알림 이벤트를 지우지 못했다", e)
        }
    }

    /**
     * 만든 지 보관 일수가 지난 알림 이벤트를 지운다.
     *
     * @param now 기준 시각. 테스트에서 지정한다
     * @return 지운 이벤트 수
     */
    @Transactional
    fun deleteOld(now: Date = Date()): Int {
        val days = properties.notificationKeepTime
        if (days <= 0) return 0
        val olds = events.findByCreatedBefore(Date(now.time - days * 86_400_000L))
        olds.forEach { events.delete(it) }
        return olds.size
    }
}
