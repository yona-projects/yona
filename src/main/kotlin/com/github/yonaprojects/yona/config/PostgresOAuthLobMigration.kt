package com.github.yonaprojects.yona.config

import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.stereotype.Component
import java.sql.Connection
import javax.sql.DataSource

/**
 * PostgreSQL에서 `@Lob`으로 만들어졌던 대용량 객체(oid) 컬럼을 text(문자열) 또는 bytea(바이트 배열)로 바꾼다.
 * 대상은 `oauth_*` 테이블(문자열)과 `user_webauthn_*` 테이블(바이트 배열)이다.
 *
 * 매핑을 @Lob에서 LONG32VARCHAR로 바꿨지만 `hibernate.hbm2ddl.auto=update`는 이미 있는 컬럼의 타입을
 * 바꾸지 않아, 이전 버전이 만든 데이터베이스에서는 text를 oid 컬럼에 쓰려다 실패한다. Hibernate가
 * 스키마를 갱신하기 전(EntityManagerFactory 생성 시점)에 실행하며, 내용은 `lo_get`으로 옮기고 원래
 * 대용량 객체는 삭제한다. 새 데이터베이스나 다른 DBMS에서는 아무 일도 하지 않는다.
 */
@Component
class PostgresOAuthLobMigration(private val dataSource: DataSource) : HibernatePropertiesCustomizer {
    override fun customize(hibernateProperties: MutableMap<String, Any>) {
        dataSource.connection.use { migrate(it) }
    }

    private data class Target(val table: String, val column: String, val type: String, val using: String)

    /** 바꾼 컬럼 수를 돌려준다. */
    fun migrate(connection: Connection): Int {
        if (connection.metaData.databaseProductName != "PostgreSQL") return 0
        val columns = oidColumns(connection)
        if (columns.isEmpty()) return 0
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            columns.forEach { convert(connection, it) }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = autoCommit
        }
        return columns.size
    }

    private fun oidColumns(connection: Connection): List<Target> =
        connection.prepareStatement(
            """
            select table_name, column_name from information_schema.columns
            where table_schema = current_schema() and data_type = 'oid'
              and (table_name like 'oauth\_%' or table_name like 'user\_webauthn\_%')
            order by table_name, ordinal_position
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        val table = rows.getString(1)
                        val column = rows.getString(2)
                        if (table.startsWith("user_webauthn_")) add(Target(table, column, "bytea", "lo_get(\"$column\")"))
                        else add(Target(table, column, "text", "convert_from(lo_get(\"$column\"), 'UTF8')"))
                    }
                }
            }
        }

    private fun convert(connection: Connection, target: Target) {
        val (table, column) = target.table to target.column
        val oids = connection.createStatement().use { statement ->
            statement.executeQuery("""select "$column" from "$table" where "$column" is not null""").use { rows ->
                buildList { while (rows.next()) add(rows.getLong(1)) }
            }
        }
        connection.createStatement().use {
            it.execute("""alter table "$table" alter column "$column" type ${target.type} using ${target.using}""")
        }
        // 값은 이미 text로 복사됐으므로 원본 대용량 객체를 지운다(그대로 두면 고아 객체로 남는다).
        oids.forEach { oid ->
            connection.prepareStatement("select lo_unlink(?)").use { statement ->
                statement.setLong(1, oid)
                statement.execute()
            }
        }
    }
}
