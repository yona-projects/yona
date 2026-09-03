package com.github.yonaprojects.yona.domain.site

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.lang.Long as JLong
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.sql.DataSource

/**
 * yona의 data/DataService.java + data/exchangers 하위 Exchanger 44개 대응.
 * yona는 테이블별 전용 Exchanger 클래스를 44개 손으로 나열해 유지하지만, yona는
 * DB 메타데이터로 테이블 목록을 스스로 찾아내는 범용 방식을 택했다 — 엔티티가
 * 추가돼도 이 서비스는 수정할 필요가 없고, 기존 SiteApiController가 하드코딩했던
 * "users/projects 필드만 export" 문제(P0-07)를 테이블 단위로 완전히 해소한다.
 *
 * 복원 시 테이블 전체를 DELETE 후 백업 내용을 다시 INSERT한다(= 완전 교체).
 * yona는 원본 PK를 그대로 복원하는데, yona도 auto-increment 컬럼을 포함해 백업된
 * 값 그대로 INSERT한다 — 신규 채번과 충돌하지 않도록 테이블마다 복원 직후
 * auto-increment/시퀀스를 재설정한다.
 *
 * yona `DefaultExchanger.exportData()`/`importSequence()`(P2-07) 대응 — export 시점에
 * 실제 DB가 갖고 있던 auto-increment/시퀀스의 "다음 값"(`INFORMATION_SCHEMA.TABLES.AUTO_INCREMENT`)을
 * 그대로 캡처해뒀다가 복원 시 그 값으로 되돌린다. 백업된 행들의 `max(id)+1`을 복원 시점에
 * 재계산하는 방식(이전 구현)은 export 이전에 이미 삭제된 행으로 생긴 시퀀스 갭을 없애버려
 * 그 ID가 재사용될 수 있었는데, 이 방식은 yona처럼 갭을 그대로 보존한다.
 *
 * datetime(`Instant`) 컬럼 왕복 버그 수정(2026-08-20) — `exportAll()`이 `Instant` 값을 JSON에
 * ISO-8601 문자열로 직렬화하는데, `importAll()`은 이를 타입 정보 없는 `Map<String, Any?>`로
 * 역직렬화해 평범한 String이 된다. 이 String을 그대로 바인딩하면 MariaDB가 ISO-8601('T'/'Z')을
 * datetime으로 파싱하지 못해 `DataIntegrityViolationException`을 던진다(해당 컬럼이 NULL인
 * 행/테이블에서는 증상이 없어 "가끔 실패하는 flake"처럼 보였지만, 실제로는 datetime 컬럼에
 * 값이 있는 모든 테이블에서 100% 결정적으로 재현됐다). `insertRow()`가 각 테이블의 실제 JDBC
 * 컬럼 타입(`dateTimeColumns()`)을 조회해 TIMESTAMP/DATE/TIME 계열이면 String을 다시
 * `Instant`→`Timestamp`로 변환해 바인딩하도록 수정했다. yona는 애초에 `DefaultExchanger`의
 * `putTimestamp()`/`timestamp()` 헬퍼로 각 필드를 타입 그대로 다루기 때문에 이 문제 자체가
 * 없었다 — 자동 테이블 탐지 방식으로 단순화하며 새로 생긴, yona에는 없던 결함이었다.
 */
