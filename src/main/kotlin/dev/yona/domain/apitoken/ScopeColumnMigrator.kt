package dev.yona.domain.apitoken

import jakarta.persistence.EntityManager
import org.hibernate.Session
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 이전 빌드가 쉼표로 이은 문자열로 저장한 범위(`api_token.scopes`, `device_authorization.scopes`)를 범위 행으로 옮긴다(`decisions.md` D-126).
 * 새 엔티티에는 그 열이 없으므로 `ddl-auto: update`가 열을 지우지 않아 남아 있는 열만 읽는다. 열이 없는 새 DB에서는 아무것도 하지 않는다.
 *
 * 옮긴 행의 옛 열은 비운다. 이미 범위 행이 있는 토큰은 건너뛰므로 몇 번을 돌려도 같다. 옛 토큰 표시가 있고 모든 범위가 적힌 행은
 * 범위 행 없이 범위 제한 없음으로 옮긴다([ScopeColumnMigration]). 서버를 시작할 때 자동으로 돌지 않고 이관 명령이 부른다.
 */
@Component
class ScopeColumnMigrator(private val entityManager: EntityManager) {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 옮길 옛 열의 위치이다. [ordered]이면 범위 행에 요청한 순서(`position`)를 함께 저장한다. */
    private data class Target(val table: String, val scopeTable: String, val foreignKey: String, val hasLegacyFlag: Boolean, val ordered: Boolean)

    private val targets = listOf(
        Target("api_token", "api_token_scope", "api_token_id", hasLegacyFlag = true, ordered = false),
        Target("device_authorization", "device_authorization_scope", "device_authorization_id", hasLegacyFlag = false, ordered = true)
    )

    /** 아직 문자열로 남은 행의 수이다. 옛 열이 없으면 0이다. */
    @Transactional(readOnly = true)
    fun pending(): Long = targets.sumOf { target ->
        if (!hasOldColumn(target.table)) 0L
        else (entityManager.createNativeQuery("select count(*) from ${target.table} where scopes is not null and scopes <> ''").singleResult as Number).toLong()
    }

    /** 문자열 범위를 범위 행으로 옮기고 옮긴 행의 수를 돌려준다. */
    @Transactional
    fun migrate(): Int {
        var moved = 0
        for (target in targets) {
            if (!hasOldColumn(target.table)) continue
            val legacyColumn = if (target.hasLegacyFlag) "is_legacy" else "cast(null as smallint)"
            val rows = entityManager.createNativeQuery("select id, scopes, $legacyColumn from ${target.table} where scopes is not null and scopes <> ''").resultList
            for (row in rows) {
                val columns = row as Array<*>
                val id = (columns[0] as Number).toLong()
                val scopes = ScopeColumnMigration.split(columns[1] as String?, legacy = columns[2] == true || (columns[2] as? Number)?.toInt() == 1)
                val existing = (entityManager.createNativeQuery("select count(*) from ${target.scopeTable} where ${target.foreignKey} = :id").setParameter("id", id).singleResult as Number).toLong()
                if (existing == 0L) {
                    scopes.forEachIndexed { index, scope ->
                        val sql = if (target.ordered) "insert into ${target.scopeTable} (${target.foreignKey}, scope, position) values (:id, :scope, :position)"
                        else "insert into ${target.scopeTable} (${target.foreignKey}, scope) values (:id, :scope)"
                        val query = entityManager.createNativeQuery(sql).setParameter("id", id).setParameter("scope", scope)
                        if (target.ordered) query.setParameter("position", index)
                        query.executeUpdate()
                    }
                }
                entityManager.createNativeQuery("update ${target.table} set scopes = null where id = :id").setParameter("id", id).executeUpdate()
                moved++
            }
        }
        if (moved > 0) log.info("Moved {} comma separated scope lists into scope rows", moved)
        return moved
    }

    /** 표에 옛 `scopes` 열이 남아 있는지 본다. 이름의 대소문자는 DB마다 달라 소문자와 대문자를 모두 찾는다. */
    private fun hasOldColumn(table: String): Boolean =
        entityManager.unwrap(Session::class.java).doReturningWork { connection ->
            val metaData = connection.metaData
            val schema = try { connection.schema } catch (_: java.sql.SQLException) { metaData.userName?.lowercase() }
            listOf(table to "scopes", table.uppercase() to "SCOPES").any { (t, c) -> metaData.getColumns(null, schema, t, c).use { it.next() } }
        }
}
