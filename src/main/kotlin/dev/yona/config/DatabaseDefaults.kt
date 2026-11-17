package dev.yona.config

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * 접속 주소(`spring.datasource.url`)로 MSSQL과 Cubrid에 필요한 설정을 기본값으로 정한다(X-46, `decisions.md` D-85).
 *
 * - `jdbc:sqlserver:`: 방언 [YonaSqlServerDialect]와 `hibernate.use_nationalized_character_data=true`(한글이 깨지지 않게 `nvarchar`로 만든다).
 * - `jdbc:cubrid:`: JDBC 드라이버 클래스(Spring Boot가 주소만으로는 알지 못한다), 방언 [YonaCubridDialect], `hibernate.auto_quote_keyword=true`
 *   (예약어 `size` 같은 열 이름을 따옴표로 감싼다), 연결 검사 질의 `select 1 from db_root`(Cubrid 드라이버의 `isValid(0)`이 거짓을 돌려주어 건강 검사가 DOWN이 된다. 건강 검사는 이 질의를 쓴다),
 *   연결 수명 5분(브로커가 오래된 연결을 먼저 끊으면 풀이 이를 모르고 내주어 드라이버가 `this.con is null`로 실패한다), 생성 키 조회 끄기(`hibernate.jdbc.use_get_generated_keys=false`,
 *   드라이버의 `getGeneratedKeys`가 같은 오류로 실패하는 일이 있어 Hibernate가 `db_serial`의 현재 값을 읽게 한다), 준비된 문장 캐시(접속 주소에 `usePreparedStmtCache=true`,
 *   `preparedStmtCacheSize=500`, `preparedStmtCacheSqlLimit=16384`를 더한다. 드라이버의 기본값은 꺼짐, 25개, SQL 256자까지라 하이버네이트의 긴 SQL이 모두 빠진다. Cubrid는
 *   준비(prepare) 한 번이 브로커를 거치는 왕복이라 같은 문장을 다시 준비하는 비용이 크다. 켜면 이슈 단건 조회 동시 16 처리량이 약 4배, 쓰기가 1.7~2.4배가 되었다. A-629).
 *   접속 주소에 `usePreparedStmtCache`를 직접 적었으면 그대로 둔다.
 *
 * 가장 낮은 우선순위로 더하므로 `application.yml`이나 환경 변수로 직접 정한 값이 우선한다. 다른 데이터베이스에는 아무것도 하지 않는다.
 */
class DatabaseDefaults : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val url = environment.getProperty("spring.datasource.url")?.trim()?.lowercase() ?: return
        val defaults = when {
            url.startsWith("jdbc:sqlserver:") -> mapOf(
                "spring.jpa.database-platform" to "dev.yona.config.YonaSqlServerDialect",
                "spring.jpa.properties.hibernate.use_nationalized_character_data" to "true"
            )
            url.startsWith("jdbc:cubrid:") -> mapOf(
                "spring.datasource.driver-class-name" to "cubrid.jdbc.driver.CUBRIDDriver",
                "spring.jpa.database-platform" to "dev.yona.config.YonaCubridDialect",
                "spring.jpa.properties.hibernate.auto_quote_keyword" to "true",
                "spring.datasource.hikari.connection-test-query" to "select 1 from db_root",
                "spring.datasource.hikari.max-lifetime" to "300000",
                "spring.jpa.properties.hibernate.jdbc.use_get_generated_keys" to "false"
            )
            else -> return
        }
        environment.propertySources.addLast(MapPropertySource("yona-database-defaults", defaults))
        // 접속 주소는 기본값(가장 낮은 우선순위)으로 더할 수 없다. 이미 있는 값을 바꾸는 것이므로 맨 앞에 두되, 직접 정한 옵션이 있으면 건드리지 않는다
        if (url.startsWith("jdbc:cubrid:") && !url.contains("usepreparedstmtcache")) {
            val original = environment.getProperty("spring.datasource.url")!!.trim()
            val separator = if (original.contains('?')) "&" else "?"
            environment.propertySources.addFirst(MapPropertySource("yona-database-url", mapOf("spring.datasource.url" to original + separator + CUBRID_STATEMENT_CACHE)))
        }
    }

    companion object {
        /**
         * Cubrid JDBC의 준비된 문장 캐시 옵션이다(연결마다 500개, SQL 16,384자까지). 브로커의 `MAX_PREPARED_STMT_COUNT` 기본값(2,000)보다 작아야 한다.
         * 시험의 접속 주소(`TestDatabase`)도 같은 값을 쓴다. 시험의 동적 속성이 이 후처리기가 더한 주소를 덮기 때문이다.
         */
        const val CUBRID_STATEMENT_CACHE = "usePreparedStmtCache=true&preparedStmtCacheSize=500&preparedStmtCacheSqlLimit=16384"
    }
}
