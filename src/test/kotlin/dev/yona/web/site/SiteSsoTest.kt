package dev.yona.web.site

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sso.Saml2Certificates
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import dev.yona.support.FakeSamlIdp
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리의 "SSO" 화면(X-16 단계 4, `decisions.md` D-80 6, 15, 19, 21번)을 검증한다. 사이트 관리자만 쓰고, OIDC와 SAML 2 설정을 저장하며, Yona가 IdP에 알릴 값을 보이고,
 * IdP 메타데이터를 가져오고, SP 키를 만들고 올리고 교체한다. 비밀 값(클라이언트 비밀, SP 비밀 키)은 화면에 다시 나오지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class SiteSsoTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var admins: SiteAdminRepository
    @Autowired private lateinit var settings: SsoSettingsService

    private val oidcIdp = FakeOidcIdp()
    private val samlIdp = FakeSamlIdp()
    private var n = 0

    @AfterEach
    fun stop() { oidcIdp.close() }

    private fun user(admin: Boolean = false): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        val u = users.save(User(loginId = "ss$k-${System.nanoTime()}", name = "ss$k", email = "ss$k-${System.nanoTime()}@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        if (admin) admins.save(SiteAdmin(admin = u))
        return u
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun admin() = login(user(admin = true))

    private fun page(session: MockHttpSession) = Jsoup.parse(mockMvc.perform(get("/sites/sso").session(session)).andReturn().response.contentAsString)

    /** 저장 뒤 이동한 화면에 보이는 경고 또는 성공 메시지 키가 아닌 글을 읽는다. */
    private fun flashed(session: MockHttpSession, path: String, vararg params: Pair<String, String>): org.springframework.mock.web.MockHttpServletResponse {
        val request = post(path).session(session); params.forEach { (k, v) -> request.param(k, v) }
        return mockMvc.perform(request).andReturn().response
    }

    private fun oidcParams(enabled: Boolean = true, issuer: String = oidcIdp.issuer, secret: String = oidcIdp.clientSecret) = arrayOf(
        "enabled" to enabled.toString(), "displayName" to "회사 계정", "issuerUri" to issuer, "clientId" to oidcIdp.clientId, "clientSecret" to secret, "scopes" to "openid profile email",
        "emailClaim" to "email", "nameClaim" to "name", "loginIdClaim" to "", "autoCreate" to "true", "linkByEmail" to "true", "clockSkewSeconds" to "60"
    )

    private fun samlParams(enabled: Boolean = true, certificates: String = samlIdp.certificatePem, entityId: String = samlIdp.entityId, ssoUrl: String = samlIdp.ssoUrl) = arrayOf(
        "enabled" to enabled.toString(), "displayName" to "회사 SAML", "idpEntityId" to entityId, "idpSsoUrl" to ssoUrl, "idpCertificates" to certificates, "spEntityId" to "", "nameIdFormat" to "",
        "emailAttribute" to "email", "nameAttribute" to "name", "loginIdAttribute" to "", "autoCreate" to "true", "linkByEmail" to "true", "clockSkewSeconds" to "60"
    )

    @Test
    fun `사이트 관리자만 열 수 있다`() {
        mockMvc.perform(get("/sites/sso")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/sso").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/sso/oidc").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/sso/saml2/keys/generate").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/sso/saml2/sp-certificate.pem").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/sso").session(admin())).andExpect(status().isOk)
    }

    @Test
    fun `두 구역과 Yona가 IdP에 알릴 값과 사이드 메뉴를 보인다`() {
        val doc = page(admin())
        assertNotNull(doc.selectFirst("ul li a[href\$=/sites/sso]"))
        assertNotNull(doc.selectFirst("form#sso-oidc-form")); assertNotNull(doc.selectFirst("form#sso-saml2-form"))
        assertTrue(doc.selectFirst("#oidc-redirect-uri")!!.text().endsWith("/sso/oidc/callback/oidc"))
        assertTrue(doc.selectFirst("#saml2-acs-url")!!.text().endsWith("/sso/saml2/acs")); assertTrue(doc.selectFirst("#saml2-entity-id")!!.text().endsWith("/sso/saml2/metadata/saml2"))
        assertTrue(doc.selectFirst("#saml2-metadata-url")!!.text().endsWith("/sso/saml2/metadata/saml2"))
    }

    @Test
    fun `화면은 탭으로 나뉘고 저장한 뒤에는 같은 탭이 열린다`() {
        val s = admin()
        val doc = page(s)
        assertEquals(listOf("sso-tab-general", "sso-tab-oidc", "sso-tab-saml2", "sso-tab-saml2-keys"), doc.select("#sso-tabs button").map { it.id() })
        assertEquals(listOf("sso-pane-general"), doc.select(".tab-pane.active").map { it.id() })
        for (tab in listOf("oidc", "saml2", "saml2-keys")) {
            val active = Jsoup.parse(mockMvc.perform(get("/sites/sso?tab=$tab").session(s)).andReturn().response.contentAsString).select(".tab-pane.active").map { it.id() }
            assertEquals(listOf("sso-pane-$tab"), active)
        }
        assertEquals(listOf("sso-pane-general"), Jsoup.parse(mockMvc.perform(get("/sites/sso?tab=nope").session(s)).andReturn().response.contentAsString).select(".tab-pane.active").map { it.id() })
        assertEquals("/sites/sso?tab=oidc", flashed(s, "/sites/sso/oidc", *oidcParams()).getHeader("Location"))
        assertEquals("/sites/sso?tab=saml2", flashed(s, "/sites/sso/saml2", *samlParams()).getHeader("Location"))
        assertEquals("/sites/sso?tab=saml2-keys", flashed(s, "/sites/sso/saml2/keys/generate").getHeader("Location"))
    }

    @Test
    fun `OIDC 설정을 저장하면 반영되고 클라이언트 비밀 값은 화면에 다시 나오지 않는다`() {
        val s = admin()
        val response = flashed(s, "/sites/sso/oidc", *oidcParams())
        assertTrue(response.status in 300..399); assertTrue(settings.oidc().enabled == true); assertEquals(oidcIdp.issuer, settings.oidc().issuerUri)
        val html = mockMvc.perform(get("/sites/sso").session(s)).andReturn().response.contentAsString
        assertFalse(html.contains(oidcIdp.clientSecret)); assertFalse(html.contains(settings.oidc().clientSecret!!))
        val doc = Jsoup.parse(html)
        assertEquals(oidcIdp.clientId, doc.selectFirst("input[name=clientId]")!!.attr("value")); assertEquals("", doc.selectFirst("input[name=clientSecret]")!!.attr("value"))
        assertNotNull(doc.selectFirst("input[name=enabled][checked]"))
        // 비밀 값을 비워 다시 저장하면 이전 값을 유지한다
        flashed(s, "/sites/sso/oidc", *oidcParams(secret = "")); assertEquals(oidcIdp.clientSecret, settings.oidcClientSecret())
    }

    @Test
    fun `연결할 수 없는 발급자는 저장하지 않고 이유를 알린다`() {
        val s = admin()
        val response = flashed(s, "/sites/sso/oidc", *oidcParams(issuer = "http://203.0.113.9/nothing"))
        assertTrue(settings.oidc().enabled != true)
        assertEquals("/sites/sso?tab=oidc", response.getHeader("Location"))
    }

    @Test
    fun `OIDC 연결 확인은 결과를 알린다`() {
        val s = admin(); flashed(s, "/sites/sso/oidc", *oidcParams())
        assertTrue(flashed(s, "/sites/sso/oidc/check").status in 300..399)
        oidcIdp.close()
        assertTrue(flashed(s, "/sites/sso/oidc/check").status in 300..399)
    }

    @Test
    fun `SAML 2 설정을 저장하고 IdP 인증서 만료일을 보인다`() {
        val s = admin()
        assertTrue(flashed(s, "/sites/sso/saml2", *samlParams()).status in 300..399)
        assertTrue(settings.saml2().enabled == true); assertEquals(samlIdp.entityId, settings.saml2().idpEntityId)
        val doc = page(s)
        assertEquals(samlIdp.ssoUrl, doc.selectFirst("input[name=idpSsoUrl]")!!.attr("value"))
        assertNotNull(doc.selectFirst("#saml2-idp-certificates .idp-cert")); assertFalse(doc.select("#saml2-idp-certificates .idp-cert.expiring").any())
    }

    @Test
    fun `곧 만료되는 인증서에는 경고를 붙인다`() {
        val soon = Saml2Certificates.generate("soon.example.com", java.time.Instant.now().minus(java.time.Duration.ofDays(5 * 365L - 10)))
        val s = admin(); flashed(s, "/sites/sso/saml2", *samlParams(certificates = soon.certificatePem))
        assertTrue(page(s).select("#saml2-idp-certificates .idp-cert.expiring").isNotEmpty())
    }

    @Test
    fun `잘못된 SAML 설정은 저장하지 않는다`() {
        val s = admin()
        flashed(s, "/sites/sso/saml2", *samlParams(certificates = "not a cert")); assertTrue(settings.saml2().enabled != true)
        flashed(s, "/sites/sso/saml2", *samlParams(entityId = "")); assertTrue(settings.saml2().enabled != true)
    }

    @Test
    fun `IdP 메타데이터를 붙여 넣으면 발급자와 주소와 인증서가 채워진다`() {
        val s = admin()
        val xml = """<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" entityID="${samlIdp.entityId}"><md:IDPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol"><md:KeyDescriptor use="signing"><ds:KeyInfo><ds:X509Data><ds:X509Certificate>${samlIdp.certificatePem.lines().filter { !it.startsWith("-----") }.joinToString("")}</ds:X509Certificate></ds:X509Data></ds:KeyInfo></md:KeyDescriptor><md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="${samlIdp.ssoUrl}"/></md:IDPSSODescriptor></md:EntityDescriptor>"""
        assertTrue(flashed(s, "/sites/sso/saml2/metadata", "metadataXml" to xml).status in 300..399)
        assertEquals(samlIdp.entityId, settings.saml2().idpEntityId); assertEquals(samlIdp.ssoUrl, settings.saml2().idpSsoUrl); assertEquals(1, settings.saml2IdpCertificates().size)
        assertTrue(settings.saml2().enabled != true)
        flashed(s, "/sites/sso/saml2/metadata", "metadataXml" to "<broken"); assertEquals(samlIdp.entityId, settings.saml2().idpEntityId)
        flashed(s, "/sites/sso/saml2/metadata", "metadataUrl" to "http://203.0.113.9/metadata"); assertEquals(samlIdp.entityId, settings.saml2().idpEntityId)
    }

    @Test
    fun `SP 키를 만들면 인증서를 내려받을 수 있고 비밀 키는 화면에 나오지 않는다`() {
        val s = admin()
        assertNull(settings.saml2().spCertificate)
        flashed(s, "/sites/sso/saml2/keys/generate")
        assertNotNull(settings.saml2().spCertificate)
        val html = mockMvc.perform(get("/sites/sso").session(s)).andReturn().response.contentAsString
        val keyBody = java.util.Base64.getEncoder().encodeToString(settings.saml2SpPrivateKey()!!.encoded).take(80)
        assertFalse(html.contains(keyBody)); assertFalse(html.contains(settings.saml2().spPrivateKey!!))
        assertNotNull(Jsoup.parse(html).selectFirst("#saml2-sp-fingerprint"))
        val pem = mockMvc.perform(get("/sites/sso/saml2/sp-certificate.pem").session(s)).andExpect(status().isOk).andReturn().response
        assertTrue(pem.contentAsString.startsWith("-----BEGIN CERTIFICATE-----")); assertTrue(pem.getHeader("Content-Disposition")!!.contains("attachment"))
    }

    @Test
    fun `키를 다시 만들면 이전 키가 교체 중으로 남고 지울 수 있다`() {
        val s = admin(); flashed(s, "/sites/sso/saml2/keys/generate"); val first = settings.saml2().spCertificate
        flashed(s, "/sites/sso/saml2/keys/generate")
        assertNotEquals(first, settings.saml2().spCertificate); assertEquals(first, settings.saml2().spPreviousCertificate)
        assertNotNull(page(s).selectFirst("#saml2-sp-previous"))
        flashed(s, "/sites/sso/saml2/keys/remove-previous"); assertNull(settings.saml2().spPreviousCertificate)
        assertNull(page(s).selectFirst("#saml2-sp-previous"))
    }

    @Test
    fun `외부 키와 인증서를 올릴 수 있고 짝이 맞지 않으면 거절한다`() {
        val s = admin(); val a = Saml2Certificates.generate("a.example.com"); val b = Saml2Certificates.generate("b.example.com")
        flashed(s, "/sites/sso/saml2/keys/upload", "privateKey" to a.privateKeyPem, "certificate" to b.certificatePem); assertNull(settings.saml2().spCertificate)
        flashed(s, "/sites/sso/saml2/keys/upload", "privateKey" to a.privateKeyPem, "certificate" to a.certificatePem); assertEquals(a.certificatePem.trim(), settings.saml2().spCertificate!!.trim())
    }

    @Test
    fun `요청 서명은 SP 키가 있어야 켤 수 있다`() {
        val s = admin()
        flashed(s, "/sites/sso/saml2", *samlParams(), "signRequests" to "true"); assertTrue(settings.saml2().signRequests != true)
        flashed(s, "/sites/sso/saml2/keys/generate")
        flashed(s, "/sites/sso/saml2", *samlParams(), "signRequests" to "true"); assertTrue(settings.saml2().signRequests == true)
    }
}
