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
    fun `가져온 뒤 없는 풀 리퀘스트나 사용자나 라벨을 가리키는 담당자와 라벨 연결 행은 정리한다 (X-14)`() {
        // v1.6 파일에는 이 두 테이블이 없어 풀 리퀘스트를 바꿔 가져오면 옛 연결 행이 엉뚱한 번호를 가리킬 수 있다
        import("""{"PULL_REQUEST_ASSIGNEE": [{"pull_request_id": 999001, "user_id": 999002}], "PULL_REQUEST_ISSUE_LABEL": [{"pull_request_id": 999001, "issue_label_id": 999003}]}""")
        assertEquals(0, count("pull_request_assignee"))
        assertEquals(0, count("pull_request_issue_label"))
    }

    @Test
    fun `가져온 뒤 없는 풀 리퀘스트나 사용자를 가리키는 리뷰 요청 행은 정리하고 내보내기에도 들어간다 (X-27)`() {
        import("""{"PULL_REQUEST_REVIEW_REQUEST": [{"pull_request_id": 999001, "user_id": 999002}]}""")
        assertEquals(0, count("pull_request_review_request"))
        assertEquals(true, "PULL_REQUEST_REVIEW_REQUEST" in exported().propertyNames().toList())
    }

    @Test
    fun `내보내기는 임시 저장 리뷰의 임시 댓글과 임시 스레드를 담지 않는다 (X-26)`() {
        fun thread(id: Int, commit: String) = """{"dtype": "ranged", "id": $id, "project_id": 99999, "author_id": null, "author_login_id": null, "author_name": null, "state": "OPEN", "commit_id": "$commit", "path": "x",
            "start_side": null, "start_line": null, "start_column": null, "end_side": null, "end_line": null, "end_column": null, "pull_request_id": null, "created_date": null, "prev_commit_id": null}"""
        fun comment(id: Int, text: String, thread: Int) = """{"id": $id, "contents": "$text", "created_date": null, "author_id": null, "author_login_id": null, "author_name": null, "thread_id": $thread}"""
        val result = import("""{"COMMENT_THREAD": [${thread(9101, "aaa")}, ${thread(9102, "bbb")}], "REVIEW_COMMENT": [${comment(9201, "공개", 9101)}, ${comment(9202, "임시 답글", 9101)}, ${comment(9203, "임시 스레드의 댓글", 9102)}]}""")
        assertEquals(emptyList<String>(), result.failed, result.toString())
        jdbc.update("update comment_thread set pending_date = current_timestamp where id = 9102")
        jdbc.update("update review_comment set pending_date = current_timestamp where id in (9202, 9203)")
        val node = exported()
        assertEquals(listOf(9101L), node.path("COMMENT_THREAD").values().map { row -> row.path("id").asLong() }.filter { id -> id in 9100..9199 })
        assertEquals(listOf(9201L), node.path("REVIEW_COMMENT").values().map { row -> row.path("id").asLong() }.filter { id -> id in 9200..9299 })
    }

    @Test
    fun `담당자와 라벨 연결 테이블도 내보내고 다시 가져온다 (X-14)`() {
        val names = exported().propertyNames().toList()
        assertEquals(true, "PULL_REQUEST_ASSIGNEE" in names && "PULL_REQUEST_ISSUE_LABEL" in names)
    }

    @Test
    fun `리뷰 테이블도 내보내고 가져오며 시퀀스를 함께 쓴다 (X-12)`() {
        val names = exported().propertyNames().toList()
        assertEquals(true, "PULL_REQUEST_REVIEW" in names && "PULL_REQUEST_REVIEW_SEQ" in names, names.toString())
        assertEquals(true, "is_dismissing_stale_approvals" in DataTables.find("PROJECT")!!.columns.map { it.name })
    }

    @Test
    fun `가져온 뒤 없는 풀 리퀘스트를 가리키는 리뷰 행은 정리한다 (X-12)`() {
        import("""{"PULL_REQUEST_REVIEW": [{"id": 1, "pull_request_id": 999001, "author_id": 1, "author_login_id": "x", "verdict": "APPROVED", "contents": null, "created_date": 1700000000000, "dismissed_date": 0, "dismissed_by_login_id": null, "dismissed_reason": null}]}""")
        assertEquals(0, count("pull_request_review"))
    }

    @Test
    fun `풀 리퀘스트는 있고 리뷰 테이블이 없는 옛 파일을 가져오면 남아 있던 리뷰는 비운다 (X-12)`() {
        // 옛 파일의 풀 리퀘스트 번호는 바뀔 수 있어 남은 리뷰가 엉뚱한 요청에 붙으면 안 된다. 리뷰는 승인 집합에서 다시 만든다(D-35 13번)
        jdbc.update("insert into pull_request(id, title) values (?, ?)", 777001L, "옛")
        jdbc.update("insert into pull_request_review(id, pull_request_id, author_id, author_login_id, verdict, created_date) values (?, ?, ?, ?, ?, current_timestamp)", 1L, 777001L, 1L, "x", "APPROVED")
        import("""{"PULL_REQUEST": [{"id": 777001, "title": "옛"}]}""")
        assertEquals(0, count("pull_request_review"))
    }

    @Test
    fun `v1_6 파일의 프로젝트 행에는 리뷰 해제 설정 키가 없어도 가져오고 켬으로 채운다 (X-12)`() {
        val table = DataTables.find("PROJECT")!!
        val row = table.columns.filter { it.name !in setOf("is_dismissing_stale_approvals", "allow_merge_commit", "allow_squash_merge", "allow_rebase_merge", "default_merge_method") }.joinToString(", ") { c ->
            val value = when (c.type) {
                DataType.STRING, DataType.CLOB -> "\"v\""
                DataType.BOOLEAN -> "false"
                DataType.STRING_LONG -> "\"1\""
                else -> "1"
            }
            "\"${c.key}\": " + if (c.name == "id") "881001" else value
        }
        import("""{"PROJECT": [{$row}]}""")
        assertEquals(true, jdbc.queryForObject("select is_dismissing_stale_approvals from project where id = 881001", Boolean::class.java))
        assertEquals(false, jdbc.queryForObject("select is_using_reviewer_count from project where id = 881001", Boolean::class.java), "있는 키는 그대로 가져온다")
        // 병합 방식 설정(X-28, D-41)이 없는 v1.6 파일은 병합 커밋만 허용으로 읽는다(v1.6 동작)
        assertEquals(true, jdbc.queryForObject("select allow_merge_commit from project where id = 881001", Boolean::class.java))
        assertEquals(false, jdbc.queryForObject("select allow_squash_merge from project where id = 881001", Boolean::class.java))
        assertEquals(false, jdbc.queryForObject("select allow_rebase_merge from project where id = 881001", Boolean::class.java))
        assertEquals("merge", jdbc.queryForObject("select default_merge_method from project where id = 881001", String::class.java))
    }

    @Test
    fun `가져온 뒤 승인 집합에서 리뷰를 다시 만든다 (X-12, D-35 13번)`() {
        jdbc.update("insert into n4user(id, login_id, name) values (?, ?, ?)", 881101L, "bfimp", "보정")
        jdbc.update("insert into pull_request(id, title, number, created) values (?, ?, ?, current_timestamp)", 881102L, "옛", 1L)
        jdbc.update("insert into pull_request_reviewers(pull_request_id, user_id) values (?, ?)", 881102L, 881101L)
        import("{}")
        assertEquals(1, jdbc.queryForObject("select count(*) from pull_request_review where pull_request_id = 881102 and author_id = 881101 and verdict = 'APPROVED'", Int::class.java))
        import("{}")
        assertEquals(1, jdbc.queryForObject("select count(*) from pull_request_review where pull_request_id = 881102", Int::class.java), "다시 가져와도 중복이 생기지 않는다")
    }

    @Test
    fun `이슈 댓글은 v1_6 파일처럼 project_id 키가 없어도 이슈의 프로젝트로 채워 가져온다 (R-130)`() {
        val issue = """{"id": 880001, "title": "t", "body": "b", "created_date": 1700000000000, "num_of_comments": 1, "milestone_id": null, "author_id": 1, "author_login_id": "a", "author_name": "A",
            "state": 1, "project_id": 880002, "assignee_id": null, "number": 1, "updated_date": 1700000000000, "due_date": null}"""
        val comment = """{"id": 880003, "created_date": 1700000000000, "author_id": 1, "author_login_id": "a", "author_name": "A", "issue_id": 880001, "contents": "hello"}"""
        val result = import("""{"ISSUE": [$issue], "ISSUE_COMMENT": [$comment]}""")
        assertEquals(listOf("ISSUE", "ISSUE_COMMENT"), result.imported.filter { it.startsWith("ISSUE") })
        assertTrue(result.failed.none { it == "ISSUE_COMMENT" }, result.failed.toString())
        assertEquals(880002L, jdbc.queryForObject("select project_id from issue_comment where id = 880003", Long::class.java))
    }

    @Test
    fun `내보내기는 v1_6과 같이 이슈 댓글에 project_id 키를 쓰지 않는다 (R-130)`() {
        val first = exported()["ISSUE_COMMENT"].firstOrNull()
        if (first != null) assertFalse(first.propertyNames().toList().contains("project_id"), first.propertyNames().toList().toString())
        assertEquals(listOf("id", "created_date", "author_id", "author_login_id", "author_name", "issue_id", "contents"), DataTables.find("ISSUE_COMMENT")!!.columns.filter { it.derivedFrom == null }.map { it.name })
    }

    @Test
    fun `프로젝트 메뉴 설정은 v1_6 파일처럼 wiki 키가 없어도 꺼짐으로 가져온다 (D-61)`() {
        val project = """{"id": 880010, "name": "wk", "owner": "a", "overview": "", "vcs": "GIT", "is_public": true, "created_date": 1700000000000, "last_issue_number": 0, "last_posting_number": 0}"""
        val menu = """{"id": 880011, "project_id": 880010, "code": true, "issue": true, "pull_request": true, "review": true, "milestone": true, "board": true}"""
        val result = import("""{"PROJECT": [$project], "PROJECT_MENU_SETTING": [$menu]}""")
        assertTrue(result.failed.none { it == "PROJECT_MENU_SETTING" }, result.failed.toString())
        assertEquals(false, jdbc.queryForObject("select wiki from project_menu_setting where id = 880011", Boolean::class.java))
    }

    @Test
    fun `프로젝트 메뉴 설정을 내보낼 때 wiki 컬럼이 들어 있다 (D-61)`() {
        assertEquals(listOf("id", "project_id", "code", "issue", "pull_request", "review", "milestone", "board", "wiki"), DataTables.find("PROJECT_MENU_SETTING")!!.columns.map { it.name })
    }

    @Test
    fun `토큰 테이블도 내보내고 가져오며 시퀀스를 함께 쓴다 (X-5)`() {
        val names = exported().propertyNames().toList()
        assertEquals(true, "API_TOKEN" in names && "API_TOKEN_SEQ" in names, names.toString())
    }

    @Test
    fun `가져온 뒤 없는 사용자를 가리키는 토큰 행은 정리한다 (X-5)`() {
        import("""{"API_TOKEN": [{"id": 1, "user_id": 999001, "name": "x", "token_hash": "abc", "token_prefix": "yona", "scopes": "repo", "is_legacy": false, "created_date": 1700000000000, "expires_date": 0, "last_used_date": 0}]}""")
        assertEquals(0, count("api_token"))
    }

    @Test
    fun `사용자는 있고 토큰 테이블이 없는 옛 파일을 가져오면 남아 있던 토큰은 비운다 (X-5)`() {
        // v1.6의 사용자 내보내기에는 `token` 열이 없으므로(옛 파일로 가져오면 토큰을 잃는 것은 v1.6과 같다) 이관할 값도 없다.
        // 옛 파일의 사용자 번호가 바뀔 수 있어 남은 토큰이 엉뚱한 사용자에 붙지 않게 비운다
        jdbc.update("insert into n4user(id, login_id, name) values (?, ?, ?)", 881201L, "tkold", "옛")
        jdbc.update("insert into api_token(id, user_id, name, token_hash, token_prefix, scopes, is_legacy, created_date) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)", 1L, 881201L, "남은", "h", "p", "repo", false)
        val row = DataTables.find("N4USER")!!.columns.joinToString(", ") { c ->
            val value = when (c.name) {
                "id" -> "881201"
                "login_id" -> "\"tkold\""
                else -> when (c.type) {
                    DataType.STRING, DataType.CLOB -> "\"v${c.name}\""
                    DataType.BOOLEAN -> "false"
                    DataType.STRING_LONG -> "\"1\""
                    else -> "1"
                }
            }
            "\"${c.key}\": $value"
        }
        import("""{"N4USER": [{$row}]}""")
        assertEquals(0, jdbc.queryForObject("select count(*) from api_token", Int::class.java))
    }

    @Test
    fun `GPG 키 테이블도 내보내고 가져오며 시퀀스를 함께 쓴다 (X-4)`() {
        val names = exported().propertyNames().toList()
        assertEquals(true, "GPG_KEY" in names && "GPG_KEY_SEQ" in names, names.toString())
    }

    @Test
    fun `가져온 뒤 없는 사용자를 가리키는 GPG 키 행은 정리한다 (X-4)`() {
        import("""{"GPG_KEY": [{"id": 1, "user_id": 999001, "parent_id": null, "name": "x", "fingerprint": "AB", "key_id": "CD", "public_key": "k", "emails": "", "can_sign": true, "created_date": 1700000000000, "expires_date": 0}]}""")
        assertEquals(0, count("gpg_key"))
    }

    @Test
    fun `사용자는 있고 GPG 키 테이블이 없는 옛 파일을 가져오면 남아 있던 키는 비운다 (X-4)`() {
        jdbc.update("insert into n4user(id, login_id, name) values (?, ?, ?)", 881301L, "gkold", "옛")
        jdbc.update("insert into gpg_key(id, user_id, name, fingerprint, key_id, public_key, can_sign, created_date) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)", 1L, 881301L, "남은", "FP1", "KID1", "k", true)
        val row = DataTables.find("N4USER")!!.columns.joinToString(", ") { c ->
            val value = when (c.name) {
                "id" -> "881301"
                "login_id" -> "\"gkold\""
                else -> when (c.type) {
                    DataType.STRING, DataType.CLOB -> "\"v${c.name}\""
                    DataType.BOOLEAN -> "false"
                    DataType.STRING_LONG -> "\"1\""
                    else -> "1"
                }
            }
            "\"${c.key}\": $value"
        }
        import("""{"N4USER": [{$row}]}""")
        assertEquals(0, count("gpg_key"))
    }

    @Test
    fun `하위 키가 있는 GPG 키 행을 내보내고 다시 가져온다 (X-4)`() {
        jdbc.update("insert into n4user(id, login_id, name) values (?, ?, ?)", 881302L, "gkrt", "왕복")
        jdbc.update("insert into gpg_key(id, user_id, name, fingerprint, key_id, public_key, emails, can_sign, created_date) values (?, ?, ?, ?, ?, ?, ?, ?, current_timestamp)", 11L, 881302L, "주", "FPA", "KIDA", "armored", "a@example.com", true)
        jdbc.update("insert into gpg_key(id, user_id, parent_id, fingerprint, key_id, can_sign, created_date) values (?, ?, ?, ?, ?, ?, current_timestamp)", 12L, 881302L, 11L, "FPB", "KIDB", true)
        val bytes = exportBytes()
        jdbc.update("delete from gpg_key where parent_id is not null"); jdbc.update("delete from gpg_key")
        service.import(ByteArrayInputStream(bytes))
        assertEquals(listOf("FPA", "FPB"), jdbc.queryForList("select fingerprint from gpg_key where user_id = 881302 order by id", String::class.java))
        assertEquals(11L, jdbc.queryForObject("select parent_id from gpg_key where id = 12", Long::class.java))
    }

    @Test
    fun `가져온 뒤 없는 사용자와 없는 프로젝트를 가리키는 SSH 키와 배포 키 행은 정리한다 (X-3)`() {
        import("""{"SSH_KEY": [{"id": 1, "user_id": 999002, "name": "x", "fingerprint": "SHA256:a", "key_type": "ssh-ed25519", "public_key": "k", "created_date": 1700000000000}],
            "DEPLOY_KEY": [{"id": 1, "project_id": 999003, "name": "x", "fingerprint": "SHA256:b", "key_type": "ssh-ed25519", "public_key": "k", "read_only": true, "created_date": 1700000000000}]}""")
        assertEquals(0, count("ssh_key")); assertEquals(0, count("deploy_key"))
    }

    @Test
    fun `SSH 키와 배포 키를 내보내고 다시 가져온다 (X-3)`() {
        jdbc.update("insert into n4user(id, login_id, name) values (?, ?, ?)", 881303L, "skrt", "왕복")
        jdbc.update("insert into project(id, name, owner) values (?, ?, ?)", 881304L, "skrt-project", "skrt")
        jdbc.update("insert into ssh_key(id, user_id, name, fingerprint, key_type, public_key, created_date) values (?, ?, ?, ?, ?, ?, current_timestamp)", 21L, 881303L, "노트북", "SHA256:RT1", "ssh-ed25519", "ssh-ed25519 AAAA")
        jdbc.update("insert into deploy_key(id, project_id, name, fingerprint, key_type, public_key, read_only, created_date) values (?, ?, ?, ?, ?, ?, ?, current_timestamp)", 22L, 881304L, "배포", "SHA256:RT2", "ssh-ed25519", "ssh-ed25519 BBBB", false)
        val bytes = exportBytes()
        jdbc.update("delete from ssh_key"); jdbc.update("delete from deploy_key")
        service.import(ByteArrayInputStream(bytes))
        assertEquals(listOf("SHA256:RT1"), jdbc.queryForList("select fingerprint from ssh_key where user_id = 881303", String::class.java))
        assertEquals(false, jdbc.queryForObject("select read_only from deploy_key where id = 22", Boolean::class.java))
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
    fun `행에 컬럼 키가 없으면 그 테이블은 빈 채로 남고 다른 테이블은 계속한다 (v1_6 TRUNCATE는 즉시 확정)`() {
        jdbc.update("insert into label(name, category) values ('stay', 'c')")
        import("""{"LABEL": [{"id": 1, "category": "x"}], "PROPERTY": [{"id": 1, "name": "MAILBOX_LAST_SEEN_UID", "value": "ok"}]}""")
        // 비우기는 이미 확정되었고 행 넣기만 되돌려지므로 이전 행도 남지 않는다 (P-10 결정 5)
        assertEquals(emptyList(), jdbc.queryForList("select name from label", String::class.java))
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
