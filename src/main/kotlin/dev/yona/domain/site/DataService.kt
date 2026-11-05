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
    private val reviewBackfill: dev.yona.domain.pullrequest.PullRequestReviewBackfill,
    private val reviewerMigration: dev.yona.domain.project.ReviewerSettingMigration
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = JsonMapper.builder().build()

    /**
     * DB 전체를 [out]에 JSON으로 쓴다. 읽기 전용 반복 읽기 트랜잭션 하나로 읽어 테이블 사이가 같은 시점의 자료가 되게 한다.
     * v1.6은 격리가 없어 쓰는 도중의 변경이 테이블마다 다르게 보였다(R-56-5). 반복 읽기가 스냅샷 방식인 DB(H2, PostgreSQL, MariaDB, MySQL)에서만
     * 쓴다. SQL Server와 Cubrid의 반복 읽기는 읽은 행에 공유 잠금을 끝까지 쥐어 쓰는 쪽과 서로 막으므로 기본 격리로 읽고, 그 DB에서는 v1.6처럼
     * 테이블 사이가 어긋날 수 있다. 격리 수준을 지원하지 않는 DB에서도 기본 격리로 읽는다.
     * 쓰다가 실패하면 예외가 그대로 나가며, 호출한 쪽은 이를 응답 실패로 다뤄야 한다. [out]은 닫지 않는다.
     */
    fun export(out: OutputStream) {
        val started = System.currentTimeMillis()
        val writer = out.bufferedWriter(Charsets.UTF_8)
        dataSource.connection.use { connection ->
            val product = connection.metaData.databaseProductName
            val ids = Identifiers(connection, product)
            val previous = beginConsistentRead(connection, product)
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
            endConsistentRead(connection, previous)
        }
        log.info("Exported all data in {} ms", System.currentTimeMillis() - started)
    }

    /** 연결 설정을 내보내기 앞의 상태로 되돌릴 때 쓰는 값이다. */
    private class ConnectionState(val autoCommit: Boolean, val readOnly: Boolean, val isolation: Int)

    /** 읽기 전용 반복 읽기 트랜잭션을 시작한다. 지원하지 않는 설정은 건너뛰고 기본대로 읽는다. */
    private fun beginConsistentRead(connection: Connection, product: String): ConnectionState {
        val state = ConnectionState(connection.autoCommit, connection.isReadOnly, connection.transactionIsolation)
        if (!usesRepeatableRead(product)) return state
        try {
            connection.autoCommit = false
            connection.isReadOnly = true
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
        } catch (e: java.sql.SQLException) {
            log.warn("Could not start a consistent read for the export; reading with the default isolation", e)
        }
        return state
    }

    /** 내보내기에서 반복 읽기를 쓰는 DB이면 `true`이다. 잠금 방식 반복 읽기를 쓰는 DB([LOCK_BASED_REPEATABLE_READ])는 쓰지 않는다. */
    internal fun usesRepeatableRead(product: String): Boolean = LOCK_BASED_REPEATABLE_READ.none { product.contains(it, ignoreCase = true) }

    /** 읽기 트랜잭션을 마치고 연결 설정을 되돌린다. 연결 풀로 돌아가는 연결이므로 되돌리지 못하면 기록만 남긴다. */
    private fun endConsistentRead(connection: Connection, previous: ConnectionState) {
        if (connection.autoCommit == previous.autoCommit && connection.transactionIsolation == previous.isolation) return
        try {
            if (!connection.autoCommit) connection.rollback()
            connection.transactionIsolation = previous.isolation
            connection.isReadOnly = previous.readOnly
            connection.autoCommit = previous.autoCommit
        } catch (e: java.sql.SQLException) {
            log.warn("Could not restore the connection after the export", e)
        }
    }

    private fun writeRows(connection: Connection, ids: Identifiers, table: DataTable, writer: java.io.Writer) {
        // 임시 저장 리뷰(X-26, D-38)의 임시 스레드와 임시 댓글은 아직 공개되지 않았으므로 내보내지 않는다
        val where = if (table.name in PENDING_TABLES) " where " + ids.column(table, "pending_date") + " is null" else ""
        val columns = table.columns.filter { it.derivedFrom == null }
        val sql = "select " + columns.joinToString(", ") { ids.column(table, it.name) } + " from " + ids.table(table) + where
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { rs ->
                var first = true
                while (rs.next()) {
                    writer.write(if (first) "\n    {" else ",\n    {")
                    first = false
                    columns.forEachIndexed { index, column ->
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
                val detachedKeys = suspendReferentialIntegrity(connection, product)
                val ids = Identifiers(connection, product)
                try {
                    connection.autoCommit = false
                    readTables(connection, product, ids, parser, imported, failed)
                    cleanOrphanLinks(connection)
                    cleanReviews(connection, imported)
                    cleanTokens(connection, imported)
                    cleanGpgKeys(connection, imported)
                    cleanSshKeys(connection, imported)
                    cleanTwoFactor(connection, imported)
                    cleanOAuthClients(connection, imported)
                    cleanLfsLocks(connection, imported)
                    cleanProtectionRules(connection, imported)
                } finally {
                    // 자동 커밋으로 바꾸면 진행 중인 트랜잭션이 확정되므로, 남은 변경은 먼저 되돌린다.
                    try { connection.rollback() } catch (e: Exception) { log.warn("Failed to roll back the import", e) }
                    connection.autoCommit = true
                    restoreReferentialIntegrity(connection, product, detachedKeys)
                }
            }
        }
        // 추가 항목(X-12, D-35 13번): 이관한 데이터도 승인 집합에서 리뷰를 다시 만들어 서버 시작 때의 보정과 같은 결과가 되게 한다
        if (imported.isNotEmpty() || failed.isEmpty()) runCatching { reviewerMigration.migrate(); reviewBackfill.backfill(); reviewBackfill.noticeIgnoredAuthorApprovals() }.onFailure { log.warn("Failed to backfill pull request reviews after the import", it) }
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

    /**
     * GPG 키 테이블(추가 개발 항목 X-4, `decisions.md` D-65)을 가져온 뒤 바로잡는다. [cleanTokens]와 같은 이유로, 파일에 사용자가 있는데 이 테이블이 없으면 비우고
     * 없는 사용자를 가리키는 행은 지운다. 하위 키 행이 가리키는 주 키 행이 없는 경우도 지운다.
     */
    private fun cleanGpgKeys(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("N4USER" in imported && "GPG_KEY" !in imported) {
                    statement.executeUpdate("delete from gpg_key where parent_id is not null")
                    statement.executeUpdate("delete from gpg_key")
                }
                statement.executeUpdate("delete from gpg_key where parent_id is not null and user_id not in (select id from n4user)")
                statement.executeUpdate("delete from gpg_key where user_id not in (select id from n4user)")
                statement.executeUpdate("delete from gpg_key where parent_id is not null and parent_id not in (select id from (select id from gpg_key) as parents)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean GPG keys after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /**
     * SSH 키와 배포 키 테이블(추가 개발 항목 X-3, `decisions.md` D-66)을 가져온 뒤 바로잡는다. [cleanGpgKeys]와 같은 이유로, 파일에 사용자(프로젝트)가 있는데 이 테이블이 없으면
     * 비우고 없는 사용자(프로젝트)를 가리키는 행은 지운다.
     */
    private fun cleanSshKeys(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("N4USER" in imported && "SSH_KEY" !in imported) statement.executeUpdate("delete from ssh_key")
                if ("PROJECT" in imported && "DEPLOY_KEY" !in imported) statement.executeUpdate("delete from deploy_key")
                statement.executeUpdate("delete from ssh_key where user_id not in (select id from n4user)")
                statement.executeUpdate("delete from deploy_key where project_id not in (select id from project)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean SSH keys after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /**
     * OAuth 앱 테이블(추가 개발 항목 X-1, `decisions.md` D-71)을 가져온 뒤 바로잡는다. [cleanTwoFactor]와 같은 이유로, 파일에 사용자가 있는데 이 테이블이 없으면(v1.6 파일) 남아 있던 행을 비우고,
     * 소유자가 사용자인데 그 사용자가 없는 행은 지운다.
     */
    private fun cleanLfsLocks(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                // 파일에 프로젝트가 있는데 잠금 테이블이 없으면(v1.6 파일) 남아 있던 잠금은 바뀐 프로젝트 번호에 엉뚱하게 붙을 수 있으므로 비운다
                if ("PROJECT" in imported && "LFS_LOCK" !in imported) statement.executeUpdate("delete from lfs_lock")
                statement.executeUpdate("delete from lfs_lock where project_id not in (select id from project) or owner_user_id not in (select id from n4user)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean LFS lock rows after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    private fun cleanOAuthClients(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("N4USER" in imported && "OAUTH_CLIENT" !in imported) statement.executeUpdate("delete from oauth_client")
                statement.executeUpdate("delete from oauth_client where owner_type = 'USER' and owner_id not in (select id from n4user)")
                statement.executeUpdate("delete from oauth_client where owner_type = 'ORGANIZATION' and owner_id not in (select id from organization)")
                if ("ORGANIZATION" in imported && "ORGANIZATION_OAUTH_POLICY" !in imported) statement.executeUpdate("delete from organization_oauth_policy")
                if ("ORGANIZATION" in imported && "ORGANIZATION_OAUTH_APPROVAL" !in imported) statement.executeUpdate("delete from organization_oauth_approval")
                statement.executeUpdate("delete from organization_oauth_policy where organization_id not in (select id from organization)")
                statement.executeUpdate("delete from organization_oauth_approval where organization_id not in (select id from organization) or client_row_id not in (select id from oauth_client)")
                if ("N4USER" in imported) {
                    for (table in listOf("oauth_authorization", "oauth_consent")) if (table.uppercase() !in imported) statement.executeUpdate("delete from $table")
                }
                for (table in listOf("oauth_authorization", "oauth_consent")) {
                    statement.executeUpdate("delete from $table where user_id not in (select id from n4user)")
                    statement.executeUpdate("delete from $table where " + (if (table == "oauth_authorization") "client_row_id" else "client_row_id") + " not in (select id from oauth_client)")
                }
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean OAuth client rows after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /**
     * 2단계 인증 테이블(추가 개발 항목 X-2, `decisions.md` D-69)을 가져온 뒤 바로잡는다. [cleanSshKeys]와 같은 이유로, 파일에 사용자가 있는데 이 테이블이 없으면(v1.6 파일) 남아 있던 행은
     * 바뀐 사용자 번호에 엉뚱하게 붙을 수 있으므로 비우고, 없는 사용자를 가리키는 행은 지운다.
     */
    private fun cleanTwoFactor(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                for (table in listOf("two_factor_credential", "two_factor_backup_code", "known_device")) {
                    if ("N4USER" in imported && table.uppercase() !in imported) statement.executeUpdate("delete from $table")
                    statement.executeUpdate("delete from $table where user_id not in (select id from n4user)")
                }
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean two-factor tables after the import", e)
            try { connection.rollback() } catch (ignored: Exception) { }
        }
    }

    /**
     * 브랜치 보호 규칙 테이블(추가 개발 항목 X-32, `decisions.md` D-42)을 가져온 뒤 바로잡는다. 파일에 프로젝트가 있는데 이 테이블이 없으면(v1.6 파일) 남아 있던
     * 규칙은 바뀐 프로젝트 번호에 엉뚱하게 붙을 수 있으므로 비우고, 없는 프로젝트를 가리키는 행은 지운다.
     */
    private fun cleanProtectionRules(connection: Connection, imported: List<String>) {
        try {
            connection.createStatement().use { statement ->
                if ("PROJECT" in imported && "RULESET" !in imported) statement.executeUpdate("delete from ruleset")
                statement.executeUpdate("delete from ruleset where project_id not in (select id from project)")
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to clean rulesets after the import", e)
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
                    // v1.6은 테이블 이름이 맞으면 먼저 TRUNCATE하고, 값이 배열이 아니면 건너뛰어 테이블만 비워졌다. 값이 배열이 아니면 비우지 않고 건너뛴다(R-84)
                    table != null -> parser.skipChildren()
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
        // MSSQL은 식별 열에 값을 직접 넣으려면 테이블마다 `IDENTITY_INSERT`를 켜야 하고, 한 세션에서 한 테이블만 켤 수 있다.
        val identityInsert = ids.isSqlServer && ids.hasIdentity(table)
        if (identityInsert) connection.createStatement().use { it.execute("SET IDENTITY_INSERT ${ids.table(table)} ON") }
        try {
            return importRows(connection, ids, table, parser)
        } finally {
            if (identityInsert) try { connection.createStatement().use { it.execute("SET IDENTITY_INSERT ${ids.table(table)} OFF") } } catch (e: Exception) { log.warn("Failed to turn IDENTITY_INSERT off for {}", table.name, e) }
        }
    }

    private fun importRows(connection: Connection, ids: Identifiers, table: DataTable, parser: tools.jackson.core.JsonParser): TableOutcome {
        var failure: Exception? = null
        var statement: PreparedStatement? = null
        try {
            if (!truncate(connection, ids, table)) throw IllegalStateException("${table.name}을 비우지 못했습니다.")
            statement = connection.prepareStatement(
                "insert into ${ids.table(table)} (" + table.columns.joinToString(", ") { ids.column(table, it.name) } + ") values (" + table.columns.joinToString(", ") { placeholder(ids, it) } + ")"
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
                    bind(statement!!, table, row, ids)
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

    /** 가져오기 SQL의 값 자리이다. 다른 테이블에서 채우는 컬럼은 그 값을 읽는 부속 질의이고, 그 자리에는 [DerivedColumn.via] 값을 바인딩한다. */
    private fun placeholder(ids: Identifiers, column: DataColumn): String {
        val derived = column.derivedFrom ?: return "?"
        val source = requireNotNull(DataTables.find(derived.table))
        return "(select ${ids.column(source, derived.column)} from ${ids.table(source)} where ${ids.column(source, "id")} = ?)"
    }

    /** Cubrid는 `smallint` 열에 `int`나 `boolean` 값을 넣지 못하므로(`Cannot coerce host var to type short`) 그 열에는 `setShort`로 넣는다. */
    private fun bind(ps: PreparedStatement, table: DataTable, row: JsonNode, ids: Identifiers) {
        table.columns.forEachIndexed { index, column ->
            val position = index + 1
            val smallintBooleans = ids.isSmallint(table, column.name)
            column.derivedFrom?.let { derived ->
                val node = requireNotNull(row.get(derived.via)) { "${table.name}의 행에 ${derived.via} 값이 없습니다." }
                ps.setLong(position, if (node.isNumber) node.longValue() else 0L)
                return@forEachIndexed
            }
            val found = row.get(column.key) ?: column.legacyKey?.let { row.get(it) }
            if (found == null && column.absentBoolean != null) { if (smallintBooleans) ps.setShort(position, if (column.absentBoolean) 1 else 0) else ps.setBoolean(position, column.absentBoolean); return@forEachIndexed }
            if (found == null && column.absentNull) { ps.setNull(position, Types.VARCHAR); return@forEachIndexed }
            if (found == null && column.absentString != null) { ps.setString(position, column.absentString); return@forEachIndexed }
            val node = requireNotNull(found) { "${table.name}의 행에 ${column.key} 값이 없습니다." }
            when (column.type) {
                // v1.6는 교환기마다 NULL을 허용한다고 표시한 컬럼만 NULL로 가져와 표시가 빠진 NULL 가능 컬럼은 0이 되었다. DB가 NULL을 허용하는 컬럼이면 JSON null을 NULL로 가져온다(R-56-6)
                DataType.LONG -> if (node.isNull && (column.nullableLong || ids.isNullable(table, column.name))) ps.setNull(position, Types.BIGINT) else ps.setLong(position, if (node.isNumber) node.longValue() else 0L)
                DataType.INT ->
                    if (node.isNull && ids.isNullable(table, column.name)) ps.setNull(position, if (smallintBooleans) Types.SMALLINT else Types.INTEGER)
                    else (if (node.isNumber) node.intValue() else 0).let { if (smallintBooleans) ps.setShort(position, it.toShort()) else ps.setInt(position, it) }
                DataType.STRING, DataType.CLOB -> if (node.isString) ps.setString(position, node.asString()) else ps.setNull(position, Types.VARCHAR)
                DataType.BOOLEAN -> (node.isBoolean && node.booleanValue()).let { if (smallintBooleans) ps.setShort(position, if (it) 1 else 0) else ps.setBoolean(position, it) }
                // v1.6는 JSON이 숫자가 아니거나 0이면 NULL로 넣어 에폭 0의 시각도 NULL이 되었다. JSON null(숫자가 아닌 값)일 때만 NULL로 넣는다(R-56-6)
                DataType.TIMESTAMP -> if (!node.isNumber) ps.setNull(position, Types.TIMESTAMP) else ps.setTimestamp(position, Timestamp(node.longValue()))
                DataType.DATE -> if (!node.isNumber) ps.setNull(position, Types.DATE) else ps.setDate(position, java.sql.Date(node.longValue()))
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
                    isMySql(product) || ids.isCubrid -> statement.execute("alter table ${ids.table(table)} auto_increment = $value")
                    product == "PostgreSQL" -> statement.execute("select setval(pg_get_serial_sequence('${table.sqlName}', 'id'), $value, false)")
                    product == "H2" -> statement.execute("alter table ${ids.table(table)} alter column ${ids.column(table, "id")} restart with $value")
                    ids.isSqlServer -> reseedSqlServer(statement, ids, table, value)
                    else -> log.warn("This database does not restore sequences: {}", product)
                }
            }
            connection.commit()
        } catch (e: Exception) {
            log.warn("Failed to restore the sequence of {}", table.name, e)
            connection.rollback()
        }
    }

    /**
     * MSSQL의 다음 식별 값을 [value]로 맞춘다. `RESEED`는 행이 한 번이라도 들어간 테이블에서는 지정한 값의 다음 번호를, 한 번도 들어가지 않은 테이블에서는
     * 지정한 값 자체를 다음 번호로 쓴다. 테이블에 식별 값이 마지막으로 쓰인 적이 있는지(`IDENT_CURRENT`로 알 수 없다)는 가져오기 직후의 행 수로 판단한다.
     */
    private fun reseedSqlServer(statement: java.sql.Statement, ids: Identifiers, table: DataTable, value: Long) {
        val used = statement.executeQuery("select count(*) from ${ids.table(table)}").use { rs -> rs.next(); rs.getLong(1) > 0 }
        statement.execute("DBCC CHECKIDENT ('${ids.table(table)}', RESEED, ${if (used) value - 1 else value})")
    }

    /** Cubrid에서 가져오기 동안 떼어 둔 외래 키의 정의이다. [deleteRule]은 JDBC `DELETE_RULE` 값이다. */
    private data class DetachedKey(val name: String, val table: String, val columns: List<String>, val parentTable: String, val parentColumns: List<String>, val deleteRule: Int)

    /**
     * 외래 키 검사를 멈춘다. H2, PostgreSQL, MySQL/MariaDB, MSSQL은 검사를 끄는 방법이 있다. Cubrid는 없어서 외래 키를 모두 떼어 두고 정의를 돌려준다([restoreReferentialIntegrity]가 다시 붙인다).
     */
    private fun suspendReferentialIntegrity(connection: Connection, product: String): List<DetachedKey> {
        if (!product.contains("CUBRID", ignoreCase = true)) { suspendByFlag(connection, product); return emptyList() }
        val dropped = mutableListOf<DetachedKey>()
        connection.createStatement().use { st ->
            for (key in readForeignKeys(connection)) {
                try { st.execute("alter table [${key.table}] drop foreign key [${key.name}]"); dropped += key }
                catch (e: Exception) { log.warn("Could not detach the foreign key {} on {}; the import continues with it in place", key.name, key.table, e) }
            }
        }
        return dropped
    }

    private fun readForeignKeys(connection: Connection): List<DetachedKey> {
        val tables = mutableListOf<String>()
        val schema = connection.metaData.userName?.lowercase()
        connection.metaData.getTables(connection.catalog, schema, "%", arrayOf("TABLE")).use { rs -> while (rs.next()) tables += rs.getString("TABLE_NAME") }
        class Row(val seq: Int, val column: String, val parentColumn: String)
        val result = mutableListOf<DetachedKey>()
        for (table in tables) {
            val rows = linkedMapOf<String, MutableList<Row>>()
            val meta = HashMap<String, Pair<String, Int>>()
            connection.metaData.getImportedKeys(connection.catalog, schema, table).use { rs ->
                while (rs.next()) {
                    val name = rs.getString("FK_NAME")
                    rows.getOrPut(name) { mutableListOf() } += Row(rs.getInt("KEY_SEQ"), rs.getString("FKCOLUMN_NAME"), rs.getString("PKCOLUMN_NAME"))
                    meta[name] = rs.getString("PKTABLE_NAME") to rs.getInt("DELETE_RULE")
                }
            }
            rows.forEach { (name, list) ->
                val sorted = list.sortedBy { it.seq }
                result += DetachedKey(name, table, sorted.map { it.column }, meta.getValue(name).first, sorted.map { it.parentColumn }, meta.getValue(name).second)
            }
        }
        return result
    }

    private fun suspendByFlag(connection: Connection, product: String) {
        if (isSqlServer(product)) { forEachSqlServerTable(connection) { "alter table $it nocheck constraint all" }; return }
        connection.createStatement().use { statement ->
            when {
                product == "H2" -> statement.execute("SET REFERENTIAL_INTEGRITY FALSE")
                product == "PostgreSQL" -> statement.execute("SET session_replication_role = replica")
                isMySql(product) -> { statement.execute("SET FOREIGN_KEY_CHECKS = 0"); statement.execute("SET NAMES 'utf8mb4'") }
            }
        }
    }

    /**
     * 외래 키 검사를 되살린다. Cubrid는 떼어 둔 외래 키를 다시 붙인다. 가져온 행 중 부모가 없는 행(고아 행)이 있으면 붙이지 못하므로, 그 행의 참조를 없애거나(`set null` 규칙)
     * 행을 지운 뒤 다시 붙이고 개수를 경고로 남긴다. v1.6은 검사를 끈 채 고아 행을 그대로 두지만 Cubrid에서는 외래 키가 없는 채로 두는 것보다 낫다고 판단했다.
     */
    private fun restoreReferentialIntegrity(connection: Connection, product: String, detached: List<DetachedKey>) {
        if (detached.isNotEmpty()) { reattach(connection, detached); return }
        restoreByFlag(connection, product)
    }

    private fun reattach(connection: Connection, keys: List<DetachedKey>) {
        for (key in keys) {
            val add = "alter table [${key.table}] add constraint [${key.name}] foreign key (${key.columns.joinToString { "[$it]" }}) references [${key.parentTable}] (${key.parentColumns.joinToString { "[$it]" }})" +
                when (key.deleteRule) { java.sql.DatabaseMetaData.importedKeyCascade -> " on delete cascade"; java.sql.DatabaseMetaData.importedKeySetNull -> " on delete set null"; else -> " on delete restrict" }
            try {
                connection.createStatement().use { it.execute(add) }
            } catch (first: Exception) {
                val orphan = key.columns.zip(key.parentColumns).joinToString(" and ") { (c, p) -> "[$c] is not null and [$c] not in (select [$p] from [${key.parentTable}])" }
                val fixed = try {
                    connection.createStatement().use { st ->
                        val count = if (key.deleteRule == java.sql.DatabaseMetaData.importedKeySetNull) st.executeUpdate("update [${key.table}] set ${key.columns.joinToString { "[$it] = null" }} where $orphan")
                        else st.executeUpdate("delete from [${key.table}] where $orphan")
                        log.warn("Cleaned {} orphan rows of {}.{} so the foreign key {} can be restored", count, key.table, key.columns, key.name)
                        st.execute(add)
                    }
                    true
                } catch (second: Exception) { false }
                if (!fixed) log.error("Failed to restore the foreign key {} on {}", key.name, key.table, first)
            }
        }
        if (!connection.autoCommit) connection.commit()
    }

    private fun restoreByFlag(connection: Connection, product: String) {
        if (isSqlServer(product)) { forEachSqlServerTable(connection) { "alter table $it check constraint all" }; return }
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
        private val identityTables = HashSet<String>()

        /** MSSQL인지 여부이다. */
        val isSqlServer = isSqlServer(product)

        /** Cubrid인지 여부이다. */
        val isCubrid = product.contains("CUBRID", ignoreCase = true)

        private val smallintColumns = HashSet<String>()
        private val nullableColumns = HashSet<String>()

        /** DB가 NULL을 허용하는 컬럼인지 여부이다(R-56-6). */
        fun isNullable(table: DataTable, column: String): Boolean = "${table.sqlName}.${column.lowercase()}" in nullableColumns

        /** Cubrid에서만 `smallint` 열을 구분한다. 그 밖의 데이터베이스는 `int`와 `boolean`을 그대로 넣어도 된다. */
        fun isSmallint(table: DataTable, column: String): Boolean = isCubrid && "${table.sqlName}.${column.lowercase()}" in smallintColumns

        init {
            connection.metaData.getColumns(connection.catalog, try { connection.schema } catch (e: java.sql.SQLException) { null }, null, null).use { rs ->
                while (rs.next()) {
                    val table = rs.getString("TABLE_NAME")
                    if (isSqlServer && rs.getString("IS_AUTOINCREMENT") == "YES") identityTables += table.lowercase()
                    if (isCubrid && rs.getInt("DATA_TYPE") == Types.SMALLINT) smallintColumns += table.lowercase() + "." + rs.getString("COLUMN_NAME").lowercase()
                    if (rs.getInt("NULLABLE") == java.sql.DatabaseMetaData.columnNullable) nullableColumns += table.lowercase() + "." + rs.getString("COLUMN_NAME").lowercase()
                    tables.putIfAbsent(table.lowercase(), table)
                    columns.getOrPut(table.lowercase()) { HashMap() }[rs.getString("COLUMN_NAME").lowercase()] = rs.getString("COLUMN_NAME")
                }
            }
        }

        /** 식별(자동 증가) 열이 있는 테이블인지 여부이다. */
        fun hasIdentity(table: DataTable): Boolean = table.sqlName in identityTables

        private fun quote(name: String): String = if (isMySql(product)) "`$name`" else "\"$name\""

        /** 인용한 테이블 이름이다. */
        fun table(table: DataTable): String = quote(tables[table.sqlName] ?: table.sqlName)

        /** 인용한 컬럼 이름이다. */
        fun column(table: DataTable, name: String): String = quote(columns[table.sqlName]?.get(name.lowercase()) ?: name)
    }

    /** MSSQL은 전체 외래 키를 한 번에 끄는 설정이 없어 테이블마다 제약을 끄고 켠다. 켤 때 기존 행을 다시 검사하지는 않는다. */
    private fun forEachSqlServerTable(connection: Connection, sql: (String) -> String) {
        val names = connection.createStatement().use { st -> st.executeQuery("select name from sys.tables").use { rs -> generateSequence { if (rs.next()) rs.getString(1) else null }.toList() } }
        connection.createStatement().use { st -> names.forEach { st.execute(sql("[${it.replace("]", "]]")}]")) } }
    }

    private fun isSqlServer(product: String) = product.contains("SQL Server", ignoreCase = true)

    private fun isMySql(product: String) = product.equals("MySQL", ignoreCase = true) || product.equals("MariaDB", ignoreCase = true)

    private companion object {
        /** 임시 저장 리뷰(X-26, D-38)의 임시 행을 내보내지 않는 테이블이다. */
        val PENDING_TABLES = setOf("COMMENT_THREAD", "REVIEW_COMMENT")

        /** 반복 읽기가 읽은 행의 공유 잠금을 끝까지 쥐는 DB의 제품 이름이다. 내보내기가 쓰는 쪽과 서로 막으므로 반복 읽기를 쓰지 않는다. */
        val LOCK_BASED_REPEATABLE_READ = listOf("Microsoft SQL Server", "CUBRID")

        /** 배치로 묶어 넣는 행 수이다 (v1.6 `data.batch.size` 기본값). */
    }
}
