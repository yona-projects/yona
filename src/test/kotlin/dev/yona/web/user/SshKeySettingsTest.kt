package dev.yona.web.user

import dev.yona.domain.sshkey.SshKeyRepository
import dev.yona.domain.sshkey.SshKeyService
import dev.yona.domain.sshkey.TestSshKey
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 설정의 SSH 키 화면을 검증한다(X-3 3단계, `decisions.md` D-66). 구성은 목록 화면(`/user/editform/ssh`)과 추가 화면(`/user/editform/ssh/new`) 두 개이고,
 * 목록의 행 동작은 삭제 버튼 하나뿐이다(편집과 상세 없음, D-65 8번을 따른다).
 */
@AutoConfigureMockMvc
@Transactional
class SshKeySettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var service: SshKeyService
    @Autowired private lateinit var keys: SshKeyRepository

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ss$n", name = "ss$n", email = "ss$n@example.com", passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(u: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", u.loginId!!).param("password", "1234")) }

    private fun page(session: MockHttpSession, path: String = "/user/editform/ssh"): Document = Jsoup.parse(mockMvc.perform(get(path).session(session)).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun add(session: MockHttpSession, name: String, key: String) = post("/user/ssh").session(session).param("name", name).param("publicKey", key)

    @Test
    fun `로그인해야 목록과 추가 화면과 요청을 쓸 수 있다`() {
        mockMvc.perform(get("/user/editform/ssh")).andExpect(status().is3xxRedirection)
        mockMvc.perform(get("/user/editform/ssh/new")).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/user/ssh").param("publicKey", "x")).andExpect(status().is3xxRedirection)
        mockMvc.perform(post("/user/ssh/1/delete")).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `설정 탭에 SSH 키 탭이 있고 목록 화면에서 활성이다`() {
        val s = login(user())
        val list = page(s)
        val tab = list.select("ul.nav-tabs a[href$=/user/editform/ssh]").single()
        assertTrue(tab.hasClass("active"))
        assertNotNull(page(s, "/user/editform").select("ul.nav-tabs a[href$=/user/editform/ssh]").singleOrNull(), "다른 탭에도 SSH 키 탭이 보인다")
    }

    @Test
    fun `키가 없으면 빈 목록 안내와 추가 링크만 보인다`() {
        val list = page(login(user()))
        assertNull(list.selectFirst("table#ssh-list"))
        assertNotNull(list.selectFirst("p.empty"))
        assertEquals("/user/editform/ssh/new", list.selectFirst("a#ssh-new")!!.attr("href"))
        assertNull(list.selectFirst("form#ssh-create"), "목록 화면에는 추가 폼이 없다")
    }

    @Test
    fun `추가 화면은 이름과 공개키 입력과 취소 링크를 가진다`() {
        val form = page(login(user()), "/user/editform/ssh/new").selectFirst("form#ssh-create")!!
        assertEquals("/user/ssh", form.attr("action"))
        assertEquals("post", form.attr("method"))
        assertNotNull(form.selectFirst("input[name=name]"))
        assertNotNull(form.selectFirst("textarea[name=publicKey]"))
        assertNotNull(form.selectFirst("button[type=submit]"))
        assertEquals("/user/editform/ssh", form.selectFirst("a.cancel")!!.attr("href"))
    }

    @Test
    fun `공개키를 추가하면 목록으로 돌아가 이름과 지문과 이메일이 보인다`() {
        val u = user(); val s = login(u)
        val source = TestSshKey.generate("alice@laptop")
        mockMvc.perform(add(s, "내 노트북", source.publicLine)).andExpect(status().is3xxRedirection)
        val row = page(s).select("table#ssh-list tbody tr.ssh-row").single()
        assertEquals("내 노트북", row.selectFirst(".ssh-name")!!.text())
        assertEquals(source.fingerprint, row.selectFirst(".ssh-fingerprint")!!.text())
        assertEquals("ssh-ed25519", row.selectFirst(".ssh-key-type")!!.text())
    }

    @Test
    fun `목록의 행 동작은 삭제 버튼 하나뿐이다`() {
        val u = user(); val s = login(u)
        mockMvc.perform(add(s, "a", TestSshKey.generate().publicLine))
        val row = page(s).select("tr.ssh-row").single()
        assertEquals(1, row.select("button, input[type=submit]").size)
        assertEquals(0, row.select("a").size, "편집이나 상세로 가는 링크가 없다")
        val form = row.selectFirst("form")!!
        assertTrue(form.attr("action").matches(Regex("/user/ssh/\\d+/delete")))
        assertEquals("post", form.attr("method"))
    }

    @Test
    fun `받을 수 없는 키는 400과 이유 메시지를 추가 화면에 보인다`() {
        val s = login(user())
        val response = mockMvc.perform(add(s, "x", "not a key")).andExpect(status().isBadRequest).andReturn().response.contentAsString
        val doc = Jsoup.parse(response)
        assertNotNull(doc.selectFirst("form#ssh-create"))
        val alert = doc.selectFirst(".alert-danger")!!
        assertTrue(alert.text().isNotBlank() && !alert.text().contains("sshKey.error"), alert.text())
        assertEquals("not a key", doc.selectFirst("textarea[name=publicKey]")!!.text().trim(), "입력한 값을 되돌려 준다")
    }

    @Test
    fun `같은 키를 다시 추가하면 400이다`() {
        val s = login(user())
        val key = TestSshKey.generate().publicLine
        mockMvc.perform(add(s, "a", key)).andExpect(status().is3xxRedirection)
        mockMvc.perform(add(s, "b", key)).andExpect(status().isBadRequest)
    }

    @Test
    fun `삭제하면 목록에서 사라지고 다른 사용자의 키는 404이다`() {
        val owner = user(); val other = user()
        val ownerSession = login(owner)
        mockMvc.perform(add(ownerSession, "a", TestSshKey.generate().publicLine))
        val id = service.list(owner).single().id!!
        mockMvc.perform(post("/user/ssh/$id/delete").session(login(other))).andExpect(status().isNotFound)
        assertEquals(1L, keys.count())
        mockMvc.perform(post("/user/ssh/$id/delete").session(ownerSession)).andExpect(status().is3xxRedirection)
        assertEquals(0L, keys.count())
        assertNull(page(ownerSession).selectFirst("table#ssh-list"))
    }

    @Test
    fun `이름에 든 태그는 이스케이프되어 보인다`() {
        val u = user(); val s = login(u)
        mockMvc.perform(add(s, "<script>alert(1)</script>", TestSshKey.generate().publicLine))
        val html = mockMvc.perform(get("/user/editform/ssh").session(s)).andReturn().response.contentAsString
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
    }
}
