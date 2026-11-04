package dev.yona.config

import dev.yona.schema.SchemaMetadata.schemaOrNull
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 범위 목록 열을 넓히는 보정(X-3, `decisions.md` D-66 구현 판단 A-323)을 검증한다. 토큰 범위가 늘어 전체 범위 문자열이 255자를 넘으므로, Hibernate의
 * `ddl-auto: update`가 넓히지 못하는 기존 데이터베이스의 열을 시작할 때 넓힌다.
 */
class SchemaColumnWidenerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var widener: SchemaColumnWidener
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource

    private fun size(table: String, column: String): Int = dataSource.connection.use { c ->
        listOf(table, table.uppercase()).firstNotNullOf { name ->
            c.metaData.getColumns(null, c.schemaOrNull(), name, column.let { if (name == table) it else it.uppercase() }).use { rs -> if (rs.next()) rs.getInt("COLUMN_SIZE") else null }
        }
    }

    @Test
    fun `다른 스키마에 같은 이름의 넓은 열이 있어도 현재 스키마의 좁은 열을 넓힌다`() {
        val product = dataSource.connection.use { it.metaData.databaseProductName }
        assumeTrue(product == "PostgreSQL" || product.startsWith("H2"), "스키마를 만들 수 있는 데이터베이스에서만 확인한다")
        val other = "other_widen"
        jdbc.execute("drop schema if exists $other cascade")
        jdbc.execute("create schema $other")
        try {
            // 이름 순서가 현재 스키마보다 앞서므로, 스키마를 지정하지 않으면 이 테이블의 크기를 먼저 읽는다
            jdbc.execute("create table $other.api_token (scopes varchar(2000))")
            jdbc.execute(if (product == "PostgreSQL") "alter table api_token alter column scopes type varchar(255)" else "alter table api_token alter column scopes varchar(255)")
            assertEquals(255, size("api_token", "scopes"))
            widener.widen()
            assertEquals(SchemaColumnWidener.SCOPES_LENGTH, size("api_token", "scopes"))
        } finally {
            jdbc.execute("drop schema $other cascade")
        }
    }

    @Test
    fun `좁은 범위 열을 넓히고 이미 넓으면 그대로 둔다`() {
        val product = dataSource.connection.use { it.metaData.databaseProductName }
        // MSSQL의 문자열 열은 nvarchar이다(X-46). varchar로 좁히면 같은 컨텍스트를 쓰는 뒤의 시험이 열을 읽지 못한다.
        val text = if (product.contains("SQL Server")) "nvarchar" else "varchar"
        val narrow = when {
            product == "PostgreSQL" -> "alter table api_token alter column scopes type varchar(255)"
            product.contains("CUBRID", ignoreCase = true) -> "alter table api_token modify scopes varchar(255)"
            else -> "alter table api_token alter column scopes $text(255)"
        }
        jdbc.execute(narrow)
        assertEquals(255, size("api_token", "scopes"))
        widener.widen()
        assertEquals(SchemaColumnWidener.SCOPES_LENGTH, size("api_token", "scopes"))
        widener.widen()
        assertEquals(SchemaColumnWidener.SCOPES_LENGTH, size("api_token", "scopes"), "다시 돌려도 같다")
        assertTrue(size("device_authorization", "scopes") >= SchemaColumnWidener.SCOPES_LENGTH)
        assertTrue(size("audit_log", "token_scopes") >= SchemaColumnWidener.SCOPES_LENGTH, "감사 기록에도 토큰의 범위 문자열이 들어간다")
    }
}
