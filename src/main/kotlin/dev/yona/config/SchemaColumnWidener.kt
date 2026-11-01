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
                TARGETS.forEach { (table, column) ->
                    val size = currentSize(connection.metaData, table, column) ?: return@forEach
                    if (size >= SCOPES_LENGTH) return@forEach
                    connection.createStatement().use { it.executeUpdate(alter(product, table, column)) }
                    if (!connection.autoCommit) connection.commit()
                    log.info("Widened {}.{} from {} to {}", table, column, size, SCOPES_LENGTH)
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to widen the scope columns; long scope lists may not fit", e)
        }
    }

    private fun currentSize(metaData: java.sql.DatabaseMetaData, table: String, column: String): Int? =
        listOf(table to column, table.uppercase() to column.uppercase()).firstNotNullOfOrNull { (t, c) ->
            metaData.getColumns(null, null, t, c).use { rs -> if (rs.next()) rs.getInt("COLUMN_SIZE") else null }
        }

    private fun alter(product: String, table: String, column: String): String = when {
        product == "PostgreSQL" -> "alter table $table alter column $column type varchar($SCOPES_LENGTH)"
        product.startsWith("MySQL") || product.startsWith("MariaDB") || product.contains("CUBRID", ignoreCase = true) -> "alter table $table modify $column varchar($SCOPES_LENGTH)"
        else -> "alter table $table alter column $column varchar($SCOPES_LENGTH)"
    }

    companion object {
        /** 범위 목록 열의 길이이다. 지금의 전체 범위 문자열보다 충분히 크다. */
        const val SCOPES_LENGTH = 1000

        private val TARGETS = listOf("api_token" to "scopes", "device_authorization" to "scopes", "audit_log" to "token_scopes")
    }
}
