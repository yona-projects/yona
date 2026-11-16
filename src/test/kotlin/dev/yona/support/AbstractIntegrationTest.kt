package dev.yona.support

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 실제 DB를 쓰는 통합 테스트의 공통 부모이다.
 *
 * 모든 통합 테스트가 같은 설정을 쓰므로 스프링 컨텍스트와 DB 스키마를 한 번만 만들어 공유한다.
 * 알림 메일 스케줄러는 끈다(`yona.notification-mail-enabled=false`). 테스트가 만든 데이터를 몰래 처리하지 않도록, 발송 테스트는 `NotificationMailScheduler.sendDue`를 직접 부른다.
 * 연결 풀은 컨텍스트 수가 늘어도 DB의 최대 연결 수를 넘지 않도록 작게 제한한다.
 * 새 저장소의 기본 브랜치는 `master`로 둔다(`yona.git-default-branch`). 기존 시험이 v1.6의 `master`를 가정하고 쓰였기 때문이며, 기본값 `main`은 `GitRepositoryServiceTest`와 `DefaultBranchMainTest`가 검증한다.
 * 스키마는 엔티티에서 생성하고(`create-drop`) 컨텍스트가 닫힐 때 지운다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "yona.notification-mail-enabled=false", "yona.background-async=false", "yona.update.interval=0", "yona.mailbox-require-sender-authentication=false", "yona.secret=test-secret", "yona.git-default-branch=master", "yona.audit-file-enabled=false"])
abstract class AbstractIntegrationTest {

    /**
     * 앞선 시험이 시험 스레드의 중단 표시(interrupt flag)를 남기면 Cubrid 드라이버가 다음 DB 호출에서 "Has been interrupted"로 실패한다.
     * 전체 시험에서 스키마 조회와 내보내기 시험이 이 때문에 흩어져 실패했다(A-607). 표시를 남긴 시험을 찾을 수 있게 알리고, 다음 시험이 영향받지 않게 지운다.
     */
    @AfterEach
    fun clearInterruptLeftByTest(info: TestInfo) {
        if (Thread.interrupted()) System.err.println("INTERRUPT-FLAG-LEFT-BY ${info.testClass.map { it.simpleName }.orElse("?")}.${info.displayName}")
    }

    @BeforeEach
    fun clearInterruptBeforeTest(info: TestInfo) {
        if (Thread.interrupted()) System.err.println("INTERRUPT-FLAG-FOUND-BEFORE ${info.testClass.map { it.simpleName }.orElse("?")}.${info.displayName}")
    }

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
