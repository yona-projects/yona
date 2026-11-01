package dev.yona.observability

import dev.yona.domain.notification.NotificationMailRepository
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import org.springframework.stereotype.Component

/**
 * 알림 메일 대기열 길이 지표(`yona.notification.mail.queue`)이다(로드맵 X-10 C5d). 대기열이 쌓이면 메일 스케줄러가 막혔거나 메일 서버가 느리다는 신호이다.
 * 내보내는 주기(60초)마다 한 번 `count`를 읽는다(A-320 6번에서 DB를 세는 gauge를 제외했지만 이것은 명시적으로 합의한 대기열 길이이다).
 */
@Component
class BackgroundQueueMetrics(private val mails: NotificationMailRepository) : MeterBinder {

    override fun bindTo(registry: MeterRegistry) {
        Gauge.builder("yona.notification.mail.queue") { runCatching { mails.count().toDouble() }.getOrDefault(0.0) }.description("발송을 기다리는 알림 메일 수").register(registry)
    }
}
