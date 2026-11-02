package dev.yona.oauth2server

import dev.yona.domain.enumeration.UserState
import dev.yona.domain.oauth2server.ClientCreation
import dev.yona.domain.oauth2server.OAuthClient
import dev.yona.domain.oauth2server.OAuthClientService
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import dev.yona.support.selectFirst
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 승인(동의) 화면(X-1 3단계, `decisions.md` D-71)을 검증한다: 로그인해야 볼 수 있고, 앱 이름과 만든 사용자와 돌아갈 주소와 요청한 범위의 설명을 보이고, `localhost`로 돌아오는 앱에는
 * 경고를 보이며, 다른 사용자의 승인 요청이나 모르는 요청은 열지 않는다. 허용은 요청한 범위 전체이고(범위를 줄이지 않는다), 거부는 `access_denied`로 돌려보낸다.
 */
@AutoConfigureMockMvc
@Transactional
class OAuthConsentPageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var clientService: OAuthClientService
    @Autowired private lateinit var organizationService: dev.yona.domain.project.OrganizationService
    @Autowired private lateinit var management: dev.yona.domain.project.OrganizationManagementService
    @Autowired private lateinit var orgPolicy: dev.yona.domain.oauth2server.OAuthOrgPolicyService

    private var n = 0

    private fun user(): User {
        val k = ++n; val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "oc$k-${System.nanoTime()}", name = "oc$k", email = "oc$k@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User) = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun app(owner: User, redirect: String = "https://app.example.com/callback", name: String = "테스트 앱", scopes: Set<String> = setOf("read:user", "repo")): OAuthClient =
        (clientService.create(owner, name, "", "https://app.example.com", redirect, false, scopes) as ClientCreation.Created).client

    private val verifier = "a".repeat(64)
    private fun challenge() = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

    /** 승인 요청을 보내 승인 화면 주소(`/oauth2/consent?...`)를 돌려받는다. */
    private fun consentUrl(session: MockHttpSession, client: OAuthClient, redirect: String = "https://app.example.com/callback", scope: String = "read:user repo"): String {
        val result = mockMvc.perform(
            get("/oauth2/authorize").session(session).queryParam("response_type", "code").queryParam("client_id", client.clientId!!).queryParam("scope", scope)
                .queryParam("state", "xyz").queryParam("redirect_uri", redirect).queryParam("code_challenge", challenge()).queryParam("code_challenge_method", "S256")
        ).andReturn()
        val location = result.response.getHeader("Location")!!
        assertTrue(location.contains("/oauth2/consent"), location)
        return location.substring(location.indexOf("/oauth2/consent"))
    }

    private fun page(session: MockHttpSession, url: String): Document = Jsoup.parse(mockMvc.perform(get(java.net.URI.create(url)).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    @Test
    fun `조직이 소유한 앱의 승인 화면은 만든 조직의 이름을 보인다`() {
        val admin = user(); val me = user()
        val org = organizationService.create("oc${System.nanoTime() % 1000000}", null, admin)
        val client = (clientService.createFor(dev.yona.domain.oauth2server.ClientOwner.of(org), admin, "조직 앱", "", "https://app.example.com", "https://app.example.com/callback", false, setOf("read:user")) as ClientCreation.Created).client
        val s = login(me)
        val line = page(s, consentUrl(s, client, scope = "read:user")).selectFirst("p.tag-line")!!.text()
        assertTrue(line.contains(org.name!!), line); assertFalse(line.contains(admin.loginId!!), line)
    }

    @Test
    fun `내가 속한 제한 조직이 승인하지 않은 앱이면 승인 화면에 경고를 보인다`() {
        val admin = user(); val me = user(); val owner = user()
        val org = organizationService.create("ow${System.nanoTime() % 1000000}", null, admin); management.addMember(org, admin, me.loginId!!)
        val client = app(owner); val s = login(me)
        assertTrue(page(s, consentUrl(s, client, scope = "read:user")).select("#org-policy-warning").isEmpty())
        orgPolicy.setRestricted(org, true, admin)
        val warning = page(s, consentUrl(s, client, scope = "read:user")).selectFirst("#org-policy-warning")!!.text()
        assertTrue(warning.contains(org.name!!), warning)
    }

    @Test
    fun `로그인해야 승인 화면을 볼 수 있다`() {
        mockMvc.perform(get("/oauth2/consent?client_id=x&scope=read%3Auser&state=s")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `앱 이름과 만든 사용자와 돌아갈 주소와 범위 설명을 보이고 허용 폼은 요청한 범위 전체를 보낸다`() {
        val owner = user(); val client = app(owner); val visitor = user(); val s = login(visitor)
        val doc = page(s, consentUrl(s, client))
        assertTrue(doc.text().contains("테스트 앱") && doc.text().contains(owner.loginId!!), "앱 이름과 만든 사용자")
        assertTrue(doc.text().contains("app.example.com"), "돌아갈 도메인")
        assertEquals(2, doc.select("ul#oauth-scopes li").size)
        assertTrue(doc.select("ul#oauth-scopes li").any { it.text().contains("repo") }, "범위 이름과 설명")
        val form = doc.selectFirst("form#consent-form")!!
        assertTrue(form.attr("action").endsWith("/oauth2/authorize") && form.attr("method").equals("post", true))
        assertEquals("yona_app_", form.selectFirst("input[name=client_id]")!!.attr("value").take(9))
        assertTrue(form.selectFirst("input[name=state]")!!.attr("value").isNotEmpty())
        assertEquals(setOf("read:user", "repo"), form.select("input[name=scope]").map { it.attr("value") }.toSet(), "범위를 줄이지 않는다")
        assertNotNull(form.selectFirst("button#consent-allow")); assertNotNull(doc.selectFirst("button#consent-deny"))
        assertNull(doc.selectFirst("#loopback-warning"))
    }

    @Test
    fun `승인 화면은 캐시를 막고 다른 곳에 끼워 넣는 것을 막는다`() {
        val owner = user(); val client = app(owner); val s = login(user())
        mockMvc.perform(get(java.net.URI.create(consentUrl(s, client))).session(s)).andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andExpect(header().string("Content-Security-Policy", "frame-ancestors 'none'")).andExpect(header().string("X-Frame-Options", "DENY"))
    }

    @Test
    fun `localhost로 돌아오는 앱에는 이 컴퓨터에서 실행 중인 프로그램이라는 경고를 보인다`() {
        val owner = user(); val client = app(owner, redirect = "http://localhost/callback"); val s = login(user())
        val doc = page(s, consentUrl(s, client, redirect = "http://127.0.0.1:51234/callback"))
        assertNotNull(doc.selectFirst("#loopback-warning"))
    }

    @Test
    fun `다른 사용자의 승인 요청이나 모르는 요청은 열지 않는다`() {
        val owner = user(); val client = app(owner); val a = login(user()); val b = login(user())
        val url = consentUrl(a, client)
        mockMvc.perform(get(java.net.URI.create(url)).session(b)).andExpect(status().isBadRequest)
        mockMvc.perform(get("/oauth2/consent?client_id=${client.clientId}&scope=read%3Auser&state=nope").session(a)).andExpect(status().isBadRequest)
        mockMvc.perform(get("/oauth2/consent?client_id=${client.clientId}&scope=read%3Auser").session(a)).andExpect(status().isBadRequest)
    }

    @Test
    fun `허용하면 코드가 나오고 거부하면 access_denied로 돌려보낸다`() {
        val owner = user(); val client = app(owner); val s = login(user())
        val state = URLDecoder.decode(consentUrl(s, client).substringAfter("state=").substringBefore('&'), StandardCharsets.UTF_8)
        val denied = mockMvc.perform(post("/oauth2/authorize").session(s).param("client_id", client.clientId!!).param("state", state)).andReturn()
        assertEquals(302, denied.response.status)
        assertTrue(denied.response.getHeader("Location")!!.contains("error=access_denied") && denied.response.getHeader("Location")!!.contains("state=xyz"), denied.response.getHeader("Location"))
        val state2 = URLDecoder.decode(consentUrl(s, client).substringAfter("state=").substringBefore('&'), StandardCharsets.UTF_8)
        val allowed = mockMvc.perform(post("/oauth2/authorize").session(s).param("client_id", client.clientId!!).param("state", state2).param("scope", "read:user").param("scope", "repo")).andReturn()
        assertTrue(allowed.response.getHeader("Location")!!.contains("code="), allowed.response.getHeader("Location"))
    }
}
