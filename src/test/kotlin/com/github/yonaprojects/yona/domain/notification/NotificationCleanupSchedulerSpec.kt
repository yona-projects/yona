package com.github.yonaprojects.yona.domain.notification

import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

// yona models/NotificationEvent.scheduleDeleteOldNotifications() 대응 (P1-27).
class NotificationCleanupSchedulerSpec : DescribeSpec({
    val notificationEventRepository = mockk<NotificationEventRepository>()

    beforeTest {
        clearMocks(notificationEventRepository)
    }

    describe("deleteOldNotifications") {
        it("keepDays가 0 이하(기본값 -1)이면 삭제 쿼리를 실행하지 않는다(legacy KEEP_TIME_IN_DAYS 기본값과 동일)") {
            val scheduler = NotificationCleanupScheduler(notificationEventRepository, -1L)

            scheduler.deleteOldNotifications()

            verify(exactly = 0) { notificationEventRepository.deleteByCreatedBefore(any()) }
        }

        it("keepDays가 양수면 그만큼 이전에 생성된 이벤트를 삭제한다") {
            every { notificationEventRepository.deleteByCreatedBefore(any()) } returns 3L
            val scheduler = NotificationCleanupScheduler(notificationEventRepository, 30L)

            scheduler.deleteOldNotifications()

            verify(exactly = 1) { notificationEventRepository.deleteByCreatedBefore(any()) }
        }

        // deleted > 0 조건의 false 분기 — 삭제 대상이 없어도 예외 없이 조용히 끝나야 한다(로그를 남기지 않음).
        it("삭제된 이벤트가 없으면 로그를 남기지 않고 조용히 끝난다") {
            every { notificationEventRepository.deleteByCreatedBefore(any()) } returns 0L
            val scheduler = NotificationCleanupScheduler(notificationEventRepository, 30L)

            scheduler.deleteOldNotifications()

            verify(exactly = 1) { notificationEventRepository.deleteByCreatedBefore(any()) }
        }
    }
})
