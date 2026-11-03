package dev.yona.config

import org.junit.jupiter.api.Test
import org.springframework.boot.SpringApplication
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 접속 주소로 MSSQL과 Cubrid의 드라이버, 방언, 문자열 설정을 정하는 기본값(X-46, D-85)을 확인한다. */
class DatabaseDefaultsTest {

    private fun apply(env: MockEnvironment): MockEnvironment = env.also { DatabaseDefaults().postProcessEnvironment(it, SpringApplication()) }

    @Test
    fun `MSSQL 주소면 유니코드 문자열과 방언을 정한다`() {
        val env = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://db:1433;databaseName=yona"))
        assertEquals("dev.yona.config.YonaSqlServerDialect", env.getProperty("spring.jpa.database-platform"))
        assertEquals("true", env.getProperty("spring.jpa.properties.hibernate.use_nationalized_character_data"))
        assertNull(env.getProperty("spring.jpa.properties.hibernate.auto_quote_keyword"))
    }

    @Test
    fun `Cubrid 주소면 드라이버와 방언과 예약어 따옴표를 정한다`() {
        val env = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:cubrid:db:33000:yona:::"))
        assertEquals("cubrid.jdbc.driver.CUBRIDDriver", env.getProperty("spring.datasource.driver-class-name"))
        assertEquals("dev.yona.config.YonaCubridDialect", env.getProperty("spring.jpa.database-platform"))
        assertEquals("true", env.getProperty("spring.jpa.properties.hibernate.auto_quote_keyword"))
    }

    @Test
    fun `직접 정한 설정이 기본값보다 우선하고 다른 데이터베이스는 건드리지 않는다`() {
        val own = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://db").withProperty("spring.jpa.database-platform", "my.Dialect"))
        assertEquals("my.Dialect", own.getProperty("spring.jpa.database-platform"))
        val pg = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/yona"))
        assertNull(pg.getProperty("spring.jpa.database-platform"))
        assertNull(apply(MockEnvironment()).getProperty("spring.jpa.database-platform"))
    }
}
