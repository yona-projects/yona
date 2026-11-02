package dev.yona.web.social

import dev.yona.domain.sso.OidcSsoForm
import dev.yona.domain.sso.Saml2SsoForm
import dev.yona.domain.sso.SsoSettingsService
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.FakeOidcIdp
import dev.yona.support.FakeSamlIdp
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사내 IdP(OIDC, SAML 2)를 켠 사이트에서는 소셜 로그인을 받지 않는다(`decisions.md` D-81). 로그인 화면에서 소셜 단추가 사라지고 `/authenticate/{제공자}`는 시작도 돌아옴도 막는다.
 * 켜지 않았으면 그대로 쓸 수 있다.
 */
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = ["yona.social.login-support=github", "yona.social.github.client-id=gh-id", "yona.social.github.client-secret=gh-secret", "yona.social.github.authorization-url=https://github.test/authorize"])
class SocialLoginSuppressionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var sso: SsoSettingsService

    private val oidcIdp = FakeOidcIdp()
    private val samlIdp = FakeSamlIdp()

    @AfterEach
    fun stop() { oidcIdp.close() }

    private fun socialButtons() = Jsoup.parse(mockMvc.perform(get("/users/loginform")).andReturn().response.contentAsString).select(".page.full a.oauth-login-btn:not(.sso-login-btn)")

    private fun start() = mockMvc.perform(get("/authenticate/github")).andReturn().response

    @Test
    fun `사내 IdP를 켜지 않으면 소셜 로그인을 쓸 수 있다`() {
        assertEquals(1, socialButtons().size); assertTrue(start().getHeader("Location")!!.startsWith("https://github.test/authorize"))
    }

    @Test
    fun `사내 OIDC를 켜면 소셜 로그인은 보이지도 시작되지도 않는다`() {
        sso.saveOidc(OidcSsoForm(true, "사내", oidcIdp.issuer, oidcIdp.clientId, oidcIdp.clientSecret, "openid", "email", "name", "", true, true, false, false, false, 60), null)
        assertTrue(socialButtons().isEmpty()); val response = start()
        assertTrue(response.getHeader("Location")!!.startsWith("/users/loginform"), response.getHeader("Location"))
        assertNull(mockMvc.perform(get("/authenticate/github").param("code", "x").param("state", "y")).andReturn().response.getHeader("Location")?.takeIf { it.startsWith("https://github.test") })
    }

    @Test
    fun `사내 SAML을 켜면 소셜 로그인은 보이지도 시작되지도 않는다`() {
        sso.saveSaml2(Saml2SsoForm(true, "사내", samlIdp.entityId, samlIdp.ssoUrl, samlIdp.certificatePem, "", "", "email", "name", "", true, true, false, false, false, false, 60), null)
        assertTrue(socialButtons().isEmpty()); assertTrue(start().getHeader("Location")!!.startsWith("/users/loginform"))
    }

    @Test
    fun `사내 IdP를 다시 끄면 소셜 로그인이 돌아온다`() {
        sso.saveOidc(OidcSsoForm(true, "사내", oidcIdp.issuer, oidcIdp.clientId, oidcIdp.clientSecret, "openid", "email", "name", "", true, true, false, false, false, 60), null)
        sso.saveOidc(OidcSsoForm(false, "사내", oidcIdp.issuer, oidcIdp.clientId, "", "openid", "email", "name", "", true, true, false, false, false, 60), null)
        assertEquals(1, socialButtons().size); assertNotNull(start().getHeader("Location")?.takeIf { it.startsWith("https://github.test") })
    }
}
