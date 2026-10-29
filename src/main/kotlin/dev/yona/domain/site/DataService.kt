package dev.yona.domain.site

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.core.JsonToken
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.InputStream
import java.io.OutputStream
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import javax.sql.DataSource

/**
 * 가져오기 결과이다.
 *
 * @property imported 비우고 다시 채운 테이블 이름
 * @property failed 오류가 나서 되돌린 테이블 이름
 */
data class DataImportResult(val imported: List<String>, val failed: List<String>)

/**
 * DB 전체를 JSON 파일 하나로 내보내고 같은 형식의 파일로 교체한다. v1.6 `DataService`와 `DefaultExchanger`
 * (`app/data/DataService.java`, `DefaultExchanger.java`)에 해당한다.
 *
 * 대상은 [DataTables]의 52개 테이블뿐이고 Git 저장소 디렉터리와 첨부 파일 본문은 다루지 않는다. 파일은 테이블 이름(대문자)마다 행의 배열이고,
 * 시퀀스가 있는 테이블은 그 뒤에 `<이름>_SEQ`로 다음 번호가 따른다. 시각은 에폭 밀리초이다.
 *
 * 가져오기는 병합이 아니라 **교체**이다. 파일에 있는 테이블만 비우고 파일의 행을 넣는다. 파일에 없는 테이블은 그대로이다. 외래 키 검사를 끄고
 * 테이블 단위 트랜잭션으로 처리하며, 한 테이블이 실패하면 그 테이블만 되돌리고 다음 테이블을 계속한다(A-92).
 */
