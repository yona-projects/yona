package dev.yona.schema

import org.springframework.core.io.ClassPathResource
import java.sql.Connection
import java.sql.Types
import javax.sql.DataSource

/** 한 컬럼의 구조이다. 비교에 쓰는 값만 담는다. */
data class ColumnInfo(val nullable: Boolean, val type: ColumnType)

/** 한 인덱스의 구조이다. 컬럼 순서가 의미를 가진다. */
data class IndexInfo(val unique: Boolean, val columns: List<String>, val descending: Set<String> = emptySet())

/** 한 외래 키의 구조이다. 삭제 규칙은 `CASCADE`, `SET NULL`, `RESTRICT` 중 하나이다. */
data class ForeignKeyInfo(val referencedTable: String, val deleteRule: String)

/**
 * DB마다 이름이 다른 컬럼 타입을 비교 가능한 종류로 줄인 값이다.
 *
 * v1.6은 MariaDB 타입(`bigint(20)`, `tinyint(1)`, `longtext`, `datetime`)을 쓰고, 같은 JPA 매핑이 DB마다
 * 다른 타입(`bool`, `text`, `timestamp(6)`)으로 만들어지므로 의미가 같은 타입을 한 종류로 본다.
 * `VARCHAR`만 길이를 정확히 비교한다.
 */
sealed interface ColumnType {
    data object BigInt : ColumnType
    data object Integer : ColumnType
    data object SmallInt : ColumnType
    data object Boolean : ColumnType
    data object Text : ColumnType
    data object Timestamp : ColumnType
    data object Date : ColumnType
    data class Varchar(val length: Int) : ColumnType
    data class Other(val jdbcType: Int, val name: String) : ColumnType
}

/**
 * 테이블 구조 전체를 `테이블 이름 -> 구조` 형태로 읽는 도구이다. 이름은 모두 소문자로 맞춘다.
 *
 * 기준(v1.6) 쪽은 `src/test/resources/schema/` 아래의 TSV 파일에서, 실제 쪽은 JDBC 메타데이터에서 읽는다.
 */
object SchemaMetadata {

    /**
     * v1.6 최종 스키마에 없지만 추가 개발 항목(D-28)에서 일부러 더한 테이블이다. 더할 때는 `decisions.md`에 근거를 적고 여기에 올린다.
     * - X-27 풀 리퀘스트 리뷰 요청(D-37): `pull_request_review_request`
     * - X-14 풀 리퀘스트의 담당자와 라벨(D-34): `pull_request_assignee`, `pull_request_issue_label`
     * - X-12 풀 리퀘스트 리뷰 판정(D-35): `pull_request_review`
     * - X-5 개인용 API 토큰(D-36): `api_token`
     */
    val INTENDED_ADDITIONS: Set<String> = setOf("pull_request_assignee", "pull_request_issue_label", "pull_request_review", "api_token", "pull_request_review_request")

    /**
     * v1.6 최종 스키마의 기존 테이블에 추가 개발 항목이 일부러 더한 컬럼이다(테이블 이름 -> 컬럼 이름들). 더할 때는 `decisions.md`에 근거를 적고 여기에 올린다.
     * - X-12 풀 리퀘스트 리뷰 판정(D-35 5번): `project.is_dismissing_stale_approvals`
     * - X-28 병합 방식(D-41): `project.allow_merge_commit`, `allow_squash_merge`, `allow_rebase_merge`, `default_merge_method`
     * - X-26 임시 저장 리뷰(D-38): `comment_thread.pending_date`, `review_comment.pending_date`, `review_comment.edited_date`(리뷰 댓글 고치기, D-38 7번)
     */
    val INTENDED_ADDED_COLUMNS: Map<String, Set<String>> = mapOf("pull_request" to setOf("rebase_blocked"), "project" to setOf("is_dismissing_stale_approvals", "allow_merge_commit", "allow_squash_merge", "allow_rebase_merge", "default_merge_method"), "comment_thread" to setOf("pending_date"), "review_comment" to setOf("pending_date", "edited_date"))

    /** v1.6 컬럼 타입 문자열(MariaDB `column_type`)을 [ColumnType]으로 바꾼다. */
    private fun parseV16Type(columnType: String): ColumnType = when {
        columnType.startsWith("bigint") -> ColumnType.BigInt
        columnType.startsWith("int") -> ColumnType.Integer
        columnType == "tinyint(1)" -> ColumnType.Boolean
        columnType.startsWith("tinyint") -> ColumnType.SmallInt
        columnType.startsWith("varchar") -> ColumnType.Varchar(columnType.substringAfter('(').substringBefore(')').toInt())
        columnType == "longtext" -> ColumnType.Text
        columnType == "datetime" -> ColumnType.Timestamp
        columnType == "date" -> ColumnType.Date
        else -> error("알 수 없는 v1.6 타입: $columnType")
    }

