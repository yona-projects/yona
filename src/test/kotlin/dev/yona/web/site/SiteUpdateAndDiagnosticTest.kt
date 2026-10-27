package dev.yona.web.site

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.PropertyName
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.misc.Property
import dev.yona.domain.misc.PropertyRepository
import dev.yona.domain.site.RemoteTags
import dev.yona.domain.site.UpdateCheckService
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import org.mockito.Mockito
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 사이트 관리의 업데이트 확인(`/sites/update`, `POST /sites/unwatchUpdate`)과 진단(`/sites/diagnostic`), 모든 화면 위쪽의 업데이트 알림을 검증한다.
 *
 * 원본: `app/controllers/SiteApp.java:318-350`, `app/views/site/update.scala.html`, `diagnostic.scala.html`,
 * `app/views/partial_update_notification.scala.html`, `app/models/YobiUpdate.java`, `app/utils/Diagnostic.java`.
 */
@AutoConfigureMockMvc
@Transactional
class SiteUpdateAndDiagnosticTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var properties: PropertyRepository
    @Autowired private lateinit var updates: UpdateCheckService
    @MockitoBean private lateinit var remoteTags: RemoteTags

    private var sequence = 0

    @BeforeEach
    fun reset() { updates.reset() }

    @AfterEach
    fun cleanUp() { updates.reset() }

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "su$n", name = "su$n", email = "su$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun admin(): MockHttpSession = login(newUser().also { siteAdmins.save(SiteAdmin(admin = it)) })

    private fun tags(vararg names: String) { Mockito.`when`(remoteTags.list(Mockito.anyString())).thenReturn(names.map { "refs/tags/$it" }) }

    // --- 업데이트 ---

    @Test
    fun `새 버전이 있으면 알려 주고 내려받기 링크를 준다`() {
        tags("v1.16.0", "v99.0.0")
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/update").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select(".span10 p").first()!!.text().contains("99.0.0"))
        assertEquals("https://github.com/yona-projects/yona/releases/tag/v99.0.0", doc.selectFirst(".span10 a.ybtn-success")!!.attr("href"))
        assertTrue(doc.select(".span10 p").any { it.text().contains(updates.currentVersion) })
        assertEquals("/sites/update", doc.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
        assertTrue(doc.select("ul.site-setting-nav li.active .notification-badge").isNotEmpty())
    }

    @Test
    fun `새 버전이 없으면 필요 없다고 알린다`() {
        tags("v0.0.1")
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/update").session(admin())).andReturn().response.contentAsString)
        assertTrue(doc.select(".span10 a.ybtn-success").isEmpty())
        assertTrue(doc.select(".span10 p").any { it.text().contains(updates.currentVersion) })
        assertTrue(doc.select(".span10 pre").isEmpty())
    }

    @Test
    fun `저장소를 읽지 못하면 오류와 스택 트레이스를 보인다`() {
        Mockito.`when`(remoteTags.list(Mockito.anyString())).thenThrow(IllegalStateException("network-down-marker"))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/update").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.selectFirst(".span10 pre")!!.text().contains("network-down-marker"))
        assertTrue(doc.select(".span10 a.ybtn-success").isEmpty())
    }

    @Test
    fun `업데이트 화면은 사이트 관리자만 본다`() {
        mockMvc.perform(get("/sites/update").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/unwatchUpdate").session(login(newUser()))).andExpect(status().isForbidden)
    }

    // --- 알림 ---

    private fun notice(session: MockHttpSession?) =
        Jsoup.parse(mockMvc.perform(get("/sites/diagnostic").apply { session?.let { session(it) } }).andReturn().response.contentAsString).select("p.center-txt")

    @Test
    fun `새 버전이 있으면 사이트 관리자의 모든 화면 위에 알림이 보이고 끌 수 있다`() {
        tags("v99.0.0")
        updates.refresh()
        val session = admin()
        val notice = notice(session).first()!!
        assertEquals("https://github.com/yona-projects/yona/releases/tag/v99.0.0", notice.selectFirst("a")!!.attr("href"))
        assertEquals("post", notice.selectFirst("button")!!.attr("data-request-method"))
        assertEquals("/sites/unwatchUpdate", notice.selectFirst("button")!!.attr("data-request-uri"))
        mockMvc.perform(post("/sites/unwatchUpdate").session(session)).andExpect(status().isOk).andExpect(content().string(""))
        assertTrue(notice(session).isEmpty())
    }

    @Test
    fun `알림은 사이트 관리자에게만 보인다`() {
        tags("v99.0.0")
        updates.refresh()
        assertTrue(notice(login(newUser())).isEmpty())
        assertTrue(notice(null).isEmpty())
    }

    // --- 진단 ---

    @Test
    fun `점검에서 문제가 없으면 없다고 알린다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/diagnostic").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertTrue(doc.select(".span10 ul li pre").isEmpty())
        assertEquals("/sites/diagnostic", doc.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
    }

    @Test
    fun `같은 이름의 속성이 둘 이상이면 문제로 알린다`() {
        properties.save(Property(name = PropertyName.MAILBOX_LAST_SEEN_UID, value = "10"))
        properties.save(Property(name = PropertyName.MAILBOX_LAST_SEEN_UID, value = "11"))
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/diagnostic").session(admin())).andReturn().response.contentAsString)
        val errors = doc.select(".span10 ul li pre").map { it.text() }
        assertEquals(1, errors.size)
        assertTrue(errors[0].startsWith("Property 'MAILBOX_LAST_SEEN_UID' has duplicated values: "))
    }

    @Test
    fun `진단은 사이트 관리자만 본다`() {
        mockMvc.perform(get("/sites/diagnostic").session(login(newUser()))).andExpect(status().isForbidden)
    }
}
