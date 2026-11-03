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
                .acceptLicense().withReuse(true).also { it.start(); dropLeftovers(it); enableSnapshotInModel(it) }
        }

        private fun <T> withMaster(c: MSSQLServerContainer, block: (java.sql.Statement) -> T): T =
            java.sql.DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn -> conn.createStatement().use(block) }

        /**
         * `model` 데이터베이스에 `READ_COMMITTED_SNAPSHOT`을 켜 둔다. 새 데이터베이스는 `model`을 본떠 만들어지므로, 만들 때마다 설정을 바꾸는 느린 단계(약 3초)를 줄인다.
         */
        private fun enableSnapshotInModel(c: MSSQLServerContainer) = withMaster(c) { st -> st.execute("ALTER DATABASE model SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE") }

        /**
         * 이전 실행이 남긴 시험용 데이터베이스를 지운다. 실행이 끝날 때 지우지 않는 것은, 종료 중인 컨텍스트가 `create-drop`으로 테이블을 지우는 동안
         * 데이터베이스를 함께 지우면 서로 기다려 종료가 20분 넘게 걸렸기 때문이다.
         */
        private fun dropLeftovers(c: MSSQLServerContainer) = withMaster(c) { st ->
            val names = st.executeQuery("select name from sys.databases where name like 'yona_it_%'").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
            names.forEach { runCatching { st.execute("ALTER DATABASE [$it] SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE [$it]") } }
        }

        /**
         * 컨텍스트마다 새 데이터베이스를 만든다(H2의 컨텍스트별 인메모리 DB와 같은 이유). 스프링 컨텍스트가 캐시에서 밀려나 닫히면 `create-drop`이 모든 테이블을 지우므로,
         * 데이터베이스를 공유하면 살아 있는 다른 컨텍스트의 테이블까지 사라져 `Invalid object name`으로 시험이 연쇄 실패한다.
         *
         * 데이터베이스는 `READ_COMMITTED_SNAPSHOT`으로 만든다(`model`에 켜 두었다). MSSQL의 기본 격리는 읽는 쪽이 쓰는 쪽을 기다려 PostgreSQL, MariaDB와 달리 다른 트랜잭션이 쓰는 중인
         * 행을 읽으면 멈춘다(감사 로그의 `REQUIRES_NEW` 기록이 그렇다). 운영에서도 같은 설정이 필요하다(`architecture/cubrid-mssql-support.md`).
         * JDBC `lockTimeout`을 두어 그래도 생기는 대기가 멈춤 대신 실패로 드러나게 한다. 만든 데이터베이스는 다음 실행이 시작할 때 지운다.
         */
        override fun register(registry: DynamicPropertyRegistry) {
            val name = "yona_it_" + java.util.UUID.randomUUID().toString().replace("-", "")
            withMaster(container) { st ->
                st.execute("CREATE DATABASE [$name]")
            }
            registry.add("spring.datasource.url") { container.jdbcUrl + ";databaseName=$name;lockTimeout=20000" }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            // 한글이 깨지지 않게 문자열 열을 nvarchar로 만든다. v1.6의 MariaDB는 utf8mb4 문자 수 기준이라 UTF-16 단위로 세는 nvarchar가 길이 의미도 가깝다.
            registry.add("spring.jpa.properties.hibernate.use_nationalized_character_data") { "true" }
            registry.add("spring.jpa.database-platform") { "dev.yona.config.YonaSqlServerDialect" }
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
        /**
         * 테스트 트랜잭션이 커밋하지 않은 행을 다른 연결이 지우거나 바꾸려 하면 MSSQL은 그 행이 풀릴 때까지 기다린다(PostgreSQL, MariaDB, H2는 보이지 않는 행이라 기다리지 않는다).
         * 사이트 데이터 올리기는 별도 연결로 테이블을 비우므로, `@Transactional` 시험이 만든 행이 있으면 MSSQL에서는 시험이 잠금 시간 초과로 끝난다.
         * 그런 시험은 MSSQL에서 건너뛰고, 같은 코드는 커밋하는 시험(`DataExchangeTest`)이 확인한다.
         */
        fun assumeOtherConnectionsIgnoreUncommittedRows() = org.junit.jupiter.api.Assumptions.assumeFalse(current == MSSQL, "MSSQL은 커밋하지 않은 행을 다른 연결의 삭제가 기다린다")

        /** 이번 실행이 사용할 DB이다. */
        val current: TestDatabase = valueOf((System.getProperty("yona.it.db") ?: "h2").uppercase())
    }
}