    /** 실제 DB 컬럼의 JDBC 타입을 [ColumnType]으로 바꾼다. */
    private fun parseActualType(jdbcType: Int, typeName: String, size: Int): ColumnType = when (jdbcType) {
        Types.BIGINT -> ColumnType.BigInt
        Types.INTEGER -> ColumnType.Integer
        Types.SMALLINT, Types.TINYINT -> ColumnType.SmallInt
        Types.BIT, Types.BOOLEAN -> ColumnType.Boolean
        Types.LONGVARCHAR, Types.CLOB, Types.LONGNVARCHAR -> ColumnType.Text
        Types.VARCHAR, Types.NVARCHAR -> if (size >= LONG_TEXT_THRESHOLD) ColumnType.Text else ColumnType.Varchar(size)
        Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ColumnType.Timestamp
        Types.DATE -> ColumnType.Date
        else -> ColumnType.Other(jdbcType, typeName)
    }

    /**
     * 두 타입이 같은 종류인지 판단한다. MariaDB의 `tinyint(1)`은 불린과 1바이트 정수를 구별하지 않으므로
     * [ColumnType.Boolean]과 [ColumnType.SmallInt]는 같은 종류로 본다.
     */
    fun sameType(expected: ColumnType, actual: ColumnType): Boolean = expected == actual ||
        (setOf(expected, actual) == setOf(ColumnType.Boolean, ColumnType.SmallInt))

    /** H2가 외래 키마다 자동으로 만드는 인덱스 이름이다(예: `fk4h5lxq7yijsep9ukboaih46ws_index_1`, 조인 테이블은 `fkokf9...`). */
    private val H2_FOREIGN_KEY_INDEX = Regex("fk[0-9a-z]{20,30}(_index_[0-9a-f]+)?")

    /** H2는 유니크 제약의 인덱스 이름 뒤에 `_INDEX_번호`를 붙인다. */
    private val H2_UNIQUE_INDEX_SUFFIX = Regex("_index_[0-9a-f]+$")

    /** 이 크기 이상의 `VARCHAR`는 `longtext`에 해당하는 큰 문자열로 본다. */
    private const val LONG_TEXT_THRESHOLD = 65_535

    private fun resource(name: String) =
        ClassPathResource("schema/$name").inputStream.bufferedReader().readLines().map { it.split('\t') }

    /** v1.6의 컬럼 구조를 읽는다. 컬럼 순서는 비교하지 않는다. */
    fun v16Columns(): Map<String, Map<String, ColumnInfo>> =
        resource("v16-columns.tsv").groupBy({ it[0].lowercase() }, {
            it[1].lowercase() to ColumnInfo(it[4] == "YES", parseV16Type(it[3]))
        }).mapValues { it.value.toMap() }

    /**
     * 이름을 바꿔 만드는 인덱스이다(`테이블 -> (v1.6 이름 -> v2 이름)`).
     *
     * MariaDB는 인덱스 이름이 테이블 안에서만 유일하면 되지만 PostgreSQL과 H2는 스키마 전체에서 유일해야 한다.
     * v1.6의 22.sql은 `ix_issue_parent_id`와 `ix_posting_parent_id`를 댓글 테이블에도 같은 이름으로 만들었으므로
     * 댓글 테이블의 인덱스에만 `comment`를 넣어 구별한다. 컬럼 구성은 v1.6과 같다.
     */
    private val RENAMED_INDEXES = mapOf(
        "issue_comment" to mapOf("ix_issue_parent_id" to "ix_issue_comment_parent_id"),
        "posting_comment" to mapOf("ix_posting_parent_id" to "ix_posting_comment_parent_id")
    )

    /**
     * v1.6에서 내림차순으로 만든 인덱스이다(`테이블 -> 인덱스 이름 -> 내림차순 컬럼`). 1~32번 evolutions 전체에서 `DESC`가 있는 인덱스는 이 하나뿐이다
     * (`conf/evolutions/default/3.sql:3`). 인덱스 목록 TSV는 MariaDB의 `information_schema`에서 뽑은 것이라 방향을 믿을 수 없어 따로 둔다.
     */
    private val V16_DESCENDING = mapOf("notification_event" to mapOf("ix_notification_event_created" to setOf("created")))

