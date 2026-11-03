package dev.yona.config

import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.TestDatabase
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * MSSQL은 자기 참조 외래 키에 `on delete set null`을 만들 수 없어(`SqlServerSelfReferenceTrigger`), 같은 효과를 내는 트리거를 둔다(X-46, D-85).
 * 부모를 지우면 자식의 부모 열이 비워져야 한다(v1.6의 외래 키 동작). MSSQL에서만 확인한다.
 */
class SqlServerSelfReferenceTriggerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Test
    fun `부모 이슈를 지우면 자식 이슈의 부모는 비워지고 자식은 남는다`() {
        assumeTrue(TestDatabase.current == TestDatabase.MSSQL)
        jdbc.update("insert into issue(title) values ('parent')")
        val parent = jdbc.queryForObject("select max(id) from issue", Long::class.java)!!
        jdbc.update("insert into issue(title, parent_id) values ('child', ?)", parent)
        val child = jdbc.queryForObject("select max(id) from issue", Long::class.java)!!

        jdbc.update("delete from issue where id = ?", parent)

        assertEquals(1, jdbc.queryForObject("select count(*) from issue where id = ?", Int::class.java, child))
        assertNull(jdbc.queryForObject("select parent_id from issue where id = ?", Long::class.javaObjectType, child))
        jdbc.update("delete from issue where id = ?", child)
    }

    @Test
    fun `이슈 댓글과 게시글과 게시글 댓글에도 트리거가 있다`() {
        assumeTrue(TestDatabase.current == TestDatabase.MSSQL)
        val names = jdbc.queryForList("select name from sys.triggers", String::class.java).toSet()
        for ((table, column) in SqlServerSelfReferenceTrigger.TARGETS) {
            assertEquals(true, "trg_${table}_${column}_set_null" in names, table)
        }
    }
}
