package dev.yona.domain.notification

import dev.yona.access.ResourceLookup
import dev.yona.config.YonaProperties
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.util.Date

/**
 * 알림 메일 대기열을 주기적으로 비우며 메일을 보낸다. v1.6 `NotificationMail.startSchedule`의 `sendMail`
 * (`app/models/NotificationMail.java:100-170`)에 해당하고, Akka 스케줄러를 `@Scheduled`로 바꿨다(D-14, D-18).
 *
 * 기동 후 `yona.notification-mail-init-delay`가 지나면 `yona.notification-mail-interval`마다 실행한다. 알림 이벤트를 만든 지
 * `yona.notification-mail-delay`가 지난 것만 보내므로 그 사이 연달아 일어난 알림을 묶을 수 있다.
 * 대기열 행은 보냈는지와 관계없이 지운다. 같은 알림을 다시 보내지 않기 위해서이며 v1.6도 그렇다.
 */
@Component
class NotificationMailScheduler(
    private val mails: NotificationMailRepository,
    private val events: NotificationEventRepository,
    private val merger: NotificationMerger,
    private val sender: NotificationMailSender,
    private val lookup: ResourceLookup,
    private val properties: YonaProperties,
    private val transactions: TransactionTemplate,
    private val jobs: dev.yona.observability.JobMetrics
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 설정에 따라 주기적으로 실행한다. `yona.notification-mail-enabled`가 꺼져 있으면 아무것도 하지 않는다. */
    @Scheduled(
        initialDelayString = "#{@schedulingDelays.notificationMailInitDelayMillis}",
        fixedRateString = "#{@schedulingDelays.notificationMailIntervalMillis}"
    )
    fun scheduled() {
        if (!properties.notificationMailEnabled) return
        try {
            jobs.run("notification_mail") { sendDue() }
        } catch (e: Exception) {
            log.warn("알림 메일을 보내는 중 오류가 발생했다", e)
        }
    }

    /**
     * 보낼 때가 된 알림을 메일로 보낸다.
     *
     * 1. 만든 지 지연 시간이 지난 알림의 대기열 행을 지우고 알림 번호를 모은다.
     * 2. 알림을 읽어 같은 사용자의 상태 변경과 댓글을 묶는다.
     * 3. 대상 리소스가 아직 있는 알림만 보낸다. 하나가 실패해도 나머지는 계속 보낸다.
     *
     * @param now 기준 시각. 테스트에서 지정한다
     */
    fun sendDue(now: Date = Date()) {
        val until = Date(now.time - properties.notificationMailDelay.toMillis())
        val ids = transactions.execute { extractEventIdsAndDelete(until) }.orEmpty()
        if (ids.isEmpty()) return
        transactions.executeWithoutResult {
            val due = events.findAllById(ids).sortedWith(compareBy({ it.created }, { it.id }))
            for (notification in merger.merge(due)) {
                try {
                    val main = notification.main
                    if (lookup.exists(requireNotNull(main.resourceType), requireNotNull(main.resourceId))) sender.send(notification)
                } catch (e: Exception) {
                    log.warn("알림 메일을 보내는 중 오류가 발생했다", e)
                }
            }
        }
    }

    private fun extractEventIdsAndDelete(until: Date): List<Long> {
        val ids = mutableListOf<Long>()
        for (mail in mails.findCreatedBefore(until)) {
            try {
                val eventId = requireNotNull(mail.notificationEvent?.id)
                mails.delete(mail)
                ids += eventId
            } catch (e: Exception) {
                log.warn("알림 이벤트를 모으는 중 오류가 발생했다", e)
            }
        }
        mails.flush()
        return ids
    }
}
