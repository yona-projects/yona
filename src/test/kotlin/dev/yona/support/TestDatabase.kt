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

        /** 이번 실행이 만든 시험용 데이터베이스 이름이다. */
        private val created = java.util.concurrent.CopyOnWriteArrayList<String>()

        /**
         * 실행이 끝날 때 만든 데이터베이스를 한 스레드에서 차례로 지운다(데이터베이스마다 약 35MB라 100개가 넘으면 디스크를 4GB 가까이 쓴다).
         * 컨텍스트마다 훅을 두면 종료 중인 컨텍스트의 `create-drop`과 서로 기다려 20분 넘게 걸렸으므로, 훅은 하나이고 `OFFLINE`으로 연결을 끊은 뒤 지운다.
         */
        private val cleanup: Unit by lazy {
            Runtime.getRuntime().addShutdownHook(Thread {
                runCatching { withMaster(container) { st -> created.forEach { runCatching { st.execute("ALTER DATABASE [$it] SET OFFLINE WITH ROLLBACK IMMEDIATE"); st.execute("DROP DATABASE [$it]") } } } }
            })
        }

        /** 이전 실행이 비정상 종료로 남긴 시험용 데이터베이스를 지운다. */
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
            created += name
            cleanup
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
                // 데이터베이스를 UTF-8로 만든다(이미지의 기본값 `en_US`는 ISO-8859-1이라 한글이 깨진다)
                .withEnv("CUBRID_LOCALE", "en_US.utf8")
                .withDatabaseName("yona_v2_it").withUsername("yona").withPassword("yona")
                .withReuse(true).also { it.start(); raiseBrokerLimit(it); dropLeftoverUsers(it) }
        }

        /**
         * 브로커의 동시 연결 한도(`MAX_NUM_APPL_SERVER`, 이미지 기본값 40)를 올린다. 시험은 스프링 컨텍스트를 수십 개 캐시하고 컨텍스트마다 연결 풀을 두므로
         * 기본값으로는 연결이 거부되어 컨텍스트 로딩이 연쇄로 실패한다. 이미 올렸으면 아무것도 하지 않는다. 운영에서도 사용자 수에 맞춰 올려야 한다.
         */
        private fun raiseBrokerLimit(c: CubridContainer) {
            val conf = "/home/cubrid/CUBRID/conf/cubrid_broker.conf"
            val result = c.execInContainer("bash", "-c",
                "grep -q 'MAX_NUM_APPL_SERVER *=300' $conf || (gosu cubrid sed -i 's/^MAX_NUM_APPL_SERVER .*/MAX_NUM_APPL_SERVER     =300/' $conf && " +
                    "gosu cubrid /home/cubrid/CUBRID/bin/cubrid broker restart > /dev/null 2>&1 < /dev/null)")
            check(result.exitCode == 0) { "Cubrid 브로커 설정을 바꾸지 못했습니다: ${result.stderr}" }
        }

        /** 관리자(`dba`, 비밀번호 없음)로 연결한다. */
        private fun <T> asDba(c: CubridContainer, block: (java.sql.Statement) -> T): T =
            java.sql.DriverManager.getConnection("jdbc:cubrid:${c.host}:${c.getMappedPort(33000)}:${c.databaseName}:dba::?charset=utf-8", "dba", "").use { conn -> conn.createStatement().use(block) }

        /**
         * 이전 실행이 남긴 시험용 사용자와 그 테이블을 지운다. 실행이 중간에 끊기면 컨텍스트가 `create-drop`으로 테이블을 지우지 못하고, 사용자가 수백 명 쌓이면
         * Cubrid의 카탈로그가 커져 메타데이터 조회가 수십 분씩 걸린다.
         */
        private fun dropLeftoverUsers(c: CubridContainer) = asDba(c) { st ->
            val owners = st.executeQuery("select distinct owner_name from db_class where owner_name like 'IT\\_%' escape '\\'").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
            owners.forEach { owner ->
                val tables = st.executeQuery("select class_name from db_class where owner_name = '$owner' and class_type = 'CLASS'").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
                tables.forEach { runCatching { st.execute("drop table ${owner.lowercase()}.[$it] cascade constraints") } }
            }
            val users = st.executeQuery("select name from db_user where name like 'IT\\_%' escape '\\'").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() }
            users.forEach { runCatching { st.execute("drop user ${it.lowercase()}") } }
        }

        /**
         * 컨텍스트마다 새 사용자를 만든다. Cubrid는 사용자가 곧 스키마라서, 컨텍스트가 `create-drop`으로 테이블을 지워도 다른 컨텍스트의 테이블은 영향을 받지 않는다
         * (H2의 컨텍스트별 인메모리 DB, MSSQL의 컨텍스트별 데이터베이스와 같은 이유).
         */
        override fun register(registry: DynamicPropertyRegistry) {
            val user = "it_" + java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            asDba(container) { st -> st.execute("create user $user password 'yona' groups dba") }
            // Cubrid 드라이버는 `charset`을 주지 않으면 한글을 ISO-8859-1로 읽는다.
            registry.add("spring.datasource.url") { container.jdbcUrl + "?charset=utf-8" }
            registry.add("spring.datasource.username") { user }
            registry.add("spring.datasource.password") { "yona" }
            registry.add("spring.datasource.driver-class-name") { container.driverClassName }
            registry.add("spring.jpa.database-platform") { "dev.yona.config.YonaCubridDialect" }
            registry.add("spring.jpa.properties.hibernate.auto_quote_keyword") { "true" }
            registry.add("spring.datasource.hikari.connection-test-query") { "select 1 from db_root" }
        }
    };

    /** 데이터 소스 연결 정보를 스프링 속성에 등록한다. */
    abstract fun register(registry: DynamicPropertyRegistry)

    companion object {
        /**
         * Cubrid는 외래 키 검사를 끄는 방법이 없어, 사이트 데이터 가져오기가 외래 키를 떼었다 다시 붙이면서 부모가 없는 행(고아 행)을 정리한다
         * (`DataService.reattach`). 고아 행을 그대로 두는 v1.6 동작을 확인하는 시험은 Cubrid에서 건너뛴다. 고아 행이 없는 데이터를 가져오는 시험은 그대로 돈다.
         */
        fun assumeOrphanRowsSurviveImport() = org.junit.jupiter.api.Assumptions.assumeFalse(current == CUBRID, "Cubrid는 가져오기 뒤 고아 행을 정리한다")

        /**
         * 직접 쓴 SQL에서 쓰는 식별자를 이 데이터베이스에 맞게 감싼다. Cubrid는 `role`, `size`, `action` 같은 예약어를 대괄호로 감싸야 한다.
         * 엔티티로 만든 SQL은 `hibernate.auto_quote_keyword`가 감싸므로 이 함수가 필요 없다.
         */
        fun q(name: String) = if (current == CUBRID) "[$name]" else name

        /**
         * 테스트 트랜잭션이 커밋하지 않은 행을 다른 연결이 지우거나 바꾸려 하면 MSSQL은 그 행이 풀릴 때까지 기다리고, Cubrid는 외래 키를 떼려는 스키마 변경이 그 테이블의 잠금을 기다린다(PostgreSQL, MariaDB, H2는 보이지 않는 행이라 기다리지 않는다).
         * 사이트 데이터 올리기는 별도 연결로 테이블을 비우므로, `@Transactional` 시험이 만든 행이 있으면 MSSQL에서는 시험이 잠금 시간 초과로 끝난다.
         * 그런 시험은 MSSQL에서 건너뛰고, 같은 코드는 커밋하는 시험(`DataExchangeTest`)이 확인한다.
         */
        fun assumeOtherConnectionsIgnoreUncommittedRows() = org.junit.jupiter.api.Assumptions.assumeFalse(current == MSSQL || current == CUBRID, "MSSQL과 Cubrid는 커밋하지 않은 행을 다른 연결의 삭제나 스키마 변경이 기다린다")

        /** 이번 실행이 사용할 DB이다. */
        val current: TestDatabase = valueOf((System.getProperty("yona.it.db") ?: "h2").uppercase())
    }
}
