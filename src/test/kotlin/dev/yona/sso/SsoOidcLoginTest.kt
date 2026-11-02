package dev.yona.sso

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.SsoSaveResult
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.user.LinkedAccountRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.transaction.TestTransaction
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.util.UriComponentsBuilder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * OIDC SSO 로그인 흐름(X-16 단계 2, `decisions.md` D-80)을 가짜 IdP로 검증한다. 로그인 시작이 IdP로 보내는 요청(`state`, `nonce`, PKCE)을 확인하고, 돌아온 인가 코드를 토큰으로 바꿔
 * ID 토큰을 검증한 뒤 기존 세션 로그인으로 이어지는지 본다. 서명, 발급자, 대상, 만료, nonce, state 오류와 이메일 미확인, 잠긴 사용자, 꺼진 설정, 열린 리디렉션은 로그인시키지 않는다.
 */
@AutoConfigureMockMvc
@Transactional
class SsoOidcLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var settings: SsoSettingsService
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var linked: LinkedAccountRepository
    @Autowired private lateinit var logs: dev.yona.domain.audit.AuditLogRepository

    private val idp = FakeOidcIdp()
    private var n = 0
    private val stamp = System.nanoTime() % 1_000_000

    @AfterEach
    fun stop() { idp.close() }

    private fun configure(enabled: Boolean = true, initiate: Boolean = false, allowUnverified: Boolean = false) {
        val result = settings.saveOidc(
            OidcSsoForm(
                enabled = enabled, displayName = "회사 계정", issuerUri = idp.issuer, clientId = idp.clientId, clientSecret = idp.clientSecret, scopes = "openid profile email", emailClaim = "email", nameClaim = "name",
                loginIdClaim = "preferred_username", autoCreate = true, linkByEmail = true, allowUnverifiedEmail = allowUnverified, nameSync = false, initiateEnabled = initiate, clockSkewSeconds = 60
            ), null
        )
        assertIs<SsoSaveResult.Saved>(result)
    }

    private fun local(email: String, state: UserState = UserState.ACTIVE) =
        users.save(User(loginId = "so$n-$stamp".also { n++ }, name = "로컬", email = email, state = state, passwordSalt = "s", password = LegacyPasswordHasher.hash("pw", "s")))

    /** 로그인 시작 요청의 결과이다. */
    private class Started(val session: MockHttpSession, val authorize: java.net.URI, val state: String, val nonce: String)

    private fun start(returnTo: String? = null, path: String = "/sso/oidc/login", params: Map<String, String> = emptyMap()): Started {
        val session = MockHttpSession()
        val first = mockMvc.perform(get(path).session(session).apply { returnTo?.let { param("returnTo", it) }; params.forEach { (k, v) -> param(k, v) } }).andExpect(status().is3xxRedirection).andReturn().response.getHeader("Location")!!
        // 앱의 로그인 시작 주소가 스프링 시큐리티의 인가 요청 주소로 한 번 더 보낸다
        val authorizeUrl = if (first.startsWith(idp.issuer)) first else mockMvc.perform(get(first).session(session)).andExpect(status().is3xxRedirection).andReturn().response.getHeader("Location")!!
        val query = UriComponentsBuilder.fromUriString(authorizeUrl).build().queryParams
        assertTrue(authorizeUrl.startsWith("${idp.issuer}/authorize")); assertEquals("code", query.getFirst("response_type")); assertEquals(idp.clientId, query.getFirst("client_id"))
        assertEquals("S256", query.getFirst("code_challenge_method")); assertNotNull(query.getFirst("code_challenge")); assertTrue(query.getFirst("scope")!!.contains("openid"))
        fun decoded(name: String) = java.net.URLDecoder.decode(query.getFirst(name)!!, "UTF-8")
        return Started(session, java.net.URI.create(authorizeUrl), decoded("state"), decoded("nonce"))
    }

    private fun callback(s: Started, token: String, state: String = s.state, code: String = "code-1") = run {
        idp.nextIdToken = token
        mockMvc.perform(get("/sso/oidc/callback/oidc").session(s.session).param("code", code).param("state", state)).andReturn().response
    }

    private fun loggedIn(session: MockHttpSession) = mockMvc.perform(get("/user/editform").session(session)).andReturn().response.status == 200

    /**
     * 경계 감사 기록은 별도 트랜잭션이지만, 이 시험이 이미 감사 기록(설정 변경, 가입)을 쌓았으면 바깥 트랜잭션이 끝난 뒤에 쓰인다. 시험 트랜잭션을 되돌려 끝내고 새로 열어 읽는다
     * (그러면 시험이 만든 사용자는 사라지므로 감사 기록 확인은 시험의 마지막에 한다).
     */
    private fun flushAudit() { TestTransaction.flagForRollback(); TestTransaction.end(); TestTransaction.start() }

    private fun failureRecorded(): Boolean { flushAudit(); return logs.findAll().any { it.action == "auth.sso_failure" } }

    // --- 시작 ---

    @Test
    fun `설정이 꺼져 있으면 로그인 시작은 404이다`() {
        mockMvc.perform(get("/sso/oidc/login")).andExpect(status().isNotFound)
        configure(enabled = false); mockMvc.perform(get("/sso/oidc/login")).andExpect(status().isNotFound)
    }

    @Test
    fun `로그인 시작은 state와 nonce와 PKCE를 담아 IdP의 인가 주소로 보낸다`() {
        configure()
        val s = start()
        assertTrue(s.authorize.toString().contains("redirect_uri=")); assertTrue(s.authorize.toString().contains("%2Fsso%2Foidc%2Fcallback%2Foidc") || s.authorize.toString().contains("/sso/oidc/callback/oidc"))
    }

    // --- 성공 ---

    @Test
    fun `새 사용자는 만들어져 로그인되고 원래 가려던 주소로 돌아간다`() {
        configure(); val s = start(returnTo = "/user/editform")
        val response = callback(s, idp.idToken(s.nonce, subject = "sub-new", email = "new-$stamp@corp.example", name = "신입"))
        assertEquals(302, response.status); assertEquals("/user/editform", response.getHeader("Location"))
        val user = users.findByEmail("new-$stamp@corp.example")!!
        assertEquals("신입", user.name); assertEquals(UserState.ACTIVE, user.state)
        assertTrue(linked.findAll().any { it.providerKey == "sso-oidc" && it.providerUserId == "${idp.issuer}|sub-new" })
        assertTrue(loggedIn(s.session))
        flushAudit(); assertTrue(logs.findAll().any { it.action == "auth.login_success" && it.details!!.contains("sso-oidc") })
    }

    @Test
    fun `토큰 엔드포인트는 인가 코드와 PKCE 검증값과 돌아올 주소를 받는다`() {
        configure(); val s = start()
        callback(s, idp.idToken(s.nonce, email = "pk-$stamp@corp.example"), code = "the-code")
        assertTrue(idp.lastTokenRequest.contains("code=the-code")); assertTrue(idp.lastTokenRequest.contains("code_verifier=")); assertTrue(idp.lastTokenRequest.contains("redirect_uri="))
    }

    @Test
    fun `확인된 같은 이메일의 기존 계정에 연결해 로그인시킨다`() {
        configure(); val u = local("exist-$stamp@corp.example"); val s = start()
        val response = callback(s, idp.idToken(s.nonce, subject = "sub-exist", email = u.email))
        assertEquals(302, response.status); assertTrue(loggedIn(s.session))
        assertEquals(1, linked.findAll().count { it.providerUserId == "${idp.issuer}|sub-exist" })
    }

    @Test
    fun `돌아갈 주소가 바깥이거나 이상한 형식이면 사이트 첫 화면으로 보낸다`() {
        configure()
        for (bad in listOf("https://evil.example/x", "//evil.example", "/\\evil.example", "javascript:alert(1)", "evil", "")) {
            val s = start(returnTo = bad)
            val response = callback(s, idp.idToken(s.nonce, subject = "sub-r${bad.hashCode()}", email = "r${bad.hashCode().toUInt()}-$stamp@corp.example"))
            assertEquals("/", response.getHeader("Location"), bad)
        }
    }

    // --- 거절 ---

    @Test
    fun `이메일을 확인하지 않은 신원은 로그인시키지 않고 로그인 화면으로 보내며 실패를 남긴다`() {
        configure(); val u = local("unv-$stamp@corp.example"); val s = start()
        val response = callback(s, idp.idToken(s.nonce, subject = "sub-unv", email = u.email, emailVerified = false))
        assertEquals(303, response.status); assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"))
        assertFalse(loggedIn(s.session)); assertTrue(linked.findAll().none { it.providerUserId == "${idp.issuer}|sub-unv" }); assertTrue(failureRecorded())
    }

    @Test
    fun `이메일 확인 클레임이 없으면 확인하지 않은 것으로 본다`() {
        configure(); val s = start()
        val response = callback(s, idp.idToken(s.nonce, subject = "sub-noclaim", email = "nc-$stamp@corp.example", emailVerified = null))
        assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform")); assertFalse(loggedIn(s.session))
    }

    @Test
    fun `설정으로 확인하지 않은 이메일을 허용하면 로그인시킨다`() {
        configure(allowUnverified = true); val s = start()
        callback(s, idp.idToken(s.nonce, subject = "sub-allow", email = "al-$stamp@corp.example", emailVerified = false)); assertTrue(loggedIn(s.session))
    }

    @Test
    fun `잠긴 사용자는 로그인시키지 않는다`() {
        configure(); val u = local("lock-$stamp@corp.example", UserState.LOCKED); val s = start()
        callback(s, idp.idToken(s.nonce, subject = "sub-lock", email = u.email)); assertFalse(loggedIn(s.session)); assertTrue(failureRecorded())
    }

    @Test
    fun `서명과 발급자와 대상과 만료와 nonce와 state가 틀린 응답은 로그인시키지 않는다`() {
        configure()
        fun rejected(label: String, build: (Started) -> Pair<String, String>) {
            val s = start(); val (token, state) = build(s)
            val response = callback(s, token, state = state)
            assertFalse(loggedIn(s.session), label); assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"), "$label ${response.status} ${response.getHeader("Location")}")
        }
        rejected("다른 키로 서명") { s -> idp.idToken(s.nonce, subject = "a1", signWith = idp.otherKey) to s.state }
        rejected("발급자 불일치") { s -> idp.idToken(s.nonce, subject = "a2", issuer = "https://evil.example") to s.state }
        rejected("대상 불일치") { s -> idp.idToken(s.nonce, subject = "a3", audience = "other-client") to s.state }
        rejected("만료") { s -> idp.idToken(s.nonce, subject = "a4", expiresInSeconds = -3600) to s.state }
        rejected("nonce 불일치") { s -> idp.idToken("another-nonce", subject = "a5") to s.state }
        rejected("nonce 없음") { s -> idp.idToken(null, subject = "a6") to s.state }
        rejected("state 불일치") { s -> idp.idToken(s.nonce, subject = "a7") to "forged-state" }
        assertTrue(users.findAll().none { it.email == "user1@corp.example" })
    }

    @Test
    fun `로그인 시작 없이 도착한 콜백은 로그인시키지 않는다`() {
        configure(); val session = MockHttpSession(); idp.nextIdToken = idp.idToken("n", subject = "b1")
        val response = mockMvc.perform(get("/sso/oidc/callback/oidc").session(session).param("code", "c").param("state", "s")).andReturn().response
        assertFalse(loggedIn(session)); assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"))
    }

    @Test
    fun `IdP가 오류를 알려도 로그인시키지 않고 로그인 화면으로 보낸다`() {
        configure(); val s = start()
        val response = mockMvc.perform(get("/sso/oidc/callback/oidc").session(s.session).param("error", "access_denied").param("state", s.state)).andReturn().response
        assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform")); assertFalse(loggedIn(s.session))
    }

    // --- IdP가 시작하는 로그인(포털) ---

    @Test
    fun `로그인 시작 주소는 설정을 켜야 받고 발급자가 설정과 같아야 한다`() {
        configure(initiate = false)
        mockMvc.perform(get("/sso/oidc/initiate").param("iss", idp.issuer)).andExpect(status().isNotFound)
        configure(initiate = true)
        mockMvc.perform(get("/sso/oidc/initiate").param("iss", "https://evil.example")).andExpect(status().isBadRequest)
        mockMvc.perform(get("/sso/oidc/initiate")).andExpect(status().isBadRequest)
    }

    @Test
    fun `포털이 여는 주소는 평소와 같은 인가 코드 흐름을 시작하고 login_hint와 이동 주소를 지킨다`() {
        configure(initiate = true)
        val s = start(path = "/sso/oidc/initiate", params = mapOf("iss" to idp.issuer, "login_hint" to "kim@corp.example", "target_link_uri" to "/user/editform"))
        assertEquals("kim@corp.example", UriComponentsBuilder.fromUri(s.authorize).build().queryParams.getFirst("login_hint")?.let { java.net.URLDecoder.decode(it, "UTF-8") })
        val response = callback(s, idp.idToken(s.nonce, subject = "sub-portal", email = "portal-$stamp@corp.example"))
        assertEquals("/user/editform", response.getHeader("Location")); assertTrue(loggedIn(s.session))
        flushAudit(); assertTrue(logs.findAll().any { it.action == "auth.login_success" && it.details!!.contains("initiated") })
    }

    @Test
    fun `로그인 시작 주소의 이동 주소도 바깥 주소는 받지 않는다`() {
        configure(initiate = true)
        val s = start(path = "/sso/oidc/initiate", params = mapOf("iss" to idp.issuer, "target_link_uri" to "https://evil.example/"))
        assertEquals("/", callback(s, idp.idToken(s.nonce, subject = "sub-evil", email = "ev-$stamp@corp.example")).getHeader("Location"))
    }
}
