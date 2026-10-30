package dev.yona.web.issue

import dev.yona.domain.comment.IssueComment
import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEvent
import dev.yona.domain.issue.IssueRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * 이슈의 다음 상태(`GET issue/:number/nextstate`)와 삭제(`DELETE issue/:number/delete`)를 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:680-700, 938-958`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueActionsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ia$n", name = "ia$n", email = "ia$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun issue(project: Project, number: Long = 1, author: User? = null, state: State = State.OPEN, parent: Issue? = null, draft: Boolean = false): Issue =
        issues.save(
            Issue(
                project = project, number = number, title = "[버그] 제목 $number", body = "본문", state = state, isDraft = draft, parent = parent, authorId = author?.id, authorLoginId = author?.loginId,
                authorName = author?.name, numOfComments = 0, weight = 0, createdDate = Date(), updatedDate = Date()
            )
        )

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    // --- 다음 상태 ---

    @Test
    fun `구성원이 다음 상태를 누르면 상태가 바뀌고 보기 화면으로 돌아온다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        val session = login(member)
        mockMvc.perform(get("${base(project)}/issue/1/nextstate").session(session)).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        assertEquals(State.CLOSED, issues.findById(i.id!!).get().state)
        val event = entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id", IssueEvent::class.java).setParameter("id", i.id).resultList.single()
        assertEquals(EventType.ISSUE_STATE_CHANGED, event.eventType); assertEquals("closed", event.newValue)
        mockMvc.perform(get("${base(project)}/issue/1/nextstate").session(session)).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.OPEN, issues.findById(i.id!!).get().state)
    }

    @Test
    fun `작성자도 다음 상태를 누를 수 있고 구성원이 아니면 막힌다`() {
        val project = newProject()
        val author = newUser()
        val i = issue(project, author = author)
        mockMvc.perform(get("${base(project)}/issue/1/nextstate").session(login(newUser()))).andExpect(status().isForbidden)
        mockMvc.perform(get("${base(project)}/issue/1/nextstate")).andExpect(status().isForbidden)
        mockMvc.perform(get("${base(project)}/issue/1/nextstate").session(login(author))).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.CLOSED, issues.findById(i.id!!).get().state)
    }

    @Test
    fun `하위 이슈를 다시 열면 닫힌 부모도 열린다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val parent = issue(project, 1, state = State.CLOSED)
        val child = issue(project, 2, parent = parent, state = State.CLOSED)
        mockMvc.perform(get("${base(project)}/issue/2/nextstate").session(login(member))).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(State.OPEN, issues.findById(child.id!!).get().state)
        assertEquals(State.OPEN, issues.findById(parent.id!!).get().state)
    }

    // --- 삭제 ---

    @Test
    fun `이슈를 지우면 목록으로 가고 모두 정리된다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member)
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorLoginId = member.loginId, issue = i, projectId = project.id!!)); entityManager.flush()
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(member))).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issues?state=open"))
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findById(i.id!!).orElse(null))
        assertEquals(0, entityManager.createQuery("select c from IssueComment c where c.issue.id = :id").setParameter("id", i.id).resultList.size)
    }

    @Test
    fun `남의 이슈는 구성원도 지울 수 없고 작성자와 프로젝트 관리자만 지운다 (X-36, D-48)`() {
        val project = newProject()
        val author = join(newUser(), project); val member = join(newUser(), project); val manager = join(newUser(), project, RoleType.MANAGER)
        val own = issue(project, number = 1, author = author); val managed = issue(project, number = 2, author = author)
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(member))).andExpect(status().isForbidden)
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(newUser()))).andExpect(status().isForbidden)
        assertEquals(true, issues.findById(own.id!!).isPresent)
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(author))).andExpect(status().isSeeOther)
        mockMvc.perform(delete("${base(project)}/issue/2/delete").session(login(manager))).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findById(own.id!!).orElse(null)); assertNull(issues.findById(managed.id!!).orElse(null))
    }

    @Test
    fun `Ajax로 지우면 204와 목록 주소를 알린다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(member)).header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isNoContent).andExpect(header().string("Location", "${base(project)}/issues?state=open"))
    }

    @Test
    fun `다른 사람의 댓글이 있으면 400이고 권한이 없으면 403이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member)
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(newUser()))).andExpect(status().isForbidden)
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorLoginId = "someone-else", issue = i, projectId = project.id!!)); entityManager.flush()
        mockMvc.perform(delete("${base(project)}/issue/1/delete").session(login(member))).andExpect(status().isBadRequest)
        assertNotNull(issues.findById(i.id!!).orElse(null))
    }

    @Test
    fun `없는 이슈는 지울 수 없다`() {
        val project = newProject()
        val member = join(newUser(), project)
        mockMvc.perform(delete("${base(project)}/issue/99/delete").session(login(member))).andExpect(status().isNotFound)
    }

    // --- 목록 조각의 숫자 표시 ---

    @Test
    fun `목록의 댓글과 추천 수는 있는 것만 보인다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project)
        entityManager.persist(IssueComment(contents = "c", createdDate = Date(), authorLoginId = member.loginId, issue = i, projectId = project.id!!)); entityManager.flush()
        val doc = org.jsoup.Jsoup.parse(mockMvc.perform(get("${base(project)}/issues")).andReturn().response.contentAsString)
        assertEquals(1, doc.select("li.post-item .comments-count").size)
        assertEquals(0, doc.select("li.post-item .vote-count, li.post-item .sharer-color").size)
    }
}
