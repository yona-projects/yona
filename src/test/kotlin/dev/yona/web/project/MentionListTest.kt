package dev.yona.web.project

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.misc.Watch
import dev.yona.domain.misc.WatchRepository
import dev.yona.domain.project.Organization
import dev.yona.domain.project.OrganizationRepository
import dev.yona.domain.project.OrganizationUser
import dev.yona.domain.project.OrganizationUserRepository
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
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals

/**
 * 편집기의 `@` 멘션 후보(`GET /:user/:project/mentionList`)를 검증한다.
 *
 * 원본: `app/controllers/ProjectApp.java:383-497, 832-930`.
 */
@AutoConfigureMockMvc
@Transactional
class MentionListTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var organizations: OrganizationRepository
    @Autowired private lateinit var organizationUsers: OrganizationUserRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(name: String? = null, state: UserState = UserState.ACTIVE, email: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(
            User(
                loginId = "mn$n", name = name ?: "mn$n", email = email ?: "mn$n@example.com", state = state,
                passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)
            )
        )
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, organization: Organization? = null): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "mo$n", name = "mp$n", vcs = "GIT", projectScope = scope, organization = organization))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun newIssue(project: Project, author: User, number: Long, title: String = "title $number") = issues.save(
        Issue(project = project, number = number, title = title, state = State.OPEN, authorId = author.id, authorLoginId = author.loginId,
            authorName = author.name, createdDate = Date(1_000_000L * number), updatedDate = Date())
    )

    private fun comment(issue: Issue, author: User, at: Long) {
        entityManager.persist(
            IssueComment(contents = "c", createdDate = Date(at), authorId = author.id, authorLoginId = author.loginId, authorName = author.name,
                issue = issue, projectId = issue.project!!.id!!)
        )
    }

    private fun mentions(project: Project, session: MockHttpSession?, vararg params: Pair<String, String>) =
        mockMvc.perform(
            get("/${project.owner}/${project.name}/mentionList").apply { session?.let { session(it) } }
                .param("mentionType", "user").apply { params.forEach { (k, v) -> param(k, v) } }
        )

    private fun loginIds(project: Project, session: MockHttpSession?, vararg params: Pair<String, String>): List<String> {
        val body = mentions(project, session, *params).andExpect(status().isOk).andReturn().response.contentAsString
        return Regex("\"loginid\":\"([^\"]*)\"").findAll(body).map { it.groupValues[1] }.toList()
    }

    // --- 사용자 ---

    @Test
    fun `구성원을 보여 주고 나는 마지막에 두며 프로젝트 전체 항목을 끝에 붙인다`() {
        val project = newProject()
        val me = join(newUser("나"), project, RoleType.MANAGER)
        val other = join(newUser("너"), project)
        assertEquals(listOf(other.loginId, me.loginId, "${project.owner}/${project.name}"), loginIds(project, login(me)))
    }

    @Test
    fun `각 항목은 아이디, 검색어, 이름, 그림을 가진다`() {
        val project = newProject()
        val me = join(newUser("나"), project, RoleType.MANAGER)
        val other = join(newUser("너"), project)
        mentions(project, login(me))
            .andExpect(jsonPath("$.result[0].loginid").value(other.loginId))
            .andExpect(jsonPath("$.result[0].searchText").value("너너${other.loginId}"))
            .andExpect(jsonPath("$.result[0].name").value("너"))
            .andExpect(jsonPath("$.result[0].image").exists())
            .andExpect(jsonPath("$.result[2].username").value(project.name))
            .andExpect(jsonPath("$.result[2].name").value("@project all:"))
            .andExpect(jsonPath("$.result[2].searchText").value("${project.owner}/${project.name}/project/member/all"))
            .andExpect(jsonPath("$.result[2].image").value("/assets/images/project_default_logo.png"))
    }

    @Test
    fun `응답은 JSON이고 Vary Accept를 붙인다`() {
        val project = newProject()
        mentions(project, null).andExpect(status().isOk).andExpect(header().string("Vary", "Accept"))
            .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
    }

    @Test
    fun `조직 프로젝트는 조직 구성원과 조직 전체 항목을 더한다`() {
        val organization = organizations.save(Organization(name = "orgx${++sequence}", created = Date()))
        val project = newProject(organization = organization)
        val me = join(newUser("나"), project, RoleType.MANAGER)
        val orgMember = newUser("조직원")
        organizationUsers.save(OrganizationUser(user = orgMember, organization = organization, role = roles.findById(RoleType.ORG_MEMBER.id).get()))
        val ids = loginIds(project, login(me))
        // 프로젝트 구성원(나)을 빼고 다시 더하므로 나는 조직 구성원 뒤에 온다.
        assertEquals(listOf(orgMember.loginId, me.loginId, "${project.owner}/${project.name}", organization.name), ids)
        mentions(project, login(me)).andExpect(jsonPath("$.result[3].name").value("@group all: "))
            .andExpect(jsonPath("$.result[3].searchText").value("${organization.name}/group/org/member/all"))
    }

    @Test
    fun `후보가 열 명을 넘으면 프로젝트 전체 항목은 열 번째 자리에 들어간다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MANAGER)
        repeat(10) { join(newUser(), project) }
        val ids = loginIds(project, login(me))
        assertEquals(12, ids.size)
        assertEquals("${project.owner}/${project.name}", ids[9])
        assertEquals(me.loginId, ids.last())
    }

    @Test
    fun `이슈를 쓰거나 구독한 사람도 후보에 들어간다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MANAGER)
        val author = newUser("작성자")
        val watcher = newUser("구독자")
        newIssue(project, author, 1)
        watches.save(Watch(user = watcher, resourceType = ResourceType.PROJECT, resourceId = project.id.toString()))
        val ids = loginIds(project, login(me))
        assertEquals(setOf(author.loginId, watcher.loginId), ids.toSet() - setOf(me.loginId, "${project.owner}/${project.name}"))
        assertEquals(me.loginId, ids[ids.size - 2])
    }

    @Test
    fun `이슈의 후보는 최근에 댓글을 쓴 사람이 먼저이고 작성자가 그 뒤이다`() {
        val project = newProject()
        val me = join(newUser(), project, RoleType.MANAGER)
        val author = newUser()
        val b = newUser()
        val c = newUser()
        val issue = newIssue(project, author, 7)
        comment(issue, b, 1); comment(issue, c, 2); comment(issue, b, 3)
        entityManager.flush()
        val ids = loginIds(project, login(me), "number" to "7", "resourceType" to "issue_post")
        assertEquals(listOf(b.loginId, c.loginId, author.loginId, me.loginId, "${project.owner}/${project.name}"), ids)
    }

    @Test
    fun `검색어가 있으면 공개 프로젝트에서는 활성 사용자를 이름, 아이디, 이메일로 찾는다`() {
        val project = newProject()
        val me = join(newUser("나"), project, RoleType.MANAGER)
        val hit = newUser("찾는사람")
        newUser("찾는잠김", state = UserState.LOCKED)
        newUser("다른사람")
        val ids = loginIds(project, login(me), "query" to "찾는")
        assertEquals(listOf(hit.loginId, me.loginId, "${project.owner}/${project.name}"), ids)
        assertEquals(listOf(hit.loginId, me.loginId, "${project.owner}/${project.name}"), loginIds(project, login(me), "query" to hit.email!!.substringBefore("@")))
    }

    @Test
    fun `비공개 프로젝트는 검색어가 있어도 구성원 목록을 쓴다`() {
        val project = newProject(ProjectScope.PRIVATE)
        val me = join(newUser("나"), project, RoleType.MANAGER)
        val member = join(newUser("구성원"), project)
        newUser("바깥사람")
        assertEquals(listOf(member.loginId, me.loginId, "${project.owner}/${project.name}"), loginIds(project, login(me), "query" to "바깥"))
    }

    @Test
    fun `익명이 부르면 로그인한 사용자를 더하지 않는다`() {
        val project = newProject()
        val member = join(newUser(), project)
        assertEquals(listOf(member.loginId, "${project.owner}/${project.name}"), loginIds(project, null))
    }

    // --- 이슈 ---

    @Test
    fun `이슈 후보는 최근 이슈 스무 개이고 번호와 제목을 가진다`() {
        val project = newProject()
        val author = newUser()
        (1L..22L).forEach { newIssue(project, author, it) }
        val body = mockMvc.perform(get("/${project.owner}/${project.name}/mentionList").param("mentionType", "issue"))
            .andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(20))
            .andExpect(jsonPath("$.result[0].issueNo").value("22")).andExpect(jsonPath("$.result[0].title").value("title 22"))
            .andExpect(jsonPath("$.result[0].name").value("22title 22")).andReturn().response.contentAsString
        assertEquals(false, body.contains("\"issueNo\":\"2\"}"))
    }

    @Test
    fun `이슈 후보는 제목에 검색어가 있거나 번호가 검색어로 시작하는 것을 찾는다`() {
        val project = newProject()
        val author = newUser()
        newIssue(project, author, 1, "로그인 오류")
        newIssue(project, author, 2, "화면 깨짐")
        newIssue(project, author, 12, "다른 문제")
        mockMvc.perform(get("/${project.owner}/${project.name}/mentionList").param("mentionType", "issue").param("query", "로그인"))
            .andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].issueNo").value("1"))
        mockMvc.perform(get("/${project.owner}/${project.name}/mentionList").param("mentionType", "issue").param("query", "1"))
            .andExpect(jsonPath("$.result.length()").value(2))
    }

    @Test
    fun `종류가 없으면 빈 결과이다`() {
        val project = newProject()
        mockMvc.perform(get("/${project.owner}/${project.name}/mentionList")).andExpect(status().isOk).andExpect(jsonPath("$.result").doesNotExist())
    }

    @Test
    fun `읽기 권한 검사가 없어 익명도 비공개 프로젝트의 후보를 받는다 (v1_6 mentionList)`() {
        val project = newProject(ProjectScope.PRIVATE)
        mockMvc.perform(get("/${project.owner}/${project.name}/mentionList")).andExpect(status().isOk).andExpect(content().json("{}", true))
        mockMvc.perform(get("/${project.owner}/${project.name}/mentionList").param("mentionType", "user")).andExpect(status().isOk).andExpect(jsonPath("$.result").isArray)
    }

    @Test
    fun `없는 프로젝트는 종류가 없으면 빈 객체이고 user나 issue이면 500이다 (v1_6 널 참조)`() {
        mockMvc.perform(get("/nobody/nothing/mentionList")).andExpect(status().isOk).andExpect(content().json("{}", true))
        mockMvc.perform(get("/nobody/nothing/mentionList").param("mentionType", "user")).andExpect(status().isInternalServerError)
        mockMvc.perform(get("/nobody/nothing/mentionList").param("mentionType", "issue")).andExpect(status().isInternalServerError)
    }
}
