package dev.yona.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** `@Scheduled` 작업(임시 첨부 정리 등)을 켠다. v1.6의 Akka 스케줄러에 해당한다(D-14). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig
