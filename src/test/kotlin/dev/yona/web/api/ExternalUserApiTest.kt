package dev.yona.web.api

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Assignee
import dev.yona.domain.issue.AssigneeRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
import dev.yona.domain.misc.Attachment
import dev.yona.domain.misc.AttachmentRepository
import dev.yona.domain.project.Project
import dev.yona.domain.project.ProjectMenuSetting
import dev.yona.domain.project.ProjectMenuSettingRepository
import dev.yona.domain.project.ProjectRepository
import dev.yona.domain.project.ProjectUser
import dev.yona.domain.project.ProjectUserRepository
import dev.yona.domain.user.RoleRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import dev.yona.domain.user.SiteAdmin
import dev.yona.domain.user.SiteAdminRepository
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.RecentProject
import dev.yona.domain.project.RecentProjectRepository
import dev.yona.domain.misc.FavoriteProjectRepository
import dev.yona.domain.misc.FavoriteOrganizationRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 외부용 사용자 API를 검증한다: 사용자 만들기, 토큰 받기, 내 이슈, 통계, 관리자 사용자 목록과 상태 바꾸기.
 *
 * 원본: `app/controllers/api/UserApi.java`.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalUserApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var siteAdmins: SiteAdminRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(state: UserState = UserState.ACTIVE, token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "eu$n", name = "eu$n", email = "eu$n@example.com", state = state, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt), token = token))
    }

    private fun admin(): User = newUser(token = "admin-token-${++sequence}").also { siteAdmins.save(SiteAdmin(admin = it)) }

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 사용자 만들기 ---

    @Test
    fun `사이트 관리자는 사용자 여러 명을 만든다`() {
        val a = admin()
        json(post("/-_-api/v1/users"), a.token, """{"users":[{"loginId":"newbie","name":"새내기","email":"newbie@example.com"},{"loginId":"newbie2","name":"둘째","email":"newbie2@example.com"}]}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].status").value(201)).andExpect(jsonPath("$[0].reason").value("Created"))
            .andExpect(jsonPath("$[0].user.loginId").value("newbie")).andExpect(jsonPath("$[0].user.email").value("newbie@example.com")).andExpect(jsonPath("$[0].user.id").isNumber)
        val created = users.findByLoginId("newbie")!!
        assertEquals(UserState.ACTIVE, created.state); assertTrue(!created.password.isNullOrEmpty())
    }

    @Test
    fun `이미 있는 이메일은 409로 알린다`() {
        val a = admin(); val existing = newUser()
        json(post("/-_-api/v1/users"), a.token, """{"users":[{"loginId":"dup","name":"중복","email":"${existing.email}"}]}""")
            .andExpect(status().isCreated).andExpect(jsonPath("$[0].status").value(409)).andExpect(jsonPath("$[0].reason").value("Conflict")).andExpect(jsonPath("$[0].message").value("Already exists!"))
            .andExpect(jsonPath("$[0].user.loginId").value("dup"))
        assertNull(users.findByLoginId("dup"))
    }

    @Test
    fun `사용자 만들기의 오류`() {
        val a = admin()
        json(post("/-_-api/v1/users"), newUser().also { it.token = "plain"; users.save(it) }.token, """{"users":[]}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("User creation with api is allowed by Site admin only."))
        mockMvc.perform(post("/-_-api/v1/users").header("Authorization", "token ${a.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Expecting Json data"))
        json(post("/-_-api/v1/users"), a.token, """{"nope":1}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No users key exists or value must be array!"))
    }

    // --- 토큰 ---

    @Test
    fun `아이디나 이메일과 비밀번호로 토큰을 받는다`() {
        val u = newUser()
        val first = json(post("/-_-api/v1/users/token"), null, """{"id":"${u.loginId}","password":"1234"}""").andExpect(status().isOk).andExpect(jsonPath("$.access_token").isString)
            .andReturn().response.contentAsString
        entityManager.flush(); entityManager.clear()
        val saved = users.findById(u.id!!).get().token
        assertTrue(first.contains(saved!!))
        json(post("/-_-api/v1/users/token"), null, """{"id":"${u.email}","password":"1234"}""").andExpect(status().isOk)
    }

    @Test
    fun `토큰 받기의 오류`() {
        val u = newUser(); val locked = newUser(UserState.LOCKED)
        mockMvc.perform(post("/-_-api/v1/users/token").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Empty request data"))
        json(post("/-_-api/v1/users/token"), null, """{"id":"nobody","password":"1234"}""").andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("No valid user by id"))
        json(post("/-_-api/v1/users/token"), null, """{"id":"${locked.loginId}","password":"1234"}""").andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("No valid user by id"))
        json(post("/-_-api/v1/users/token"), null, """{"id":"${u.loginId}","password":"bad"}""").andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("No user by id and password"))
    }

    // --- 내 이슈 ---

    @Test
    fun `토큰으로 내 이슈를 필터별로 본다`() {
        val me = newUser(token = "metk-1"); val other = newUser()
        val project = projects.save(Project(owner = "euo", name = "eup", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 2))
        issues.save(Issue(project = project, number = 1, title = "내게 할당", body = "b", state = State.OPEN, authorId = other.id, authorLoginId = other.loginId, authorName = other.name, assignee = assignees.save(Assignee(user = me, project = project)), numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_001_000L)))
        issues.save(Issue(project = project, number = 2, title = "내가 작성", body = "b", state = State.OPEN, authorId = me.id, authorLoginId = me.loginId, authorName = me.name, numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_002_000L)))
        json(get("/-_-api/v1/user/issues"), me.token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(1))
            .andExpect(jsonPath("$.result[0].title").value("내게 할당")).andExpect(jsonPath("$.result[0].number").value(1)).andExpect(jsonPath("$.result[0].state").value("OPEN"))
            .andExpect(jsonPath("$.result[0].author.loginId").value(other.loginId)).andExpect(jsonPath("$.result[0].assignee.loginId").value(me.loginId))
            .andExpect(jsonPath("$.result[0].project.name").value("eup")).andExpect(jsonPath("$.result[0].owner").value("euo"))
            .andExpect(jsonPath("$.result[0].refUrl").value("http://localhost/euo/eup/issue/1")).andExpect(jsonPath("$.result[0].createdDate").isString)
        json(get("/-_-api/v1/user/issues?filter=created"), me.token, null).andExpect(jsonPath("$.result[0].title").value("내가 작성")).andExpect(jsonPath("$.result[0].assignee").isMap)
        json(get("/-_-api/v1/user/issues?filter=all"), me.token, null).andExpect(jsonPath("$.result.length()").value(2)).andExpect(jsonPath("$.result[0].title").value("내가 작성"))
    }

    @Test
    fun `내 이슈는 토큰이 있어야 하고 쪽 나누기를 따른다`() {
        val me = newUser(token = "metk-2")
        json(get("/-_-api/v1/user/issues"), null, null).andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("unauthorized request"))
        json(get("/-_-api/v1/user/issues?filter=bogus"), me.token, null).andExpect(status().isBadRequest)
        json(get("/-_-api/v1/user/issues?page=2&pageNum=1"), me.token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(0))
    }

    // --- 통계 ---

    @Test
    fun `사용자의 활동 통계`() {
        val me = newUser(); val s = newUser()
        val project = projects.save(Project(owner = "eso", name = "esp", vcs = "GIT", projectScope = ProjectScope.PUBLIC, lastIssueNumber = 1))
        issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = me.id, authorLoginId = me.loginId, authorName = me.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()))
        val session = MockHttpSession().also { mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", s.loginId!!).param("password", "1234")) }
        mockMvc.perform(get("/-_-api/v1/users/${me.loginId}/statistics").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.issue").value(1)).andExpect(jsonPath("$.posting").value(0)).andExpect(jsonPath("$.assignedIssue").value(0))
            .andExpect(jsonPath("$.issueComment").value(0)).andExpect(jsonPath("$.postingComment").value(0)).andExpect(jsonPath("$.issueVoter").value(0)).andExpect(jsonPath("$.issueCommentVoter").value(0))
        mockMvc.perform(get("/-_-api/v1/users/nobody/statistics").session(session)).andExpect(status().isOk).andExpect(jsonPath("$.issue").value(0))
        mockMvc.perform(get("/-_-api/v1/users/${me.loginId}/statistics")).andExpect(status().is3xxRedirection)
    }

    // --- 관리자 ---

    @Test
    fun `관리자는 활성 사용자 목록을 본다`() {
        val a = admin(); val locked = newUser(UserState.LOCKED); val active = newUser()
        val body = json(get("/-_-api/v1/admin/users"), a.token, null).andExpect(status().isOk).andReturn().response.contentAsString
        assertTrue(body.contains("\"login_id\":\"${active.loginId}\"") && body.contains("\"is_guest\"") && !body.contains(locked.loginId!!), body)
        json(get("/-_-api/v1/admin/users"), newUser(token = "x-${++sequence}").token, null).andExpect(status().isForbidden)
    }

    @Test
    fun `관리자는 사용자 상태를 바꾼다`() {
        val a = admin(); val u = newUser()
        json(patch("/-_-api/v1/admin/users/${u.loginId}"), a.token, """{"state":"locked"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(u.id)).andExpect(jsonPath("$.login_id").value(u.loginId)).andExpect(jsonPath("$.state").value("LOCKED"))
        entityManager.flush(); entityManager.clear()
        assertEquals(UserState.LOCKED, users.findById(u.id!!).get().state)
    }

    @Test
    fun `상태 바꾸기의 오류`() {
        val a = admin(); val u = newUser()
        json(patch("/-_-api/v1/admin/users/nobody"), a.token, """{"state":"locked"}""").andExpect(status().isUnauthorized)
        mockMvc.perform(patch("/-_-api/v1/admin/users/${u.loginId}").header("Authorization", "token ${a.token}").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/admin/users/${u.loginId}"), a.token, """{"state":"nonsense"}""").andExpect(status().isBadRequest)
        json(patch("/-_-api/v1/admin/users/${u.loginId}"), a.token, """{"state":"SITE_ADMIN"}""").andExpect(status().isForbidden)
        json(patch("/-_-api/v1/admin/users/${u.loginId}"), newUser(token = "y-${++sequence}").token, """{"state":"locked"}""").andExpect(status().isForbidden)
    }
}
