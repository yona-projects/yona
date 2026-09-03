package com.github.yonaprojects.yona.domain.notification

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface NotificationMailRepository : JpaRepository<NotificationMail, Long> {
    fun findByNotificationEvent(notificationEvent: NotificationEvent): NotificationMail?

    // yona NotificationMail.startSchedule()의
    // `.lt("notificationEvent.created", createdUntil).orderBy("notificationEvent.created ASC")` 대응 (P1-27).
    fun findByNotificationEvent_CreatedBeforeOrderByNotificationEvent_CreatedAsc(threshold: Instant): List<NotificationMail>
}
