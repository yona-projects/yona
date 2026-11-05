package dev.yona.web.site

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import jakarta.mail.Message
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 사이트 관리의 메일 쓰기(`/sites/mail`)와 대량 메일(`/sites/massmail`, `POST /sites/mailList`)을 검증한다.
 *
 * 원본: `app/controllers/SiteApp.java:87-123, 277-313`, `app/views/site/mail.scala.html`, `app/views/site/massMail.scala.html`,
 * `public/javascripts/service/yobi.site.MassMail.js`.
 */
@AutoConfigureMockMvc
@Transactional
class SiteMailTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @MockitoBean private lateinit var mailSender: JavaMailSender

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "sm$n", name = "sm$n", email = "sm$n@example.com", state = UserState.ACTIVE, createdDate = Date(), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun admin(): MockHttpSession = login(newUser().also { siteAdmins.save(SiteAdmin(admin = it)) })

    private fun project(owner: User, name: String) = projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = ProjectScope.PRIVATE, createdDate = Date()))

    private fun join(user: User, project: Project, role: RoleType) = projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))

    // --- 메일 쓰기 ---

    @Test
    fun `메일 쓰기 화면에는 네 개의 입력과 보내기 버튼이 있다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/mail").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals("/sites/mail", doc.selectFirst("form#mailForm")!!.attr("action"))
        assertEquals("post", doc.selectFirst("form#mailForm")!!.attr("method"))
        assertEquals(listOf("from", "to", "subject"), doc.select("form#mailForm input[name]").map { it.attr("name") })
        assertTrue(doc.select("form#mailForm textarea[name=body]").isNotEmpty())
        assertEquals("/sites/mail", doc.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
    }

    @Test
    fun `메일 서버가 설정되지 않았으면 빠진 항목을 알린다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/mail").session(admin())).andReturn().response.contentAsString)
        val items = doc.select(".alert-danger ul li").map { it.text().trim() }
        assertEquals(listOf("spring.mail.host", "spring.mail.username", "spring.mail.password"), items)
    }

    @Test
    fun `errorMessage와 sended 쿼리는 안내로 보인다`() {
        val session = admin()
        val failed = Jsoup.parse(mockMvc.perform(get("/sites/mail").session(session).param("errorMessage", "site.mail.fail")).andReturn().response.contentAsString)
        assertTrue(failed.select(".alert-danger").first()!!.text().isNotEmpty())
        val sent = Jsoup.parse(mockMvc.perform(get("/sites/mail").session(session).param("sended", "true")).andReturn().response.contentAsString)
        assertTrue(sent.select(".alert-success").isNotEmpty())
    }

    @Test
    fun `메일을 보내지 못하면 성공 안내 대신 실패 안내가 보인다 (R-54-2, v1_6은 보내지 못해도 성공 안내였다)`() {
        Mockito.doThrow(org.springframework.mail.MailSendException("refused")).`when`(mailSender).send(Mockito.any(MimeMessage::class.java))
        val response = mockMvc.perform(
            post("/sites/mail").session(admin()).param("from", "noreply@example.com").param("to", "a@example.com").param("subject", "제목입니다").param("body", "본문 내용")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        val doc = Jsoup.parse(response)
        assertTrue(doc.select(".alert-success").isEmpty(), "실패했는데 성공 안내가 보인다")
        assertTrue(doc.select(".alert-danger").isNotEmpty(), "실패 안내가 보여야 한다")
    }

    @Test
    fun `메일을 보내면 입력한 내용으로 한 통이 나가고 같은 응답에 성공 안내가 보인다`() {
        val response = mockMvc.perform(
            post("/sites/mail").session(admin()).param("from", "noreply@example.com").param("to", "a@example.com").param("subject", "제목입니다").param("body", "본문 내용")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(Jsoup.parse(response).select(".alert-success").isNotEmpty())
        val captor = ArgumentCaptor.forClass(MimeMessage::class.java)
        Mockito.verify(mailSender).send(captor.capture())
        val message = captor.value
        assertEquals("noreply@example.com", (message.from.single() as InternetAddress).address)
        assertEquals("a@example.com", (message.getRecipients(Message.RecipientType.TO).single() as InternetAddress).address)
        assertEquals("제목입니다", message.subject)
        assertEquals("본문 내용", message.content.toString())
        assertTrue(message.contentType.startsWith("text/plain"))
    }

    @Test
    fun `본문이 비었거나 주소가 올바르지 않으면 500이 아니라 400이다 (R-54-1)`() {
        val session = admin()
        mockMvc.perform(post("/sites/mail").session(session).param("from", "a@example.com").param("to", "b@example.com").param("subject", "s").param("body", "")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/sites/mail").session(session).param("from", "not an address").param("to", "b@example.com").param("body", "x")).andExpect(status().isBadRequest)
        Mockito.verifyNoInteractions(mailSender)
    }

    @Test
    fun `메일 쓰기는 사이트 관리자만 쓴다`() {
        mockMvc.perform(get("/sites/mail").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/sites/mail").session(login(newUser())).param("from", "a@example.com").param("to", "b@example.com").param("body", "x")).andExpect(status().isForbidden)
        Mockito.verifyNoInteractions(mailSender)
    }

    // --- 대량 메일 화면 ---

    @Test
    fun `대량 메일 화면에는 받는 사람 종류 두 개와 프로젝트 입력이 있다`() {
        val doc = Jsoup.parse(mockMvc.perform(get("/sites/massmail").session(admin())).andExpect(status().isOk).andReturn().response.contentAsString)
        assertEquals(listOf("all", "projects"), doc.select("input[name=mailingType]").map { it.attr("value") })
        assertEquals("checked", doc.selectFirst("#mailtoAll")!!.attr("checked"))
        assertTrue(doc.selectFirst("#project-list-wrap")!!.hasClass("hide"))
        assertTrue(doc.selectFirst("#write-email") != null && doc.selectFirst("#input-project") != null && doc.selectFirst("#selected-projects") != null)
        assertEquals("/sites/massmail", doc.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
        val script = doc.select("script").joinToString("\n") { it.data() }.replace("\\/", "/")
        assertTrue(script.contains("site.MassMail") && script.contains("/sites/mailList") && script.contains("/projects"))
    }

    // --- 받는 사람 목록 ---

    private fun mailList(session: MockHttpSession, accept: String? = "application/json", vararg params: Pair<String, String>) =
        mockMvc.perform(post("/sites/mailList").session(session).apply { accept?.let { accept(it) }; params.forEach { (k, v) -> param(k, v) } })

    @Test
    fun `all이 true이면 활성 사용자의 이메일만 돌려준다 (R-54-5, v1_6은 삭제되거나 잠긴 사용자도 담았다)`() {
        val a = newUser(); val b = newUser()
        val gone = newUser().also { it.state = UserState.DELETED; users.save(it) }
        val locked = newUser().also { it.state = UserState.LOCKED; users.save(it) }
        val body = mailList(admin(), "application/json", "all" to "true").andExpect(status().isOk).andReturn().response.contentAsString
        listOf(a, b).forEach { assertTrue(body.contains(it.email!!), it.email) }
        listOf(gone, locked).forEach { assertTrue(!body.contains(it.email!!), "${it.state} 사용자의 이메일 ${it.email}이 들어 있다") }
    }

    @Test
    fun `이메일 순서는 HashSet 순서이고 프로젝트 이름은 파라미터 이름의 해시 순서로 훑는다 (v1_6 mailList)`() {
        val owner = newUser()
        val p1 = project(owner, "mq1"); val p2 = project(owner, "mq2")
        val m1 = List(4) { newUser().also { u -> join(u, p1, RoleType.MEMBER) } }
        val m2 = List(4) { newUser().also { u -> join(u, p2, RoleType.MEMBER) } }
        val body = mailList(admin(), "application/json", "zeta" to "${owner.loginId}/mq1", "alpha" to "${owner.loginId}/mq2").andExpect(status().isOk).andReturn().response.contentAsString
        // 기대 순서: 파라미터 이름의 해시 순서(HashMap)로 프로젝트를 훑고, 구성원 이메일을 기본 HashSet에 하나씩 넣은 순서
        val params = HashMap<String, String>().also { it["zeta"] = "${owner.loginId}/mq1"; it["alpha"] = "${owner.loginId}/mq2" }
        val byProject = mapOf("${owner.loginId}/mq1" to m1.map { it.email!! }, "${owner.loginId}/mq2" to m2.map { it.email!! })
        val expected = HashSet<String>().also { set -> params.values.forEach { name -> byProject.getValue(name).forEach { e -> set.add(e) } } }.toList()
        val actual = Regex("\"([^\"]+@[^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(expected, actual, body)
    }

    @Test
    fun `all이 true가 아니면 빈 목록이다`() {
        mailList(admin(), "application/json", "all" to "false").andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `프로젝트 이름 목록이면 구성원의 이메일을 중복 없이 모은다`() {
        val owner = newUser(); val member = newUser(); val shared = newUser(); val manager = newUser()
        val p1 = project(owner, "mp1"); val p2 = project(owner, "mp2")
        join(member, p1, RoleType.MEMBER); join(shared, p1, RoleType.MEMBER); join(shared, p2, RoleType.MANAGER)
        join(manager, p1, RoleType.SITEMANAGER)
        val body = mailList(admin(), "application/json", "0" to "${owner.loginId}/mp1", "1" to "${owner.loginId}/mp2").andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains(member.email!!)); assertTrue(body.contains(shared.email!!))
        assertEquals(1, Regex(Regex.escape(shared.email!!)).findAll(body).count())
        assertTrue(!body.contains(manager.email!!), "사이트 관리자 역할 행은 뺀다")
    }

    @Test
    fun `폼이 비어 있으면 빈 목록이다`() {
        mailList(admin()).andExpect(status().isOk).andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `Accept에 json이 없으면 406이고 사이트 관리자가 아니면 403이다`() {
        mailList(admin(), "text/plain", "all" to "true").andExpect(status().isNotAcceptable)
        mailList(login(newUser()), "application/json", "all" to "true").andExpect(status().isForbidden)
    }

    @Test
    fun `없는 프로젝트나 모양이 다른 이름이면 500이 아니라 400이다 (R-54-4)`() {
        mailList(admin(), "application/json", "0" to "nobody/nothing").andExpect(status().isBadRequest)
        mailList(admin(), "application/json", "0" to "noslash").andExpect(status().isBadRequest)
    }
}
