package dev.yona.domain.apitoken

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Date
import kotlin.test.assertEquals

/**
 * 이전 빌드가 쉼표로 이은 문자열로 저장한 범위(`api_token.scopes`, `device_authorization.scopes`)를 범위 행으로 옮기는 이관을 검증한다.
 * 새 엔티티에는 그 열이 없으므로 시험이 열을 직접 만들었다가 지운다. DDL은 트랜잭션에 묶이지 않으므로 이 시험은 트랜잭션 없이 돈다.
 */
class ScopeColumnMigratorTest : AbstractIntegrationTest() {

    @Autowired private lateinit var migrator: ScopeColumnMigrator
    @Autowired private lateinit var tokens: ApiTokenRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var jdbc: JdbcTemplate
    @PersistenceContext private lateinit var em: EntityManager

    private val created = mutableListOf<Long>()

    @BeforeEach
    fun addOldColumn() {
        jdbc.execute("alter table api_token add column scopes varchar(1000)")
    }

    @AfterEach
    fun dropOldColumn() {
        created.forEach { id -> jdbc.update("delete from api_token_scope where api_token_id = ?", id); jdbc.update("delete from api_token where id = ?", id) }
        jdbc.execute("alter table api_token drop column scopes")
    }

    private fun token(raw: String?, legacy: Boolean): Long {
        val owner = users.save(User(loginId = "sc${System.nanoTime()}", name = "sc", email = "sc${System.nanoTime()}@example.com", state = UserState.ACTIVE))
        val t = tokens.save(ApiToken(user = owner, name = "t", tokenHash = TokenHasher.sha256Hex("sc${System.nanoTime()}"), isLegacy = legacy, createdDate = Date()))
        jdbc.update("update api_token set scopes = ? where id = ?", raw, t.id)
        created += t.id!!
        return t.id!!
    }

    private fun rows(id: Long): List<String> = jdbc.query("select scope from api_token_scope where api_token_id = ?", { rs, _ -> rs.getString(1) }, id).sorted()

    @Test
    fun `쉼표 문자열의 범위를 행으로 옮기고 옛 열은 비운다`() {
        val id = token("repo,user", legacy = false)
        assertEquals(1L, migrator.pending())
        assertEquals(1, migrator.migrate())
        assertEquals(listOf("repo", "user"), rows(id))
        assertEquals(null, jdbc.query("select scopes from api_token where id = ?", { rs, _ -> rs.getString(1) }, id).single())
        assertEquals(0L, migrator.pending())
    }

    @Test
    fun `모든 범위가 적힌 옛 토큰은 범위 행 없이 범위 제한 없음으로 옮긴다`() {
        val id = token(ApiScopes.format(ApiScopes.selectable(siteManager = true)), legacy = true)
        migrator.migrate()
        assertEquals(emptyList(), rows(id))
    }

    @Test
    fun `이미 범위 행이 있으면 다시 만들지 않고 몇 번을 돌려도 같다`() {
        val id = token("repo", legacy = false)
        jdbc.update("insert into api_token_scope (api_token_id, scope) values (?, 'user')", id)
        migrator.migrate(); migrator.migrate()
        assertEquals(listOf("user"), rows(id), "행이 이미 있는 토큰은 그대로 둔다")
    }
}