    /** v1.6의 PK를 제외한 인덱스를 `테이블 -> (인덱스 이름 -> 구조)`로 읽는다. */
    fun v16Indexes(): Map<String, Map<String, IndexInfo>> =
        resource("v16-indexes.tsv").filter { it[1] != "PRIMARY" }
            .groupBy { it[0].lowercase() to it[1].lowercase() }
            .map { (key, rows) -> (key.first to (RENAMED_INDEXES[key.first]?.get(key.second) ?: key.second)) to IndexInfo(rows.first()[2] == "0", rows.sortedBy { it[3].toInt() }.map { it[4].lowercase() }, V16_DESCENDING[key.first]?.get(key.second).orEmpty()) }
            .groupBy({ it.first.first }, { it.first.second to it.second })
            .mapValues { it.value.toMap() }

    /** v1.6의 외래 키를 `테이블 -> (컬럼 -> 구조)`로 읽는다. */
    fun v16ForeignKeys(): Map<String, Map<String, ForeignKeyInfo>> =
        resource("v16-foreign-keys.tsv").groupBy({ it[0].lowercase() }, {
            it[2].lowercase() to ForeignKeyInfo(it[3].lowercase(), it[5])
        }).mapValues { it.value.toMap() }

    private fun <T> Connection.tables(read: (String) -> T): Map<String, T> {
        val names = mutableListOf<String>()
        metaData.getTables(catalog, schema, "%", arrayOf("TABLE")).use { rs ->
            while (rs.next()) names += rs.getString("TABLE_NAME")
        }
        return names.associate { it.lowercase() to read(it) }
    }