@Service
class DataBackupServiceImpl(
    private val dataSource: DataSource,
    private val objectMapper: ObjectMapper
) : DataBackupService {

    private val logger = LoggerFactory.getLogger(DataBackupServiceImpl::class.java)
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private enum class Dialect { MYSQL_COMPATIBLE, POSTGRES, H2, OTHER }

    override fun exportAll(): ByteArray {
        val tables = listTables()
        val dialect = detectDialect()
        val dump = LinkedHashMap<String, List<Map<String, Any?>>>()
        val sequences = LinkedHashMap<String, Long>()
        for (table in tables) {
            dump[table] = jdbcTemplate.queryForList("SELECT * FROM $table")
            nextSequenceValue(table, dialect)?.let { sequences[table] = it }
        }
        logger.info("데이터 백업 완료: ${tables.size}개 테이블")
        return objectMapper.writeValueAsBytes(mapOf("tables" to dump, "sequences" to sequences))
    }

    @Transactional
    @Suppress("UNCHECKED_CAST")
    override fun importAll(bytes: ByteArray) {
        val root = objectMapper.readValue(bytes, Map::class.java) as Map<String, Any?>
        val dump = root["tables"] as Map<String, List<Map<String, Any?>>>
        val sequences = (root["sequences"] as? Map<String, Any?>)
            ?.mapValues { (_, value) -> (value as Number).toLong() }
            ?: emptyMap()
        val dialect = detectDialect()

        setForeignKeyChecks(dialect, enabled = false)
        try {
            for ((table, rows) in dump) {
                jdbcTemplate.update("DELETE FROM $table")
                val dateTimeColumns = dateTimeColumns(table)
                for (row in rows) {
                    insertRow(table, row, dateTimeColumns)
                }
                sequences[table]?.let { restoreSequence(table, dialect, it) }
            }
            logger.info("데이터 복원 완료: ${dump.size}개 테이블")
        } finally {
            setForeignKeyChecks(dialect, enabled = true)
        }
    }

    private fun listTables(): List<String> {
        dataSource.connection.use { connection ->
            val meta = connection.metaData
            val tables = mutableListOf<String>()
            meta.getTables(connection.catalog, connection.schema, "%", arrayOf("TABLE")).use { rs ->
                while (rs.next()) {
                    tables.add(rs.getString("TABLE_NAME"))
                }
            }
            return tables.sorted()
        }
    }

    // 원인 진단(2026-08-20, 사용자 요청으로 근본 원인 추적): `exportAll()`이 `Instant` 컬럼 값을
    // ObjectMapper로 직렬화하면 JSON에는 ISO-8601 문자열(`"2026-08-20T06:21:04.973Z"`)로 남는데,
    // `importAll()`이 이를 타입 정보 없는 `Map<String, Any?>`로 역직렬화하면 그 값은 그냥 평범한
    // Kotlin String이 된다. 이 String을 그대로 PreparedStatement에 바인딩하면 MariaDB가
    // `'yyyy-MM-dd HH:mm:ss[.f...]'` 형식이 아닌 ISO-8601('T'/'Z' 포함)을 datetime으로 파싱하지
    // 못해 `DataIntegrityViolationException`을 던진다. 이 값이 있는 테이블에 대해서만 재현되므로
    // (해당 컬럼이 전부 NULL인 테이블/행에서는 증상이 없음) "가끔 실패하는 flake"처럼 보였지만,
    // 실제로는 datetime 컬럼에 값이 있는 모든 테이블에서 100% 결정적으로 재현되는 버그였다.
    // 컬럼의 실제 JDBC 타입을 조회해 TIMESTAMP/DATE/TIME 계열이면 String -> Instant -> Timestamp로
    // 되돌려 바인딩한다.
    private fun insertRow(table: String, row: Map<String, Any?>, dateTimeColumns: Set<String>) {
        if (row.isEmpty()) {
            return
        }
        val columns = row.keys.toList()
        val placeholders = columns.joinToString(",") { "?" }
        val sql = "INSERT INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)"
        val values = columns.map { column -> coerceForInsert(row[column], column.lowercase() in dateTimeColumns) }
        jdbcTemplate.update(sql, *values.toTypedArray())
    }

    private fun coerceForInsert(value: Any?, isDateTimeColumn: Boolean): Any? {
        if (!isDateTimeColumn || value !is String) {
            return value
        }
        return try {
            Timestamp.from(Instant.parse(value))
        } catch (e: DateTimeParseException) {
            value
        }
    }

    // 대상 테이블에서 TIMESTAMP/DATE/TIME 계열 컬럼명을 조회한다(컬럼명은 소문자로 정규화 —
    // `row`의 키가 `ColumnMapRowMapper`가 반환하는 실제 컬럼명과 대소문자까지 일치해야 매칭된다).
    private fun dateTimeColumns(table: String): Set<String> {
        dataSource.connection.use { connection ->
            val columns = mutableSetOf<String>()
            connection.metaData.getColumns(connection.catalog, connection.schema, table, null).use { rs ->
                while (rs.next()) {
                    val jdbcType = rs.getInt("DATA_TYPE")
                    if (jdbcType == Types.TIMESTAMP || jdbcType == Types.DATE || jdbcType == Types.TIME ||
                        jdbcType == Types.TIMESTAMP_WITH_TIMEZONE || jdbcType == Types.TIME_WITH_TIMEZONE
                    ) {
                        columns.add(rs.getString("COLUMN_NAME").lowercase())
                    }
                }
            }
            return columns
        }
    }

    // yona DefaultExchanger.exportData()의 hasSequence() 분기 대응 (P2-07) — export 시점에
    // 실제 DB가 다음으로 배정할 auto-increment/시퀀스 값을 그대로 조회해둔다. id 컬럼이
    // 없는 조인 테이블 등은 조회 결과가 null이라 자연히 스킵된다(yona의 hasSequence()=false와
    // 동일한 효과를 하드코딩된 테이블 목록 없이 얻는다).
    private fun nextSequenceValue(table: String, dialect: Dialect): Long? {
        return when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> {
                val catalog = dataSource.connection.use { it.catalog }
                jdbcTemplate.queryForObject(
                    "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                    JLong::class.java,
                    catalog,
                    table
                )?.toLong()
            }
            // 조인 테이블처럼 "id" 컬럼 자체가 없는 테이블에 pg_get_serial_sequence(table, 'id')를
            // 호출하면 (시퀀스가 없어서 null을 돌려주는 게 아니라) "column "id" of relation ...
            // does not exist" 예외가 난다 — 호출 전에 컬럼 존재 여부를 먼저 확인해야 한다.
            Dialect.POSTGRES -> {
                if (!hasIdColumn(table)) return null
                val sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')", String::class.java, table
                ) ?: return null
                jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM $sequenceName",
                    JLong::class.java
                )?.toLong()
            }
            // h2 지원 추가(H2는 legacy가 기본값으로 제공하던 임베디드 DB 대응). identity 컬럼이
            // MariaDB의 AUTO_INCREMENT와 달리 명시적 INSERT 값을 보고 스스로 전진하지 않는다는
            // 걸 실측으로 확인(DataBackupServiceH2IntegrationSpec — 복원 직후 PK 충돌 재현).
            // H2는 PostgreSQL의 pg_get_serial_sequence()처럼 identity 컬럼의 "다음 값"을 직접
            // 조회하는 표준 SQL이 없어(INFORMATION_SCHEMA.SEQUENCES에 identity 컬럼용 항목이
            // 노출되지 않음, 실측 확인) MAX(id)+1로 근사한다 — MariaDB/PostgreSQL과 달리
            // export 이전에 이미 삭제된 행으로 생긴 시퀀스 갭까지는 보존하지 못하는 것으로
            // 알려진 제약(문서화된 한계, docs/PARITY_BACKLOG.md P3 참고).
            Dialect.H2 -> {
                if (!hasIdColumn(table)) return null
                jdbcTemplate.queryForObject(
                    "SELECT COALESCE(MAX(id), 0) + 1 FROM $table", JLong::class.java
                )?.toLong()
            }
            Dialect.OTHER -> null
        }
    }

    // columnNamePattern에 "id"(소문자)를 그대로 넘기면 MariaDB/PostgreSQL(소문자로 컬럼명을
    // 저장)에서는 맞지만, H2는 unquoted DDL 컬럼명을 대문자로 저장해(실측 확인, COLUMN_NAME=ID)
    // 패턴이 안 맞아 컬럼이 있어도 없다고 오판한다 — columnNamePattern을 null로 열어 전체를
    // 받아온 뒤 대소문자 무관 비교로 직접 걸러낸다.
    private fun hasIdColumn(table: String): Boolean {
        dataSource.connection.use { connection ->
            connection.metaData.getColumns(connection.catalog, connection.schema, table, null).use { rs ->
                while (rs.next()) {
                    if (rs.getString("COLUMN_NAME").equals("id", ignoreCase = true)) return true
                }
                return false
            }
        }
    }

    // yona DefaultExchanger.importSequence() 대응 (P2-07) — export 시점에 캡처해둔 "다음 값"을
    // 그대로 복원한다(백업된 행들의 max(id)+1을 재계산하지 않음 — 그러면 export 이전에 이미
    // 삭제된 행으로 생긴 시퀀스 갭이 사라져 그 ID가 재사용될 수 있다).
    private fun restoreSequence(table: String, dialect: Dialect, nextValue: Long) {
        when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> {
                jdbcTemplate.execute("ALTER TABLE $table AUTO_INCREMENT = $nextValue")
            }
            Dialect.POSTGRES -> {
                if (!hasIdColumn(table)) return
                val sequenceName = jdbcTemplate.queryForObject(
                    "SELECT pg_get_serial_sequence(?, 'id')",
                    String::class.java,
                    table
                )
                if (sequenceName != null) {
                    // setval(seq, nextValue, false): is_called=false로 설정해, 다음 nextval() 호출이
                    // nextValue-1이 아니라 정확히 nextValue를 반환하도록 한다.
                    jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, sequenceName, nextValue)
                }
            }
            // ALTER COLUMN ... RESTART WITH — H2 고유 구문(실측 확인, PostgreSQL의 setval()과
            // 동등한 효과).
            Dialect.H2 -> {
                if (!hasIdColumn(table)) return
                jdbcTemplate.execute("ALTER TABLE $table ALTER COLUMN id RESTART WITH $nextValue")
            }
            Dialect.OTHER -> logger.warn("알 수 없는 DB 방언이라 $table 의 auto-increment/시퀀스를 재설정하지 않습니다")
        }
    }

    private fun detectDialect(): Dialect {
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName ?: ""
            return when {
                product.contains("MySQL", ignoreCase = true) || product.contains("MariaDB", ignoreCase = true) ->
                    Dialect.MYSQL_COMPATIBLE
                product.contains("PostgreSQL", ignoreCase = true) -> Dialect.POSTGRES
                product.contains("H2", ignoreCase = true) -> Dialect.H2
                else -> Dialect.OTHER
            }
        }
    }

    private fun setForeignKeyChecks(dialect: Dialect, enabled: Boolean) {
        when (dialect) {
            Dialect.MYSQL_COMPATIBLE -> jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = ${if (enabled) 1 else 0}")
            Dialect.POSTGRES -> jdbcTemplate.execute("SET session_replication_role = '${if (enabled) "origin" else "replica"}'")
            // SET REFERENTIAL_INTEGRITY — H2 고유 구문(실측 확인).
            Dialect.H2 -> jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY ${if (enabled) "TRUE" else "FALSE"}")
            Dialect.OTHER -> logger.warn("알 수 없는 DB 방언이라 외래키 제약을 토글하지 않습니다")
        }
    }
}
