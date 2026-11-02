package dev.yona.domain.twofactor

import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.support.AbstractIntegrationTest
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 보안 키(WebAuthn, 패스키)를 두 번째 단계로 쓰는 서비스(X-2 7단계, `decisions.md` D-69)를 검증한다. 소프트웨어 인증기([SoftAuthenticator])가 실제 ES256 서명과 CBOR 증명 객체를 만들어
 * 서버의 등록과 인증 검증을 끝까지 통과시킨다: 등록, 인증, 도전 값 재사용과 변조, 다른 출처, 다른 사용자의 키, 서명 횟수 역행, 삭제.
 */
@Transactional
class WebAuthnServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: WebAuthnService
    @Autowired private lateinit var twoFactor: TwoFactorService
    @Autowired private lateinit var credentials: TwoFactorCredentialRepository
    @Autowired private lateinit var users: UserRepository
    @PersistenceContext private lateinit var em: EntityManager

    private val ctx = WebAuthnContext(rpId = "localhost", origin = "http://localhost")
    private val at = Instant.parse("2026-11-02T00:00:00Z")
    private var n = 0

    private fun user() = users.save(User(loginId = "wa${++n}-${System.nanoTime()}", name = "wa$n", email = "wa$n-${System.nanoTime()}@example.com"))

    private fun register(u: User, key: SoftAuthenticator = SoftAuthenticator(ctx.origin, ctx.rpId), name: String = "내 보안 키"): Pair<SoftAuthenticator, WebAuthnRegistration> {
        val options = service.startRegistration(u, ctx)
        val result = service.finishRegistration(u, name, options.server, key.create(options.browser), ctx, at)
        em.flush(); em.clear()
        return key to result
    }

    @Test
    fun `보안 키를 등록하면 2단계 인증이 켜지고 첫 수단이면 백업 코드를 한 번 돌려준다`() {
        val u = user(); val (_, result) = register(u)
        val registered = assertIs<WebAuthnRegistration.Registered>(result)
        assertEquals(10, assertNotNull(registered.backupCodes).size)
        assertTrue(twoFactor.isEnabled(u)); assertTrue(service.hasSecurityKey(u))
        val stored = credentials.findByUserIdOrderById(u.id!!).single()
        assertEquals("WEBAUTHN", stored.type); assertEquals("내 보안 키", stored.name); assertEquals(true, stored.confirmed)
    }

    @Test
    fun `이미 2단계 인증을 켠 사용자가 키를 더하면 백업 코드를 다시 만들지 않는다`() {
        val u = user(); register(u)
        val (_, second) = register(u, name = "두 번째")
        assertEquals(null, assertIs<WebAuthnRegistration.Registered>(second).backupCodes)
        assertEquals(2, credentials.findByUserIdOrderById(u.id!!).size)
    }

    @Test
    fun `도전 값이 다르거나 출처가 다르면 등록을 거부한다`() {
        val u = user(); val key = SoftAuthenticator(ctx.origin, ctx.rpId)
        val options = service.startRegistration(u, ctx)
        assertIs<WebAuthnRegistration.Rejected>(service.finishRegistration(u, "x", options.server, key.create(options.browser, tamperChallenge = true), ctx, at))
        assertIs<WebAuthnRegistration.Rejected>(service.finishRegistration(u, "x", options.server, key.create(options.browser, origin = "http://evil.example"), ctx, at))
        assertIs<WebAuthnRegistration.Rejected>(service.finishRegistration(u, "x", options.server, "not json", ctx, at))
        assertFalse(twoFactor.isEnabled(u))
    }

    @Test
    fun `같은 키는 두 번 등록하지 못한다`() {
        val u = user(); val key = SoftAuthenticator(ctx.origin, ctx.rpId); register(u, key)
        val options = service.startRegistration(u, ctx)
        assertIs<WebAuthnRegistration.Rejected>(service.finishRegistration(u, "again", options.server, key.create(options.browser), ctx, at))
        assertEquals(1, credentials.findByUserIdOrderById(u.id!!).size)
    }

    @Test
    fun `인증 요청에 맞게 서명하면 통과하고 서명 횟수가 올라간다`() {
        val u = user(); val (key) = register(u)
        val request = service.startAssertion(u, ctx)
        assertTrue(service.finishAssertion(u, request.server, key.get(request.browser), ctx, at.plusSeconds(10)))
        em.flush(); em.clear()
        val stored = credentials.findByUserIdOrderById(u.id!!).single()
        assertEquals(1L, stored.signCount); assertNotNull(stored.lastUsedDate)
    }

    @Test
    fun `다른 도전 값이나 다른 출처나 서명 횟수 역행은 거부한다`() {
        val u = user(); val (key) = register(u)
        val first = service.startAssertion(u, ctx)
        assertTrue(service.finishAssertion(u, first.server, key.get(first.browser, nextCounter = 5), ctx, at))
        em.flush(); em.clear()
        val replay = service.startAssertion(u, ctx)
        assertFalse(service.finishAssertion(u, replay.server, key.get(first.browser), ctx, at), "지난 도전 값에 대한 응답")
        val rollback = service.startAssertion(u, ctx)
        assertFalse(service.finishAssertion(u, rollback.server, key.get(rollback.browser, nextCounter = 3), ctx, at), "서명 횟수 역행(복제된 키 의심)")
        assertFalse(service.finishAssertion(u, rollback.server, "{}", ctx, at))
        assertFalse(service.finishAssertion(u, rollback.server, "not json", ctx, at))
    }

    @Test
    fun `다른 사용자의 키로는 인증하지 못한다`() {
        val a = user(); val b = user(); val (keyA) = register(a); register(b)
        val request = service.startAssertion(b, ctx)
        assertFalse(service.finishAssertion(b, request.server, keyA.get(request.browser), ctx, at))
    }

    @Test
    fun `키가 없는 사용자의 인증 요청은 만들지 못한다`() {
        assertEquals(null, service.startAssertionOrNull(user(), ctx))
    }

    @Test
    fun `보안 키만 있는 사용자도 인증 앱을 더할 수 있다`() {
        val u = user(); register(u)
        assertNotNull(twoFactor.beginTotp(u, "Yona", u.loginId!!), "보안 키가 있어도 인증 앱을 더할 수 있다")
    }

    @Test
    fun `마지막 수단을 지우면 2단계 인증이 꺼지고 남은 수단이 있으면 켜진 채이다`() {
        val u = user(); register(u); register(u, name = "둘째")
        val ids = credentials.findByUserIdOrderById(u.id!!).map { it.id!! }
        assertTrue(twoFactor.removeCredential(u, ids[0])); em.flush(); em.clear()
        assertTrue(twoFactor.isEnabled(u))
        assertTrue(twoFactor.removeCredential(u, ids[1])); em.flush(); em.clear()
        assertFalse(twoFactor.isEnabled(u)); assertEquals(0L, twoFactor.backupCodesRemaining(u), "마지막 수단을 지우면 백업 코드도 지워진다")
        assertFalse(twoFactor.removeCredential(u, ids[1]))
    }

    @Test
    fun `남의 수단은 지우지 못한다`() {
        val a = user(); val b = user(); register(a)
        assertFalse(twoFactor.removeCredential(b, credentials.findByUserIdOrderById(a.id!!).single().id!!))
    }
}
