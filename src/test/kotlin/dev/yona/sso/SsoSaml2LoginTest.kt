package dev.yona.sso

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sso.Saml2Certificates
import dev.yona.domain.sso.Saml2SsoForm
import dev.yona.domain.sso.SsoSaveResult
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeSamlIdp
import dev.yona.util.SiteUrls
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SAML 2 SSO 로그인 흐름(X-16 단계 3, `decisions.md` D-80)을 가짜 IdP로 검증한다. 로그인 시작이 IdP로 보내는 AuthnRequest를 확인하고, OpenSAML로 서명한 응답을 ACS에 보내
 * 기존 세션 로그인으로 이어지는지 본다. 서명, 발급자, 대상, 수신 주소, 만료, 요청 ID가 틀린 응답과 이메일 없음, 잠긴 사용자, 꺼진 설정은 로그인시키지 않는다.
 * 암호화한 어설션과 요청 서명, SP 메타데이터도 확인한다.
 */
@AutoConfigureMockMvc
@Transactional
class SsoSaml2LoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var settings: SsoSettingsService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var linked: LinkedAccountRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository
    @Autowired private lateinit var siteUrls: SiteUrls

    private val idp = FakeSamlIdp()
    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    private val acs get() = siteUrls.origin() + siteUrls.path("/sso/saml2/acs")
    private val spEntityId get() = siteUrls.origin() + siteUrls.path("/sso/saml2/metadata/saml2")

    private fun configure(enabled: Boolean = true, signRequests: Boolean = false, allowUnverified: Boolean = false) {
        if (signRequests) settings.generateSpKeys("yona.example.com", null)
        val result = settings.saveSaml2(
            Saml2SsoForm(
                enabled = enabled, displayName = "회사 SAML", idpEntityId = idp.entityId, idpSsoUrl = idp.ssoUrl, idpCertificates = idp.certificatePem, spEntityId = "", nameIdFormat = "",
                emailAttribute = "email", nameAttribute = "name", loginIdAttribute = "uid", autoCreate = true, linkByEmail = true, allowUnverifiedEmail = allowUnverified, nameSync = false,
                initiateEnabled = false, signRequests = signRequests, clockSkewSeconds = 60
            ), null
        )
        assertIs<SsoSaveResult.Saved>(result)
    }

    private fun local(email: String, state: UserState = UserState.ACTIVE) =
        users.save(User(loginId = "sa$n-$stamp".also { n++ }, name = "로컬", email = email, state = state, passwordSalt = "s", password = LegacyPasswordHasher.hash("pw", "s")))

    private class Started(val session: MockHttpSession, val location: String, val requestId: String)

    private fun start(returnTo: String? = null): Started {
        val session = MockHttpSession()
        val first = mockMvc.perform(get("/sso/saml2/login").session(session).apply { returnTo?.let { param("returnTo", it) } }).andExpect(status().is3xxRedirection).andReturn().response.getHeader("Location")!!
        val location = if (first.startsWith(idp.ssoUrl)) first else mockMvc.perform(get(first).session(session)).andExpect(status().is3xxRedirection).andReturn().response.getHeader("Location")!!
        assertTrue(location.startsWith(idp.ssoUrl), location)
        return Started(session, location, idp.requestId(location))
    }

    private fun attributes(email: String? = "kim-$stamp@corp.example", name: String? = "김사원", uid: String? = null) =
        listOfNotNull(email?.let { "email" to listOf(it) }, name?.let { "name" to listOf(it) }, uid?.let { "uid" to listOf(it) }).toMap()

    private fun acsPost(s: Started, samlResponse: String) = mockMvc.perform(post("/sso/saml2/acs").session(s.session).param("SAMLResponse", samlResponse)).andReturn().response


    private fun loggedIn(session: MockHttpSession) = mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status == 200

    private fun flushAudit() { TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start() }

    private fun failureRecorded(): Boolean { flushAudit(); return logs.findAll().any { it.action == "auth.sso_failure" } }

    private fun assertRejected(response: org.springframework.mock.web.MockHttpServletResponse, session: MockHttpSession) {
        assertEquals(303, response.status); assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"), response.getHeader("Location"))
        assertTrue(!loggedIn(session))
    }

    // --- 시작 ---

    @Test
    fun `설정이 꺼져 있으면 로그인 시작은 404이다`() {
        mockMvc.perform(get("/sso/saml2/login")).andExpect(status().isNotFound)
        configure(enabled = false); mockMvc.perform(get("/sso/saml2/login")).andExpect(status().isNotFound)
    }

    @Test
    fun `로그인 시작은 SP의 Entity ID와 ACS 주소를 담은 AuthnRequest를 IdP로 보낸다`() {
        configure()
        val s = start()
        val xml = idp.requestXml(s.location)
        assertTrue(xml.contains("Destination=\"${idp.ssoUrl}\"")); assertTrue(xml.contains(spEntityId)); assertTrue(xml.contains("AssertionConsumerServiceURL=\"$acs\""))
        assertTrue(!s.location.contains("Signature="))
    }

    @Test
    fun `요청 서명을 켜면 AuthnRequest에 서명이 붙는다`() {
        configure(signRequests = true)
        val s = start()
        assertTrue(s.location.contains("Signature=") && s.location.contains("SigAlg="))
    }

    @Test
    fun `SP 메타데이터는 Entity ID와 ACS 주소와 SP 인증서를 알린다`() {
        configure(signRequests = true)
        val body = mockMvc.perform(get("/sso/saml2/metadata/saml2")).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("entityID=\"$spEntityId\"")); assertTrue(body.contains(acs)); assertTrue(body.contains("X509Certificate"))
        val certificate = settings.saml2().spCertificate!!.lines().filter { !it.startsWith("-----") }.joinToString("")
        assertTrue(body.replace("\n", "").replace("\r", "").contains(certificate.take(60)))
    }

    @Test
    fun `꺼져 있으면 SP 메타데이터도 열리지 않는다`() {
        mockMvc.perform(get("/sso/saml2/metadata/saml2")).andExpect(status().isNotFound)
        configure(enabled = false); mockMvc.perform(get("/sso/saml2/metadata/saml2")).andExpect(status().isNotFound)
    }

    // --- 성공 ---

    @Test
    fun `새 사용자는 만들어져 로그인되고 원래 가려던 주소로 돌아간다`() {
        configure(); val s = start(returnTo = "/user/editform")
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-new", attributes("new-$stamp@corp.example", "신입")))
        assertEquals(302, response.status); assertEquals("/user/editform", response.getHeader("Location"))
        val user = users.findByEmail("new-$stamp@corp.example")!!
        assertEquals("신입", user.name); assertEquals(UserState.ACTIVE, user.state)
        assertTrue(linked.findAll().any { it.providerKey == "sso-saml2" && it.providerUserId == "${idp.entityId}|subject-new" })
        assertTrue(loggedIn(s.session))
    }

    @Test
    fun `같은 이메일의 기존 계정에 연결해 로그인시키고 로그인 ID 속성을 받는다`() {
        configure(); val user = local("link-$stamp@corp.example"); val s = start()
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-link", attributes("link-$stamp@corp.example")))
        assertEquals(302, response.status); assertTrue(loggedIn(s.session)); assertEquals(1, users.findAll().count { it.email == user.email })
        assertTrue(linked.findAll().any { it.providerKey == "sso-saml2" && it.userCredential?.user?.id == user.id })
    }

    @Test
    fun `이메일 속성이 없으면 NameID가 이메일일 때 그것을 쓴다`() {
        configure(); val s = start()
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "nameid-$stamp@corp.example", emptyMap(), nameIdFormat = "urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"))
        assertEquals(302, response.status); assertNotNull(users.findByEmail("nameid-$stamp@corp.example")); assertTrue(loggedIn(s.session))
    }

    @Test
    fun `응답 전체에 서명한 경우도 받는다`() {
        configure(); val s = start()
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-r", attributes("resp-$stamp@corp.example"), signAssertion = false, signResponse = true))
        assertEquals(302, response.status); assertTrue(loggedIn(s.session))
    }

    @Test
    fun `암호화된 어설션은 SP 키로 풀어 로그인시킨다`() {
        settings.generateSpKeys("yona.example.com", null); configure(); val s = start()
        val cert = Saml2Certificates.parseCertificate(settings.saml2().spCertificate!!)
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-enc", attributes("enc-$stamp@corp.example"), encryptFor = cert, signResponse = true))
        assertEquals(302, response.status); assertTrue(loggedIn(s.session))
    }

    @Test
    fun `암호화된 어설션은 응답에 서명이 없으면 풀지 않는다`() {
        settings.generateSpKeys("yona.example.com", null); configure(); val s = start()
        val cert = Saml2Certificates.parseCertificate(settings.saml2().spCertificate!!)
        assertRejected(acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-enc2", attributes("enc2-$stamp@corp.example"), encryptFor = cert)), s.session)
    }

    @Test
    fun `교체 중인 이전 SP 키로 암호화한 어설션도 푼다`() {
        settings.generateSpKeys("yona.example.com", null)
        val oldCert = Saml2Certificates.parseCertificate(settings.saml2().spCertificate!!)
        settings.generateSpKeys("yona.example.com", null); configure(); val s = start()
        val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-old", attributes("old-$stamp@corp.example"), encryptFor = oldCert, signResponse = true))
        assertEquals(302, response.status); assertTrue(loggedIn(s.session))
    }

    // --- 거절 ---

    @Test
    fun `다른 키로 서명했거나 서명이 없으면 로그인시키지 않는다`() {
        configure()
        for (variant in listOf(true, false)) {
            val s = start()
            val response = acsPost(s, idp.response(s.requestId, acs, spEntityId, "x", attributes("bad-$stamp-$variant@corp.example"), wrongKey = variant, signAssertion = variant))
            assertRejected(response, s.session)
        }
        assertTrue(users.findByEmail("bad-$stamp-true@corp.example") == null && users.findByEmail("bad-$stamp-false@corp.example") == null)
        assertTrue(failureRecorded())
    }

    @Test
    fun `발급자와 대상과 수신 주소와 요청 ID와 유효 시간이 틀린 응답은 로그인시키지 않는다`() {
        configure()
        val cases = mapOf<String, (Started) -> String>(
            "issuer" to { s -> idp.response(s.requestId, acs, spEntityId, "x", attributes(), issuer = "https://evil.example/idp") },
            "audience" to { s -> idp.response(s.requestId, acs, "https://other-sp.example/", "x", attributes()) },
            "destination" to { s -> idp.response(s.requestId, "https://other.example/acs", spEntityId, "x", attributes(), recipient = acs) },
            "recipient" to { s -> idp.response(s.requestId, acs, spEntityId, "x", attributes(), recipient = "https://other.example/acs") },
            "inResponseTo" to { _ -> idp.response("_another-request", acs, spEntityId, "x", attributes()) },
            "expired" to { s -> idp.response(s.requestId, acs, spEntityId, "x", attributes(), notBefore = java.time.Instant.now().minusSeconds(900), notOnOrAfter = java.time.Instant.now().minusSeconds(600)) },
            "notYet" to { s -> idp.response(s.requestId, acs, spEntityId, "x", attributes(), notBefore = java.time.Instant.now().plusSeconds(900), notOnOrAfter = java.time.Instant.now().plusSeconds(1200)) }
        )
        cases.forEach { (name, build) ->
            val s = start()
            val response = acsPost(s, build(s))
            assertRejected(response, s.session)
        }
        assertTrue(failureRecorded())
    }

    @Test
    fun `같은 응답을 다시 보내면 거절한다`() {
        configure(); val s = start()
        val saml = idp.response(s.requestId, acs, spEntityId, "subject-once", attributes("once-$stamp@corp.example"))
        assertEquals(302, acsPost(s, saml).status)
        val second = MockHttpSession()
        assertRejected(acsPost(Started(second, "", s.requestId), saml), second)
    }

    @Test
    fun `로그인 시작 없이 도착한 응답은 로그인시키지 않는다`() {
        configure()
        val session = MockHttpSession()
        val response = mockMvc.perform(post("/sso/saml2/acs").session(session).param("SAMLResponse", idp.response(null, acs, spEntityId, "x", attributes("none-$stamp@corp.example")))).andReturn().response
        assertRejected(response, session)
    }

    @Test
    fun `이메일을 알 수 없으면 로그인시키지 않는다`() {
        configure(); val s = start()
        assertRejected(acsPost(s, idp.response(s.requestId, acs, spEntityId, "opaque-id", emptyMap())), s.session)
        assertTrue(failureRecorded())
    }

    @Test
    fun `잠긴 사용자는 로그인시키지 않는다`() {
        configure(); local("lock-$stamp@corp.example", UserState.LOCKED); val s = start()
        assertRejected(acsPost(s, idp.response(s.requestId, acs, spEntityId, "subject-lock", attributes("lock-$stamp@corp.example"))), s.session)
    }

    @Test
    fun `IdP가 실패를 알린 응답은 로그인시키지 않는다`() {
        configure(); val s = start()
        assertRejected(acsPost(s, idp.response(s.requestId, acs, spEntityId, "x", attributes(), statusValue = "urn:oasis:names:tc:SAML:2.0:status:AuthnFailed")), s.session)
    }
}
