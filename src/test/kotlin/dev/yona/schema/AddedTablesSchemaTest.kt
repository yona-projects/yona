package dev.yona.schema

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource
import kotlin.test.assertEquals

/**
 * 추가 개발 항목이 더한 테이블의 구조를 검증한다(`SchemaMetadata.INTENDED_ADDITIONS`). `-Dyona.it.db=h2|postgres|mariadb`로 DB를 바꿔 반복한다.
 * X-14(D-34): `pull_request_assignee`(풀 리퀘스트, 사용자)와 `pull_request_issue_label`(풀 리퀘스트, 이슈 라벨)은 두 컬럼 모두 NULL이 아니고
 * 각각 대상 테이블의 `id`를 가리키며, 두 컬럼이 기본 키라서 한 쌍은 한 번만 들어간다.
 */
class AddedTablesSchemaTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var dataSource: DataSource

    @Test
    fun `담당자 연결 테이블의 컬럼과 외래 키와 유일 인덱스`() = check("pull_request_assignee", "user_id", "n4user")

    @Test
    fun `라벨 연결 테이블의 컬럼과 외래 키와 유일 인덱스`() = check("pull_request_issue_label", "issue_label_id", "issue_label")

    /** 테이블의 기본 키 컬럼이다. DB마다 테이블 이름의 대소문자가 달라 찾아서 쓴다. */
    private fun primaryKey(table: String): Set<String> = dataSource.connection.use { c ->
        val names = mutableListOf<String>()
        c.metaData.getTables(c.catalog, c.schema, "%", arrayOf("TABLE")).use { rs -> while (rs.next()) names += rs.getString("TABLE_NAME") }
        val actual = names.first { it.equals(table, ignoreCase = true) }
        val columns = mutableSetOf<String>()
        c.metaData.getPrimaryKeys(c.catalog, c.schema, actual).use { rs -> while (rs.next()) columns += rs.getString("COLUMN_NAME").lowercase() }
        columns
    }

    private fun check(table: String, second: String, secondTable: String) {
        val columns = SchemaMetadata.actualColumns(dataSource)[table] ?: error("$table 테이블이 없다")
        assertEquals(setOf("pull_request_id", second), columns.keys)
        columns.values.forEach { assertEquals(false, it.nullable) }
        val foreignKeys = SchemaMetadata.actualForeignKeys(dataSource)[table].orEmpty().values.map { it.referencedTable }.toSet()
        assertEquals(setOf("pull_request", secondTable), foreignKeys)
        assertEquals(setOf("pull_request_id", second), primaryKey(table), "두 컬럼이 기본 키이므로 한 쌍은 한 번만 들어간다")
    }
}
