package dev.yona.domain.apitoken

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 토큰의 범위는 한 칸에 쉼표로 이은 문자열이 아니라 범위마다 한 행씩 `api_token_scope`에 저장한다. v1.6의 사용자 토큰에는 범위가 없었으므로
 * 옛 토큰은 범위 행 없이 옮기고, 접속할 때 범위 제한 없이 사용자의 실제 권한만 적용한다(`decisions.md` D-126).
 */
@Transactional
class ApiTokenScopeRowsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ApiTokenService
    @Autowired private lateinit var tokens: ApiTokenRepository
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(token: String? = null) =
        users.save(User(loginId = "sr${++sequence}", name = "sr$sequence", email = "sr$sequence@example.com", state = UserState.ACTIVE, token = token))

    private fun rows(id: Long?) =
        em.createNativeQuery("select scope from api_token_scope where api_token_id = :id").setParameter("id", id).resultList.map { it.toString() }.sorted()

    @Test
    fun `범위는 토큰마다 범위당 한 행씩 api_token_scope에 저장된다`() {
        val token = tokens.save(ApiToken(user = user(), name = "t", tokenHash = TokenHasher.sha256Hex("v1"), scopes = mutableSetOf("repo", "user"), isLegacy = false, createdDate = Date()))
        em.flush()
        assertEquals(listOf("repo", "user"), rows(token.id))
    }

    @Test
    fun `토큰을 지우면 범위 행도 함께 지워진다`() {
        val token = tokens.save(ApiToken(user = user(), name = "t", tokenHash = TokenHasher.sha256Hex("v2"), scopes = mutableSetOf("repo"), isLegacy = false, createdDate = Date()))
        em.flush()
        tokens.delete(token); em.flush()
        assertEquals(emptyList(), rows(token.id))
    }

    @Test
    fun `옛 토큰은 범위 행 없이 옮기고 접속하면 모든 범위로 본다`() {
        val owner = user("old-token-value-for-scope-rows-test-0123456789=")
        em.flush()
        assertEquals(1, service.migrateLegacyTokens())
        em.flush(); em.clear()
        val row = tokens.findByUserIdOrderByCreatedDateDescIdDesc(owner.id!!).single()
        assertTrue(row.scopes.isEmpty(), "v1.6 토큰에는 범위가 없었다")
        assertEquals(emptyList(), rows(row.id))
        assertEquals(ApiScopes.selectable(siteManager = true).toSet(), service.scopesOf(row), "범위 제한 없음: 사용자의 실제 권한만 적용된다")
    }

    @Test
    fun `범위를 고른 일반 토큰은 고른 범위만 가진다`() {
        val token = tokens.save(ApiToken(user = user(), name = "t", tokenHash = TokenHasher.sha256Hex("v3"), scopes = mutableSetOf("read:user"), isLegacy = false, createdDate = Date()))
        assertEquals(setOf("read:user"), service.scopesOf(token))
    }

    @Test
    fun `범위를 고쳐 저장한 옛 토큰은 고른 범위만 가진다`() {
        val owner = user("old-token-value-for-scope-edit-test-0123456789==")
        em.flush(); service.migrateLegacyTokens(); em.flush(); em.clear()
        val legacy = tokens.findByUserIdOrderByCreatedDateDescIdDesc(owner.id!!).single()
        val edited = service.update(users.findById(owner.id!!).get(), legacy.id!!, "이전 토큰", setOf("read:user"))
        assertTrue(edited is TokenEdit.Done)
        em.flush(); em.clear()
        val after = tokens.findById(legacy.id!!).get()
        assertEquals(setOf("read:user"), service.scopesOf(after))
        assertEquals(true, after.isLegacy, "값이 추측 가능하다는 표시는 그대로이다")
    }

    @Test
    fun `값을 새로 발급하면 옛 토큰은 그때의 모든 범위를 행으로 가진 일반 토큰이 된다`() {
        val owner = user("old-token-value-for-regenerate-test-0123456789=")
        em.flush(); service.migrateLegacyTokens(); em.flush(); em.clear()
        val legacy = tokens.findByUserIdOrderByCreatedDateDescIdDesc(owner.id!!).single()
        val result = service.regenerate(users.findById(owner.id!!).get(), legacy.id!!, null, siteManager = false)
        assertTrue(result is TokenCreation.Created)
        em.flush(); em.clear()
        val after = tokens.findById(legacy.id!!).get()
        assertFalse(after.isLegacy == true)
        assertEquals(ApiScopes.selectable(siteManager = false).toSet(), after.scopes.toSet())
        assertEquals(ApiScopes.selectable(siteManager = false).toList().sorted(), rows(after.id))
    }
}
