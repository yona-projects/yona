package dev.yona.web.user

import dev.yona.domain.twofactor.Base32
import dev.yona.domain.twofactor.KnownDeviceService
import dev.yona.domain.twofactor.Totp
import dev.yona.domain.twofactor.TwoFactorService
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
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정의 2단계 인증 화면을 검증한다(X-2 5단계, `decisions.md` D-69). 목록 화면(`/user/editform/two-factor`)에서 인증 앱 추가를 시작하면 QR 코드와 비밀 값이 있는 설정 화면이 나오고,
 * 코드를 확인하면 백업 코드를 한 번 보여 준다. 켜진 뒤의 목록에는 인증 앱 삭제, 백업 코드 다시 만들기, 알려진 기기 삭제가 있다.
 */
@AutoConfigureMockMvc
@Transactional
class TwoFactorSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var service: TwoFactorService
    @Autowired private lateinit var devices: KnownDeviceService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ts$n-${System.nanoTime()}", name = "ts$n", email = "ts$n@example.com", passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun doc(body: String): Document = Jsoup.parse(body)

    private fun page(s: MockHttpSession, path: String = "/user/editform/two-factor"): Document = doc(mockMvc.perform(get(path).session(s)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun code(secret: ByteArray) = Totp.code(secret, Instant.now().epochSecond / 30)

    /** 설정 화면에서 비밀 값을 읽어 확인까지 마친다. 백업 코드 화면을 돌려준다. */
    private fun enable(u: User, s: MockHttpSession): Pair<ByteArray, Document> {
        val setup = doc(mockMvc.perform(post("/user/two-factor/begin").session(s)).andExpect(status().isOk).andReturn().response.contentAsString)
        val secret = Base32.decode(setup.selectFirst("#two-factor-secret")!!.text().replace(" ", ""))
        val done = doc(mockMvc.perform(post("/user/two-factor/totp").session(s).param("code", code(secret))).andExpect(status().isOk).andReturn().response.contentAsString)
        return secret to done
    }

    @Test
    fun `로그인해야 쓸 수 있다`() {
        mockMvc.perform(get("/user/editform/two-factor")).andExpect(status().is3xxRedirection)
        for (path in listOf("begin", "totp", "credentials/1/delete", "backup-codes", "devices/1/delete", "webauthn/options", "webauthn/register")) mockMvc.perform(post("/user/two-factor/$path")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `설정 탭에 2단계 인증 탭이 있고 목록 화면에서 활성이다`() {
        val s = login(user())
        assertTrue(page(s).select("ul.nav-tabs a[href$=/user/editform/two-factor]").single().hasClass("active"))
        assertNotNull(page(s, "/user/editform").select("ul.nav-tabs a[href$=/user/editform/two-factor]").singleOrNull())
    }

    @Test
    fun `켜지 않았으면 안내와 인증 앱 추가 버튼만 보인다`() {
        val list = page(login(user()))
        assertNotNull(list.selectFirst("#two-factor-off")); assertNotNull(list.selectFirst("form[action$=/user/two-factor/begin] button"))
        assertNull(list.selectFirst("form[action$=/user/two-factor/disable]")); assertNull(list.selectFirst("#two-factor-secret"))
    }

    @Test
    fun `보안 키 추가는 목록과 따로 있는 화면이고 이름 입력란과 등록 단추가 있다`() {
        mockMvc.perform(get("/user/editform/two-factor/security-key")).andExpect(status().is3xxRedirection)
        val s = login(user())
        assertTrue(page(s).selectFirst("a#security-key-new")!!.attr("href").endsWith("/user/editform/two-factor/security-key"))
        val add = page(s, "/user/editform/two-factor/security-key")
        assertNotNull(add.selectFirst("input#security-key-name")); assertNotNull(add.selectFirst("button#security-key-add"))
        assertTrue(add.select("ul.nav-tabs a[href$=/user/editform/two-factor]").single().hasClass("active"))
    }

    @Test
    fun `추가를 시작하면 QR 코드와 비밀 값과 코드 입력란이 나오고 캐시를 막는다`() {
        val s = login(user())
        val result = mockMvc.perform(post("/user/two-factor/begin").session(s)).andExpect(status().isOk).andExpect(header().string("Cache-Control", "no-cache, no-store, must-revalidate")).andReturn()
        val setup = doc(result.response.contentAsString)
        assertTrue(setup.selectFirst("#two-factor-qr svg") != null, "QR 코드는 인라인 SVG이다")
        assertTrue(setup.selectFirst("#two-factor-secret")!!.text().isNotBlank())
        assertTrue(setup.selectFirst("form#two-factor-confirm")!!.attr("action").endsWith("/user/two-factor/totp"))
        assertNotNull(setup.selectFirst("form#two-factor-confirm input[name=code]"))
    }

    @Test
    fun `틀린 코드는 같은 비밀 값으로 설정 화면을 다시 보이며 400이다`() {
        val s = login(user())
        val first = doc(mockMvc.perform(post("/user/two-factor/begin").session(s)).andReturn().response.contentAsString).selectFirst("#two-factor-secret")!!.text()
        val retry = mockMvc.perform(post("/user/two-factor/totp").session(s).param("code", "abc")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        assertEquals(first, doc(retry).selectFirst("#two-factor-secret")!!.text(), "비밀 값이 바뀌면 이미 등록한 앱이 쓸모없어진다")
        assertNotNull(doc(retry).selectFirst(".alert-danger"))
    }

    @Test
    fun `맞는 코드로 확인하면 백업 코드 열 개를 한 번 보이고 목록은 켜진 상태이다`() {
        val u = user(); val s = login(u)
        val (_, done) = enable(u, s)
        val codes = done.select("#backup-codes li").map { it.text().trim() }
        assertEquals(10, codes.size); assertTrue(codes.all { Regex("[a-z2-9]{5}-[a-z2-9]{5}").matches(it) })
        val list = page(s)
        assertNull(list.selectFirst("#two-factor-off")); assertEquals(1, list.select("table#two-factor-list tr.method-row").size)
        assertTrue(list.selectFirst("#backup-remaining")!!.text().contains("10"))
        assertTrue(list.select("#backup-codes li").isEmpty(), "백업 코드는 다시 보이지 않는다")
    }

    @Test
    fun `이미 켜져 있으면 다시 추가를 시작하지 못한다`() {
        val u = user(); val s = login(u); enable(u, s)
        mockMvc.perform(post("/user/two-factor/begin").session(s)).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `백업 코드를 다시 만들면 새 코드를 한 번 보인다`() {
        val u = user(); val s = login(u); val (_, first) = enable(u, s)
        val again = doc(mockMvc.perform(post("/user/two-factor/backup-codes").session(s)).andExpect(status().isOk).andReturn().response.contentAsString)
        val old = first.select("#backup-codes li").map { it.text().trim() }.toSet(); val fresh = again.select("#backup-codes li").map { it.text().trim() }
        assertEquals(10, fresh.size); assertTrue(fresh.none { it in old })
    }

    @Test
    fun `끄면 목록은 다시 꺼진 상태이다`() {
        val u = user(); val s = login(u); enable(u, s)
        val id = doc(mockMvc.perform(get("/user/editform/two-factor").session(s)).andReturn().response.contentAsString).selectFirst("tr.method-row form")!!.attr("action").substringAfter("/credentials/").substringBefore("/delete")
        mockMvc.perform(post("/user/two-factor/credentials/$id/delete").session(s)).andExpect(status().is3xxRedirection)
        assertNotNull(page(s).selectFirst("#two-factor-off"))
        assertFalse(service.isEnabled(users.findById(u.id!!).get()))
    }

    @Test
    fun `남의 수단을 지우려 하면 404이다`() {
        val a = user(); val sa = login(a); enable(a, sa); val b = user(); val sb = login(b)
        val id = doc(mockMvc.perform(get("/user/editform/two-factor").session(sa)).andReturn().response.contentAsString).selectFirst("tr.method-row form")!!.attr("action").substringAfter("/credentials/").substringBefore("/delete")
        mockMvc.perform(post("/user/two-factor/credentials/$id/delete").session(sb)).andExpect(status().isNotFound)
    }

    @Test
    fun `알려진 기기는 목록에 보이고 본인 것만 지울 수 있다`() {
        val u = user(); val s = login(u); enable(u, s); val other = user(); enable(other, login(other))
        devices.issue(u, "Firefox on Linux", "10.0.0.1"); devices.issue(other, "Chrome on Windows", "10.0.0.2")
        val rows = page(s).select("table#device-list tr.device-row")
        assertEquals(1, rows.size); assertTrue(rows[0].text().contains("Firefox on Linux"))
        val otherId = devices.list(other).single().id
        mockMvc.perform(post("/user/two-factor/devices/$otherId/delete").session(s)).andExpect(status().isNotFound)
        val mine = devices.list(u).single().id
        mockMvc.perform(post("/user/two-factor/devices/$mine/delete").session(s)).andExpect(status().is3xxRedirection)
        assertTrue(page(s).select("tr.device-row").isEmpty())
    }
}
