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
 *   드라이버의 `getGeneratedKeys`가 같은 오류로 실패하는 일이 있어 Hibernate가 `db_serial`의 현재 값을 읽게 한다).
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
    }
}
