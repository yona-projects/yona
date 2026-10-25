package dev.yona.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 실제 DB를 쓰는 통합 테스트의 공통 부모이다.
 *
 * 모든 통합 테스트가 같은 설정을 쓰므로 스프링 컨텍스트와 DB 스키마를 한 번만 만들어 공유한다.
 * 알림 메일 스케줄러는 끈다(`yona.notification-mail-enabled=false`). 테스트가 만든 데이터를 몰래 처리하지 않도록, 발송 테스트는 `NotificationMailScheduler.sendDue`를 직접 부른다.
 * 연결 풀은 컨텍스트 수가 늘어도 DB의 최대 연결 수를 넘지 않도록 작게 제한한다.
 * 스키마는 엔티티에서 생성하고(`create-drop`) 컨텍스트가 닫힐 때 지운다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.secret=test-secret"])
abstract class AbstractIntegrationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) {
            TestDatabase.current.register(registry)
            // 속성 조합마다 스프링 컨텍스트가 따로 만들어져 캐시에 남는다. 컨텍스트마다 큰 연결 풀을 두면
            // PostgreSQL의 최대 연결 수(기본 100)를 넘으므로 풀을 작게 제한하고 놀고 있는 연결은 두지 않는다.
            registry.add("spring.datasource.hikari.maximum-pool-size") { "3" }
            registry.add("spring.datasource.hikari.minimum-idle") { "0" }
        }
    }
}
