package dev.yona.schema

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 엔티티가 만들어 내는 스키마가 Yona v1.6의 최종 스키마(evolutions 1~32 적용 결과)와 같은 구조인지
 * 검증한다. `-Dyona.it.db=h2|postgres|mariadb`로 DB를 바꿔 같은 검증을 반복한다.
 *
 * 기준 파일은 v1.6 evolutions의 `Up` 구간을 MariaDB 10.11에 순서대로 적용한 뒤 `information_schema`에서
 * 추출한 것이다(`src/test/resources/schema/`). 비교 항목은 다음과 같다.
 *
 * - 테이블 집합
 * - 컬럼 이름, NULL 허용 여부, 타입 종류, `VARCHAR` 길이
 * - 인덱스(이름, 컬럼 순서, 유니크 여부)
 * - 외래 키의 참조 대상과 삭제 규칙
 *
 * - 컬럼 기본값과 자동 증가 여부
 * - 외래 키의 갱신(`ON UPDATE`) 규칙
 *
 * 비교하지 않는 항목은 외래 키 이름과 문자 집합이다.
 */
class V16SchemaTest : AbstractIntegrationTest() {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Test
    fun `v1_6의 테이블 집합과 같다`() {
        val expected = SchemaMetadata.v16Columns().keys
        val actual = SchemaMetadata.actualColumns(dataSource).keys
        assertEquals(emptySet(), expected - actual, "누락된 테이블")
        assertEquals(emptySet(), actual - expected, "추가된 테이블")
    }

    @Test
    fun `모든 컬럼의 이름과 NULL 허용 여부가 v1_6과 같다`() {
        val actual = SchemaMetadata.actualColumns(dataSource)
        val problems = SchemaMetadata.v16Columns().flatMap { (table, columns) ->
            val actualTable = actual[table] ?: return@flatMap emptyList()
            (columns.keys - actualTable.keys).map { "$table.$it 누락" } +
                (actualTable.keys - columns.keys).map { "$table.$it 추가됨" } +
                columns.filter { (c, v) -> actualTable[c]?.let { it.nullable != v.nullable } == true }
                    .map { (c, v) -> "$table.$c NULL 허용 기대=${v.nullable}" }
        }
        assertTrue(problems.isEmpty(), "컬럼 불일치 ${problems.size}건:\n" + problems.sorted().joinToString("\n"))
    }

    @Test
    fun `모든 컬럼의 타입 종류와 문자열 길이가 v1_6과 같다`() {
        val actual = SchemaMetadata.actualColumns(dataSource)
        val problems = SchemaMetadata.v16Columns().flatMap { (table, columns) ->
            columns.mapNotNull { (c, v) ->
                val a = actual[table]?.get(c) ?: return@mapNotNull null
                if (SchemaMetadata.sameType(v.type, a.type)) null else "$table.$c 기대=${v.type} 실제=${a.type}"
            }
        }
        assertTrue(problems.isEmpty(), "타입 불일치 ${problems.size}건:\n" + problems.sorted().joinToString("\n"))
    }

    @Test
    fun `인덱스의 이름과 컬럼 순서와 유니크 여부가 v1_6과 같다`() {
        // MariaDB 10.7 이하는 DESC 인덱스 문법을 받아도 무시하고 오름차순으로 만든다. v1.6의 MariaDB도 같으므로 방향은 H2와 PostgreSQL에서만 비교한다.
        val directional = dataSource.connection.use { !it.metaData.databaseProductName.contains("MariaDB", ignoreCase = true) && !it.metaData.databaseProductName.contains("MySQL", ignoreCase = true) }
        fun Map<String, Map<String, IndexInfo>>.normalized() = if (directional) this else mapValues { (_, v) -> v.mapValues { (_, i) -> i.copy(descending = emptySet()) } }
        val actual = SchemaMetadata.actualIndexes(dataSource).normalized()
        val expected = SchemaMetadata.v16Indexes().normalized()
        val problems = (expected.keys + actual.keys).sorted().flatMap { table ->
            val e = expected[table].orEmpty()
            val a = actual[table].orEmpty()
            (e.keys - a.keys).map { "$table.$it 누락 ${e[it]}" } +
                (a.keys - e.keys).map { "$table.$it 추가됨 ${a[it]}" } +
                e.filter { (n, v) -> a[n]?.let { it != v } == true }
                    .map { (n, v) -> "$table.$n 기대=$v 실제=${a[n]}" }
        }
        assertTrue(problems.isEmpty(), "인덱스 불일치 ${problems.size}건:\n" + problems.joinToString("\n"))
    }

