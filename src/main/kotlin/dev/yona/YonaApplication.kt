package dev.yona

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * Yona v2 애플리케이션의 진입점이다.
 *
 * Yona v1.6(Play 2.3 / Ebean)의 기능, 동작, 요청·응답 계약을 Kotlin과 Spring Boot 위에서
 * 그대로 재현하는 것을 목표로 한다.
 */
@SpringBootApplication
class YonaApplication

fun main(args: Array<String>) {
    runApplication<YonaApplication>(*args)
}
