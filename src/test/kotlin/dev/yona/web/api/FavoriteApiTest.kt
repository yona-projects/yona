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
 * 프로젝트와 조직 즐겨찾기 API(`/user/favoriteProjects`, `/user/favoriteOrganizations`)를 검증한다.
 *
 * 원본: `app/controllers/api/UserApi.java:60-90, 190-218`, `app/models/User.java:951-1040`.
 */
@AutoConfigureMockMvc
@Transactional
class FavoriteApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var favoriteProjects: FavoriteProjectRepository
    @Autowired private lateinit var favoriteOrganizations: FavoriteOrganizationRepository
    @Autowired private lateinit var recentProjects: RecentProjectRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "fa$n", name = "fa$n", email = "fa$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun project(): Project = projects.save(Project(owner = "fao${++sequence}", name = "fap$sequence", vcs = "GIT", projectScope = ProjectScope.PUBLIC))

    private fun organization(): Organization = organizations.save(Organization(name = "faorg${++sequence}", descr = "d", created = Date()))

    @Test
    fun `프로젝트 즐겨찾기를 켜고 끈다`() {
        val user = newUser(); val p = project()
        val session = login(user)
        mockMvc.perform(post("/user/favoriteProjects/${p.id}").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.projectId").value(p.id.toString())).andExpect(jsonPath("$.favored").value(true))
        val saved = favoriteProjects.findAll().single()
        assertEquals(p.owner, saved.owner); assertEquals(p.name, saved.projectName)
        mockMvc.perform(post("/user/favoriteProjects/${p.id}").session(session)).andExpect(jsonPath("$.favored").value(false))
        assertEquals(0, favoriteProjects.count())
    }

    @Test
    fun `즐겨찾기를 끄면 최근 방문 기록도 지운다`() {
        val user = newUser(); val p = project()
        recentProjects.save(RecentProject(userId = user.id, owner = p.owner, project = p, projectName = p.name))
        val session = login(user)
        mockMvc.perform(post("/user/favoriteProjects/${p.id}").session(session))
        assertEquals(1, recentProjects.count())
        mockMvc.perform(post("/user/favoriteProjects/${p.id}").session(session))
        assertEquals(0, recentProjects.count())
    }

    @Test
    fun `프로젝트 즐겨찾기 목록`() {
        val user = newUser(); val a = project(); val b = project()
        val session = login(user)
        mockMvc.perform(post("/user/favoriteProjects/${a.id}").session(session)); mockMvc.perform(post("/user/favoriteProjects/${b.id}").session(session))
        mockMvc.perform(get("/user/favoriteProjects").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.projectIds[0]").value(a.id)).andExpect(jsonPath("$.projectIds[1]").value(b.id))
            .andExpect(jsonPath("$.projects[1].projectName").value(b.name)).andExpect(jsonPath("$.projects[1].owner").value(b.owner)).andExpect(jsonPath("$.projects[0].projectId").value(a.id))
    }

    @Test
    fun `조직 즐겨찾기를 켜고 끄고 목록을 본다`() {
        val user = newUser(); val org = organization()
        val session = login(user)
        mockMvc.perform(post("/user/favoriteOrganizations/${org.id}").session(session)).andExpect(status().isOk)
            .andExpect(jsonPath("$.organizationId").value(org.id.toString())).andExpect(jsonPath("$.favored").value(true))
        assertEquals(org.name, favoriteOrganizations.findAll().single().organizationName)
        mockMvc.perform(get("/user/favoriteOrganizations").session(session)).andExpect(jsonPath("$.organizationIds[0]").value(org.id)).andExpect(jsonPath("$.organizations[0].organizationName").value(org.name))
        mockMvc.perform(post("/user/favoriteOrganizations/${org.id}").session(session)).andExpect(jsonPath("$.favored").value(false))
        assertEquals(0, favoriteOrganizations.count())
    }

    @Test
    fun `익명이거나 잘못된 번호는 거절한다`() {
        val p = project()
        mockMvc.perform(post("/user/favoriteProjects/${p.id}")).andExpect(status().isForbidden)
        // v1.6는 익명의 `currentUser()`가 빈 즐겨찾기 목록을 가진 NullUser라 목록 조회는 200이다(공식 v1.16.0 실행으로 확인). 바꾸기는 오류(500)였다
        mockMvc.perform(get("/user/favoriteProjects")).andExpect(status().isOk).andExpect(content().json("""{"projectIds":[],"projects":[]}""", true))
        mockMvc.perform(get("/user/favoriteOrganizations")).andExpect(status().isOk).andExpect(content().json("""{"organizationIds":[],"organizations":[]}""", true))
        // 이슈 목록은 익명이면 NullPointerException으로 500이다(v1.6 익명의 favoriteIssues는 null)
        mockMvc.perform(get("/user/favoriteIssues")).andExpect(status().isInternalServerError)
        val session = login(newUser())
        mockMvc.perform(post("/user/favoriteProjects/abc").session(session)).andExpect(status().isBadRequest)
        mockMvc.perform(post("/user/favoriteProjects/999999").session(session)).andExpect(status().isBadRequest)
        mockMvc.perform(post("/user/favoriteOrganizations/999999").session(session)).andExpect(status().isBadRequest)
    }
}
