package com.github.yonaprojects.yona

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.cubrid.CubridContainer

// MSSQLServerContainer는 자기참조 제네릭(Self-bounded generic, `MSSQLServerContainer<SELF extends
// MSSQLServerContainer<SELF>>`)이라 Kotlin에서 타입 인자 없이 바로 쓸 수 없다 — 다른 컨테이너들처럼
// 구체 타입을 직접 만들어 SELF로 고정한다.
private class KMSSQLServerContainer(imageName: String) : MSSQLServerContainer<KMSSQLServerContainer>(imageName)

/**
 * yona가 지원해야 하는 6개 DB(MariaDB/PostgreSQL/MySQL/SQL Server/CUBRID/H2)를 전부 동일한
 * 통합테스트 스위트(이 클래스를 상속하는 모든 Spec)로 검증하기 위해 컨테이너 선택을 시스템
 * 프로퍼티로 파라미터화했다. H2만 예외 — 임베디드 DB라 도커 컨테이너가 필요 없다(아래
 * registerProperties()에서 별도 분기).
 *
 * 실행: `./gradlew test -Dyona.it.db=postgres` (기본값은 mariadb). 값: mariadb|postgres|mysql|mssql|cubrid|h2
 */
@SpringBootTest
@ActiveProfiles("test")
abstract class AbstractIntegrationTest : DescribeSpec() {

