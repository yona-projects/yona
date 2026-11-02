package dev.yona.domain.sso

import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OIDC 설정의 저장과 연결 확인(X-16 단계 2, `decisions.md` D-80 3, 4, 5번)을 검증한다: 기본은 꺼짐이고, 저장할 때 발견 문서를 확인해 결과를 캐시하며(발급자가 설정과 같아야 한다),
 * 클라이언트 비밀 값은 암호화해 저장하고(비워 두면 유지), https가 아닌 발급자는 루프백이 아니면 받지 않는다. 사용을 켜려면 연결 확인이 성공해야 하고, 바꾼 설정은 감사 기록에 남지만 비밀 값은 남기지 않는다.
 */
@Transactional
class SsoSettingsServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: SsoSettingsService
    @Autowired private lateinit var repository: OidcSsoSettingsRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private val idp = FakeOidcIdp()

    @AfterEach
    fun stop() { idp.close() }

    private fun form(enabled: Boolean = true, issuer: String = idp.issuer, secret: String? = idp.clientSecret, clientId: String = idp.clientId) = OidcSsoForm(
        enabled = enabled, displayName = "회사 계정", issuerUri = issuer, clientId = clientId, clientSecret = secret, scopes = "openid profile email",
        emailClaim = "email", nameClaim = "name", loginIdClaim = "preferred_username", autoCreate = true, linkByEmail = true, allowUnverifiedEmail = false, nameSync = false, initiateEnabled = false, clockSkewSeconds = 60
    )

    @Test
    fun `기본은 꺼져 있고 정책은 기본값이다`() {
        assertFalse(service.oidc().enabled == true); assertEquals(SsoPolicy(), service.oidcPolicy()); assertNull(service.oidcClientSecret())
    }

    @Test
    fun `저장하면 발견 문서를 확인해 캐시하고 비밀 값은 암호화해 저장한다`() {
        assertIs<SsoSaveResult.Saved>(service.saveOidc(form(), null))
        val stored = repository.findAll().single()
        assertTrue(stored.enabled == true); assertEquals("회사 계정", stored.displayName); assertEquals(idp.issuer, stored.issuerUri)
        assertNotEquals(idp.clientSecret, stored.clientSecret); assertTrue(stored.clientSecret!!.startsWith("v1.")); assertEquals(idp.clientSecret, service.oidcClientSecret())
        val discovery = service.oidcDiscovery()!!
        assertEquals("${idp.issuer}/authorize", discovery.authorizationEndpoint); assertEquals("${idp.issuer}/token", discovery.tokenEndpoint); assertEquals("${idp.issuer}/jwks", discovery.jwksUri)
        assertEquals(idp.issuer, discovery.issuer)
    }

    @Test
    fun `비밀 값을 비워 두면 이전 값을 유지하고 새 값을 주면 바꾼다`() {
        service.saveOidc(form(), null)
        service.saveOidc(form(secret = ""), null); assertEquals(idp.clientSecret, service.oidcClientSecret())
        service.saveOidc(form(secret = "new-secret"), null); assertEquals("new-secret", service.oidcClientSecret())
    }

    @Test
    fun `발견 문서의 발급자가 설정과 다르면 거절하고 저장하지 않는다`() {
        idp.discoveryIssuer = "https://evil.example"
        assertEquals(SsoSaveError.ISSUER_MISMATCH, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(), null)).error)
        assertTrue(repository.findAll().isEmpty())
    }

    @Test
    fun `연결할 수 없는 주소와 https가 아닌 바깥 주소는 거절한다`() {
        assertEquals(SsoSaveError.UNREACHABLE, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(issuer = "http://127.0.0.1:1"), null)).error)
        assertEquals(SsoSaveError.HTTPS_REQUIRED, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(issuer = "http://idp.corp.example"), null)).error)
        assertEquals(SsoSaveError.INVALID_ISSUER, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(issuer = "not a url"), null)).error)
        assertEquals(SsoSaveError.INVALID_ISSUER, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(issuer = "ftp://idp.corp.example"), null)).error)
    }

    @Test
    fun `필수 값이 없으면 거절하고 끄는 저장은 연결 확인 없이 된다`() {
        assertEquals(SsoSaveError.MISSING_CLIENT, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(clientId = ""), null)).error)
        assertEquals(SsoSaveError.MISSING_CLIENT, assertIs<SsoSaveResult.Rejected>(service.saveOidc(form(secret = null), null)).error)
        service.saveOidc(form(), null)
        idp.close()
        assertIs<SsoSaveResult.Saved>(service.saveOidc(form(enabled = false), null)); assertFalse(service.oidc().enabled == true)
    }

    @Test
    fun `정책 값이 설정에서 그대로 나온다`() {
        service.saveOidc(form().copy(autoCreate = false, linkByEmail = false, allowUnverifiedEmail = true, nameSync = true), null)
        assertEquals(SsoPolicy(autoCreate = false, linkByEmail = false, allowUnverifiedEmail = true, nameSync = true), service.oidcPolicy())
    }

    @Test
    fun `바꾼 설정은 감사 기록에 남지만 비밀 값은 남기지 않는다`() {
        service.saveOidc(form(), null)
        val event = logs.findAll().single { it.action == "site.sso_changed" }
        assertTrue(event.details!!.contains("oidc")); assertFalse(event.details!!.contains(idp.clientSecret)); assertFalse((event.details + event.targetLabel).contains("v1."))
    }
}
