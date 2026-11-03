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
 *   (예약어 `size` 같은 열 이름을 따옴표로 감싼다).
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
                "spring.jpa.properties.hibernate.auto_quote_keyword" to "true"
            )
            else -> return
        }
        environment.propertySources.addLast(MapPropertySource("yona-database-defaults", defaults))
    }
}
