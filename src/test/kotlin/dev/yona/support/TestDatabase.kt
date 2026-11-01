package dev.yona.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 통합 테스트가 사용할 DB이다. `-Dyona.it.db=h2|postgres|mariadb`로 고르며 기본값은 H2이다.
 *
 * PostgreSQL과 MariaDB는 Testcontainers로 띄우고, `reuse`를 켜서 테스트가 끝나도 컨테이너를 내리지
 * 않는다(`~/.testcontainers.properties`에 `testcontainers.reuse.enable=true`가 필요하다).
 * 스키마는 테스트 컨텍스트가 시작될 때 만들고 끝날 때 지우므로 컨테이너를 오래 두어도 데이터가 쌓이지 않는다.
 */
enum class TestDatabase {
    H2 {
        override fun register(registry: DynamicPropertyRegistry) {
            // 컨텍스트마다 고유한 인메모리 DB를 쓴다. 같은 이름을 공유하면 닫히는 컨텍스트(캐시에서 밀려나거나 @DirtiesContext)의 `create-drop`이 살아 있는
            // 다른 컨텍스트의 테이블까지 지워 "테이블 없음"으로 시험이 깨진다. 이름은 컨텍스트를 만들 때 한 번 정해 같은 컨텍스트 안에서는 바뀌지 않게 한다.
            val url = "jdbc:h2:mem:yona-it-${java.util.UUID.randomUUID()};DB_CLOSE_DELAY=-1"
            registry.add("spring.datasource.url") { url }
            registry.add("spring.datasource.username") { "sa" }
            registry.add("spring.datasource.password") { "" }
        }
    },
    POSTGRES {
        private val container by lazy {
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("yona_v2_it").withUsername("yona").withPassword("yona")
                .withReuse(true).also { it.start() }
        }

        override fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    },
    MARIADB {
        private val container by lazy {
            MariaDBContainer("mariadb:10.11")
                .withDatabaseName("yona_v2_it").withUsername("yona").withPassword("yona")
                .withReuse(true).also { it.start() }
        }

        override fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    };

    /** 데이터 소스 연결 정보를 스프링 속성에 등록한다. */
    abstract fun register(registry: DynamicPropertyRegistry)

    companion object {
        /** 이번 실행이 사용할 DB이다. */
        val current: TestDatabase = valueOf((System.getProperty("yona.it.db") ?: "h2").uppercase())
    }
}
