package com.github.yonaprojects.yona.domain.notification

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * yona `models/NotificationEvent.scheduleDeleteOldNotifications()` 대응. legacy와 동일하게
 * `keep-days`가 0 이하이면(기본값 -1) 비활성 상태로, 아무 것도 삭제하지 않는다.
 */
@Component
class NotificationCleanupScheduler(
    private val notificationEventRepository: NotificationEventRepository,
    @Value("\${yona.notification.bymail.keep-days:-1}") private val keepDays: Long
) {
    private val logger = LoggerFactory.getLogger(NotificationCleanupScheduler::class.java)

    @Scheduled(initialDelay = 60_000L, fixedDelay = 24L * 60 * 60 * 1000)
    @Transactional
    fun deleteOldNotifications() {
        if (keepDays <= 0) {
            return
        }
        val threshold = Instant.now().minus(keepDays, ChronoUnit.DAYS)
        val deleted = notificationEventRepository.deleteByCreatedBefore(threshold)
        if (deleted > 0) {
            logger.info("Deleted $deleted old notification events created before $threshold")
        }
    }
}
