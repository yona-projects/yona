package com.github.yonaprojects.yona

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.core.env.MapPropertySource
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.cubrid.CubridContainer
import java.nio.file.Files
import java.util.UUID

private class KMSSQLServerContainer(imageName: String) : MSSQLServerContainer<KMSSQLServerContainer>(imageName)

class IntegrationTestEnvironmentInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment
        val dataRoot = environment.getProperty("yona.data")
            ?: Files.createTempDirectory("yona-it-").toString()
        val properties = linkedMapOf<String, Any>(
            "yona.data" to dataRoot,
            "yona.git.base-dir" to "$dataRoot/git",
            "yona.svn.base-dir" to "$dataRoot/svn",
            "yona.hg.base-dir" to "$dataRoot/hg",
            "yona.lfs.base-dir" to "$dataRoot/lfs"
        )
        if (environment.getProperty("yona.it.db", "mariadb") == "cubrid") {
            properties["spring.datasource.hikari.connection-test-query"] = "SELECT 1"
            properties["spring.datasource.hikari.max-lifetime"] = "300000"
        }
        // Available before servlet initialization; explicit test paths retain higher precedence.
        environment.propertySources.addLast(MapPropertySource("integrationTestDefaults", properties))
    }
}

/**
 * Spring Boot starts container beans before their clients and stops them after JPA's create-drop
 * and datasource shutdown. Each cached context owns its database, so closing one cannot drop
 * another context's schema. Container reuse is deliberately disabled for that same reason.
 */
@TestConfiguration(proxyBeanMethods = false)
class IntegrationTestDatabaseConfiguration(
    @Value("\${yona.it.db:mariadb}") private val selectedDb: String
) {
    @Bean
    @ConditionalOnExpression("'\${yona.it.db:mariadb}' != 'h2'")
    fun databaseContainer(): JdbcDatabaseContainer<*> = when (selectedDb) {
        "postgres" -> PostgreSQLContainer("postgres:16")
            .withDatabaseName("yona")
            .withUsername("yona")
            .withPassword("yona_password")
        "mysql" -> MySQLContainer("mysql:8.4")
            .withDatabaseName("yona")
            .withUsername("yona")
            .withPassword("yona_password")
        // SQL Server uses sa/master; Unicode parameters must survive LIKE comparisons too.
        "mssql" -> KMSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withUrlParam("sendStringParametersAsUnicode", "true")
        // Both the database locale and JDBC character set must be UTF-8 for Korean text.
        "cubrid" -> CubridContainer("cubrid/cubrid:11.4")
            .withDatabaseName("yona")
            .withUsername("yona")
            .withPassword("yona_password")
            .withUrlParam("charSet", "utf-8")
            .withEnv("CUBRID_LOCALE", "en_US.utf8")
        else -> MariaDBContainer("mariadb:10.11")
            .withDatabaseName("yona")
            .withUsername("yona")
            .withPassword("yona_password")
    }

    @Bean
    fun jdbcConnectionDetails(containers: ObjectProvider<JdbcDatabaseContainer<*>>): JdbcConnectionDetails {
        val container = containers.ifAvailable
        // Resolving the managed bean starts it before datasource creation, including RANDOM_PORT.
        val url = container?.jdbcUrl ?: "jdbc:h2:mem:yona-it-${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
        return object : JdbcConnectionDetails {
            override fun getJdbcUrl() = url
            override fun getUsername() = container?.username ?: "sa"
            override fun getPassword() = container?.password ?: ""
            override fun getDriverClassName() = container?.driverClassName ?: "org.h2.Driver"
        }
    }

    @Bean
    fun databaseHibernateProperties() = HibernatePropertiesCustomizer { properties ->
        when (selectedDb) {
            "h2" -> properties["hibernate.dialect"] = "org.hibernate.dialect.H2Dialect"
            "cubrid" -> {
                // Avoid BOOLEAN/timezone and generated-key driver defects; quote only reserved words.
                properties["hibernate.dialect"] = "com.github.yonaprojects.yona.config.YonaCubridDialect"
                properties["hibernate.physical_naming_strategy"] = "com.github.yonaprojects.yona.config.YonaCubridNamingStrategy"
                properties["hibernate.jdbc.use_get_generated_keys"] = false
            }
            // Persist Strings as nvarchar, not the server's non-Unicode code page.
            "mssql" -> properties["hibernate.use_nationalized_character_data"] = true
        }
    }
}
