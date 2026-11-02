package dev.yona.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.sso.Saml2SsoForm
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.domain.twofactor.Base32
import dev.yona.domain.twofactor.Totp
import dev.yona.domain.twofactor.TotpConfirmation
import dev.yona.domain.twofactor.TwoFactorService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeSamlIdp
import dev.yona.support.OAuthProviderFlow
import dev.yona.util.SiteUrls
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Yona가 OIDC 제공자일 때(외부 앱이 Yona로 로그인을 요청) 사용자가 **어떤 방법으로 로그인하든** 보관해 둔 승인 요청이 이어지는지 검증한다: 비밀번호, 2단계 인증을 켠 비밀번호, SAML 2.
 * (사내 OIDC는 [dev.yona.sso.SsoWithOidcProviderTest], LDAP은 [OAuthResumeAfterLdapLoginTest]에서 확인한다.)
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.data-dir=build/tmp/it-resume-login", "yona.oauth2.oidc.signing-key-file=src/test/resources/oauth2/oidc-test-key.pem"])
class OAuthResumeAfterLoginTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clients: OAuthClientService
    @Autowired private lateinit var twoFactor: TwoFactorService
    @Autowired private lateinit var sso: SsoSettingsService
    @Autowired private lateinit var siteUrls: SiteUrls

    private val stamp = System.nanoTime() % 1_000_000
    private fun flow() = OAuthProviderFlow(mockMvc, users, clients, "$stamp")

    private fun local(suffix: String): User {
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "rl$suffix-$stamp", name = "로컬 $suffix", email = "rl$suffix-$stamp@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    /** 로그인 화면의 숨은 `redirectUrl`을 그대로 실어 폼으로 로그인한다(사용자가 하는 일과 같다). */
    private fun formLogin(session: MockHttpSession, loginPage: String, user: User) = run {
        val doc = Jsoup.parse(mockMvc.perform(get(loginPage).session(session)).andReturn().response.contentAsString)
        val redirectUrl = doc.selectFirst("form[action$=/users/login] input[name=redirectUrl]")!!.attr("value")
        mockMvc.perform(post("/users/login").session(session).param("loginIdOrEmail", user.loginId!!).param("password", "1234").param("redirectUrl", redirectUrl)).andReturn().response
    }

    @Test
    fun `비밀번호로 로그인하면 승인 요청이 이어진다`() {
        val flow = flow(); val user = local("a"); val session = MockHttpSession()
        val page = flow.begin(session)
        val response = formLogin(session, page, user)
        assertEquals("/oauth2/resume", response.getHeader("Location"))
        assertEquals(user.id.toString(), flow.finish(session).subject)
    }

    @Test
    fun `2단계 인증을 켠 사용자는 코드를 확인한 뒤에 승인 요청이 이어진다`() {
        val flow = flow(); val user = local("b"); val session = MockHttpSession()
        val secret = Base32.decode(twoFactor.beginTotp(user, "Yona", user.loginId!!)!!.secret)
        twoFactor.confirmTotp(user, Totp.code(secret, Instant.now().epochSecond / 30 - 5), Instant.now().minusSeconds(150)) as TotpConfirmation.Confirmed
        val page = flow.begin(session)
        assertEquals("/users/login/2fa", formLogin(session, page, user).getHeader("Location"))
        val done = mockMvc.perform(post("/users/login/2fa").session(session).param("code", Totp.code(secret, Instant.now().epochSecond / 30))).andReturn().response
        assertEquals("/oauth2/resume", done.getHeader("Location"))
        assertEquals(user.id.toString(), flow.finish(session).subject)
    }

    @Test
    fun `SAML로 로그인하면 승인 요청이 이어진다`() {
        val idp = FakeSamlIdp(); val flow = flow(); val session = MockHttpSession()
        sso.saveSaml2(Saml2SsoForm(true, "사내 SAML", idp.entityId, idp.ssoUrl, idp.certificatePem, "", "", "email", "name", "", true, true, false, false, false, false, 60), null)
        val page = flow.begin(session)
        val href = Jsoup.parse(mockMvc.perform(get(page).session(session)).andReturn().response.contentAsString).selectFirst(".page.full a.sso-login-btn[href*=saml2]")!!.attr("href")
        val first = mockMvc.perform(get(href).session(session)).andReturn().response.getHeader("Location")!!
        val toIdp = if (first.startsWith(idp.ssoUrl)) first else mockMvc.perform(get(first).session(session)).andReturn().response.getHeader("Location")!!
        val acs = siteUrls.origin() + siteUrls.path("/sso/saml2/acs"); val spEntityId = siteUrls.origin() + siteUrls.path("/sso/saml2/metadata/saml2")
        val saml = idp.response(idp.requestId(toIdp), acs, spEntityId, "saml-subject", mapOf("email" to listOf("saml-$stamp@corp.example"), "name" to listOf("사내 사용자")))
        val back = mockMvc.perform(post("/sso/saml2/acs").session(session).param("SAMLResponse", saml)).andReturn().response
        assertEquals("/oauth2/resume", back.getHeader("Location"))
        assertEquals(users.findByEmail("saml-$stamp@corp.example")!!.id.toString(), flow.finish(session).subject)
    }
}
