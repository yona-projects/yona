package dev.yona.web.site

import dev.yona.support.selectFirst
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
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
import jakarta.persistence.EntityManager
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 사이트 관리의 사용자 관리(`/sites/userList`와 잠금, 게스트 전환, 관리자 권한, 삭제, 비밀번호 초기화)를 검증한다.
 *
 * 원본: `app/controllers/SiteApp.java:131-136, 163-205, 238-275`, `app/utils/SiteManagerAuthAction.java`,
 * `app/views/site/userList.scala.html`, `app/views/site/siteMngLayout.scala.html`, `app/models/User.java:399-433, 694-720`,
 * `app/controllers/UserApp.java:1338-1362`.
 */
@AutoConfigureMockMvc
@Transactional
class SiteUserManagementTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private var clock = 1_700_000_000_000L

    private fun newUser(name: String? = null, state: UserState = UserState.ACTIVE, guest: Boolean = false, email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(
            loginId = "su$n", name = name ?: "su$n", email = email ?: "su$n@example.com", state = state, isGuest = guest, createdDate = Date(++clock),
            passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
        ))
    }

    private fun admin(): User = newUser().also { siteAdmins.save(SiteAdmin(admin = it)) }

    private fun login(user: User, password: String = "1234"): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", password))
    }

    private fun userList(session: MockHttpSession, vararg params: Pair<String, String>): Document =
        Jsoup.parse(mockMvc.perform(get("/sites/userList").session(session).apply { params.forEach { (k, v) -> param(k, v) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun names(doc: Document) = doc.select("ul.user-list-wrap li.listitem a.user-id").map { it.text().trim() }

    private fun project(owner: User, name: String): Project =
        projects.save(Project(owner = owner.loginId, name = name, vcs = "GIT", projectScope = ProjectScope.PUBLIC, createdDate = Date()))

    private fun join(user: User, project: Project, role: RoleType) = projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))

    // --- 접근 ---

    @Test
    fun `사이트 관리자가 아니면 403이다`() {
        mockMvc.perform(get("/sites/userList")).andExpect(status().isForbidden)
        mockMvc.perform(get("/sites/userList").session(login(newUser()))).andExpect(status().isForbidden)
    }

    @Test
    fun `사이트 관리자는 사용자 목록을 본다`() {
        val doc = userList(login(admin()))
        assertEquals("Site management", doc.selectFirst(".site-breadcrumb-inner h3")!!.text())
        assertEquals(8, doc.select("ul.site-setting-nav li").size)
        assertEquals("/sites/userList", doc.selectFirst("ul.site-setting-nav li.active a")!!.attr("href"))
    }

    // --- 목록 ---

    @Test
    fun `기본은 활성 사용자를 가입 최근 순으로 보이고 기본 관리자와 익명 사용자는 뺀다`() {
        val me = admin(); val first = newUser(); val second = newUser(); newUser(state = UserState.LOCKED)
        val shown = names(userList(login(me)))
        assertTrue(shown.indexOf("@${second.loginId}") < shown.indexOf("@${first.loginId}"))
        assertFalse(shown.contains("@admin")); assertFalse(shown.contains("@anonymous"))
        assertFalse(shown.any { it.contains("LOCKED") })
    }

    @Test
    fun `state로 탭을 고른다`() {
        val me = admin(); val locked = newUser(state = UserState.LOCKED); val guest = newUser(guest = true); val deleted = newUser(state = UserState.DELETED)
        val session = login(me)
        assertTrue(names(userList(session, "state" to "LOCKED")).contains("@${locked.loginId}"))
        assertTrue(names(userList(session, "state" to "GUEST")).contains("@${guest.loginId}"))
        assertTrue(names(userList(session, "state" to "DELETED")).contains("@${deleted.loginId}"))
        assertTrue(names(userList(session, "state" to "SITE_ADMIN")).contains("@${me.loginId}"))
        assertEquals("LOCKED", userList(session, "state" to "LOCKED").selectFirst("form.form-search input[name=state]")!!.attr("value"))
        assertEquals(listOf("ACTIVE", "LOCKED", "DELETED", "GUEST", "SITE_ADMIN").size, userList(session).select(".span10 > ul.nav-tabs > li").size)
    }

    @Test
    fun `삭제된 탭에는 조작 버튼 대신 상태 변경 시각이 있다`() {
        val me = admin(); newUser(state = UserState.DELETED)
        val doc = userList(login(me), "state" to "DELETED")
        assertTrue(doc.select("ul.user-list-wrap .action-buttons").isEmpty())
    }

    @Test
    fun `검색어는 로그인 ID, 이름, 영문 이름, 이메일에서 대소문자 없이 찾는다`() {
        val me = admin(); val byName = newUser("Findme 이름"); val byEmail = newUser(email = "needle-mail@example.com"); newUser("무관")
        val session = login(me)
        assertEquals(listOf("@${byName.loginId}"), names(userList(session, "query" to "findme")))
        assertEquals(listOf("@${byEmail.loginId}"), names(userList(session, "query" to "NEEDLE-MAIL")))
    }

    @Test
    fun `사용자 줄에는 다섯 개의 조작 버튼이 있다`() {
        val me = admin(); val target = newUser()
        val row = userList(login(me), "query" to target.loginId!!).selectFirst("li.listitem")!!
        val buttons = row.select(".action-buttons > a, .action-buttons > button")
        assertEquals(5, buttons.size)
        assertEquals("/sites/toggleGuestMode?loginId=${target.loginId}&state=ACTIVE&query=${target.loginId}", buttons[0].attr("data-request-uri").replace("&amp;", "&"))
        assertEquals("post", buttons[0].attr("data-request-method"))
        assertEquals("/sites/user/delete${target.id}", buttons.last()!!.attr("data-href"))
    }

    @Test
    fun `로그인 ID가 anonymous인 실제 사용자도 목록에 나온다 (v1_6은 로그인 ID가 빈 NullUser 행만 뺀다)`() {
        val me = admin()
        val salt = LegacyPasswordHasher.generateSalt()
        users.save(User(loginId = "anonymous", name = "익명이름", email = "anon-real@example.com", state = UserState.ACTIVE, createdDate = Date(++clock), passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        assertTrue(names(userList(login(me), "query" to "anon-real")).contains("@anonymous"))
    }

    @Test
    fun `쪽은 30명씩이다`() {
        val me = admin(); repeat(35) { newUser() }
        val total = users.findAll().count { it.state == UserState.ACTIVE && it.id != 1L && !it.loginId.isNullOrEmpty() }
        assertEquals(30, names(userList(login(me))).size)
        assertEquals(total - 30, names(userList(login(me), "pageNum" to "2")).size)
    }

    // --- 잠금과 게스트 전환 ---

    @Test
    fun `잠금 전환은 활성을 잠그고 그 밖의 상태는 활성으로 되돌린다`() {
        val me = admin(); val target = newUser(); val session = login(me)
        mockMvc.perform(post("/sites/toggleAccountLock").session(session).param("loginId", target.loginId!!).param("state", "ACTIVE")).andExpect(status().isOk)
        assertEquals(UserState.LOCKED, users.findById(target.id!!).get().state)
        mockMvc.perform(post("/sites/toggleAccountLock").session(session).param("loginId", target.loginId!!).param("state", "LOCKED")).andExpect(status().isOk)
        assertEquals(UserState.ACTIVE, users.findById(target.id!!).get().state)
        val deleted = newUser(state = UserState.DELETED)
        mockMvc.perform(post("/sites/toggleAccountLock").session(session).param("loginId", deleted.loginId!!)).andExpect(status().isOk)
        assertEquals(UserState.ACTIVE, users.findById(deleted.id!!).get().state)
    }

    @Test
    fun `없는 사용자의 잠금 전환은 안내와 함께 목록으로 보낸다`() {
        mockMvc.perform(post("/sites/toggleAccountLock").session(login(admin())).param("loginId", "nobody-here")).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/userList?pageNum=0"))
    }

    @Test
    fun `잠금 전환은 응답으로 목록 화면을 돌려준다`() {
        val me = admin(); val target = newUser()
        val html = mockMvc.perform(post("/sites/toggleAccountLock").session(login(me)).param("loginId", target.loginId!!).param("state", "ACTIVE").param("query", ""))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(Jsoup.parse(html).select("ul.user-list-wrap").isNotEmpty())
    }

    @Test
    fun `게스트 전환은 게스트 여부만 뒤집는다`() {
        val me = admin(); val target = newUser(); val session = login(me)
        mockMvc.perform(post("/sites/toggleGuestMode").session(session).param("loginId", target.loginId!!)).andExpect(status().isOk)
        assertEquals(true, users.findById(target.id!!).get().isGuest)
        assertEquals(UserState.ACTIVE, users.findById(target.id!!).get().state)
        mockMvc.perform(post("/sites/toggleGuestMode").session(session).param("loginId", target.loginId!!)).andExpect(status().isOk)
        assertEquals(false, users.findById(target.id!!).get().isGuest)
    }

    // --- 관리자 권한 ---

    @Test
    fun `관리자 권한을 부여하고 해제한다`() {
        val me = admin(); val target = newUser(); val session = login(me)
        mockMvc.perform(post("/sites/toggleSiteAdminRole/${target.loginId}").session(session)).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/userList"))
        assertTrue(siteAdmins.existsByAdminId(target.id!!))
        mockMvc.perform(post("/sites/toggleSiteAdminRole/${target.loginId}").session(session)).andExpect(status().isSeeOther)
        assertFalse(siteAdmins.existsByAdminId(target.id!!))
    }

    // --- 삭제 ---

    @Test
    fun `사용자를 삭제하면 상태와 개인 정보가 지워지고 프로젝트와 담당에서 빠진다`() {
        val me = admin(); val target = newUser("지울 사람"); val owner = newUser()
        val p = project(owner, "keep"); join(owner, p, RoleType.MANAGER); join(target, p, RoleType.MEMBER)
        val assignee = assignees.save(Assignee(user = target, project = p))
        val issue = issues.save(Issue(project = p, number = 1, title = "t", state = State.OPEN, authorId = owner.id, assignee = assignee, weight = 0, numOfComments = 0, createdDate = Date(), updatedDate = Date()))
        mockMvc.perform(delete("/sites/user/delete${target.id}").session(login(me))).andExpect(status().isSeeOther).andExpect(redirectedUrl("/sites/userList"))
        entityManager.flush(); entityManager.clear()
        val gone = users.findById(target.id!!).get()
        assertEquals(UserState.DELETED, gone.state)
        assertEquals("[DELETED]지울 사람", gone.name)
        assertEquals("deleted-${target.loginId}@noreply.yona.io", gone.email)
        assertEquals("", gone.password); assertEquals("", gone.passwordSalt)
        assertNotNull(gone.lastStateModifiedDate)
        assertNull(projectUsers.findFirstByUserIdAndProjectId(target.id!!, p.id!!))
        assertNull(issues.findById(issue.id!!).get().assignee)
        assertFalse(assignees.existsById(assignee.id!!))
    }

    @Test
    fun `프로젝트의 유일한 매니저는 삭제할 수 없다`() {
        val me = admin(); val target = newUser(); val p = project(target, "solo"); join(target, p, RoleType.MANAGER)
        mockMvc.perform(delete("/sites/user/delete${target.id}").session(login(me))).andExpect(status().isForbidden)
        assertEquals(UserState.ACTIVE, users.findById(target.id!!).get().state)
    }

    @Test
    fun `다른 매니저가 있으면 매니저도 삭제할 수 있다`() {
        val me = admin(); val target = newUser(); val other = newUser(); val p = project(target, "duo")
        join(target, p, RoleType.MANAGER); join(other, p, RoleType.MANAGER)
        mockMvc.perform(delete("/sites/user/delete${target.id}").session(login(me))).andExpect(status().isSeeOther)
        assertEquals(UserState.DELETED, users.findById(target.id!!).get().state)
    }

    // --- 비밀번호 초기화 ---

    @Test
    fun `사이트 관리자가 사용자의 비밀번호를 새로 만든다`() {
        val me = admin(); val target = newUser("초기화 대상")
        val body = mockMvc.perform(post("/${target.loginId}").session(login(me)).param("action", "resetPassword"))
            .andExpect(status().isOk).andExpect(jsonPath("$.isSuccess").value(true)).andExpect(jsonPath("$.loginId").value(target.loginId!!))
            .andExpect(jsonPath("$.name").value("초기화 대상")).andReturn().response.contentAsString
        val password = Regex("\"newPassword\"\\s*:\\s*\"([^\"]+)\"").find(body)!!.groupValues[1]
        assertEquals(6, password.length)
        mockMvc.perform(post("/users/login").param("loginIdOrEmail", target.loginId!!).param("password", password)).andExpect(status().is3xxRedirection)
    }

    @Test
    fun `비밀번호 초기화는 action이 resetPassword가 아니면 400이다`() {
        val me = admin(); val target = newUser()
        mockMvc.perform(post("/${target.loginId}").session(login(me)).param("action", "other")).andExpect(status().isBadRequest).andExpect(jsonPath("$.reason").value("BAD_REQUEST"))
    }

    @Test
    fun `비밀번호 초기화는 사이트 관리자가 아니거나 없는 사용자이면 403이다`() {
        val target = newUser()
        mockMvc.perform(post("/${target.loginId}").session(login(newUser())).param("action", "resetPassword")).andExpect(status().isForbidden).andExpect(jsonPath("$.reason").value("FORBIDDEN"))
        mockMvc.perform(post("/nobody-here").session(login(admin())).param("action", "resetPassword")).andExpect(status().isForbidden)
    }
}
