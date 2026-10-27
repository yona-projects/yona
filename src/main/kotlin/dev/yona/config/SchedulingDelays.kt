package dev.yona.config

import org.springframework.stereotype.Component

/**
 * `@Scheduled`의 지연 값을 밀리초로 내어 주는 도우미이다.
 *
 * 기간 설정은 `30s`, `6h` 같은 문자열도 받으므로([YonaProperties]) 어노테이션의 숫자 자리표시자(`${...}`)로 바로 읽을 수 없다.
 * SpEL에서 이 빈(`@schedulingDelays`)의 값을 읽는다.
 */
@Component("schedulingDelays")
class SchedulingDelays(private val properties: YonaProperties) {

    /** 알림 메일 첫 발송까지의 지연(밀리초) (`application.notification.bymail.initdelay`). */
    val notificationMailInitDelayMillis: Long get() = properties.notificationMailInitDelay.toMillis()

    /** 알림 메일 발송 주기(밀리초) (`application.notification.bymail.interval`). */
    val notificationMailIntervalMillis: Long get() = properties.notificationMailInterval.toMillis()

    /** 임시 파일 정리의 첫 실행과 주기(밀리초) (`application.temporaryfiles.keep-up.time`). */
    val temporaryFilesKeepUpMillis: Long get() = properties.temporaryFilesKeepUpTime.toMillis()
}
