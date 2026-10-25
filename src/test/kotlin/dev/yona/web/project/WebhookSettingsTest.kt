package dev.yona.web.project

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.enumeration.WebhookType
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
import dev.yona.domain.user.User
import dev.yona.domain.user.UserRepository
import dev.yona.domain.webhook.WebhookRepository
import dev.yona.domain.webhook.WebhookService
import dev.yona.security.LegacyPasswordHasher
import dev.yona.support.AbstractIntegrationTest
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 웹훅 설정(목록, 등록, 삭제)을 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java`(`webhooks`, `newWebhook`, `deleteWebhook`), `app/views/project/webhooks.scala.html`, `partial_webhooks_list.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class WebhookSettingsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var webhooks: WebhookRepository
    @Autowired private lateinit var service: WebhookService

    private var sequence = 0

    private fun user(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "wh$n", name = "이름wh$n", email = "wh$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234")) }

    private fun project(): Project {
        val n = ++sequence
        val p = projects.save(Project(owner = "who$n", name = "whp$n", vcs = "GIT", projectScope = ProjectScope.PUBLIC))
        menuSettings.save(ProjectMenuSetting(project = p, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return p
    }

    private fun manager(p: Project): MockHttpSession {
        val u = user()
        projectUsers.save(ProjectUser(user = u, project = p, role = roles.findById(RoleType.MANAGER.id).get()))
        return login(u)
    }

    @Test
    fun `웹훅이 없으면 안내가 나오고 등록 폼이 보인다`() {
        val p = project(); val session = manager(p)
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/webhooks").session(session)).andExpect(status().isOk).andReturn().response.contentAsString)
        assertNotNull(doc.selectFirst("#webhooksList .error-wrap"))
        assertEquals("/${p.owner}/${p.name}/webhooks", doc.selectFirst("form#formNewWebhook")!!.attr("action"))
        assertEquals(listOf("SIMPLE", "DETAIL_SLACK", "DETAIL_HANGOUT_CHAT", "JSON"), doc.select("input[name=webhookType]").map { it.attr("value") })
        assertEquals("SIMPLE", doc.selectFirst("input[name=webhookType][checked]")!!.attr("value"))
    }

    @Test
    fun `수정 권한이 없으면 볼 수 없다`() {
        val p = project()
        mockMvc.perform(get("/${p.owner}/${p.name}/webhooks").session(login(user()))).andExpect(status().isForbidden)
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(login(user())).param("payloadUrl", "http://x")).andExpect(status().isForbidden)
    }

    @Test
    fun `등록하면 목록으로 이동하고 기본값이 채워진다`() {
        val p = project(); val session = manager(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(session).param("payloadUrl", "http://example.com/hook")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/${p.owner}/${p.name}/webhooks"))
        val hook = webhooks.findByProjectId(p.id!!).single()
        assertEquals("http://example.com/hook", hook.payloadUrl); assertEquals("", hook.secret); assertEquals(false, hook.gitPush); assertEquals(WebhookType.SIMPLE, hook.webhookType); assertNotNull(hook.createdAt)
        val doc = Jsoup.parse(mockMvc.perform(get("/${p.owner}/${p.name}/webhooks").session(session)).andReturn().response.contentAsString)
        val row = doc.selectFirst(".list-item")!!
        assertEquals("http://example.com/hook", row.selectFirst("h6.truncate")!!.text())
        assertEquals("NONE", row.select("h6")[1].text()); assertEquals("SIMPLE", row.select("h6")[2].text())
        assertEquals("/${p.owner}/${p.name}/webhooks/${hook.id}", row.selectFirst("button[data-request-method=delete]")!!.attr("data-request-uri"))
    }

    @Test
    fun `토큰, 형식, 푸시 포함 값을 저장한다`() {
        val p = project()
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(manager(p)).param("payloadUrl", "http://h").param("secret", "abc").param("webhookType", "JSON").param("gitPush", "on")).andExpect(status().isSeeOther)
        val hook = webhooks.findByProjectId(p.id!!).single()
        assertEquals("abc", hook.secret); assertEquals(WebhookType.JSON, hook.webhookType); assertEquals(true, hook.gitPush)
    }

    @Test
    fun `검증 오류는 오류 JSON을 문구로 담은 오류 화면으로 400이다`() {
        val p = project(); val session = manager(p)
        val body = mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(session).param("payloadUrl", "")).andExpect(status().isBadRequest)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("text/html")).andReturn().response.contentAsString
        assertTrue(body.contains("<html") && body.contains("payloadUrl") && body.contains("This field is required"), body)
    }

    @Test
    fun `보낼 주소가 없거나 너무 길면 400이고 만들지 않는다`() {
        val p = project(); val session = manager(p)
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(session).param("payloadUrl", "")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(session).param("payloadUrl", "x".repeat(2001))).andExpect(status().isBadRequest)
        mockMvc.perform(post("/${p.owner}/${p.name}/webhooks").session(session).param("payloadUrl", "http://h").param("secret", "s".repeat(251))).andExpect(status().isBadRequest)
        assertTrue(webhooks.findByProjectId(p.id!!).isEmpty())
    }

    @Test
    fun `웹훅을 지우고 없는 번호나 다른 프로젝트의 웹훅은 404이다`() {
        val p = project(); val other = project(); val session = manager(p)
        val mine = service.create(p, "http://a", null, false, null); val theirs = service.create(other, "http://b", null, false, null)
        mockMvc.perform(delete("/${p.owner}/${p.name}/webhooks/${theirs.id}").session(session)).andExpect(status().isNotFound)
        mockMvc.perform(delete("/${p.owner}/${p.name}/webhooks/${mine.id}").session(session)).andExpect(status().isOk)
        assertNull(webhooks.findById(mine.id!!).orElse(null)); assertNotNull(webhooks.findById(theirs.id!!).orElse(null))
        mockMvc.perform(delete("/${p.owner}/${p.name}/webhooks/99999").session(session)).andExpect(status().isNotFound)
    }
}
