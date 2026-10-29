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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * v1.6의 사용자 토큰(`n4user.token`)을 새 토큰 테이블로 옮기는 보정을 검증한다(`decisions.md` D-36 5번). 같은 값이 계속 동작하고
 * 일반 Classic 형태(해시, 범위 목록)이며 만료가 없고 몇 번을 돌려도 결과가 같아야 한다.
 */
@Transactional
class ApiTokenMigrationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ApiTokenService
    @Autowired private lateinit var tokens: ApiTokenRepository
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(token: String?) = users.save(User(loginId = "mg${++sequence}", name = "mg$sequence", email = "mg$sequence@example.com", state = UserState.ACTIVE, token = token))

    @Test
    fun `옛 토큰은 해시로 옮기고 같은 값이 계속 동작하며 사용자 열은 비운다`() {
        val old = "Zm9vYmFyLXRpbWUtaGFzaC10b2tlbi1mb3ItdGVzdA=="
        val u = user(old); em.flush()
        assertEquals(1, service.migrateLegacyTokens())
        em.flush(); em.clear()
        val row = tokens.findByUserIdOrderByCreatedDateDescIdDesc(u.id!!).single()
        assertEquals(TokenHasher.sha256Hex(old), row.tokenHash); assertEquals(true, row.isLegacy); assertEquals(ApiTokenService.LEGACY_TOKEN_NAME, row.name)
        assertNull(row.expiresDate, "이관한 토큰은 만료가 없다"); assertEquals(old.take(4), row.tokenPrefix)
        val scopes = ApiScopes.parse(row.scopes)
        assertTrue(scopes.containsAll(ApiScopes.selectable(siteManager = true)), scopes.toString())
        assertNull(users.findById(u.id!!).get().token, "평문 열은 비운다")
        assertNotNull(service.authenticate(old), "같은 값이 계속 동작한다")
    }

    @Test
    fun `토큰이 없거나 빈 사용자는 건너뛰고 몇 번을 돌려도 같다`() {
        val withToken = user("abcdEFGHijklMNOPqrstUVWXyz0123456789abcdefg="); user(null); user(""); em.flush()
        assertEquals(1, service.migrateLegacyTokens())
        assertEquals(0, service.migrateLegacyTokens())
        em.flush(); em.clear()
        assertEquals(1, tokens.findByUserIdOrderByCreatedDateDescIdDesc(withToken.id!!).size)
    }

    @Test
    fun `이미 이관 행이 있는 사용자의 열은 다시 옮기지 않는다`() {
        val u = user(null)
        tokens.save(ApiToken(user = u, name = ApiTokenService.LEGACY_TOKEN_NAME, tokenHash = TokenHasher.sha256Hex("first"), tokenPrefix = "firs", scopes = "repo", isLegacy = true, createdDate = java.util.Date()))
        u.token = "second-value-from-an-old-export-file-xx=="; users.save(u); em.flush()
        assertEquals(0, service.migrateLegacyTokens())
        assertEquals(1, tokens.findByUserIdOrderByCreatedDateDescIdDesc(u.id!!).size)
        assertNull(users.findById(u.id!!).get().token, "다시 옮기지는 않지만 평문 열은 비운다")
    }
}
