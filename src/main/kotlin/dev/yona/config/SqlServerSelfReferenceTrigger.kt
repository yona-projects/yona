package dev.yona.config

import jakarta.persistence.EntityManagerFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component
import javax.sql.DataSource

/**
 * MSSQL에서 자기 참조 외래 키의 `on delete set null`을 트리거로 대신한다(추가 개발 항목 X-46, `decisions.md` D-85).
 *
 * v1.6은 이슈(`issue.parent_id`), 이슈 댓글(`issue_comment.parent_comment_id`), 게시글(`posting.parent_id`), 게시글 댓글(`posting_comment.parent_comment_id`)의
 * 부모 열에 같은 테이블을 가리키는 외래 키를 `on delete set null`로 둔다. MSSQL은 이런 외래 키를 "순환 또는 다중 cascade 경로"로 보고 만들지 못하므로
 * Hibernate가 만든 스키마에는 이 네 외래 키가 없다. 대신 부모 행을 지운 뒤(`after delete`) 그 행을 가리키던 자식의 부모 열을 비우는 트리거를 둬서,
 * 지우는 경로(직접 삭제, 프로젝트 삭제의 cascade, 사이트 데이터 가져오기)와 상관없이 외래 키와 같은 결과가 되게 한다.
 *
 * MSSQL이 아니면 아무것도 하지 않는다. [EntityManagerFactory]를 받는 것은 Hibernate의 스키마 생성이 끝난 뒤에 실행되게 하려는 것이다.
 * 이미 있는 트리거는 다시 만들지 않는다. 실패하면 시작을 막지 않고 경고만 남긴다.
 */
@Component
class SqlServerSelfReferenceTrigger(private val dataSource: DataSource, @Suppress("unused") private val entityManagerFactory: EntityManagerFactory) : InitializingBean {

    private val log = LoggerFactory.getLogger(SqlServerSelfReferenceTrigger::class.java)

    override fun afterPropertiesSet() {
        try {
            dataSource.connection.use { connection ->
                if (!connection.metaData.databaseProductName.contains("SQL Server", ignoreCase = true)) return
                TARGETS.forEach { (table, column) -> create(connection, table, column) }
            }
        } catch (e: Exception) {
            log.warn("Failed to create the self-reference triggers; deleting a parent row will leave a dangling parent id", e)
        }
    }

    private fun create(connection: java.sql.Connection, table: String, column: String) {
        val name = "trg_${table}_${column}_set_null"
        val exists = connection.prepareStatement("select count(*) from sys.triggers where name = ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> rs.next() && rs.getInt(1) > 0 }
        }
        if (exists) return
        connection.createStatement().use {
            it.execute("create trigger $name on $table after delete as begin set nocount on; update $table set $column = null where $column in (select id from deleted) end")
        }
        log.info("Created the trigger {}", name)
    }

    companion object {
        /** `테이블 to 부모 열`이다. */
        val TARGETS = listOf("issue" to "parent_id", "issue_comment" to "parent_comment_id", "posting" to "parent_id", "posting_comment" to "parent_comment_id")
    }
}
