package dev.yona.domain.site

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 내보내기와 가져오기의 테이블 명세(`DataTables`)가 v1.6과 같은 모양이고 실제 스키마의 테이블과 컬럼을 가리키는지 검증한다. */
class DataTablesTest : AbstractIntegrationTest() {

    @Autowired private lateinit var dataSource: DataSource

    @Test
    fun `테이블은 61개이고(v1_6의 47개와 추가한 테이블 14개) 이름 순서로 정렬되어 있다`() {
        assertEquals(61, DataTables.all.size)
        assertEquals(DataTables.all.map { it.name }.sorted(), DataTables.all.map { it.name })
        assertEquals("API_TOKEN", DataTables.all.first().name)
        assertEquals("WATCH", DataTables.all.last().name)
    }

    @Test
    fun `조인 테이블 열두 개와 사이트 설정만 시퀀스가 없다`() {
        assertEquals(
            setOf(
                "COMMENT_THREAD_N4USER", "ISSUE_COMMENT_VOTER", "ISSUE_ISSUE_LABEL", "ISSUE_VOTER", "NOTIFICATION_EVENT_N4USER",
                "PROJECT_LABEL", "PULL_REQUEST_ASSIGNEE", "PULL_REQUEST_ISSUE_LABEL", "PULL_REQUEST_REVIEWERS", "PULL_REQUEST_REVIEW_REQUEST", "SITE_SETTING", "USER_ENROLLED_ORGANIZATION", "USER_ENROLLED_PROJECT"
            ),
            DataTables.all.filter { !it.hasSequence }.map { it.name }.toSet()
        )
    }

    @Test
    fun `특수한 컬럼 이름과 종류가 v1_6과 같다`() {
        val attachment = DataTables.find("attachment")!!
        assertEquals("content_type", attachment.columns.first { it.name == "container_type" }.key)
        assertEquals(DataType.DATE, attachment.columns.first { it.name == "created_date" }.type)
        assertEquals(DataType.STRING_LONG, attachment.columns.first { it.name == "container_id" }.type)
        assertEquals(11, DataTables.find("N4USER")!!.columns.size)
        // v1.6의 19개에 추가 개발 항목 X-28의 `rebase_blocked`가 더해져 20개이다
        assertEquals(20, DataTables.find("PULL_REQUEST")!!.columns.size)
        assertEquals(32, DataTables.all.sumOf { table -> table.columns.count { it.nullableLong } })
    }

    @Test
    fun `모든 테이블과 컬럼이 실제 스키마에 있다`() {
        dataSource.connection.use { connection ->
            val metadata = connection.metaData
            for (table in DataTables.all) {
                val columns = HashSet<String>()
                metadata.getColumns(connection.catalog, null, null, null).use { rs ->
                    while (rs.next()) if (rs.getString("TABLE_NAME").equals(table.sqlName, ignoreCase = true)) columns += rs.getString("COLUMN_NAME").lowercase()
                }
                assertTrue(columns.isNotEmpty(), "테이블이 없다: ${table.name}")
                for (column in table.columns) assertTrue(column.name in columns, "컬럼이 없다: ${table.name}.${column.name}")
            }
        }
    }
}
