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
        assertEquals("select 1 from db_root", env.getProperty("spring.datasource.hikari.connection-test-query"))
    }

    @Test
    fun `Cubrid 주소면 생성 키 조회를 끄고 연결 수명을 브로커 제한보다 짧게 정한다`() {
        val env = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:cubrid:db:33000:yona:::"))
        assertEquals("false", env.getProperty("spring.jpa.properties.hibernate.jdbc.use_get_generated_keys"))
        assertEquals("300000", env.getProperty("spring.datasource.hikari.max-lifetime"))
        val sqlServer = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://db:1433"))
        assertNull(sqlServer.getProperty("spring.jpa.properties.hibernate.jdbc.use_get_generated_keys"))
        assertNull(sqlServer.getProperty("spring.datasource.hikari.max-lifetime"))
    }

    @Test
    fun `직접 정한 설정이 기본값보다 우선하고 다른 데이터베이스는 건드리지 않는다`() {
        val own = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:sqlserver://db").withProperty("spring.jpa.database-platform", "my.Dialect"))
        assertEquals("my.Dialect", own.getProperty("spring.jpa.database-platform"))
        val pg = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://db/yona"))
        assertNull(pg.getProperty("spring.jpa.database-platform"))
        assertNull(apply(MockEnvironment()).getProperty("spring.jpa.database-platform"))
    }

    @Test
    fun `Cubrid 주소에는 준비된 문장 캐시 옵션을 더한다`() {
        val plain = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:cubrid:db:33000:yona:::"))
        assertEquals(
            "jdbc:cubrid:db:33000:yona:::?usePreparedStmtCache=true&preparedStmtCacheSize=500&preparedStmtCacheSqlLimit=16384",
            plain.getProperty("spring.datasource.url"), "물음표가 없으면 새로 붙인다"
        )
        val withCharset = apply(MockEnvironment().withProperty("spring.datasource.url", "jdbc:cubrid:db:33000:yona:dba::?charset=utf-8"))
        assertEquals(
            "jdbc:cubrid:db:33000:yona:dba::?charset=utf-8&usePreparedStmtCache=true&preparedStmtCacheSize=500&preparedStmtCacheSqlLimit=16384",
            withCharset.getProperty("spring.datasource.url"), "이미 옵션이 있으면 앰퍼샌드로 잇는다"
        )
    }

    @Test
    fun `Cubrid 주소에 문장 캐시를 직접 정했으면 그대로 둔다`() {
        val off = "jdbc:cubrid:db:33000:yona:::?charset=utf-8&usePreparedStmtCache=false"
        assertEquals(off, apply(MockEnvironment().withProperty("spring.datasource.url", off)).getProperty("spring.datasource.url"))
        val sized = "jdbc:cubrid:db:33000:yona:::?USEPREPAREDSTMTCACHE=true&preparedStmtCacheSize=50"
        assertEquals(sized, apply(MockEnvironment().withProperty("spring.datasource.url", sized)).getProperty("spring.datasource.url"), "대소문자를 구별하지 않는다")
    }

    @Test
    fun `다른 데이터베이스의 주소는 바꾸지 않는다`() {
        for (url in listOf("jdbc:postgresql://db/yona", "jdbc:mariadb://db/yona", "jdbc:sqlserver://db:1433;databaseName=yona", "jdbc:h2:mem:yona")) {
            assertEquals(url, apply(MockEnvironment().withProperty("spring.datasource.url", url)).getProperty("spring.datasource.url"), url)
        }
    }
}
