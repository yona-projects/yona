package dev.yona.web.user

import dev.yona.domain.deviceauth.DeviceAuthorizationService
import dev.yona.domain.deviceauth.DevicePoll
import dev.yona.domain.deviceauth.DeviceStart
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 기기 인증의 웹 화면(`/users/device`)을 검증한다(로드맵 X-37i, `decisions.md` D-52, A-281). 로그인한 사용자가 CLI가 보여 준 코드를 입력하고, 요청한 기기와 범위를 확인한 뒤
 * 승인하거나 거절한다. 승인 결과는 CLI의 토큰 교환에 반영된다.
 */
@AutoConfigureMockMvc
@Transactional
class DeviceAuthorizationPageTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var service: DeviceAuthorizationService

    private var sequence = 0

    private fun user(siteAdmin: Boolean = false): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "dp$n", name = "dp$n", email = "dp$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt))).also { if (siteAdmin) siteAdmins.save(SiteAdmin(admin = it)) }
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun issue(client: String = "yona-cli on laptop", vararg scopes: String): DeviceStart.Issued = service.start(client, scopes.toList()) as DeviceStart.Issued

    private fun page(session: MockHttpSession, path: String, expected: Int = 200): Document =
        Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().`is`(expected)).andReturn().response.contentAsString)

    @Test
    fun `로그인해야 하고 코드 입력 폼이 보인다`() {
        mockMvc.perform(get("/users/device")).andExpect(status().is3xxRedirection)
        val doc = page(login(user()), "/users/device")
        val form = doc.selectFirst("form#device-code-form")
        assertNotNull(form)
        assertEquals("/users/device", form.attr("action"))
        assertEquals("get", form.attr("method").lowercase())
        assertNotNull(form.selectFirst("input[name=user_code]"))
        assertNull(doc.selectFirst("#device-consent"))
    }

    @Test
    fun `코드를 입력하면 기기 이름과 받게 될 범위를 보여 주고 승인과 거절 폼이 있다`() {
        val issued = issue("yona-cli on laptop", "repo", "read:org")
        // 하이픈과 대소문자는 가리지 않는다
        val doc = page(login(user()), "/users/device?user_code=" + issued.code.userCode.replace("-", "").lowercase())
        val consent = doc.selectFirst("#device-consent")
        assertNotNull(consent)
        assertTrue(consent.text().contains("yona-cli on laptop"))
        assertEquals(listOf("repo", "read:org"), consent.select("ul#device-scopes li code").map { it.text() })
        val approve = consent.selectFirst("form#device-approve")
        assertNotNull(approve)
        assertEquals("/users/device/approve", approve.attr("action"))
        assertEquals(issued.code.userCode, approve.selectFirst("input[name=user_code]")!!.attr("value"))
        assertEquals("/users/device/deny", consent.selectFirst("form#device-deny")!!.attr("action"))
    }

    @Test
    fun `승인 화면은 요청한 기기의 주소와 요청 시각을 보여 사용자가 자기 기기인지 확인하게 한다 (oauth-F6)`() {
        val issued = service.start("laptop", listOf("repo"), java.util.Date(), "203.0.113.7") as DeviceStart.Issued
        val consent = page(login(user()), "/users/device?user_code=" + issued.code.userCode).selectFirst("#device-consent")!!
        assertEquals("203.0.113.7", consent.selectFirst("#device-request-ip")!!.text())
        assertTrue(consent.selectFirst("#device-request-time")!!.text().isNotBlank())
        // 주소를 모르는 요청은 그 줄을 보이지 않는다
        val noIp = service.start("laptop", listOf("repo")) as DeviceStart.Issued
        assertNull(page(login(user()), "/users/device?user_code=" + noIp.code.userCode).selectFirst("#device-request-ip"))
    }

    @Test
    fun `사이트 관리자 범위는 사이트 관리자에게만 받게 될 범위로 보인다`() {
        val issued = issue("laptop", "repo", "site_admin")
        assertEquals(listOf("repo"), page(login(user()), "/users/device?user_code=" + issued.code.userCode).select("ul#device-scopes li code").map { it.text() })
        assertEquals(listOf("repo", "site_admin"), page(login(user(siteAdmin = true)), "/users/device?user_code=" + issued.code.userCode).select("ul#device-scopes li code").map { it.text() })
    }

    @Test
    fun `틀린 코드를 5번 입력하면 15분 동안 맞는 코드도 429로 받지 않는다 (oauth-F5, 코드 공간이 작아 맞힐 수 있었다)`() {
        val me = user(); val session = login(me); val issued = issue("laptop", "repo")
        repeat(4) { page(session, "/users/device?user_code=BCDF-GHJK", 400) }
        page(session, "/users/device?user_code=BCDF-GHJK", 429) // 다섯 번째 실패가 잠금을 건다
        val locked = page(session, "/users/device?user_code=" + issued.code.userCode, 429)
        assertNotNull(locked.selectFirst(".alert-danger"))
        assertNull(locked.selectFirst("#device-consent"))
        mockMvc.perform(post("/users/device/approve").session(session).param("user_code", issued.code.userCode)).andExpect(status().`is`(429))
        // 다른 사용자는 영향이 없다
        assertNotNull(page(login(user()), "/users/device?user_code=" + issued.code.userCode).selectFirst("#device-consent"))
    }

    @Test
    fun `코드를 맞히면 틀린 횟수가 지워진다`() {
        val session = login(user()); val issued = issue("laptop", "repo")
        repeat(4) { page(session, "/users/device?user_code=BCDF-GHJK", 400) }
        page(session, "/users/device?user_code=" + issued.code.userCode)
        repeat(4) { page(session, "/users/device?user_code=BCDF-GHJK", 400) }
        page(session, "/users/device?user_code=" + issued.code.userCode)
    }

    @Test
    fun `없는 코드는 400과 오류 문구이고 다시 입력할 수 있다`() {
        val doc = page(login(user()), "/users/device?user_code=BCDF-GHJK", 400)
        assertNotNull(doc.selectFirst(".alert-danger"))
        assertNotNull(doc.selectFirst("form#device-code-form"))
        assertNull(doc.selectFirst("#device-consent"))
    }

    @Test
    fun `승인하면 CLI의 교환이 토큰을 받는다`() {
        val issued = issue("yona-cli on laptop", "repo")
        val user = user(); val session = login(user)
        val doc = Jsoup.parse(mockMvc.perform(post("/users/device/approve").session(session).param("user_code", issued.code.userCode)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst("#device-done"))
        val granted = service.exchange(issued.code.deviceCode)
        assertTrue(granted is DevicePoll.Granted && granted.scopes == setOf("repo"), granted.toString())
    }

    @Test
    fun `거절하면 CLI의 교환이 access_denied를 받는다`() {
        val issued = issue()
        val session = login(user())
        mockMvc.perform(post("/users/device/deny").session(session).param("user_code", issued.code.userCode)).andExpect(status().isOk)
        assertEquals(DevicePoll.AccessDenied, service.exchange(issued.code.deviceCode))
    }

    @Test
    fun `없거나 이미 처리한 코드의 승인은 400이고 익명은 로그인으로 보낸다`() {
        val issued = issue()
        val session = login(user())
        mockMvc.perform(post("/users/device/approve").session(session).param("user_code", "BCDF-GHJK")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/users/device/approve").session(session).param("user_code", issued.code.userCode)).andExpect(status().isOk)
        mockMvc.perform(post("/users/device/approve").session(session).param("user_code", issued.code.userCode)).andExpect(status().isBadRequest)
        mockMvc.perform(post("/users/device/approve").param("user_code", issue().code.userCode)).andExpect(status().is3xxRedirection)
    }
}
