package dev.yona.domain.oauth2server

import dev.yona.domain.apitoken.TokenHasher
import dev.yona.domain.audit.AuditLogRepository
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
import kotlin.test.assertTrue

/** 승인한 앱 목록과 취소(X-1 6단계, `decisions.md` D-71), 앱 변경의 감사 기록을 검증한다. */
@Transactional
class OAuthAuthorizedAppsServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: OAuthAuthorizedAppsService
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var consents: OAuthConsentRepository
    @Autowired private lateinit var authorizations: OAuthAuthorizationRepository
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var logs: AuditLogRepository

    private var n = 0

    private fun user() = users.save(User(loginId = "oa${++n}-${System.nanoTime()}", name = "oa$n", email = "oa$n-${System.nanoTime()}@example.com", state = UserState.ACTIVE))

    private fun app(owner: User, name: String = "앱") = (clientService.create(owner, name, "", "", "https://a.example/cb", false, setOf("read:user", "repo")) as ClientCreation.Created).client

    private fun approve(user: User, client: OAuthClient, scopes: String = "read:user", withToken: Boolean = true) {
        consents.save(OAuthConsent(clientRowId = client.id, userId = user.id, scopes = scopes, createdDate = Date()))
        if (withToken) authorizations.save(OAuthAuthorization(id = "a-${System.nanoTime()}", clientRowId = client.id, userId = user.id, grantType = "authorization_code", attributes = "{}", accessTokenHash = TokenHasher.sha256Hex("t${System.nanoTime()}"), createdDate = Date()))
    }

    private fun events(action: String) = logs.findAll().filter { it.action == action }

    @Test
    fun `승인한 앱을 앱 이름과 만든 사용자와 범위와 함께 보이고 남의 승인은 보이지 않는다`() {
        val owner = user(); val me = user(); val other = user(); val a = app(owner, "내가 승인한 앱"); val b = app(owner, "남이 승인한 앱")
        approve(me, a, "read:user,repo"); approve(other, b)
        val list = service.list(me)
        assertEquals(1, list.size)
        assertEquals("내가 승인한 앱", list[0].name); assertEquals(owner.loginId, list[0].ownerLoginId); assertEquals(listOf("read:user", "repo"), list[0].scopes); assertTrue(list[0].activeTokens)
    }

    @Test
    fun `앱을 지우면 그 앱의 승인은 목록에서 사라진다`() {
        val owner = user(); val me = user(); val a = app(owner); approve(me, a)
        clientService.delete(owner, a.id!!)
        assertTrue(service.list(me).isEmpty())
    }

    @Test
    fun `승인을 취소하면 인가와 승인이 지워지고 감사 기록이 남고 두 번째는 거짓이다`() {
        val owner = user(); val me = user(); val a = app(owner); approve(me, a)
        assertTrue(service.revoke(me, a.id!!))
        assertEquals(0, authorizations.count()); assertTrue(consents.findAll().isEmpty())
        assertEquals(1, events("oauth.authorization_revoked").size)
        assertFalse(service.revoke(me, a.id!!))
    }

    @Test
    fun `남의 승인은 취소하지 못한다`() {
        val owner = user(); val me = user(); val other = user(); val a = app(owner); approve(other, a)
        assertFalse(service.revoke(me, a.id!!)); assertEquals(1, authorizations.count())
    }

    @Test
    fun `앱을 만들고 고치고 비밀 값을 다시 만들고 지우면 감사 기록이 남고 비밀 값은 없다`() {
        val owner = user(); val created = assertIs<ClientCreation.Created>(clientService.create(owner, "감사 앱", "", "", "https://a.example/cb", true, setOf("read:user")))
        clientService.update(owner, created.client.id!!, "새 이름", "", "", "https://a.example/cb", setOf("read:user"))
        val fresh = clientService.regenerateSecret(owner, created.client.id!!)!!
        clientService.delete(owner, created.client.id!!)
        for (action in listOf("oauth.client_created", "oauth.client_updated", "oauth.client_secret_regenerated", "oauth.client_deleted")) assertEquals(1, events(action).size, action)
        assertTrue(logs.findAll().none { (it.details.orEmpty() + it.targetLabel.orEmpty()).let { text -> text.contains(created.secret!!) || text.contains(fresh) } }, "비밀 값은 기록에 없다")
        val admin = user(); val other = app(owner)
        clientService.deleteAsAdmin(other.id!!)
        assertTrue(events("oauth.client_deleted").any { it.details!!.contains("admin") })
    }
}
