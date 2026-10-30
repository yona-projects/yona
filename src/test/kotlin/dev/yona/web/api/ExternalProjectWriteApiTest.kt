package dev.yona.web.api

import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueLabel
import dev.yona.domain.issue.IssueLabelCategory
import dev.yona.domain.issue.IssueLabelCategoryRepository
import dev.yona.domain.issue.IssueLabelRepository
import dev.yona.domain.issue.IssueRepository
import dev.yona.domain.issue.Milestone
import dev.yona.domain.issue.MilestoneRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 라벨과 마일스톤 관리 REST(로드맵 X-29c, `decisions.md` D-47)를 검증한다. 만들기는 v1.6에서 옮긴 `ExternalProjectApiController`에 이미 있고,
 * 여기서는 고치기와 지우기를 본다. 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). 웹 화면과 같은 판정(라벨과 마일스톤은 구성원이 고치고 지운다)을 따른다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalProjectWriteApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0
    private val tokens = mutableMapOf<Long, String>()

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "pw$n", name = "pw$n", email = "pw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let { tokens[saved.id!!] = "pt$n$it"; dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) }
        return saved
    }

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "pwo$n", name = "pwp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun joined(project: Project, role: RoleType = RoleType.MEMBER): Pair<User, String> {
        val user = newUser("a"); projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user to tokenOf(user)
    }

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, token: String?, body: String?) =
        mockMvc.perform(builder.apply { token?.let { header("Authorization", "token $it") } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun label(project: Project, categoryName: String = "Type", name: String = "bug", exclusive: Boolean = false): IssueLabel {
        val category = categories.findAll().firstOrNull { it.project?.id == project.id && it.name == categoryName } ?: categories.save(IssueLabelCategory(project = project, name = categoryName, isExclusive = exclusive))
        return labels.save(IssueLabel(project = project, category = category, name = name, color = "#ff0000"))
    }

    private fun milestone(project: Project, title: String = "1.0", state: State = State.OPEN): Milestone =
        milestones.save(Milestone(project = project, title = title, contents = "설명", state = state, dueDate = null))

    private fun reload(l: IssueLabel): IssueLabel { entityManager.flush(); entityManager.clear(); return labels.findById(l.id!!).get() }
    private fun reload(m: Milestone): Milestone { entityManager.flush(); entityManager.clear(); return milestones.findById(m.id!!).get() }

    // --- 라벨 고치기 ---

    @Test
    fun `라벨은 보낸 항목만 바꾸고 분류가 없으면 새로 만들며 구성원이 고친다`() {
        val project = newProject(); val (_, token) = joined(project); val l = label(project)
        json(patch(api(project, "labels/${l.id}")), token, """{"labelName":"defect"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.labelName").value("defect")).andExpect(jsonPath("$.result.labelColor").value("#ff0000")).andExpect(jsonPath("$.result.category").value("Type"))
        json(patch(api(project, "labels/${l.id}")), token, """{"labelColor":"#00ff00","category":"Priority"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.labelColor").value("#00ff00")).andExpect(jsonPath("$.result.category").value("Priority"))
        val saved = reload(l)
        assertEquals("defect", saved.name); assertEquals("#00ff00", saved.color); assertEquals("Priority", saved.category?.name)
        assertEquals(false, saved.category?.isExclusive)
    }

    @Test
    fun `라벨 고치기의 오류와 중복과 권한`() {
        val project = newProject(); val other = newProject(); val (_, token) = joined(project)
        val a = label(project, "Type", "bug"); label(project, "Type", "feature"); val foreign = label(other)
        json(patch(api(project, "labels/${a.id}")), token, """{}""").andExpect(status().isBadRequest)
        json(patch(api(project, "labels/${a.id}")), token, """{"labelName":" "}""").andExpect(status().isBadRequest)
        json(patch(api(project, "labels/${a.id}")), token, """{"labelColor":""}""").andExpect(status().isBadRequest)
        json(patch(api(project, "labels/${a.id}")), token, """{"labelName":"${"x".repeat(256)}"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "labels/${a.id}")), token, """{"labelName":"feature"}""").andExpect(status().isConflict)
        json(patch(api(project, "labels/${a.id}")), token, """{"labelName":"bug","labelColor":"#111"}""").andExpect(status().isOk)
        json(patch(api(project, "labels/${a.id}")), token, null).andExpect(status().isBadRequest)
        json(patch(api(project, "labels/${foreign.id}")), token, """{"labelName":"x"}""").andExpect(status().isNotFound)
        json(patch(api(project, "labels/999999")), token, """{"labelName":"x"}""").andExpect(status().isNotFound)
        json(patch(api(project, "labels/${a.id}")), tokenOf(newUser("x")), """{"labelName":"x"}""").andExpect(status().isForbidden)
        json(patch(api(project, "labels/${a.id}")), null, """{"labelName":"x"}""").andExpect(status().isUnauthorized)
        assertEquals("bug", reload(a).name)
    }

    // --- 라벨 지우기 ---

    @Test
    fun `라벨을 지우면 이슈에서 떨어지고 남은 라벨이 없는 분류도 지워진다`() {
        val project = newProject(); val (member, token) = joined(project)
        val l = label(project, "Solo", "only"); val keep = label(project, "Type", "bug"); val categoryId = l.category!!.id!!
        project.lastIssueNumber = 1; projects.save(project)
        val i = issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = member.id, authorLoginId = member.loginId, authorName = member.name, numOfComments = 0, labels = mutableSetOf(l, keep)))
        json(delete(api(project, "labels/${l.id}")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(delete(api(project, "labels/${l.id}")), token, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(labels.findById(l.id!!).orElse(null)); assertNull(categories.findById(categoryId).orElse(null))
        assertEquals(setOf(keep.id), issues.findById(i.id!!).get().labels.map { it.id }.toSet())
        json(delete(api(project, "labels/${l.id}")), token, null).andExpect(status().isNotFound)
        json(delete(api(project, "labels/${label(newProject()).id}")), token, null).andExpect(status().isNotFound)
    }

    // --- 마일스톤 고치기 ---

    @Test
    fun `마일스톤은 보낸 항목만 바꾸고 마감일은 그날의 마지막 초이며 null이면 지운다`() {
        val project = newProject(); val (_, token) = joined(project); val m = milestone(project)
        json(patch(api(project, "milestones/${m.id}")), token, """{"title":"2.0"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.title").value("2.0")).andExpect(jsonPath("$.result.description").value("설명")).andExpect(jsonPath("$.result.state").value("open"))
        json(patch(api(project, "milestones/${m.id}")), token, """{"description":"새 설명","state":"closed","due_on":"2030-01-02"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.state").value("closed")).andExpect(jsonPath("$.result.dueDate").exists())
        var saved = reload(m)
        assertEquals("2.0", saved.title); assertEquals("새 설명", saved.contents); assertEquals(State.CLOSED, saved.state)
        val cal = java.util.Calendar.getInstance().apply { time = saved.dueDate!! }
        assertEquals(23, cal.get(java.util.Calendar.HOUR_OF_DAY)); assertEquals(59, cal.get(java.util.Calendar.MINUTE)); assertEquals(59, cal.get(java.util.Calendar.SECOND))
        json(patch(api(project, "milestones/${m.id}")), token, """{"due_on":null,"state":"open"}""").andExpect(status().isOk)
        saved = reload(m)
        assertNull(saved.dueDate); assertEquals(State.OPEN, saved.state)
    }

    @Test
    fun `마일스톤 고치기의 오류와 중복과 권한`() {
        val project = newProject(); val other = newProject(); val (_, token) = joined(project)
        val a = milestone(project, "1.0"); milestone(project, "2.0"); val foreign = milestone(other, "x")
        json(patch(api(project, "milestones/${a.id}")), token, """{}""").andExpect(status().isBadRequest)
        json(patch(api(project, "milestones/${a.id}")), token, """{"title":" "}""").andExpect(status().isBadRequest)
        json(patch(api(project, "milestones/${a.id}")), token, """{"state":"maybe"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "milestones/${a.id}")), token, """{"due_on":"not-a-date"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "milestones/${a.id}")), token, """{"title":"2.0"}""").andExpect(status().isConflict)
        json(patch(api(project, "milestones/${a.id}")), token, """{"title":"1.0","description":"같은 제목은 그대로 둔다"}""").andExpect(status().isOk)
        json(patch(api(project, "milestones/${a.id}")), token, null).andExpect(status().isBadRequest)
        json(patch(api(project, "milestones/${foreign.id}")), token, """{"title":"y"}""").andExpect(status().isNotFound)
        json(patch(api(project, "milestones/999999")), token, """{"title":"y"}""").andExpect(status().isNotFound)
        json(patch(api(project, "milestones/${a.id}")), tokenOf(newUser("x")), """{"title":"y"}""").andExpect(status().isForbidden)
        json(patch(api(project, "milestones/${a.id}")), null, """{"title":"y"}""").andExpect(status().isUnauthorized)
        assertEquals("1.0", reload(a).title)
    }

    // --- 마일스톤 지우기 ---

    @Test
    fun `마일스톤을 지우면 이슈의 마일스톤이 비워진다`() {
        val project = newProject(); val (member, token) = joined(project); val m = milestone(project)
        project.lastIssueNumber = 1; projects.save(project)
        val i = issues.save(Issue(project = project, number = 1, title = "t", body = "b", state = State.OPEN, authorId = member.id, authorLoginId = member.loginId, authorName = member.name, numOfComments = 0, milestone = m))
        json(delete(api(project, "milestones/${m.id}")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(delete(api(project, "milestones/${m.id}")), token, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertNull(milestones.findById(m.id!!).orElse(null)); assertNull(issues.findById(i.id!!).get().milestone)
        json(delete(api(project, "milestones/${m.id}")), token, null).andExpect(status().isNotFound)
        json(delete(api(project, "milestones/${milestone(newProject()).id}")), token, null).andExpect(status().isNotFound)
        assertTrue(true)
    }
}
