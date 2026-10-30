package dev.yona.web.api

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.ResourceType
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.IssueSharerRepository
import dev.yona.domain.misc.WatchRepository
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
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이슈의 Yona 고유 쓰기 REST(로드맵 X-29d, `decisions.md` D-47)를 검증한다: 공유자, 가중치, 지켜보기. 웹 화면에만 있던 기능의 토큰 버전이라
 * 웹과 같은 판정을 따른다(공유자와 가중치는 `UPDATE`, 지켜보기는 `READ`). 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번).
 */
@AutoConfigureMockMvc
@Transactional
class ExternalIssueExtrasApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository
    @Autowired private lateinit var sharers: IssueSharerRepository
    @Autowired private lateinit var watches: WatchRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "ex$n", name = "ex$n", email = "ex$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let { tokens[saved.id!!] = "et$n$it"; dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "exo$n", name = "exp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun joined(project: Project, role: RoleType = RoleType.MEMBER): Pair<User, String> {
        val user = newUser("a"); projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user to tokenOf(user)
    }

    private fun issue(project: Project, author: User, number: Long = 1): Issue {
        project.lastIssueNumber = maxOf(project.lastIssueNumber ?: 0, number); projects.save(project)
        return issues.save(Issue(project = project, number = number, title = "제목", body = "본문", state = State.OPEN, authorId = author.id, authorLoginId = author.loginId, authorName = author.name, numOfComments = 0))
    }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    // --- 공유자 ---

    @Test
    fun `공유자를 더하고 조회하고 빼며 이벤트가 남고 이미 공유자인 사용자를 더해도 한 번만 있다`() {
        val project = newProject(); val (member, token) = joined(project); val i = issue(project, member)
        val a = newUser(); val b = newUser()
        json(post(api(project, "issues/1/sharers")), token, """{"sharers":["${a.loginId}","${b.loginId}"]}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.length()").value(2)).andExpect(jsonPath("$.result[0].loginId").value(a.loginId))
        json(post(api(project, "issues/1/sharers")), token, """{"sharers":["${a.loginId}"]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2))
        assertEquals(2, sharers.findByIssueId(i.id!!).size)
        json(get(api(project, "issues/1/sharers")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2))
        json(delete(api(project, "issues/1/sharers")), token, """{"sharers":["${a.loginId}"]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(1)).andExpect(jsonPath("$.result[0].loginId").value(b.loginId))
        assertEquals(1, sharers.findByIssueId(i.id!!).size)
        entityManager.flush()
        assertTrue(issueEvents.findByIssueIdOrderByCreatedAscIdAsc(i.id!!).any { it.eventType == EventType.ISSUE_SHARER_CHANGED })
    }

    @Test
    fun `프로젝트를 공유자로 더하면 구성원이 모두 공유자가 되고 틀린 항목이 있으면 아무것도 바꾸지 않는다`() {
        val project = newProject(); val other = newProject(); val (member, token) = joined(project); val i = issue(project, member)
        val (m1, _) = joined(other); val (m2, _) = joined(other)
        json(post(api(project, "issues/1/sharers")), token, """{"projects":[${other.id}]}""").andExpect(status().isOk)
        assertEquals(setOf(m1.loginId, m2.loginId), sharers.findByIssueId(i.id!!).map { it.loginId }.toSet())
        val ghost = "nobody-" + sequence
        json(post(api(project, "issues/1/sharers")), token, """{"sharers":["${newUser().loginId}","$ghost"]}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/sharers")), token, """{"projects":[999999]}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/sharers")), token, """{"sharers":[]}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/sharers")), token, """{}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/sharers")), token, null).andExpect(status().isBadRequest)
        assertEquals(2, sharers.findByIssueId(i.id!!).size)
    }

    @Test
    fun `공유자는 구성원이 바꾸고 읽을 수 없거나 구성원이 아닌 토큰은 거절한다`() {
        val secret = newProject(ProjectScope.PRIVATE); val (member, token) = joined(secret); issue(secret, member); val target = newUser()
        val outsider = tokenOf(newUser("x"))
        json(get(api(secret, "issues/1/sharers")), outsider, null).andExpect(status().isForbidden)
        json(post(api(secret, "issues/1/sharers")), outsider, """{"sharers":["${target.loginId}"]}""").andExpect(status().isForbidden)
        json(delete(api(secret, "issues/1/sharers")), outsider, """{"sharers":["${target.loginId}"]}""").andExpect(status().isForbidden)
        json(post(api(secret, "issues/1/sharers")), null, """{"sharers":["${target.loginId}"]}""").andExpect(status().isUnauthorized)
        json(post(api(secret, "issues/9/sharers")), token, """{"sharers":["${target.loginId}"]}""").andExpect(status().isNotFound)
        val pub = newProject(); val (pm, _) = joined(pub); issue(pub, pm)
        json(get(api(pub, "issues/1/sharers")), outsider, null).andExpect(status().isOk)
        json(post(api(pub, "issues/1/sharers")), outsider, """{"sharers":["${target.loginId}"]}""").andExpect(status().isForbidden)
    }

    // --- 가중치 ---

    @Test
    fun `가중치는 1이나 -1만큼 올리고 내리며 그 밖의 값은 거절한다`() {
        val project = newProject(); val (member, token) = joined(project); val i = issue(project, member)
        json(post(api(project, "issues/1/weight")), token, """{"delta":1}""").andExpect(status().isOk).andExpect(jsonPath("$.result.weight").value(1))
        json(post(api(project, "issues/1/weight")), token, """{"delta":1}""").andExpect(jsonPath("$.result.weight").value(2))
        json(post(api(project, "issues/1/weight")), token, """{"delta":-1}""").andExpect(jsonPath("$.result.weight").value(1))
        json(post(api(project, "issues/1/weight")), token, """{"delta":5}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/weight")), token, """{"delta":"up"}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/weight")), token, """{}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/weight")), token, null).andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/weight")), tokenOf(newUser("x")), """{"delta":1}""").andExpect(status().isForbidden)
        json(post(api(project, "issues/1/weight")), null, """{"delta":1}""").andExpect(status().isUnauthorized)
        json(post(api(project, "issues/9/weight")), token, """{"delta":1}""").andExpect(status().isNotFound)
        entityManager.flush(); entityManager.clear()
        assertEquals(1, issues.findById(i.id!!).get().weight)
    }

    // --- 지켜보기 ---

    @Test
    fun `지켜보기와 해제는 읽을 수 있으면 누구나 하고 되풀이해도 그대로이다`() {
        val project = newProject(ProjectScope.PRIVATE); val (member, token) = joined(project); val i = issue(project, member)
        val userId = member.id!!
        json(post(api(project, "issues/1/watchers")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.watching").value(true))
        json(post(api(project, "issues/1/watchers")), token, null).andExpect(status().isOk)
        assertEquals(1, watches.findByUserIdAndResourceTypeAndResourceId(userId, ResourceType.ISSUE_POST, i.id.toString()).size)
        json(delete(api(project, "issues/1/watchers")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.watching").value(false))
        json(delete(api(project, "issues/1/watchers")), token, null).andExpect(status().isOk)
        assertEquals(0, watches.findByUserIdAndResourceTypeAndResourceId(userId, ResourceType.ISSUE_POST, i.id.toString()).size)
        val outsider = tokenOf(newUser("x"))
        json(post(api(project, "issues/1/watchers")), outsider, null).andExpect(status().isForbidden)
        json(delete(api(project, "issues/1/watchers")), outsider, null).andExpect(status().isForbidden)
        json(post(api(project, "issues/1/watchers")), null, null).andExpect(status().isUnauthorized)
        json(post(api(project, "issues/9/watchers")), token, null).andExpect(status().isNotFound)
    }
}