    companion object {
        private const val DB_PROPERTY = "yona.it.db"
        private val selectedDb = System.getProperty(DB_PROPERTY, "mariadb")

        // h2는 Testcontainers를 쓰지 않으므로(임베디드 DB, 도커 불필요) container가 없다.
        //
        // 전 컨테이너에 .withReuse(true)를 붙여 로컬 개발 시 반복 실행 속도를 크게 개선한다 —
        // CUBRID는 특히 컨테이너 부팅(로케일 설정 등) 자체가 느려 매번 새로 띄우면 실행마다
        // 수 분이 그냥 낭비된다. reuse는 Testcontainers 전역 스위치
        // (~/.testcontainers.properties의 testcontainers.reuse.enable=true)가 켜져 있어야만
        // 실제로 동작하는 opt-in 메커니즘이라, 그 파일이 없는 CI 환경에서는 이 플래그가 있어도
        // 자동으로 무시되고 기존과 동일하게 매번 새 컨테이너를 띄운다(안전) — 로컬 전용 최적화다.
        // 단, 테스트 프로파일의 ddl-auto=create-drop은 그대로 유지되므로(스키마 정합성 검증이
        // CI에서 여전히 필요) 컨테이너 자체의 기동 비용만 절약되고, 매 실행마다 스키마를 새로
        // 만드는 비용은 재사용 여부와 무관하게 남는다.
        //
        // 주의: 컨테이너가 재사용되면 이전 실행이 남긴 데이터가 계속 쌓인다 — 각 Spec의
        // beforeEach/afterSpec에서 deleteAll()로 정리하는 기존 관례를 지키지 않은 스펙이 있으면
        // 로컬에서만 재현되는 새로운 실패가 날 수 있다.
        private val container: JdbcDatabaseContainer<*>? = when (selectedDb) {
            "h2" -> null
            "postgres" -> PostgreSQLContainer("postgres:16")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
                .withReuse(true)
            "mysql" -> MySQLContainer("mysql:8.4")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
                .withReuse(true)
            // SQL Server 공식 이미지는 고정 계정(sa)/DB(master)만 지원해 withDatabaseName이 없다.
            // sendStringParametersAsUnicode=true가 없으면(mssql-jdbc 기본값은 false) 문자열
            // 파라미터가 클라이언트 쪽에서 비유니코드로 좁혀져 전송돼, varchar 컬럼과 LIKE 비교 시
            // 한글 등 비ASCII 문자가 매치되지 않는다(데이터 자체는 정상 저장/조회되는데도
            // LIKE만 0건으로 실패 — 실측 확인).
            "mssql" -> KMSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .acceptLicense()
                .withUrlParam("sendStringParametersAsUnicode", "true")
                .withReuse(true)
            // CUBRID 공식 Testcontainers 모듈(org.cubrid:testcontainers-cubrid, CUBRID사 직접 관리).
            // 공식 도커 이미지(cubrid/cubrid-docker)의 CUBRID_LOCALE 기본값이 "en_US"(UTF-8이
            // 아님)라 한글 등 비ASCII 문자열이 깨져서 저장된다(실측 확인 — "홍길동"이 다른
            // 인코딩으로 잘못 해석된 바이트로 저장됨). DB 생성 시점 로케일을 UTF-8로 지정해야
            // 하고, 클라이언트 쪽도 JDBC URL의 charSet=utf-8로 맞춰줘야 한다.
            "cubrid" -> CubridContainer("cubrid/cubrid:11.4")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
                .withUrlParam("charSet", "utf-8")
                .withEnv("CUBRID_LOCALE", "en_US.utf8")
                .withReuse(true)
            else -> MariaDBContainer("mariadb:10.11")
                .withDatabaseName("yona")
                .withUsername("yona")
                .withPassword("yona_password")
                .withReuse(true)
        }

        init {
            container?.start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            if (selectedDb == "h2") {
                // 임베디드 인메모리 DB — 테스트 프로세스마다(JVM 기동 시점 nanoTime) 별도
                // 스키마를 써서 병렬/반복 실행 시 이전 실행 데이터와 절대 섞이지 않게 한다.
                // DB_CLOSE_DELAY=-1은 마지막 커넥션이 끊겨도(테스트 사이 커넥션 풀이 비는
                // 순간) DB를 닫지 않고 JVM 종료까지 유지한다 — 안 그러면 같은 스펙 안에서도
                // 커넥션이 비는 순간 스키마가 통째로 사라질 수 있다.
                registry.add("spring.datasource.url") { "jdbc:h2:mem:yona-it-${System.nanoTime()};DB_CLOSE_DELAY=-1" }
                registry.add("spring.datasource.username") { "sa" }
                registry.add("spring.datasource.password") { "" }
                registry.add("spring.datasource.driver-class-name") { "org.h2.Driver" }
                registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.H2Dialect" }
                return
            }

            val jdbcContainer = container!!
            registry.add("spring.datasource.url") { jdbcContainer.jdbcUrl }
            registry.add("spring.datasource.username") { jdbcContainer.username }
            registry.add("spring.datasource.password") { jdbcContainer.password }
            registry.add("spring.datasource.driver-class-name") { jdbcContainer.driverClassName }
            // CUBRIDDialect는 hibernate-core가 아니라 hibernate-community-dialects에 있어
            // Hibernate가 JDBC 메타데이터만으로 자동 인식하지 못한다 — 명시적으로 지정해야 한다.
            if (selectedDb == "cubrid") {
                // CUBRIDDialect의 BOOLEAN→bit 매핑/타임존 지원이 CUBRID JDBC 드라이버 결함과
                // 부딪히는 문제를 우회하는 커스텀 방언 — config/YonaCubridDialect.kt 주석 참고.
                registry.add("spring.jpa.database-platform") { "com.github.yonaprojects.yona.config.YonaCubridDialect" }
                // CUBRID는 role 등 다른 DB에서는 평범한 테이블/컬럼명을 예약어로 취급해 DDL이
                // 깨진다. globally_quoted_identifiers는 기본 물리 네이밍 전략(snake_case 변환)
                // 자체를 무력화하는 부작용이 있어(실측 확인) 쓰지 않고, 예약어와 겹칠 때만
                // 개별적으로 인용하는 전용 네이밍 전략을 쓴다 — config/YonaCubridNamingStrategy.kt.
                registry.add("spring.jpa.hibernate.naming.physical-strategy") { "com.github.yonaprojects.yona.config.YonaCubridNamingStrategy" }
                // CUBRID 브로커가 유휴/오래된 커넥션을 서버 쪽에서 먼저 끊는 경우가 있는데,
                // HikariCP가 이를 감지 못하고 죽은 커넥션을 내주면 드라이버 내부에서 NPE(this.con
                // is null)가 난다 — 대규모 스위트를 통으로 돌릴 때만 재현되는 걸 실측으로 확인했다
                // (개별 실행 시엔 통과). 커넥션을 실제 사용 전에 검증하도록 강제해 걸러낸다.
                registry.add("spring.datasource.hikari.connection-test-query") { "SELECT 1" }
                registry.add("spring.datasource.hikari.max-lifetime") { "300000" }
                // 커넥션 검증으로도 못 잡는다 — JDBC getGeneratedKeys() 기반 ID 회수
                // (GetGeneratedKeysDelegate)가 대규모 스위트에서만 CUBRID 드라이버 내부 NPE로
                // 죽는 걸 실측으로 확인했다. 방언이 제공하는 identity-select 방식(예: CUBRID의
                // LAST_INSERT_ID() 계열)으로 우회한다.
                registry.add("spring.jpa.properties.hibernate.jdbc.use_get_generated_keys") { "false" }
            }
            if (selectedDb == "mssql") {
                // Hibernate가 String을 기본적으로 varchar(비유니코드, SQL Server 기본 코드페이지)로
                // 매핑해 한글 등 비ASCII 문자가 INSERT 시점에 '?'로 뭉개진다(LIKE 검색에서만
                // 0건으로 드러남 — 같은 트랜잭션 안에서는 Hibernate 1차 캐시가 방금 저장한
                // 자바 객체를 그대로 돌려줘서 findById/findAll로는 이 손실이 감춰진다). 모든
                // String을 nvarchar(유니코드)로 매핑하도록 강제한다.
                registry.add("spring.jpa.properties.hibernate.use_nationalized_character_data") { "true" }
            }
        }
    }
}
