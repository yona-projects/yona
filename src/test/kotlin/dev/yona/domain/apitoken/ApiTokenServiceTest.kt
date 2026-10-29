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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 개인용 API 토큰 서비스를 검증한다(로드맵 X-5, `decisions.md` D-36). 새 토큰은 `yona_pat_` + 임의 40자이고 해시만 저장하며,
 * 만료, 사용자 상태, 개수 한도, 마지막 사용 시각의 쓰기 줄이기를 지킨다.
 */
@Transactional
class ApiTokenServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: ApiTokenService
    @Autowired private lateinit var tokens: ApiTokenRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteUsers: dev.yona.domain.site.SiteUserService
    @PersistenceContext private lateinit var em: EntityManager

    private var sequence = 0

    private fun user(state: UserState = UserState.ACTIVE) = users.save(User(loginId = "tk${++sequence}", name = "tk$sequence", email = "tk$sequence@example.com", state = state))

    private fun days(n: Int) = Date(System.currentTimeMillis() + n * 86_400_000L)

    private fun created(result: TokenCreation) = (result as TokenCreation.Created)

    @Test
    fun `새 토큰은 접두사와 임의 40자이고 값은 한 번만 돌려주며 해시만 저장한다`() {
        val u = user()
        val c = created(service.create(u, "내 노트북", setOf("repo", "user"), days(30)))
        assertTrue(Regex("yona_pat_[0-9A-Za-z]{40}").matches(c.value), c.value)
        assertEquals(c.value.take(13), c.token.tokenPrefix)
        assertEquals(TokenHasher.sha256Hex(c.value), c.token.tokenHash)
        assertEquals(64, c.token.tokenHash!!.length)
        em.flush()
        assertEquals(0L, (em.createNativeQuery("select count(*) from api_token where token_hash = :v or token_prefix = :v").setParameter("v", c.value).singleResult as Number).toLong(), "원문은 저장하지 않는다")
        assertEquals(setOf("repo", "user"), ApiScopes.parse(c.token.scopes)); assertEquals(false, c.token.isLegacy); assertEquals(u.id, c.token.user?.id)
        assertNotNull(c.token.createdDate); assertNull(c.token.lastUsedDate)
    }

    @Test
    fun `같은 입력으로 만들어도 값은 매번 다르다`() {
        val u = user()
        assertTrue(created(service.create(u, "a", emptySet(), null)).value != created(service.create(u, "a", emptySet(), null)).value)
    }

    @Test
    fun `이름은 필수이고 100자까지이며 만료는 오늘 이후여야 한다`() {
        val u = user()
        assertEquals(TokenCreation.Rejected("apiToken.error.name"), service.create(u, " ", emptySet(), null))
        assertEquals(TokenCreation.Rejected("apiToken.error.name"), service.create(u, "x".repeat(101), emptySet(), null))
        assertEquals(TokenCreation.Rejected("apiToken.error.expires"), service.create(u, "이름", emptySet(), days(-1)))
        assertTrue(service.create(u, "x".repeat(100), emptySet(), null) is TokenCreation.Created)
    }

    @Test
    fun `사이트 관리자가 아니면 site admin 범위를 줄 수 없다`() {
        val u = user()
        assertEquals(TokenCreation.Rejected("apiToken.error.scope"), service.create(u, "관리", setOf("site_admin"), null, siteManager = false))
        assertTrue(service.create(u, "관리", setOf("site_admin"), null, siteManager = true) is TokenCreation.Created)
    }

    @Test
    fun `사용자당 토큰은 50개까지이다`() {
        val u = user()
        repeat(ApiTokenService.MAX_TOKENS) { assertTrue(service.create(u, "t$it", emptySet(), null) is TokenCreation.Created) }
        assertEquals(TokenCreation.Rejected("apiToken.error.limit"), service.create(u, "넘침", emptySet(), null))
    }

    @Test
    fun `설정한 최대 수명을 넘는 만료일은 받지 않는다`() {
        val u = user()
        assertEquals(TokenCreation.Rejected("apiToken.error.expires"), service.create(u, "길게", emptySet(), days(400), maxDays = 365))
        assertTrue(service.create(u, "적당히", emptySet(), days(300), maxDays = 365) is TokenCreation.Created)
        assertEquals(TokenCreation.Rejected("apiToken.error.expires"), service.create(u, "없음", emptySet(), null, maxDays = 365), "최대 수명이 있으면 만료 없음은 받지 않는다")
    }

    @Test
    fun `값으로 토큰을 찾고 틀린 값과 만료된 토큰은 거절한다`() {
        val u = user(); val c = created(service.create(u, "a", setOf("repo"), days(5)))
        assertEquals(c.token.id, service.authenticate(c.value)?.id)
        assertNull(service.authenticate(c.value + "x")); assertNull(service.authenticate("yona_pat_" + "a".repeat(40))); assertNull(service.authenticate(""))
        c.token.expiresDate = Date(System.currentTimeMillis() - 1000); tokens.save(c.token)
        assertNull(service.authenticate(c.value), "만료된 토큰")
    }

    @Test
    fun `잠기거나 삭제된 사용자의 토큰은 거절한다`() {
        val locked = user(); val c = created(service.create(locked, "a", emptySet(), null))
        locked.state = UserState.LOCKED; users.save(locked)
        assertNull(service.authenticate(c.value))
        locked.state = UserState.DELETED; users.save(locked)
        assertNull(service.authenticate(c.value))
        locked.state = UserState.ACTIVE; users.save(locked)
        assertNotNull(service.authenticate(c.value))
    }

    @Test
    fun `마지막 사용 시각은 1분에 한 번만 갱신한다`() {
        val u = user(); val c = created(service.create(u, "a", emptySet(), null))
        service.authenticate(c.value)
        val first = tokens.findById(c.token.id!!).get().lastUsedDate
        assertNotNull(first)
        service.authenticate(c.value)
        assertEquals(first, tokens.findById(c.token.id!!).get().lastUsedDate, "1분 안의 사용은 쓰지 않는다")
        c.token.lastUsedDate = Date(System.currentTimeMillis() - 120_000); tokens.save(c.token)
        service.authenticate(c.value)
        assertTrue(tokens.findById(c.token.id!!).get().lastUsedDate!!.time > System.currentTimeMillis() - 60_000)
    }

    @Test
    fun `자기 토큰만 목록에 보이고 지울 수 있다`() {
        val a = user(); val b = user()
        val mine = created(service.create(a, "내것", emptySet(), null)); service.create(b, "남의것", emptySet(), null)
        assertEquals(listOf("내것"), service.list(a).map { it.name })
        assertFalse(service.delete(b, mine.token.id!!), "다른 사용자의 토큰은 지울 수 없다")
        assertTrue(service.delete(a, mine.token.id!!))
        assertNull(service.authenticate(mine.value))
    }

    @Test
    fun `옛 URL용 로그인 토큰은 이름이 같은 토큰 하나를 교체하고 다른 토큰은 건드리지 않는다`() {
        val u = user(); val other = created(service.create(u, "다른", setOf("repo"), null))
        val first = service.issueLoginToken(u)
        val second = service.issueLoginToken(u)
        assertNull(service.authenticate(first.value), "이전 로그인 토큰은 무효가 된다")
        assertNotNull(service.authenticate(second.value)); assertNotNull(service.authenticate(other.value))
        assertEquals(1, service.list(u).count { it.name == ApiTokenService.LOGIN_TOKEN_NAME })
        val scopes = ApiScopes.parse(second.token.scopes)
        assertTrue("repo" in scopes && "user" in scopes && "site_admin" !in scopes, scopes.toString())
        assertNotNull(second.token.expiresDate); assertTrue(second.token.expiresDate!!.time > System.currentTimeMillis() + 29 * 86_400_000L)
    }

    @Test
    fun `1년 넘게 쓰지 않은 토큰은 지운다`() {
        val u = user()
        val stale = created(service.create(u, "오래됨", emptySet(), null)); stale.token.createdDate = days(-400); stale.token.lastUsedDate = days(-370); tokens.save(stale.token)
        val neverUsedOld = created(service.create(u, "안 쓴 옛것", emptySet(), null)); neverUsedOld.token.createdDate = days(-400); tokens.save(neverUsedOld.token)
        val fresh = created(service.create(u, "최근", emptySet(), null))
        val legacy = tokens.save(ApiToken(user = u, name = "이전 토큰", tokenHash = TokenHasher.sha256Hex("legacy-value"), tokenPrefix = "lega", scopes = "repo", isLegacy = true, createdDate = days(-800)))
        em.flush()
        assertEquals(2, service.deleteUnused())
        assertEquals(setOf("최근", "이전 토큰"), service.list(u).map { it.name }.toSet())
        assertNotNull(tokens.findById(legacy.id!!).orElse(null), "이관한 이전 토큰은 자동 삭제하지 않는다(기존 사용자 유지)")
        assertNotNull(service.authenticate(fresh.value))
    }

    @Test
    fun `사용자를 삭제 처리하면 토큰이 모두 지워진다`() {
        val gone = user(); val stay = user()
        val a = created(service.create(gone, "a", emptySet(), null)); val b = created(service.create(stay, "b", emptySet(), null))
        em.flush()
        siteUsers.deleteUser(gone)
        em.flush(); em.clear()
        assertNull(service.authenticate(a.value)); assertNotNull(service.authenticate(b.value))
        assertEquals(0L, (em.createNativeQuery("select count(*) from api_token where user_id = ${gone.id}").singleResult as Number).toLong())
    }
}