    @Test
    fun `외래 키의 대상과 삭제 규칙이 v1_6과 같다`() {
        val actual = SchemaMetadata.actualForeignKeys(dataSource)
        val expected = SchemaMetadata.v16ForeignKeys()
        val problems = (expected.keys + actual.keys).sorted().flatMap { table ->
            val e = expected[table].orEmpty()
            val a = actual[table].orEmpty()
            (e.keys - a.keys).map { "$table.$it 누락 ${e[it]}" } +
                (a.keys - e.keys).map { "$table.$it 추가됨 ${a[it]}" } +
                e.filter { (c, v) -> a[c]?.let { it != v } == true }
                    .map { (c, v) -> "$table.$c 기대=$v 실제=${a[c]}" }
        }
        assertTrue(problems.isEmpty(), "외래 키 불일치 ${problems.size}건:\n" + problems.joinToString("\n"))
    }

    /**
     * v1.6의 20.sql은 모든 enum CHECK 제약을 제거해 새 enum 값을 저장할 수 있게 했다.
     * 새 DB에서도 enum 컬럼은 CHECK 제약이나 DB 고유 enum 타입 없이 저장해야 한다.
     * 문자열 enum(`property.name`)과 서수 enum(`issue.state`, `webhook.webhook_type`)을 모두 확인한다.
     */
    @Test
    fun `enum 컬럼은 정의되지 않은 값도 저장할 수 있다`() {
        val quote = dataSource.connection.use { it.metaData.identifierQuoteString.trim() }
        jdbc.update("insert into property (name, ${quote}value$quote) values (?, ?)", "FUTURE_KEY", "x")
        jdbc.update("insert into issue (state) values (?)", 99)
        jdbc.update("insert into webhook (webhook_type) values (?)", 99)
        assertEquals(1, jdbc.queryForObject("select count(*) from property where name = 'FUTURE_KEY'", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select count(*) from issue where state = 99", Int::class.java))
        assertEquals(1, jdbc.queryForObject("select count(*) from webhook where webhook_type = 99", Int::class.java))
    }

    @Test
    fun `컬럼 기본값이 v1_6과 같다`() {
        val actual = SchemaMetadata.actualDefaults(dataSource)
        val problems = SchemaMetadata.v16Defaults().flatMap { (table, columns) ->
            columns.mapNotNull { (c, expected) ->
                val a = actual[table]?.get(c)
                if (actual[table]?.containsKey(c) == true && a != expected) "$table.$c 기대=$expected 실제=$a" else null
            }
        }
        assertTrue(problems.isEmpty(), "기본값 불일치 ${problems.size}건:\n" + problems.sorted().joinToString("\n"))
    }

    @Test
    fun `자동 증가 컬럼이 v1_6과 같다`() {
        assertEquals(SchemaMetadata.v16AutoIncrement(), SchemaMetadata.actualAutoIncrement(dataSource))
    }

    @Test
    fun `외래 키의 갱신 규칙이 v1_6과 같다`() {
        val actual = SchemaMetadata.actualUpdateRules(dataSource)
        val problems = SchemaMetadata.v16UpdateRules().flatMap { (table, columns) ->
            columns.mapNotNull { (c, expected) -> actual[table]?.get(c)?.takeIf { it != expected }?.let { "$table.$c 기대=$expected 실제=$it" } }
        }
        assertTrue(problems.isEmpty(), "갱신 규칙 불일치 ${problems.size}건:\n" + problems.sorted().joinToString("\n"))
    }
}
