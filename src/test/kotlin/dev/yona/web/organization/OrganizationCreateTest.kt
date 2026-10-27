package dev.yona.web.organization

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUserRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 조직 생성 폼(`GET /organizations/new`)과 생성(`POST /organizations/new`)을 검증한다.
 *
 * 원본: `app/controllers/OrganizationApp.java:60-115`, `app/views/organization/create.scala.html`,
 * `app/models/Organization.java:60-75`, `app/models/User.java:911-922`.
 */
@AutoConfigureMockMvc
@Transactional
class OrganizationCreateTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository

    private var sequence = 0

    private fun newUser(guest: Boolean = false, loginId: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = loginId ?: "oc$n", name = "oc$n", email = "oc$n@example.com", state = UserState.ACTIVE, isGuest = guest,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun submit(session: MockHttpSession?, name: String?, descr: String? = null): MvcResult {
        val request = post("/organizations/new")
        session?.let { request.session(it) }
        name?.let { request.param("name", it) }
        descr?.let { request.param("descr", it) }
        return mockMvc.perform(request).andReturn()
    }

    private fun doc(result: MvcResult): Document = Jsoup.parse(result.response.contentAsString)

    /** v1.6 `create.scala.html`은 폼 위에 앞 요청이 남긴 안내(`requestHeader.flash`)를 보이므로, 같은 세션으로 폼을 다시 열어 읽는다. */
    private fun warningOnNextForm(session: MockHttpSession): String? =
        Jsoup.parse(mockMvc.perform(get("/organizations/new").session(session)).andReturn().response.contentAsString).selectFirst(".n-alert .warning")?.text()

    /** 이번 응답이 알림(`$yobi.notify`)으로 보이는 안내이다. */
    private fun notified(result: MvcResult): String? =
        Regex("\\\$yobi\\.notify\\(\"([^\"]*)\"").find(result.response.contentAsString)?.groupValues?.get(1)

    // --- 폼 ---

    @Test
    fun `폼은 로그인해야 열 수 있다`() {
        mockMvc.perform(get("/organizations/new"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", "/users/loginform?redirectUrl=/organizations/new"))
            .andExpect(flash().attribute("warning", "user.login.alert"))
    }

    @Test
    fun `폼의 구성`() {
        val page = Jsoup.parse(mockMvc.perform(get("/organizations/new").session(login(newUser()))).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("Yona", page.title())
        val form = page.selectFirst("form[name=new-org]")!!
        assertEquals("/organizations/new", form.attr("action"))
        assertEquals("post", form.attr("method").lowercase())
        assertEquals("New Group", form.selectFirst("legend")?.text()?.trim())
        assertEquals("250", form.selectFirst("input#name[name=name]")?.attr("maxlength"))
        assertNotNull(form.selectFirst("textarea#descr[name=descr]"))
        assertEquals("input group name", form.selectFirst("label[for=name]")?.text())
        assertEquals("Create Group", form.selectFirst("button.ybtn-success")?.text()?.trim())
        assertEquals("/", form.selectFirst("a.ybtn")?.attr("href"))
        assertNotNull(form.selectFirst(".n-alert[data-errType=name]"))
    }

    // --- 생성 ---

    @Test
    fun `조직을 만들면 만든 사람이 조직 관리자가 되고 조직 화면으로 간다`() {
        val creator = newUser()
        val result = submit(login(creator), "myorg", "설명")
        assertEquals(303, result.response.status)
        assertEquals("/organizations/myorg", result.response.getHeader("Location"))
        val org = organizations.findByNameIgnoreCase("myorg")!!
        assertEquals("설명", org.descr)
        assertNotNull(org.created)
        assertTrue(organizationUsers.existsByOrganizationIdAndUserIdAndRoleId(org.id!!, creator.id!!, RoleType.ORG_ADMIN.id))
    }

    @Test
    fun `이름이 없으면 거부하고 안내한다`() {
        val session = login(newUser())
        val result = submit(session, null)
        assertEquals(400, result.response.status)
        assertEquals("This field is required", notified(result))
        assertNull(doc(result).selectFirst(".n-alert .warning"))
        assertEquals("This field is required", warningOnNextForm(session))
    }

    @Test
    fun `이름 형식이 틀리면 거부하고 입력값을 유지한다`() {
        val session = login(newUser())
        val result = submit(session, "bad name", "내 설명")
        assertEquals(400, result.response.status)
        val page = doc(result)
        assertEquals("Enter Valid ID", notified(result))
        assertEquals("Enter Valid ID", warningOnNextForm(session))
        assertEquals("bad name", page.selectFirst("input#name")?.attr("value"))
        assertEquals("내 설명", page.selectFirst("textarea#descr")?.text())
    }

    @Test
    fun `예약어 이름은 거부한다`() {
        val session = login(newUser())
        val result = submit(session, "Projects")
        assertEquals(400, result.response.status)
        assertEquals("This is a reserved system word.", notified(result))
        assertEquals("This is a reserved system word.", warningOnNextForm(session))
    }

    @Test
    fun `이미 있는 사용자 로그인 ID나 조직 이름은 거부한다`() {
        val taken = newUser(loginId = "takenname")
        val creator = newUser()
        val session = login(creator)
        assertEquals(400, submit(session, "TakenName").response.status)
        submit(session, "firstorg")
        val again = submit(session, "FirstOrg")
        assertEquals(400, again.response.status)
        assertEquals("Already existent user&#x27;s login id or group name.", notified(again))
        assertEquals("Already existent user's login id or group name.", warningOnNextForm(session))
        assertNotNull(taken.id)
    }

    @Test
    fun `생성은 로그인해야 하고 게스트는 쓸 수 없다`() {
        assertEquals(303, submit(null, "someorg").response.status)
        val guest = newUser(guest = true)
        val result = submit(login(guest), "guestorg")
        assertEquals(303, result.response.status)
        assertEquals(null, organizations.findByNameIgnoreCase("guestorg"))
    }
}
