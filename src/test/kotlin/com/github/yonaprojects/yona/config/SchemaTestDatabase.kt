package com.github.yonaprojects.yona.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MSSQLServerContainer
import org.testcontainers.containers.MariaDBContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.cubrid.CubridContainer
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

private class SchemaSqlServer : MSSQLServerContainer<SchemaSqlServer>("mcr.microsoft.com/mssql/server:2022-latest")

/** Isolated schema/upgrade database. Never points at the application's configured database. */
internal object SchemaTestDatabase {
    val kind = System.getProperty("yona.it.db", "mariadb")
    private val h2File = if (kind == "h2") {
        System.getProperty("yona.queue.acceptance.h2-file")?.let { Path.of(it) }
            ?: Files.createTempDirectory("yona-schema-acceptance-").also { directory ->
                // Keep close/reopen data until JVM exit; never delete a caller-supplied database.
                directory.toFile().deleteOnExit()
                for (suffix in listOf(".mv.db", ".trace.db", ".lock.db")) {
                    directory.resolve("database$suffix").toFile().deleteOnExit()
                }
            }.resolve("database")
    } else null
    private val container: JdbcDatabaseContainer<*>? = when (kind) {
        "h2" -> null
        "mariadb" -> MariaDBContainer("mariadb:10.11")
        "postgres" -> PostgreSQLContainer("postgres:16")
        "mysql" -> MySQLContainer("mysql:8.4")
        "mssql" -> SchemaSqlServer().acceptLicense().withUrlParam("sendStringParametersAsUnicode", "true")
        "cubrid" -> CubridContainer("cubrid/cubrid:11.4")
            .withEnv("CUBRID_LOCALE", "en_US.utf8").withUrlParam("charSet", "utf-8")
        else -> error("Unknown acceptance database: $kind")
    }

    init { container?.start() }

    fun dataSource() = HikariDataSource().apply {
        // Existing Property.value requires this H2 compatibility option; no schema errors are suppressed.
        jdbcUrl = container?.jdbcUrl ?: "jdbc:h2:file:$h2File;NON_KEYWORDS=VALUE"
        username = container?.username ?: "sa"
        password = container?.password ?: ""
        driverClassName = container?.driverClassName ?: "org.h2.Driver"
        maximumPoolSize = 4
        minimumIdle = 0
        connectionTimeout = 10_000
        if (kind == "cubrid") connectionTestQuery = "SELECT 1"
    }

    fun factory(dataSource: DataSource, vararg packages: String) = LocalContainerEntityManagerFactoryBean().apply {
        setDataSource(dataSource)
        setJpaVendorAdapter(HibernateJpaVendorAdapter())
        setPackagesToScan(*packages)
        val properties = mutableMapOf<String, Any>(
            "hibernate.hbm2ddl.auto" to "update",
            "hibernate.hbm2ddl.halt_on_error" to true,
            "hibernate.jdbc.time_zone" to "UTC",
            "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl",
        )
        if (kind == "cubrid") {
            properties["hibernate.dialect"] = "com.github.yonaprojects.yona.config.YonaCubridDialect"
            properties["hibernate.physical_naming_strategy"] = "com.github.yonaprojects.yona.config.YonaCubridNamingStrategy"
            properties["hibernate.jdbc.use_get_generated_keys"] = false
            if ("com.github.yonaprojects.yona.domain" in packages) {
                CubridSchemaGuard(dataSource).customize(properties)
                setMappingResources("META-INF/orm-cubrid.xml")
            }
        }
        if (kind == "mssql") properties["hibernate.use_nationalized_character_data"] = true
        setJpaPropertyMap(properties)
    }
}