    /** 실제 DB의 컬럼 구조를 읽는다. */
    fun actualColumns(dataSource: DataSource): Map<String, Map<String, ColumnInfo>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                buildMap {
                    c.metaData.getColumns(c.catalog, c.schema, table, "%").use { rs ->
                        while (rs.next()) {
                            put(
                                rs.getString("COLUMN_NAME").lowercase(),
                                ColumnInfo(
                                    rs.getInt("NULLABLE") != java.sql.DatabaseMetaData.columnNoNulls,
                                    parseActualType(rs.getInt("DATA_TYPE"), rs.getString("TYPE_NAME"), rs.getInt("COLUMN_SIZE"))
                                )
                            )
                        }
                    }
                }
            }
        }

    /** 실제 DB의 PK를 제외한 인덱스를 읽는다. */
    fun actualIndexes(dataSource: DataSource): Map<String, Map<String, IndexInfo>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                val primaryKey = buildList {
                    c.metaData.getPrimaryKeys(c.catalog, c.schema, table).use { rs ->
                        while (rs.next()) add(rs.getString("COLUMN_NAME").lowercase())
                    }
                }
                val byName = linkedMapOf<String, MutableList<Pair<Triple<Int, String, Boolean>, Boolean>>>()
                c.metaData.getIndexInfo(c.catalog, c.schema, table, false, false).use { rs ->
                    while (rs.next()) {
                        val column = rs.getString("COLUMN_NAME") ?: continue
                        byName.getOrPut(rs.getString("INDEX_NAME").lowercase()) { mutableListOf() }
                            .add(Triple(rs.getInt("ORDINAL_POSITION"), column.lowercase(), !rs.getBoolean("NON_UNIQUE")) to (rs.getString("ASC_OR_DESC") == "D"))
                    }
                }
                byName.filterKeys { !H2_FOREIGN_KEY_INDEX.matches(it) }
                    .mapKeys { (name, _) -> name.replace(H2_UNIQUE_INDEX_SUFFIX, "") }
                    .mapValues { (_, parts) ->
                        IndexInfo(
                            parts.first().first.third, parts.sortedBy { it.first.first }.map { it.first.second },
                            parts.filter { it.second }.map { it.first.second }.toSet()
                        )
                    }.filterValues { !(it.unique && it.columns == primaryKey) }
            }
        }

    /** 실제 DB의 외래 키를 읽는다. */
    fun actualForeignKeys(dataSource: DataSource): Map<String, Map<String, ForeignKeyInfo>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                buildMap {
                    c.metaData.getImportedKeys(c.catalog, c.schema, table).use { rs ->
                        while (rs.next()) {
                            put(
                                rs.getString("FKCOLUMN_NAME").lowercase(),
                                ForeignKeyInfo(
                                    rs.getString("PKTABLE_NAME").lowercase(),
                                    when (rs.getInt("DELETE_RULE")) {
                                        java.sql.DatabaseMetaData.importedKeyCascade -> "CASCADE"
                                        java.sql.DatabaseMetaData.importedKeySetNull -> "SET NULL"
                                        else -> "RESTRICT"
                                    }
                                )
                            )
                        }
                    }
                }
            }
        }

    // --- 기본값, 자동 증가, 외래 키 갱신 규칙 ---

    /** 기본값 문자열을 DB와 상관없이 비교할 수 있게 바꾼다. 불린은 `0`과 `1`로, 없음은 `null`로 본다. */
    private fun normalizeDefault(value: String?): String? = when (value?.lowercase()?.removeSurrounding("'")) {
        null, "null", "<null>" -> null
        "false", "b'0'", "0" -> "0"
        "true", "b'1'", "1" -> "1"
        else -> value
    }

    private fun columnRows() = resource("v16-columns.tsv")

    /** v1.6 컬럼의 기본값을 `테이블 -> (컬럼 -> 기본값)`으로 읽는다. 기본값이 없으면 `null`이다. 자동 증가 컬럼은 뺀다. */
    fun v16Defaults(): Map<String, Map<String, String?>> =
        columnRows().filter { !it[7].contains("auto_increment") }
            .groupBy({ it[0].lowercase() }, { it[1].lowercase() to normalizeDefault(it[5]) }).mapValues { it.value.toMap() }

    /** v1.6의 자동 증가 컬럼을 `테이블 -> 컬럼 집합`으로 읽는다. */
    fun v16AutoIncrement(): Map<String, Set<String>> =
        columnRows().filter { it[7].contains("auto_increment") }.groupBy({ it[0].lowercase() }, { it[1].lowercase() }).mapValues { it.value.toSet() }

    /** v1.6 외래 키의 갱신(`ON UPDATE`) 규칙을 `테이블 -> (컬럼 -> 규칙)`으로 읽는다. */
    fun v16UpdateRules(): Map<String, Map<String, String>> =
        resource("v16-foreign-keys.tsv").groupBy({ it[0].lowercase() }, { it[2].lowercase() to it[6] }).mapValues { it.value.toMap() }

    /** 실제 DB 컬럼의 기본값(자동 증가 컬럼 제외)을 읽는다. */
    fun actualDefaults(dataSource: DataSource): Map<String, Map<String, String?>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                buildMap {
                    c.metaData.getColumns(c.catalog, c.schema, table, "%").use { rs ->
                        while (rs.next()) {
                            if (rs.getString("IS_AUTOINCREMENT") == "YES") continue
                            put(rs.getString("COLUMN_NAME").lowercase(), normalizeDefault(rs.getString("COLUMN_DEF")))
                        }
                    }
                }
            }
        }

    /** 실제 DB의 자동 증가 컬럼을 읽는다. */
    fun actualAutoIncrement(dataSource: DataSource): Map<String, Set<String>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                buildSet {
                    c.metaData.getColumns(c.catalog, c.schema, table, "%").use { rs ->
                        while (rs.next()) if (rs.getString("IS_AUTOINCREMENT") == "YES") add(rs.getString("COLUMN_NAME").lowercase())
                    }
                }
            }
        }.filterValues { it.isNotEmpty() }

    /**
     * 실제 DB 외래 키의 갱신 규칙을 읽는다. `RESTRICT`와 `NO ACTION`은 PostgreSQL과 H2가 이름만 다르게 보고하는 같은 동작이므로
     * `RESTRICT`로 합친다.
     */
    fun actualUpdateRules(dataSource: DataSource): Map<String, Map<String, String>> =
        dataSource.connection.use { c ->
            c.tables { table ->
                buildMap {
                    c.metaData.getImportedKeys(c.catalog, c.schema, table).use { rs ->
                        while (rs.next()) {
                            put(
                                rs.getString("FKCOLUMN_NAME").lowercase(),
                                when (rs.getInt("UPDATE_RULE")) {
                                    java.sql.DatabaseMetaData.importedKeyCascade -> "CASCADE"
                                    java.sql.DatabaseMetaData.importedKeySetNull -> "SET NULL"
                                    java.sql.DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT"
                                    else -> "RESTRICT"
                                }
                            )
                        }
                    }
                }
            }
        }
}
