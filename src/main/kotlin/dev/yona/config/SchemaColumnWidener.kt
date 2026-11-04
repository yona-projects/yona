package dev.yona.config

import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * 범위 목록 열을 넓힌다(추가 개발 항목 X-3, `decisions.md` D-66, 구현 판단 A-323). 토큰 범위가 늘어 모든 범위를 이은 문자열(옛 토큰 이관, 전체 범위 선택)이 255자를 넘는다.
 * 새 데이터베이스는 엔티티의 열 길이([SCOPES_LENGTH])로 만들어지지만 `ddl-auto: update`는 이미 있는 열의 길이를 늘리지 못하므로, 시작할 때 좁은 열을 찾아 넓힌다.
 * 실패해도 시작을 막지 않고 경고만 남긴다(옛 값은 그대로 읽힌다). 이 프로젝트가 추가한 테이블(`api_token`, `device_authorization`, `audit_log`)에만 쓴다.
 *
 * [EntityManagerFactory]를 받는 것은 Hibernate의 스키마 갱신이 끝난 뒤에 실행되게 하려는 것이다.
 */
@Component
class SchemaColumnWidener(private val dataSource: DataSource, @Suppress("unused") private val entityManagerFactory: EntityManagerFactory) : InitializingBean {

    private val log = LoggerFactory.getLogger(SchemaColumnWidener::class.java)

    override fun afterPropertiesSet() = widen()

    /** 대상 열이 [SCOPES_LENGTH]보다 좁으면 넓힌다. 이미 넓으면 아무것도 하지 않는다. */
    fun widen() {
        try {
            dataSource.connection.use { connection ->
                val product = connection.metaData.databaseProductName
                val schema = currentSchema(connection)
                TARGETS.forEach { (table, column) ->
                    val size = currentSize(connection.metaData, schema, table, column) ?: return@forEach
                    if (size >= SCOPES_LENGTH) return@forEach
                    connection.createStatement().use { it.executeUpdate(alter(product, table, column, nationalized(connection.metaData, schema, table, column))) }
                    if (!connection.autoCommit) connection.commit()
                    log.info("Widened {}.{} from {} to {}", table, column, size, SCOPES_LENGTH)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to widen the scope columns; long scope lists may not fit", e)
        }
    }

    /**
     * 연결의 현재 스키마이다. 스키마를 지정하지 않고 메타데이터를 읽으면 같은 이름의 테이블이 있는 다른 스키마의 열이 먼저 나와, 다른 스키마의 크기를 보고
     * 현재 스키마의 좁은 열을 넓히지 않을 수 있다. Cubrid 드라이버는 `getSchema()`를 지원하지 않아 접속 사용자 이름(곧 스키마)으로 대신한다.
     */
    private fun currentSchema(connection: java.sql.Connection): String? =
        try { connection.schema } catch (_: java.sql.SQLException) { connection.metaData.userName?.lowercase() }

    private fun currentSize(metaData: java.sql.DatabaseMetaData, schema: String?, table: String, column: String): Int? =
        listOf(table to column, table.uppercase() to column.uppercase()).firstNotNullOfOrNull { (t, c) ->
            metaData.getColumns(null, schema, t, c).use { rs -> if (rs.next()) rs.getInt("COLUMN_SIZE") else null }
        }

    /** 열이 유니코드 문자열(`nvarchar`)인지 본다. MSSQL에서 `varchar`로 바꾸면 한글이 깨지고 Hibernate가 읽지 못한다(X-46). */
    private fun nationalized(metaData: java.sql.DatabaseMetaData, schema: String?, table: String, column: String): Boolean =
        listOf(table to column, table.uppercase() to column.uppercase()).firstNotNullOfOrNull { (t, c) ->
            metaData.getColumns(null, schema, t, c).use { rs -> if (rs.next()) rs.getString("TYPE_NAME") else null }
        }?.startsWith("n", ignoreCase = true) == true

    private fun alter(product: String, table: String, column: String, nationalized: Boolean = false): String = when {
        product == "PostgreSQL" -> "alter table $table alter column $column type varchar($SCOPES_LENGTH)"
        product.startsWith("MySQL") || product.startsWith("MariaDB") || product.contains("CUBRID", ignoreCase = true) -> "alter table $table modify $column varchar($SCOPES_LENGTH)"
        else -> "alter table $table alter column $column ${if (nationalized) "nvarchar" else "varchar"}($SCOPES_LENGTH)"
    }

    companion object {
        /** 범위 목록 열의 길이이다. 지금의 전체 범위 문자열보다 충분히 크다. */
        const val SCOPES_LENGTH = 1000

        private val TARGETS = listOf("api_token" to "scopes", "device_authorization" to "scopes", "audit_log" to "token_scopes")
    }
}
