package com.github.yonaprojects.yona.config

import com.github.yonaprojects.yona.AbstractIntegrationTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

// PostgreSQL 전용 - 다른 DBMS에서는 마이그레이션이 아무 일도 하지 않으므로 이 스펙도 건너뛴다.
class PostgresOAuthLobMigrationSpec @Autowired constructor(
    private val dataSource: DataSource
) : AbstractIntegrationTest() {
    private val migration = PostgresOAuthLobMigration(dataSource)
    private val onPostgres = System.getProperty("yona.it.db") == "postgres"

    init {
        describe("이전 버전(@Lob)이 만든 oid 컬럼을 text로 바꾸는 마이그레이션") {
            it("oid 컬럼의 내용을 보존해 text로 바꾸고 원본 대용량 객체를 지워야 한다").config(enabled = onPostgres) {
                dataSource.connection.use { connection ->
                    connection.createStatement().use {
                        it.execute("drop table if exists oauth_migration_probe")
                        it.execute("create table oauth_migration_probe (id int primary key, payload oid, untouched varchar(20))")
                    }
                    val oid = connection.createStatement().use { statement ->
                        statement.executeQuery("select lo_from_bytea(0, convert_to('토큰-값', 'UTF8'))").use { rows -> rows.next(); rows.getLong(1) }
                    }
                    connection.createStatement().use {
                        it.execute("insert into oauth_migration_probe values (1, $oid, 'keep'), (2, null, null)")
                    }

                    migration.migrate(connection) shouldBe 1

                    connection.createStatement().use { statement ->
                        statement.executeQuery("select data_type from information_schema.columns where table_name = 'oauth_migration_probe' and column_name = 'payload'").use { rows ->
                            rows.next(); rows.getString(1) shouldBe "text"
                        }
                        statement.executeQuery("select payload, untouched from oauth_migration_probe where id = 1").use { rows ->
                            rows.next(); rows.getString(1) shouldBe "토큰-값"; rows.getString(2) shouldBe "keep"
                        }
                        statement.executeQuery("select payload from oauth_migration_probe where id = 2").use { rows ->
                            rows.next(); rows.getString(1) shouldBe null
                        }
                        statement.executeQuery("select count(*) from pg_largeobject_metadata where oid = $oid").use { rows ->
                            rows.next(); rows.getInt(1) shouldBe 0
                        }
                    }
                    // 이미 text이므로 두 번째 실행은 아무 것도 바꾸지 않는다(멱등).
                    migration.migrate(connection) shouldBe 0
                    connection.createStatement().use { it.execute("drop table oauth_migration_probe") }
                }
            }
        }
        describe("이전 버전(@Lob ByteArray)이 만든 WebAuthn oid 컬럼을 bytea로 바꾸는 마이그레이션") {
            it("UTF-8이 아닌 바이트도 그대로 보존해 bytea로 바꿔야 한다").config(enabled = onPostgres) {
                dataSource.connection.use { connection ->
                    connection.createStatement().use {
                        it.execute("drop table if exists user_webauthn_migration_probe")
                        it.execute("create table user_webauthn_migration_probe (id int primary key, cose oid not null)")
                    }
                    val oid = connection.createStatement().use { statement ->
                        statement.executeQuery("select lo_from_bytea(0, '\\xff00fe80'::bytea)").use { rows -> rows.next(); rows.getLong(1) }
                    }
                    connection.createStatement().use { it.execute("insert into user_webauthn_migration_probe values (1, $oid)") }

                    migration.migrate(connection) shouldBe 1

                    connection.createStatement().use { statement ->
                        statement.executeQuery("select data_type from information_schema.columns where table_name = 'user_webauthn_migration_probe' and column_name = 'cose'").use { rows ->
                            rows.next(); rows.getString(1) shouldBe "bytea"
                        }
                        statement.executeQuery("select cose from user_webauthn_migration_probe where id = 1").use { rows ->
                            rows.next(); rows.getBytes(1).toList() shouldBe listOf(0xff, 0x00, 0xfe, 0x80).map { it.toByte() }
                        }
                        statement.executeQuery("select count(*) from pg_largeobject_metadata where oid = $oid").use { rows ->
                            rows.next(); rows.getInt(1) shouldBe 0
                        }
                    }
                    connection.createStatement().use { it.execute("drop table user_webauthn_migration_probe") }
                }
            }
        }
    }
}