@Service
class DataService(
    private val dataSource: DataSource,
    private val properties: dev.yona.config.YonaProperties,
    private val reviewBackfill: dev.yona.domain.pullrequest.PullRequestReviewBackfill
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = JsonMapper.builder().build()

    /** DB 전체를 [out]에 JSON으로 쓴다. 트랜잭션이나 스냅샷 격리는 없다. [out]은 닫지 않는다. */
    fun export(out: OutputStream) {
        val started = System.currentTimeMillis()
        val writer = out.bufferedWriter(Charsets.UTF_8)
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName
            val ids = Identifiers(connection, product)
            writer.write("{")
            var firstTable = true
            for (table in DataTables.all) {
                if (!firstTable) writer.write(",")
                firstTable = false
                writer.write("\n  \"${table.name}\" : [")
                writeRows(connection, ids, table, writer)
                writer.write(" ]")
                if (table.hasSequence) writer.write(",\n  \"${table.sequenceName}\" : ${nextValue(connection, product, ids, table)}")
            }
            writer.write("\n}\n")
            writer.flush()
        }
        log.info("Exported all data in {} ms", System.currentTimeMillis() - started)
    }

    private fun writeRows(connection: Connection, ids: Identifiers, table: DataTable, writer: java.io.Writer) {
        // 임시 저장 리뷰(X-26, D-38)의 임시 스레드와 임시 댓글은 아직 공개되지 않았으므로 내보내지 않는다
        val where = if (table.name in PENDING_TABLES) " where " + ids.column(table, "pending_date") + " is null" else ""
        val sql = "select " + table.columns.joinToString(", ") { ids.column(table, it.name) } + " from " + ids.table(table) + where
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                var first = true
                while (rs.next()) {
                    writer.write(if (first) "\n    {" else ",\n    {")
                    first = false
                    table.columns.forEachIndexed { index, column ->
                        if (index > 0) writer.write(", ")
                        writer.write("\"${column.key}\": ")
                        writer.write(jsonValue(rs, index + 1, column.type))
                    }
                    writer.write("}")
                }
            }
        }
    }

    /** DB 값을 JSON 조각으로 만든다. DB NULL은 `null`이고 불린만 `false`이다. */
    private fun jsonValue(rs: java.sql.ResultSet, index: Int, type: DataType): String = when (type) {
        DataType.LONG -> rs.getLong(index).let { if (rs.wasNull()) "null" else it.toString() }
        DataType.INT -> rs.getInt(index).let { if (rs.wasNull()) "null" else it.toString() }
        DataType.BOOLEAN -> rs.getBoolean(index).toString()
        DataType.TIMESTAMP -> rs.getTimestamp(index)?.time?.toString() ?: "null"
        DataType.DATE -> rs.getDate(index)?.time?.toString() ?: "null"
        DataType.STRING, DataType.CLOB, DataType.STRING_LONG -> rs.getString(index)?.let { mapper.writeValueAsString(it) } ?: "null"
    }

    /**
     * 테이블의 다음 번호이다. MariaDB와 MySQL은 `AUTO_INCREMENT`, 그 밖의 DB는 가장 큰 번호에 1을 더한 값이다(A-92).
     */
    private fun nextValue(connection: Connection, product: String, ids: Identifiers, table: DataTable): Long {
        if (isMySql(product)) {
            connection.prepareStatement("select AUTO_INCREMENT from information_schema.TABLES where TABLE_SCHEMA = database() and TABLE_NAME = ?").use { ps ->
                ps.setString(1, table.sqlName)
                ps.executeQuery().use { rs -> if (rs.next()) return rs.getLong(1) }
            }
        }
        connection.createStatement().use { statement ->
            statement.executeQuery("select coalesce(max(${ids.column(table, "id")}), 0) + 1 from ${ids.table(table)}").use { rs -> rs.next(); return rs.getLong(1) }
        }
    }

    /**
     * 파일로 DB를 교체한다. 첫 토큰이 객체가 아니면 아무것도 하지 않는다. 모르는 필드는 건너뛴다. 파일의 필드 순서는 가리지 않는다(v1.6은 테이블 이름 순서가
     * 아니면 뒤쪽을 무시한다, A-92).
     *
     * 테이블마다 따로 확정하거나 되돌린다(v1.6 `DefaultExchanger.importData`는 테이블마다 트랜잭션을 잡고 모든 예외를 삼킨다). 파일이 도중에
     * 잘렸거나 구문이 틀리면 그 테이블을 되돌리고, 파서를 더 믿을 수 없으므로 남은 테이블은 건드리지 않고 멈춘다. 앞서 끝낸 테이블은 그대로 남는다.
     * 이 경우도 예외를 밖으로 던지지 않는다(v1.6은 302 `/`). 밖으로 나가는 구문 오류는 **첫 토큰**의 것뿐이다(v1.6은 400).
     */
    fun import(input: InputStream): DataImportResult {
        val started = System.currentTimeMillis()
        val imported = mutableListOf<String>()
        val failed = mutableListOf<String>()
        mapper.createParser(input).use { parser ->
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                log.info("The data file is not a JSON object; nothing was imported")
                return DataImportResult(imported, failed)
            }
            dataSource.connection.use { connection ->
                val product = connection.metaData.databaseProductName
                suspendReferentialIntegrity(connection, product)
                val ids = Identifiers(connection, product)
                try {
                    connection.autoCommit = false
                    readTables(connection, product, ids, parser, imported, failed)
                    cleanOrphanLinks(connection)
                    cleanReviews(connection, imported)
                    cleanTokens(connection, imported)
                } finally {
                    // 자동 커밋으로 바꾸면 진행 중인 트랜잭션이 확정되므로, 남은 변경은 먼저 되돌린다.
                    try { connection.rollback() } catch (e: Exception) { log.warn("Failed to roll back the import", e) }
                    connection.autoCommit = true
                    restoreReferentialIntegrity(connection, product)
                }
            }
        }
        // 추가 항목(X-12, D-35 13번): 이관한 데이터도 승인 집합에서 리뷰를 다시 만들어 서버 시작 때의 보정과 같은 결과가 되게 한다
        if (imported.isNotEmpty() || failed.isEmpty()) runCatching { reviewBackfill.backfill(); reviewBackfill.noticeIgnoredAuthorApprovals() }.onFailure { log.warn("Failed to backfill pull request reviews after the import", it) }
        log.info("Imported data in {} ms", System.currentTimeMillis() - started)
        return DataImportResult(imported, failed)
    }

    /**
     * 가져온 뒤 없는 풀 리퀘스트, 사용자, 이슈 라벨을 가리키는 담당자와 라벨 연결 행을 지운다(추가 개발 항목 X-14, `decisions.md` D-34).
     * v1.6 파일에는 이 두 테이블이 없어서 풀 리퀘스트를 바꿔 가져오면 파일에 없는 옛 연결 행이 엉뚱한 번호를 가리킬 수 있다.
     */
    /**
     * 리뷰 판정 테이블(추가 개발 항목 X-12, `decisions.md` D-35)을 가져온 뒤 바로잡는다. 파일에 풀 리퀘스트가 있는데 리뷰 테이블이 없으면(v1.6 파일) 남아 있던
     * 리뷰는 바뀐 풀 리퀘스트 번호에 엉뚱하게 붙을 수 있으므로 비우고, 없는 풀 리퀘스트를 가리키는 행은 지운다. 리뷰는 승인 집합에서 다시 만든다(서버 시작 때의 보정).
     */
    private fun cleanReviews(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("PULL_REQUEST" in imported && "PULL_REQUEST_REVIEW" !in imported) statement.executeUpdate("delete from pull_request_review")
                statement.executeUpdate("delete from pull_request_review where pull_request_id not in (select id from pull_request)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean pull request reviews after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /**
     * 토큰 테이블(추가 개발 항목 X-5, `decisions.md` D-36)을 가져온 뒤 바로잡는다. 파일에 사용자가 있는데 토큰 테이블이 없으면(v1.6 파일) 남아 있던 토큰은
     * 바뀐 사용자 번호에 엉뚱하게 붙을 수 있으므로 비우고, 없는 사용자를 가리키는 행은 지운다. v1.6의 사용자 내보내기에는 `token` 열이 없어
     * 옛 파일로 가져오면 토큰을 잃는 것은 v1.6과 같다(이관할 값이 없다).
     */
    private fun cleanTokens(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("N4USER" in imported && "API_TOKEN" !in imported) statement.executeUpdate("delete from api_token")
                statement.executeUpdate("delete from api_token where user_id not in (select id from n4user)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean API tokens after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    private fun cleanOrphanLinks(connection: Connection) {
        try {
            connection.createStatement().use { statement ->
                statement.executeUpdate("delete from pull_request_assignee where pull_request_id not in (select id from pull_request) or user_id not in (select id from n4user)")
                statement.executeUpdate("delete from pull_request_review_request where pull_request_id not in (select id from pull_request) or user_id not in (select id from n4user)")
                statement.executeUpdate("delete from pull_request_issue_label where pull_request_id not in (select id from pull_request) or issue_label_id not in (select id from issue_label)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean orphan pull request links after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /** 파일의 필드를 차례로 읽어 테이블과 시퀀스를 처리한다. 파서가 깨지면 그 자리에서 멈춘다. */
    private fun readTables(connection: Connection, product: String, ids: Identifiers, parser: tools.jackson.core.JsonParser, imported: MutableList<String>, failed: MutableList<String>) {
        try {
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                val name = parser.currentName()
                val value = parser.nextToken()
                val table = DataTables.find(name)
                val sequenceOf = DataTables.findBySequenceName(name)
                when {
                    table != null && value == JsonToken.START_ARRAY -> when (importTable(connection, ids, table, parser)) {
                        TableOutcome.IMPORTED -> imported += table.name
                        TableOutcome.FAILED -> failed += table.name
                        TableOutcome.ABORTED -> { failed += table.name; return }
                    }
                    // v1.6은 테이블 이름이 맞으면 먼저 TRUNCATE하고, 값이 배열이 아니면 건너뛰므로 테이블만 비워진다
                    table != null -> { truncate(connection, ids, table); parser.skipChildren() }
                    sequenceOf != null && value.isNumeric -> restoreSequence(connection, product, ids, sequenceOf, parser.longValue)
                    else -> parser.skipChildren()
                }
            }
        } catch (e: Exception) {
            log.error("The data file is broken; the remaining tables were not imported", e)
        }
    }

    /**
     * 테이블을 비우고 바로 확정한다. v1.6의 `TRUNCATE`는 DDL이라 즉시 확정되어, 뒤이어 행을 넣다 실패해 되돌려도 테이블은 빈 채로 남는다.
     * 비우는 데 실패해도 가져오기는 계속한다.
     */
    private fun truncate(connection: Connection, ids: Identifiers, table: DataTable): Boolean = try {
        connection.createStatement().use { it.executeUpdate("delete from ${ids.table(table)}") }
        connection.commit()
        true
    } catch (e: Exception) {
        log.error("Failed to empty the table {}", table.name, e)
        try { connection.rollback() } catch (r: Exception) { log.warn("Failed to roll back", r) }
        false
    }

    /** 테이블 하나를 가져온 결과이다. [ABORTED]는 파서가 깨져 남은 테이블을 읽을 수 없다는 뜻이다. */
    private enum class TableOutcome { IMPORTED, FAILED, ABORTED }

    /**
     * 테이블을 비워(즉시 확정) 파서가 가리키는 배열의 행을 넣는다. 행 오류가 나면 넣은 행을 되돌리고(테이블은 빈 채로 남는다) 배열의 남은 부분을 읽어 넘긴다.
     * 파서 자체가 깨지면 되돌리고 [TableOutcome.ABORTED]를 돌려준다.
     */
    private fun importTable(connection: Connection, ids: Identifiers, table: DataTable, parser: tools.jackson.core.JsonParser): TableOutcome {
        var failure: Exception? = null
        var statement: PreparedStatement? = null
        try {
            if (!truncate(connection, ids, table)) throw IllegalStateException("${table.name}을 비우지 못했습니다.")
            statement = connection.prepareStatement(
                "insert into ${ids.table(table)} (" + table.columns.joinToString(", ") { ids.column(table, it.name) } + ") values (" + table.columns.joinToString(", ") { "?" } + ")"
            )
        } catch (e: Exception) {
            failure = e
        }
        var pending = 0
        var aborted = false
        try {
            while (true) {
                val token = parser.nextToken() ?: break
                if (token == JsonToken.END_ARRAY) break
                if (token != JsonToken.START_OBJECT) { parser.skipChildren(); continue }
                val row = parser.readValueAsTree<JsonNode>()
                if (failure != null) continue
                try {
                    bind(statement!!, table, row)
                    statement.addBatch()
                    if (++pending >= maxOf(1, properties.dataBatchSize)) { statement.executeBatch(); pending = 0 }
                } catch (e: Exception) {
                    failure = e
                }
            }
        } catch (e: Exception) {
            // 파일이 도중에 잘렸거나 구문이 틀렸다(v1.6: 이 테이블만 되돌리고 302 /).
            aborted = true
            failure = failure ?: e
        }
        try {
            if (failure == null && pending > 0) statement!!.executeBatch()
        } catch (e: Exception) {
            failure = e
        }
        try { statement?.close() } catch (e: Exception) { log.warn("Failed to close the statement", e) }
        return if (failure == null) {
            connection.commit()
            TableOutcome.IMPORTED
        } else {
            log.error("Failed to import the table {}", table.name, failure)
            connection.rollback()
            if (aborted) TableOutcome.ABORTED else TableOutcome.FAILED
        }
    }

    private fun bind(ps: PreparedStatement, table: DataTable, row: JsonNode) {
        table.columns.forEachIndexed { index, column ->
            val position = index + 1
            val found = row.get(column.key)
            if (found == null && column.absentBoolean != null) { ps.setBoolean(position, column.absentBoolean); return@forEachIndexed }
            val node = requireNotNull(found) { "${table.name}의 행에 ${column.key} 값이 없습니다." }
            when (column.type) {
                DataType.LONG -> if (node.isNull && column.nullableLong) ps.setNull(position, Types.BIGINT) else ps.setLong(position, if (node.isNumber) node.longValue() else 0L)
                DataType.INT -> ps.setInt(position, if (node.isNumber) node.intValue() else 0)
                DataType.STRING, DataType.CLOB -> if (node.isString) ps.setString(position, node.asString()) else ps.setNull(position, Types.VARCHAR)
                DataType.BOOLEAN -> ps.setBoolean(position, node.isBoolean && node.booleanValue())
                DataType.TIMESTAMP -> timeOf(node).let { if (it == 0L) ps.setNull(position, Types.TIMESTAMP) else ps.setTimestamp(position, Timestamp(it)) }
                DataType.DATE -> timeOf(node).let { if (it == 0L) ps.setNull(position, Types.DATE) else ps.setDate(position, java.sql.Date(it)) }
                DataType.STRING_LONG -> {
                    val number = if (node.isNumber) node.longValue() else node.takeIf { it.isString }?.asString()?.toLongOrNull()
                    if (number == null) ps.setNull(position, Types.BIGINT) else ps.setLong(position, number)
                }
            }
        }
    }

    private fun timeOf(node: JsonNode): Long = if (node.isNumber) node.longValue() else 0L

    /** 시퀀스(다음 번호)를 복원한다. 실패해도 가져오기는 계속한다. */
    private fun restoreSequence(connection: Connection, product: String, ids: Identifiers, table: DataTable, value: Long) {
        if (value < 1) return
        try {
            connection.createStatement().use { statement ->
                when {
                    isMySql(product) -> statement.execute("alter table ${ids.table(table)} auto_increment = $value")
                    product == "PostgreSQL" -> statement.execute("select setval(pg_get_serial_sequence('${table.sqlName}', 'id'), $value, false)")
                    product == "H2" -> statement.execute("alter table ${ids.table(table)} alter column ${ids.column(table, "id")} restart with $value")
                    else -> log.warn("This database does not restore sequences: {}", product)
                }
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to restore the sequence of {}", table.name, e)
            connection.rollback()
        }
    }

    private fun suspendReferentialIntegrity(connection: Connection, product: String) {
        connection.createStatement().use { statement ->
            when {
                product == "H2" -> statement.execute("SET REFERENTIAL_INTEGRITY FALSE")
                product == "PostgreSQL" -> statement.execute("SET session_replication_role = replica")
                isMySql(product) -> { statement.execute("SET FOREIGN_KEY_CHECKS = 0"); statement.execute("SET NAMES 'utf8mb4'") }
            }
        }
    }

    private fun restoreReferentialIntegrity(connection: Connection, product: String) {
        connection.createStatement().use { statement ->
            when {
                product == "H2" -> statement.execute("SET REFERENTIAL_INTEGRITY TRUE")
                product == "PostgreSQL" -> statement.execute("SET session_replication_role = DEFAULT")
                isMySql(product) -> statement.execute("SET FOREIGN_KEY_CHECKS = 1")
            }
        }
    }

    /**
     * DB에 실제로 만들어진 테이블과 컬럼 이름이다. `value` 같은 예약어 컬럼은 Hibernate가 소문자로 따옴표를 쳐서 만들고 나머지는 DB가 정한 대소문자로
     * 만들므로 메타데이터에서 읽어 그대로 따옴표로 감싼다.
     */
    private inner class Identifiers(connection: Connection, private val product: String) {
        private val tables = HashMap<String, String>()
        private val columns = HashMap<String, MutableMap<String, String>>()

        init {
            connection.metaData.getColumns(connection.catalog, connection.schema, null, null).use { rs ->
                while (rs.next()) {
                    val table = rs.getString("TABLE_NAME")
                    tables.putIfAbsent(table.lowercase(), table)
                    columns.getOrPut(table.lowercase()) { HashMap() }[rs.getString("COLUMN_NAME").lowercase()] = rs.getString("COLUMN_NAME")
                }
            }
        }

        private fun quote(name: String): String = if (isMySql(product)) "`$name`" else "\"$name\""

        /** 인용한 테이블 이름이다. */
        fun table(table: DataTable): String = quote(tables[table.sqlName] ?: table.sqlName)

        /** 인용한 컬럼 이름이다. */
        fun column(table: DataTable, name: String): String = quote(columns[table.sqlName]?.get(name.lowercase()) ?: name)
    }

    private fun isMySql(product: String) = product.equals("MySQL", ignoreCase = true) || product.equals("MariaDB", ignoreCase = true)

    private companion object {
        /** 임시 저장 리뷰(X-26, D-38)의 임시 행을 내보내지 않는 테이블이다. */
        val PENDING_TABLES = setOf("COMMENT_THREAD", "REVIEW_COMMENT")

        /** 배치로 묶어 넣는 행 수이다 (v1.6 `data.batch.size` 기본값). */
    }
}
