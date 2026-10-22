package dev.yona.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/**
 * 실제 DB를 쓰는 통합 테스트의 공통 부모이다.
 *
 * 모든 통합 테스트가 같은 설정을 쓰므로 스프링 컨텍스트와 DB 스키마를 한 번만 만들어 공유한다.
 * 스키마는 엔티티에서 생성하고(`create-drop`) 컨텍스트가 닫힐 때 지운다.
 */
@SpringBootTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
abstract class AbstractIntegrationTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun database(registry: DynamicPropertyRegistry) = TestDatabase.current.register(registry)
    }
}
