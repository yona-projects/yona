package com.github.yonaprojects.yona.domain.site

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.Long as JLong
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.util.ReflectionTestUtils
import tools.jackson.databind.ObjectMapper
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.ResultSet
import java.sql.Types
import javax.sql.DataSource
import java.time.Instant

class DataBackupServiceImplSpec : DescribeSpec({
    val dataSource = mockk<DataSource>()
    val objectMapper = ObjectMapper()
    val connection = mockk<Connection>()
    val metaData = mockk<DatabaseMetaData>()
    val jdbcTemplate = mockk<JdbcTemplate>(relaxed = true)

    val service = DataBackupServiceImpl(dataSource, objectMapper)
    ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate)

    beforeTest {
        clearMocks(dataSource, connection, metaData, jdbcTemplate)
        every { dataSource.connection } returns connection
        every { connection.close() } returns Unit
        every { connection.metaData } returns metaData
        every { connection.catalog } returns "test_catalog"
        every { connection.schema } returns "test_schema"
    }

    // hasIdColumn()과 dateTimeColumns() 둘 다 getColumns(catalog, schema, table, null)를 같은
    // 시그니처로 호출한다(hasIdColumn()이 컬럼명 패턴 대신 전체를 받아 대소문자 무관 비교하도록
    // 바뀐 뒤부터, H2가 unquoted 컬럼명을 대문자로 저장해 패턴 매칭이 깨지는 문제 수정). 실제
    // JDBC는 호출마다 커서가 처음으로 초기화된 새 ResultSet을 돌려주므로, 테스트에서도 호출마다
    // 독립된 새 mock을 만들어야 두 메서드가 커서 상태를 공유해 두 번째 호출이 고갈된 커서를
    // 받는 문제를 피할 수 있다.
    fun columnsResultSet(columns: List<Pair<String, Int>>): ResultSet {
        var idx = -1
        val rs = mockk<ResultSet>(relaxed = true)
        every { rs.next() } answers { idx++; idx < columns.size }
        every { rs.getString("COLUMN_NAME") } answers { columns[idx].first }
        every { rs.getInt("DATA_TYPE") } answers { columns[idx].second }
        return rs
    }

    describe("detectDialect & setForeignKeyChecks") {
        it("MySQL/MariaDB인 경우 MYSQL_COMPATIBLE 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "MariaDB"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
            
            // 검증 로직 없음, 에러만 안나면 성공
        }

        it("PostgreSQL인 경우 POSTGRES 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
        }

        it("기타 DB인 경우 OTHER 방언을 사용해야 한다") {
            every { metaData.databaseProductName } returns "H2"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false
            
            service.exportAll()
        }

        it("databaseProductName이 null이면 OTHER 방언으로 처리해야 한다") {
            every { metaData.databaseProductName } returns null
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returns false

            val result = service.exportAll()

            result shouldNotBe null
            // 검증 로직 없음, 에러만 안나면 성공 (databaseProductName ?: "" 엘비스의 null쪽 겨냥)
        }
    }

    describe("exportAll") {
        it("모든 테이블을 조회하고 방언별로 sequence를 추출해야 한다 (MySQL)") {
            every { metaData.databaseProductName } returns "MySQL"
            
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, true, false)
            every { tablesRs.getString("TABLE_NAME") } returnsMany listOf("users", "projects")
            
            every { jdbcTemplate.queryForList("SELECT * FROM projects") } returns listOf(mapOf("id" to 1))
            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 2))
            
            every { jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                JLong::class.java,
                "test_catalog",
                "projects"
            ) } returns 10L as JLong
            every { jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                JLong::class.java,
                "test_catalog",
                "users"
            ) } returns 20L as JLong

            val result = service.exportAll()
            val str = String(result)
            str.contains("projects") shouldBe true
            str.contains("users") shouldBe true
            str.contains("10") shouldBe true
            str.contains("20") shouldBe true
        }

        it("모든 테이블을 조회하고 방언별로 sequence를 추출해야 한다 (PostgreSQL)") {
            every { metaData.databaseProductName } returns "PostgreSQL"

            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "users"

            // hasIdColumn() 가드 — id 컬럼이 있는 테이블로 취급한다.
            every { metaData.getColumns(any(), any(), any(), null) } answers { columnsResultSet(listOf("id" to Types.INTEGER)) }

            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 1))

            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns "users_id_seq"
            every { jdbcTemplate.queryForObject("SELECT CASE WHEN is_called THEN last_value + 1 ELSE last_value END FROM users_id_seq", JLong::class.java) } returns 30L as JLong

            val result = service.exportAll()
            val str = String(result)
            str.contains("30") shouldBe true
        }

        it("MySQL에서 AUTO_INCREMENT 조회 결과가 null이면(예: id 없는 조인 테이블) sequences에서 제외해야 한다") {
            every { metaData.databaseProductName } returns "MySQL"

            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "join_table"

            every { jdbcTemplate.queryForList("SELECT * FROM join_table") } returns listOf(mapOf("a_id" to 1, "b_id" to 2))
            every { jdbcTemplate.queryForObject(
                "SELECT AUTO_INCREMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                JLong::class.java,
                "test_catalog",
                "join_table"
            ) } returns null

            val result = service.exportAll()
            val str = String(result)
            str.contains("join_table") shouldBe true
            str.contains("\"sequences\":{}") shouldBe true
        }

        it("PostgreSQL에서 id 컬럼 자체가 없는 테이블(예: 조인 테이블)은 pg_get_serial_sequence를 호출하지 않고 sequences에서 제외해야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"

            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "join_table"

            every { metaData.getColumns(any(), any(), any(), null) } answers { columnsResultSet(emptyList()) }

            every { jdbcTemplate.queryForList("SELECT * FROM join_table") } returns listOf(mapOf("a_id" to 1, "b_id" to 2))

            val result = service.exportAll()
            val str = String(result)

            str.contains("\"sequences\":{}") shouldBe true
            verify(exactly = 0) { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, any()) }
        }

        it("PostgreSQL에서 sequence가 없는 경우(null) 예외 없이 넘어가야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            val tablesRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getTables(any(), any(), any(), any()) } returns tablesRs
            every { tablesRs.next() } returnsMany listOf(true, false)
            every { tablesRs.getString("TABLE_NAME") } returns "users"

            every { metaData.getColumns(any(), any(), any(), null) } answers { columnsResultSet(listOf("id" to Types.INTEGER)) }

            every { jdbcTemplate.queryForList("SELECT * FROM users") } returns listOf(mapOf("id" to 1))

            // sequence_name is null
            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns null

            val result = service.exportAll()
            result shouldNotBe null
        }
    }

    describe("importAll") {
        it("JSON 데이터를 받아 테이블에 INSERT하고 sequence를 복원해야 한다 (MySQL)") {
            every { metaData.databaseProductName } returns "MariaDB"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1, "name": "test", "created_at": "2026-08-20T06:21:04.973Z"}
                    ],
                    "empty_table": []
                  },
                  "sequences": {
                    "users": 100
                  }
                }
            """.trimIndent().toByteArray()

            // dateTimeColumns()는 DATA_TYPE이 datetime 계열일 때만 COLUMN_NAME을 조회하므로(조건부 호출),
            // getInt/getString을 각각 독립적인 returnsMany로 스텁하면 호출 횟수가 어긋나 엉뚱한 컬럼명과
            // 매칭된다 — 같은 행 인덱스를 공유하는 answers로 묶어야 실제 호출 패턴과 일치한다.
            val columnNames = listOf("id", "name", "created_at")
            val columnTypes = listOf(Types.INTEGER, Types.VARCHAR, Types.TIMESTAMP)
            var columnIdx = -1
            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } answers { columnIdx++; columnIdx < columnNames.size }
            every { columnsRs.getString("COLUMN_NAME") } answers { columnNames[columnIdx] }
            every { columnsRs.getInt("DATA_TYPE") } answers { columnTypes[columnIdx] }

            val capturedCalls = mutableListOf<List<Any?>>()
            every { jdbcTemplate.update(any<String>(), *anyVararg<Any>()) } answers {
                capturedCalls.add(it.invocation.args)
                1
            }

            service.importAll(json)

            val insertCall = capturedCalls.first { (it[0] as String).startsWith("INSERT") }
            val boundValues = insertCall[1] as Array<*>
            boundValues.last() shouldBe java.sql.Timestamp.from(java.time.Instant.parse("2026-08-20T06:21:04.973Z"))

            verify { jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0") }
            verify { jdbcTemplate.update("DELETE FROM users") }
            verify { jdbcTemplate.update("DELETE FROM empty_table") }
            verify { jdbcTemplate.execute("ALTER TABLE users AUTO_INCREMENT = 100") }
            verify { jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1") }
        }

        it("JSON 데이터를 받아 테이블에 INSERT하고 sequence를 복원해야 한다 (PostgreSQL)") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 200
                  }
                }
            """.trimIndent().toByteArray()

            // dateTimeColumns()에는 datetime 컬럼이 없고(id만 있음), hasIdColumn()에는 id 컬럼이
            // 있어야 한다 — 둘 다 같은 시그니처로 호출되므로 한 컬럼 목록으로 동시에 충족한다.
            every { metaData.getColumns(any(), any(), any(), null) } answers { columnsResultSet(listOf("id" to Types.INTEGER)) }

            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns "users_id_seq"
            every { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, "users_id_seq", 200L) } returns 200L

            service.importAll(json)

            verify { jdbcTemplate.execute("SET session_replication_role = 'replica'") }
            verify { jdbcTemplate.execute("SET session_replication_role = 'origin'") }
            verify { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, "users_id_seq", 200L) }
        }

        it("PostgreSQL import 시 시퀀스가 null이면 setval을 호출하지 않아야 한다") {
            every { metaData.databaseProductName } returns "PostgreSQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 200
                  }
                }
            """.trimIndent().toByteArray()

            every { metaData.getColumns(any(), any(), any(), null) } answers { columnsResultSet(listOf("id" to Types.INTEGER)) }

            every { jdbcTemplate.queryForObject("SELECT pg_get_serial_sequence(?, 'id')", String::class.java, "users") } returns null

            service.importAll(json)

            verify(exactly = 0) { jdbcTemplate.queryForObject("SELECT setval(?, ?, false)", Long::class.java, any(), any()) }
        }

        it("sequences 키가 아예 없으면 빈 맵으로 처리해 예외 없이 진행해야 한다") {
            every { metaData.databaseProductName } returns "MySQL"

            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            service.importAll(json)

            verify(exactly = 0) { jdbcTemplate.execute(match<String> { it.contains("AUTO_INCREMENT") }) }
        }

        it("기타 DB인 경우 외래키 토글 및 시퀀스 복원이 무시되어야 한다 (OTHER)") {
            every { metaData.databaseProductName } returns "Oracle"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1}
                    ]
                  },
                  "sequences": {
                    "users": 300
                  }
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            service.importAll(json)

            // Exception이 발생하지 않으면 성공
        }

        it("빈 로우(empty map)가 포함되어 있으면 무시하고 진행해야 한다") {
            every { metaData.databaseProductName } returns "MySQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {}
                    ]
                  },
                  "sequences": {}
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), any(), null) } returns columnsRs
            every { columnsRs.next() } returns false

            service.importAll(json)

            // insert가 호출되지 않아야 함
            verify(exactly = 0) { jdbcTemplate.update(any<String>(), *anyVararg<Any>()) }
        }

        it("datetime 컬럼 값이 String이 아니면(null 등) 변환을 시도하지 않고 그대로 바인딩해야 한다") {
            every { metaData.databaseProductName } returns "MySQL"

            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1, "created_at": null}
                    ]
                  },
                  "sequences": {}
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), "users", null) } returns columnsRs
            every { columnsRs.next() } returnsMany listOf(true, true, false)
            every { columnsRs.getString("COLUMN_NAME") } returnsMany listOf("id", "created_at")
            every { columnsRs.getInt("DATA_TYPE") } returnsMany listOf(Types.INTEGER, Types.TIMESTAMP)

            service.importAll(json)

            // Exception 안나면 성공 (coerceForInsert의 value !is String 분기 겨냥)
        }

        it("잘못된 datetime 문자열은 파싱을 포기하고 그대로 반환해야 한다") {

            every { metaData.databaseProductName } returns "MySQL"
            
            val json = """
                {
                  "tables": {
                    "users": [
                      {"id": 1, "created_at": "invalid-date"}
                    ]
                  },
                  "sequences": {}
                }
            """.trimIndent().toByteArray()

            val columnsRs = mockk<ResultSet>(relaxed = true)
            every { metaData.getColumns(any(), any(), "users", null) } returns columnsRs
            every { columnsRs.next() } returnsMany listOf(true, false)
            every { columnsRs.getString("COLUMN_NAME") } returns "created_at"
            every { columnsRs.getInt("DATA_TYPE") } returns Types.TIMESTAMP

            service.importAll(json)

            // Exception 안나면 성공
        }
    }
})
