package dev.yona.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.cubrid.CubridContainer
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.mssqlserver.MSSQLServerContainer
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * 통합 테스트가 사용할 DB이다. `-Dyona.it.db=h2|postgres|mariadb|mssql|cubrid`로 고르며 기본값은 H2이다.
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
    },
    MSSQL {
        private val container by lazy {
            MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense().withReuse(true).also { it.start(); prepare(it) }
        }

        /**
         * 시험용 데이터베이스를 `READ_COMMITTED_SNAPSHOT`으로 만든다. MSSQL의 기본 격리는 읽는 쪽이 쓰는 쪽을 기다려
         * PostgreSQL, MariaDB와 달리 다른 트랜잭션이 쓰는 중인 행을 읽으면 멈춘다(감사 로그의 `REQUIRES_NEW` 기록이 그렇다).
         * 운영에서도 같은 설정이 필요하다(`architecture/cubrid-mssql-support.md`).
         */
        private fun prepare(c: MSSQLServerContainer) {
            java.sql.DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
                conn.createStatement().use { st ->
                    st.execute("IF DB_ID('yona_v2_it') IS NULL CREATE DATABASE yona_v2_it")
                    st.execute("ALTER DATABASE yona_v2_it SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE")
                }
            }
        }

        override fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl + ";databaseName=yona_v2_it;lockTimeout=20000" }
            // 한글이 깨지지 않게 문자열 열을 nvarchar로 만든다. v1.6의 MariaDB는 utf8mb4 문자 수 기준이라 UTF-16 단위로 세는 nvarchar가 길이 의미도 가깝다.
            registry.add("spring.jpa.properties.hibernate.use_nationalized_character_data") { "true" }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
        }
    },
    CUBRID {
        private val container by lazy {
            CubridContainer("cubrid/cubrid:11.4")
                .withDatabaseName("yona_v2_it").withUsername("yona").withPassword("yona")
                .withReuse(true).also { it.start() }
        }

        override fun register(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            registry.add("spring.datasource.driver-class-name") { container.driverClassName }
            registry.add("spring.jpa.database-platform") { "dev.yona.config.YonaCubridDialect" }
            registry.add("spring.jpa.properties.hibernate.auto_quote_keyword") { "true" }
        }
    };

    /** 데이터 소스 연결 정보를 스프링 속성에 등록한다. */
    abstract fun register(registry: DynamicPropertyRegistry)

    companion object {
        /** 이번 실행이 사용할 DB이다. */
        val current: TestDatabase = valueOf((System.getProperty("yona.it.db") ?: "h2").uppercase())
    }
}
