package dev.yona.domain.site

import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.Timestamp
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 데이터 내보내기와 가져오기(`DataService`)를 검증한다. 가져오기는 테이블을 비우고 다시 채우므로 이 테스트는 트랜잭션으로 감싸지 않고,
 * 시작할 때 내보낸 파일로 끝날 때 원래 상태를 되돌린다.
 *
 * 원본: `app/data/DataService.java`, `app/data/DefaultExchanger.java`, `app/data/exchangers` 아래 클래스들, `docs/ko/spec/export-and-import.md`.
 */
class DataExchangeTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: DataService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val mapper = JsonMapper.builder().build()

    /** `value`는 예약어라 따옴표가 필요하다. */
    private val value = if (dev.yona.support.TestDatabase.current == dev.yona.support.TestDatabase.MARIADB) "`value`" else "\"value\""
    private lateinit var baseline: ByteArray

    @BeforeEach
    fun saveBaseline() { baseline = exportBytes() }

    @AfterEach
    fun restoreBaseline() { service.import(ByteArrayInputStream(baseline)) }

    private fun exportBytes(): ByteArray = ByteArrayOutputStream().also { service.export(it) }.toByteArray()

    private fun exported(): JsonNode = mapper.readTree(exportBytes())

    private fun import(json: String) = service.import(ByteArrayInputStream(json.toByteArray(Charsets.UTF_8)))

    private fun count(table: String): Int = jdbc.queryForObject("select count(*) from $table", Int::class.java)!!

    // --- 내보내기 ---

    @Test
    fun `테이블 이름 순서로 쓰고 시퀀스가 있는 테이블은 시퀀스 필드가 이어진다`() {
        val expected = DataTables.all.flatMap { if (it.hasSequence) listOf(it.name, it.sequenceName) else listOf(it.name) }
        assertEquals(expected, exported().propertyNames().toList())
    }

    @Test
    fun `행은 컬럼 순서대로 소문자 키로 쓰고 사용자 테이블은 열한 개 컬럼이다`() {
        val users = exported()["N4USER"]
        assertTrue(users.size() >= 1)
        assertEquals(
            listOf("id", "name", "login_id", "password", "password_salt", "email", "remember_me", "created_date", "state", "last_state_modified_date", "lang"),
            users[0].propertyNames().toList()
        )
        assertEquals("admin", users[0]["login_id"].asString())
    }

    @Test
    fun `시각은 에폭 밀리초이고 NULL은 null이며 불린의 NULL은 false이다`() {
        jdbc.update("insert into organization(name, descr, created) values ('dx-org', null, ?)", Timestamp(1_700_000_000_000L))
        jdbc.update("insert into role(id, name, active) values (900, 'dx-role', null)")
        val tree = exported()
        val organization = tree["ORGANIZATION"].first { it["name"].asString() == "dx-org" }
        assertEquals(1_700_000_000_000L, organization["created"].asLong())
        assertTrue(organization["descr"].isNull)
        assertFalse(tree["ROLE"].first { it["id"].asLong() == 900L }["active"].asBoolean())
    }

    @Test
    fun `첨부는 컨테이너 종류를 content_type 키로 쓰고 컨테이너 번호는 문자열이다`() {
        jdbc.update("insert into attachment(name, hash, container_type, mime_type, size, container_id, created_date) values ('a.txt', 'h1', 'ISSUE_POST', 'text/plain', 5, 7, ?)", Timestamp(1_700_000_000_000L))
        val row = exported()["ATTACHMENT"].first { it["name"].asString() == "a.txt" }
        assertEquals("ISSUE_POST", row["content_type"].asString())
        assertTrue(row["container_id"].isString)
        assertEquals("7", row["container_id"].asString())
        assertTrue(row["created_date"].isNumber)
        assertFalse(row.has("container_type"))
    }

    // --- 가져오기 ---

    @Test
    fun `루트가 객체가 아니면 아무것도 바꾸지 않는다`() {
        jdbc.update("insert into label(name, category) values ('keep', 'c')")
        import("[1, 2, 3]")
        assertEquals(1, count("label"))
    }

    @Test
    fun `파일에 있는 테이블만 비우고 다시 채우며 모르는 필드는 건너뛴다`() {
        jdbc.update("insert into label(name, category) values ('old', 'c')")
        jdbc.update("insert into property(name, $value) values ('MAILBOX_LAST_SEEN_UID', 'keep-me')")
        import("""{"UNKNOWN_TABLE": [{"a": 1}], "LABEL": [{"id": 71, "name": "new", "category": "k"}], "LABEL_SEQ": 80}""")
        assertEquals(listOf("new"), jdbc.queryForList("select name from label", String::class.java))
        assertEquals(1, count("property"))
    }

    @Test
    fun `테이블 이름은 대소문자를 구분하지 않고 찾는다`() {
        import("""{"label": [{"id": 72, "name": "lower", "category": null}]}""")
        assertEquals(listOf("lower"), jdbc.queryForList("select name from label", String::class.java))
    }

    @Test
    fun `JSON null은 정수 컬럼에서 허용 여부에 따라 NULL이나 0이 된다`() {
        import("""{"MENTION": [{"id": 1, "resource_type": "ISSUE_POST", "resource_id": null, "user_id": null}],
                    "ASSIGNEE": [{"id": 1, "user_id": null, "project_id": null}]}""")
        assertNull(jdbc.queryForObject("select user_id from mention", Long::class.javaObjectType))
        assertNull(jdbc.queryForObject("select resource_id from mention", String::class.java))
        assertEquals(0L, jdbc.queryForObject("select user_id from assignee", Long::class.java))
        assertEquals(0L, jdbc.queryForObject("select project_id from assignee", Long::class.java))
    }

    @Test
    fun `시각 0은 NULL이고 불린 null은 false이며 문자열이 아닌 값은 NULL이다`() {
        import("""{"ORGANIZATION": [{"id": 5, "name": 123, "descr": "d", "created": 0}],
                    "ROLE": [{"id": 905, "name": "r", "active": null}]}""")
        assertNull(jdbc.queryForObject("select created from organization", Timestamp::class.java))
        assertNull(jdbc.queryForObject("select name from organization", String::class.java))
        assertEquals(false, jdbc.queryForObject("select active from role where id = 905", Boolean::class.java))
    }

    @Test
    fun `시퀀스 값을 복원해 다음 행이 그 번호부터 만들어진다`() {
        import("""{"PROPERTY": [{"id": 1, "name": "MAILBOX_LAST_SEEN_UID", "value": "v"}], "PROPERTY_SEQ": 500}""")
        jdbc.update("insert into property(name, $value) values ('MAILBOX_LAST_UID_VALIDITY', 'next')")
        assertTrue(jdbc.queryForObject("select max(id) from property", Long::class.java)!! >= 500L)
    }

    @Test
    fun `행에 컬럼 키가 없으면 그 테이블만 되돌리고 다른 테이블은 계속한다`() {
        jdbc.update("insert into label(name, category) values ('stay', 'c')")
        import("""{"LABEL": [{"id": 1, "category": "x"}], "PROPERTY": [{"id": 1, "name": "MAILBOX_LAST_SEEN_UID", "value": "ok"}]}""")
        assertEquals(listOf("stay"), jdbc.queryForList("select name from label", String::class.java))
        assertEquals(listOf("ok"), jdbc.queryForList("select $value from property", String::class.java))
    }

    @Test
    fun `가져오기는 실패한 테이블을 알려 준다`() {
        val result = import("""{"LABEL": [{"id": 1}], "PROPERTY": [{"id": 1, "name": "MAILBOX_LAST_SEEN_UID", "value": "ok"}]}""")
        assertEquals(listOf("LABEL"), result.failed)
        assertEquals(listOf("PROPERTY"), result.imported)
    }

    // --- 왕복 ---

    @Test
    fun `내보낸 파일을 가져오면 모든 테이블이 그대로 돌아온다`() {
        jdbc.update("insert into organization(name, descr, created) values ('rt-org', '설명', ?)", Timestamp(1_700_000_000_000L))
        jdbc.update("insert into label(name, category) values ('rt-label', 'cat')")
        jdbc.update("insert into attachment(name, hash, container_type, mime_type, size, container_id, created_date) values ('rt.txt', 'h2', 'ISSUE_POST', 'text/plain', 5, 7, ?)", Timestamp(1_700_000_000_000L))
        val before = exportBytes()
        jdbc.update("delete from organization"); jdbc.update("delete from label"); jdbc.update("delete from attachment")
        assertEquals(0, count("label"))
        import(String(before, Charsets.UTF_8))
        assertEquals(listOf("rt-label"), jdbc.queryForList("select name from label", String::class.java))
        assertEquals(listOf("설명"), jdbc.queryForList("select descr from organization where name = 'rt-org'", String::class.java))
        assertEquals("7", jdbc.queryForObject("select cast(container_id as varchar(20)) from attachment where name = 'rt.txt'", String::class.java))
        assertEquals(mapper.readTree(before)["N4USER"], exported()["N4USER"])
        assertEquals(mapper.readTree(before).propertyNames().toList(), exported().propertyNames().toList())
    }
}
