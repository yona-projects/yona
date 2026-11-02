package dev.yona.domain.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사용자가 만드는 OAuth 앱(클라이언트) 서비스(X-1 1단계, `decisions.md` D-71)를 검증한다: 공개와 기밀 앱 만들기, 비밀 값은 만든 직후에만 보이고 해시만 저장, 리디렉션 주소와 범위 검증,
 * 사용자당 한도, 소유자만 고치고 지우기, 비밀 값 다시 만들기, 관리자의 전체 목록과 삭제.
 */
@Transactional
class OAuthClientServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: OAuthClientService
    @Autowired private lateinit var clients: OAuthClientRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteUsers: dev.yona.domain.site.SiteUserService
    @Autowired private lateinit var em: jakarta.persistence.EntityManager

    private var n = 0

    private fun user() = users.save(User(loginId = "oc${++n}-${System.nanoTime()}", name = "oc$n", email = "oc$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE))

    private fun create(owner: User, name: String = "내 앱", redirect: String = "https://app.example.com/callback", confidential: Boolean = true, scopes: Set<String> = setOf("read:user"), description: String = "", home: String = "") =
        service.create(owner, name, description, home, redirect, confidential, scopes)

    @Test
    fun `기밀 앱을 만들면 클라이언트 ID와 비밀 값을 한 번 돌려주고 비밀 값은 해시로만 저장한다`() {
        val u = user(); val created = assertIs<ClientCreation.Created>(create(u))
        val secret = assertNotNull(created.secret)
        assertTrue(created.client.clientId!!.startsWith("yona_app_") && secret.startsWith("yona_cs_"))
        val stored = clients.findByClientId(created.client.clientId!!)!!
        assertNotEquals(secret, stored.secretHash); assertEquals(64, stored.secretHash!!.length)
        assertEquals(true, stored.confidential); assertEquals("USER", stored.ownerType); assertEquals(u.id, stored.ownerId); assertEquals("USER", stored.registration)
    }

    @Test
    fun `공개 앱은 비밀 값이 없다`() {
        val created = assertIs<ClientCreation.Created>(create(user(), confidential = false))
        assertNull(created.secret); assertNull(created.client.secretHash); assertEquals(false, created.client.confidential)
    }

    @Test
    fun `클라이언트 ID는 앱마다 다르다`() {
        val u = user()
        val a = assertIs<ClientCreation.Created>(create(u)).client.clientId; val b = assertIs<ClientCreation.Created>(create(u)).client.clientId
        assertNotEquals(a, b)
    }

    @Test
    fun `이름과 설명과 홈 주소와 리디렉션 주소와 범위를 검증한다`() {
        val u = user()
        assertEquals("oauth.error.name", assertIs<ClientCreation.Rejected>(create(u, name = "  ")).messageKey)
        assertEquals("oauth.error.name", assertIs<ClientCreation.Rejected>(create(u, name = "a".repeat(101))).messageKey)
        assertEquals("oauth.error.description", assertIs<ClientCreation.Rejected>(create(u, description = "a".repeat(501))).messageKey)
        assertEquals("oauth.error.homeUrl", assertIs<ClientCreation.Rejected>(create(u, home = "javascript:alert(1)")).messageKey)
        assertEquals("oauth.error.redirect", assertIs<ClientCreation.Rejected>(create(u, redirect = "")).messageKey)
        assertEquals("oauth.error.redirect", assertIs<ClientCreation.Rejected>(create(u, redirect = "http://example.com/cb")).messageKey)
        assertEquals("oauth.error.redirect", assertIs<ClientCreation.Rejected>(create(u, redirect = (1..11).joinToString("\n") { "https://a$it.example/cb" })).messageKey, "주소는 10개까지")
        assertEquals("oauth.error.scope", assertIs<ClientCreation.Rejected>(create(u, scopes = emptySet())).messageKey)
        assertEquals("oauth.error.scope", assertIs<ClientCreation.Rejected>(create(u, scopes = setOf("nope"))).messageKey)
        assertEquals("oauth.error.scope", assertIs<ClientCreation.Rejected>(create(u, scopes = setOf("site_admin"))).messageKey, "사이트 관리자 범위는 OAuth 앱에 주지 않는다")
        assertEquals(0, clients.count())
    }

    @Test
    fun `SSH 키와 GPG 키와 배포 키의 모든 범위는 OAuth 앱에 주지 않는다`() {
        val u = user()
        for (scope in listOf("admin", "write", "read").flatMap { p -> listOf("ssh_key", "gpg_key", "deploy_key").map { "$p:$it" } }) {
            assertEquals("oauth.error.scope", assertIs<ClientCreation.Rejected>(create(u, scopes = setOf("read:user", scope))).messageKey, scope)
        }
        assertTrue(OAuthScopes.grantable().none { it.endsWith("_key") || it == "site_admin" })
        assertTrue(OAuthScopes.grantable().containsAll(listOf("repo", "read:user", "read:org", "user:email")))
        assertEquals(0, clients.count())
    }

    @Test
    fun `여러 줄 리디렉션 주소는 정리해 저장한다`() {
        val c = assertIs<ClientCreation.Created>(create(user(), redirect = " https://a.example/cb \n\nhttp://localhost/cb\nhttps://a.example/cb")).client
        assertEquals(listOf("https://a.example/cb", "http://localhost/cb"), RedirectUris.parse(c.redirectUris))
    }

    @Test
    fun `사용자당 앱은 20개까지이다`() {
        val u = user(); repeat(20) { assertIs<ClientCreation.Created>(create(u, name = "앱$it")) }
        assertEquals("oauth.error.limit", assertIs<ClientCreation.Rejected>(create(u)).messageKey)
        assertIs<ClientCreation.Created>(create(user()))
    }

    @Test
    fun `목록은 소유자의 앱만 최근 순으로 보인다`() {
        val a = user(); val b = user()
        val first = assertIs<ClientCreation.Created>(create(a, name = "첫째")).client; val second = assertIs<ClientCreation.Created>(create(a, name = "둘째")).client; create(b, name = "남의 앱")
        assertEquals(listOf(second.id, first.id), service.list(a).map { it.id })
    }

    @Test
    fun `소유자만 고치고 비밀 값을 다시 만들고 지운다`() {
        val owner = user(); val other = user(); val c = assertIs<ClientCreation.Created>(create(owner)).client; val id = c.id!!
        assertIs<ClientEdit.Rejected>(service.update(other, id, "해킹", "", "", "https://evil.example/cb", setOf("read:user")))
        assertEquals("내 앱", service.find(owner, id)!!.name); assertNull(service.find(other, id))
        val done = assertIs<ClientEdit.Done>(service.update(owner, id, "새 이름", "설명", "https://home.example", "https://new.example/cb", setOf("read:user", "read:org")))
        assertEquals("새 이름", done.client.name); assertEquals("https://new.example/cb", done.client.redirectUris); assertEquals("read:user,read:org", done.client.allowedScopes)
        assertEquals(c.clientId, done.client.clientId, "클라이언트 ID는 바뀌지 않는다")
        assertNull(service.regenerateSecret(other, id)); assertFalse(service.delete(other, id)); assertNotNull(clients.findById(id).orElse(null))
        assertTrue(service.delete(owner, id)); assertNull(clients.findById(id).orElse(null))
    }

    @Test
    fun `비밀 값을 다시 만들면 새 값이 나오고 이전 값은 쓸 수 없고 공개 앱은 다시 만들 수 없다`() {
        val u = user(); val created = assertIs<ClientCreation.Created>(create(u)); val oldHash = created.client.secretHash
        val fresh = assertNotNull(service.regenerateSecret(u, created.client.id!!))
        assertNotEquals(created.secret, fresh); assertNotEquals(oldHash, clients.findById(created.client.id!!).get().secretHash)
        assertTrue(service.verifySecret(created.client.clientId!!, fresh)); assertFalse(service.verifySecret(created.client.clientId!!, created.secret!!))
        val publicApp = assertIs<ClientCreation.Created>(create(u, confidential = false)).client
        assertNull(service.regenerateSecret(u, publicApp.id!!)); assertFalse(service.verifySecret(publicApp.clientId!!, "x"))
    }

    @Test
    fun `관리자는 사이트 전체 앱을 보고 지울 수 있다`() {
        val a = user(); val b = user(); val c1 = assertIs<ClientCreation.Created>(create(a)).client; assertIs<ClientCreation.Created>(create(b))
        assertTrue(service.listAll().map { it.id }.containsAll(listOf(c1.id)) && service.listAll().size >= 2)
        assertTrue(service.deleteAsAdmin(c1.id!!)); assertFalse(service.deleteAsAdmin(c1.id!!))
    }

    @Test
    fun `사용자를 지우면 그 사용자의 앱도 지워진다`() {
        val u = user(); val other = user(); create(u); val keep = assertIs<ClientCreation.Created>(create(other)).client
        service.deleteOwnedBy(u)
        assertTrue(service.list(u).isEmpty()); assertNotNull(clients.findById(keep.id!!).orElse(null))
    }

    @Test
    fun `사이트 관리의 사용자 삭제 처리도 그 사용자의 앱을 지운다`() {
        val gone = user(); val stay = user(); create(gone); val keep = assertIs<ClientCreation.Created>(create(stay)).client
        em.flush(); siteUsers.deleteUser(gone); em.flush(); em.clear()
        assertTrue(service.list(gone).isEmpty()); assertNotNull(clients.findById(keep.id!!).orElse(null))
    }
}
