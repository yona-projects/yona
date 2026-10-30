package dev.yona.web.api

import dev.yona.domain.enumeration.EventType
import dev.yona.domain.enumeration.ProjectScope
import dev.yona.domain.enumeration.RoleType
import dev.yona.domain.enumeration.State
import dev.yona.domain.enumeration.UserState
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.issue.Issue
import dev.yona.domain.issue.IssueEventRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 이슈 쓰기 REST의 a단계(로드맵 X-29a, `decisions.md` D-47)를 검증한다. 경로는 v1.6 접두사(`/-_-api/v1/owners/{owner}/projects/{projectName}`)에 GitHub 자원 경로를 얹은 것이다.
 * 토큰이 할 수 있는 일은 범위와 사용자의 실제 권한의 교집합이다(D-36 4번). v1.6 계약(상태만 바꾸는 `PATCH`, `PUT`)은 그대로 동작해야 한다.
 */
@AutoConfigureMockMvc
@Transactional
class ExternalIssueWriteApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var apiTokenRepository: dev.yona.domain.apitoken.ApiTokenRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var issueEvents: IssueEventRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(token: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        val saved = users.save(User(loginId = "iw$n", name = "iw$n", email = "iw$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
        token?.let { tokens[saved.id!!] = "tk$n$it"; dev.yona.support.ApiTokenSupport.grant(apiTokenRepository, saved, tokens.getValue(saved.id!!)) }
        return saved
    }

    private val tokens = mutableMapOf<Long, String>()

    private fun tokenOf(user: User): String = tokens.getValue(user.id!!)

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "iwo$n", name = "iwp$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0, lastPostingNumber = 0))
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

    private fun reload(i: Issue): Issue { entityManager.flush(); entityManager.clear(); return issues.findById(i.id!!).get() }

    // --- a0: 토큰이 사용자의 실제 권한도 확인한다 (D-36 4번) ---

    @Test
    fun `구성원이 아닌 토큰은 비공개 프로젝트의 이슈를 읽지 못하고 공개 프로젝트의 이슈는 읽는다`() {
        val secret = newProject(ProjectScope.PRIVATE); val open = newProject()
        val owner = newUser(); issue(secret, owner); issue(open, owner)
        val outsider = tokenOf(newUser("x"))
        json(get(api(secret, "issues/1")), outsider, null).andExpect(status().isForbidden)
        json(get(api(open, "issues/1")), outsider, null).andExpect(status().isOk)
        val (_, member) = joined(secret)
        json(get(api(secret, "issues/1")), member, null).andExpect(status().isOk)
    }

    @Test
    fun `구성원이 아닌 토큰은 이슈를 고치지 못하고 구성원은 고친다 (PUT과 PATCH)`() {
        val project = newProject(); issue(project, newUser())
        val outsider = tokenOf(newUser("x"))
        val body = """{"title":"t","body":"b","state":"OPEN","assignees":[]}"""
        json(put(api(project, "issues/1")), outsider, body).andExpect(status().isForbidden)
        json(patch(api(project, "issues/1")), outsider, """{"state":"closed"}""").andExpect(status().isForbidden)
        val (_, member) = joined(project)
        json(put(api(project, "issues/1")), member, body).andExpect(status().isOk)
        json(patch(api(project, "issues/1")), member, """{"state":"closed"}""").andExpect(status().isOk)
    }

    // --- a1: PATCH 부분 수정 ---

    @Test
    fun `PATCH는 보낸 항목만 바꾼다`() {
        val project = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        json(patch(api(project, "issues/1")), token, """{"title":"새 제목"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.title").value("새 제목")).andExpect(jsonPath("$.result.body").value("본문"))
        assertEquals("새 제목", reload(i).title); assertEquals("본문", reload(i).body); assertEquals(State.OPEN, reload(i).state)
        json(patch(api(project, "issues/1")), token, """{"body":"새 본문"}""").andExpect(status().isOk)
        assertEquals("새 제목", reload(i).title); assertEquals("새 본문", reload(i).body)
    }

    @Test
    fun `PATCH는 제목과 본문과 상태를 함께 바꾸고 상태 변경 이벤트를 남긴다`() {
        val project = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        json(patch(api(project, "issues/1")), token, """{"title":"t2","body":"b2","state":"closed"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.state").value("CLOSED"))
        val saved = reload(i)
        assertEquals(State.CLOSED, saved.state); assertEquals("t2", saved.title); assertEquals("b2", saved.body)
        assertEquals(listOf(EventType.ISSUE_STATE_CHANGED), issueEvents.findByIssueIdOrderByCreatedAscIdAsc(saved.id!!).map { it.eventType })
    }

    @Test
    fun `PATCH는 마일스톤을 번호로 정하고 null이면 지운다`() {
        val project = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        json(patch(api(project, "issues/1")), token, """{"milestone":${milestone.id}}""").andExpect(status().isOk)
        assertEquals(milestone.id, reload(i).milestone?.id)
        json(patch(api(project, "issues/1")), token, """{"milestone":null}""").andExpect(status().isOk)
        assertNull(reload(i).milestone)
    }

    @Test
    fun `PATCH는 다른 프로젝트의 마일스톤과 없는 마일스톤을 거절한다`() {
        val project = newProject(); val other = newProject(); issue(project, newUser()); val (_, token) = joined(project)
        val foreign = milestones.save(Milestone(project = other, title = "x", state = State.OPEN))
        json(patch(api(project, "issues/1")), token, """{"milestone":${foreign.id}}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1")), token, """{"milestone":999999}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH는 담당자를 로그인 ID로 정하고 빈 목록이면 지우며 변경 이벤트를 남긴다`() {
        val project = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        val assignee = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        json(patch(api(project, "issues/1")), token, """{"assignees":["${assignee.loginId}"]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.assignees[0].loginId").value(assignee.loginId))
        assertEquals(assignee.id, reload(i).assignee?.user?.id)
        json(patch(api(project, "issues/1")), token, """{"assignees":[]}""").andExpect(status().isOk)
        assertNull(reload(i).assignee)
        assertEquals(listOf(EventType.ISSUE_ASSIGNEE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED), issueEvents.findByIssueIdOrderByCreatedAscIdAsc(i.id!!).map { it.eventType })
    }

    @Test
    fun `PATCH는 담당자를 둘 이상 받지 않고 없는 사용자를 거절한다`() {
        val project = newProject(); issue(project, newUser()); val (_, token) = joined(project)
        val a = newUser(); val b = newUser()
        json(patch(api(project, "issues/1")), token, """{"assignees":["${a.loginId}","${b.loginId}"]}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1")), token, """{"assignees":["nobody-here"]}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH는 라벨을 번호 목록으로 바꾸고 다른 프로젝트의 라벨과 같은 분류의 중복을 거절한다`() {
        val project = newProject(); val other = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        val exclusive = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = true))
        val a = labels.save(IssueLabel(project = project, category = exclusive, name = "a", color = "#fff")); val b = labels.save(IssueLabel(project = project, category = exclusive, name = "b", color = "#fff"))
        val free = categories.save(IssueLabelCategory(project = project, name = "Free", isExclusive = false))
        val c = labels.save(IssueLabel(project = project, category = free, name = "c", color = "#fff")); val d = labels.save(IssueLabel(project = project, category = free, name = "d", color = "#fff"))
        val foreign = labels.save(IssueLabel(project = other, category = categories.save(IssueLabelCategory(project = other, name = "o", isExclusive = false)), name = "f", color = "#fff"))
        json(patch(api(project, "issues/1")), token, """{"labels":[${a.id},${c.id},${d.id}]}""").andExpect(status().isOk)
        assertEquals(setOf(a.id, c.id, d.id), reload(i).labels.map { it.id }.toSet())
        json(patch(api(project, "issues/1")), token, """{"labels":[]}""").andExpect(status().isOk)
        assertEquals(emptySet(), reload(i).labels.map { it.id }.toSet())
        json(patch(api(project, "issues/1")), token, """{"labels":[${a.id},${b.id}]}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1")), token, """{"labels":[${foreign.id}]}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1")), token, """{"labels":[999999]}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `PATCH는 고칠 항목이 없거나 JSON이 아니면 거절하고 한 항목이라도 틀리면 아무것도 바꾸지 않는다`() {
        val project = newProject(); val i = issue(project, newUser()); val (_, token) = joined(project)
        json(patch(api(project, "issues/1")), token, "{}").andExpect(status().isBadRequest)
        mockMvc.perform(patch(api(project, "issues/1")).header("Authorization", "token $token").contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1")), token, """{"title":"바뀌면 안 됨","milestone":999999}""").andExpect(status().isBadRequest)
        assertEquals("제목", reload(i).title)
    }

    @Test
    fun `PATCH는 토큰이 없으면 401이고 없는 이슈는 400이다`() {
        val project = newProject(); issue(project, newUser()); val (_, token) = joined(project)
        json(patch(api(project, "issues/1")), null, """{"title":"x"}""").andExpect(status().isUnauthorized)
        json(patch(api(project, "issues/9")), token, """{"title":"x"}""").andExpect(status().is4xxClientError)
    }

    // --- a2: 댓글 목록, 수정, 삭제 ---

    private fun comment(issue: Issue, author: User, text: String = "댓글"): IssueComment =
        comments.save(IssueComment(contents = text, createdDate = java.util.Date(), authorId = author.id, authorLoginId = author.loginId, authorName = author.name, issue = issue, projectId = requireNotNull(issue.project?.id))).also {
            issue.numOfComments = (issue.numOfComments ?: 0) + 1; issues.save(issue)
        }

    @Test
    fun `댓글 목록은 오래된 순서로 내용과 작성자를 돌려주고 읽을 수 없는 토큰은 거절한다`() {
        val secret = newProject(ProjectScope.PRIVATE); val (member, token) = joined(secret); val i = issue(secret, member)
        comment(i, member, "첫째"); comment(i, member, "둘째")
        json(get(api(secret, "issues/1/comments")), token, null).andExpect(status().isOk).andExpect(jsonPath("$.result.length()").value(2))
            .andExpect(jsonPath("$.result[0].body").value("첫째")).andExpect(jsonPath("$.result[1].body").value("둘째")).andExpect(jsonPath("$.result[0].author.loginId").value(member.loginId))
        json(get(api(secret, "issues/1/comments")), tokenOf(newUser("x")), null).andExpect(status().isForbidden)
        json(get(api(secret, "issues/1/comments")), null, null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `댓글은 프로젝트 구성원이 고치고 구성원이 아닌 토큰은 고치지 못한다`() {
        val project = newProject(); val (author, authorToken) = joined(project); val outsiderToken = tokenOf(newUser("x")); val (_, managerToken) = joined(project, RoleType.MANAGER)
        val c = comment(issue(project, author), author, "원래")
        json(patch(api(project, "issues/1/comments/${c.id}")), outsiderToken, """{"body":"남의 것"}""").andExpect(status().isForbidden)
        json(patch(api(project, "issues/1/comments/${c.id}")), authorToken, """{"body":"고침"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.body").value("고침")).andExpect(jsonPath("$.result.id").value(c.id!!.toInt()))
        entityManager.flush(); entityManager.clear()
        assertEquals("고침", comments.findById(c.id!!).get().contents)
        json(patch(api(project, "issues/1/comments/${c.id}")), managerToken, """{"body":"관리자가 고침"}""").andExpect(status().isOk)
    }

    @Test
    fun `댓글 수정은 빈 내용과 다른 이슈의 댓글과 없는 댓글을 거절한다`() {
        val project = newProject(); val (author, token) = joined(project)
        val c = comment(issue(project, author), author); val other = issue(project, author, 2); val foreign = comment(other, author)
        json(patch(api(project, "issues/1/comments/${c.id}")), token, """{"body":""}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1/comments/${c.id}")), token, """{"title":"x"}""").andExpect(status().isBadRequest)
        json(patch(api(project, "issues/1/comments/${foreign.id}")), token, """{"body":"x"}""").andExpect(status().is4xxClientError)
        json(patch(api(project, "issues/1/comments/999999")), token, """{"body":"x"}""").andExpect(status().is4xxClientError)
        json(patch(api(project, "issues/1/comments/${c.id}")), null, """{"body":"x"}""").andExpect(status().isUnauthorized)
    }

    @Test
    fun `댓글은 작성자와 프로젝트 관리자만 지우고 다른 구성원과 구성원이 아닌 토큰은 지우지 못하며 이슈의 댓글 수가 줄어든다 (D-48)`() {
        val project = newProject(); val (author, authorToken) = joined(project); val (_, memberToken) = joined(project); val outsiderToken = tokenOf(newUser("x")); val (_, managerToken) = joined(project, RoleType.MANAGER)
        val i = issue(project, author); val a = comment(i, author); val b = comment(i, author)
        json(delete(api(project, "issues/1/comments/${a.id}")), outsiderToken, null).andExpect(status().isForbidden)
        json(delete(api(project, "issues/1/comments/${a.id}")), memberToken, null).andExpect(status().isForbidden)
        json(delete(api(project, "issues/1/comments/${a.id}")), authorToken, null).andExpect(status().isNoContent)
        json(delete(api(project, "issues/1/comments/${b.id}")), managerToken, null).andExpect(status().isNoContent)
        entityManager.flush(); entityManager.clear()
        assertEquals(0, comments.findByIssueIdOrderByCreatedDateAscIdAsc(i.id!!).size); assertEquals(0, issues.findById(i.id!!).get().numOfComments)
        json(delete(api(project, "issues/1/comments/${a.id}")), authorToken, null).andExpect(status().is4xxClientError)
    }

    // --- a3: 이슈 삭제, 라벨, 담당자 ---

    @Test
    fun `이슈는 작성자와 프로젝트 관리자만 지우고 다른 구성원과 구성원이 아닌 토큰은 지우지 못하며 다른 사람의 댓글이 있으면 지우지 못한다 (D-48)`() {
        val project = newProject(); val (author, authorToken) = joined(project); val outsiderToken = tokenOf(newUser("x")); val (other2, memberToken) = joined(project); val (_, managerToken) = joined(project, RoleType.MANAGER)
        val own = issue(project, author, 1); val managed = issue(project, author, 2); val discussed = issue(project, author, 3); comment(discussed, other2)
        json(delete(api(project, "issues/1")), outsiderToken, null).andExpect(status().isForbidden)
        json(delete(api(project, "issues/1")), memberToken, null).andExpect(status().isForbidden)
        json(delete(api(project, "issues/1")), authorToken, null).andExpect(status().isNoContent)
        json(delete(api(project, "issues/2")), managerToken, null).andExpect(status().isNoContent)
        json(delete(api(project, "issues/3")), authorToken, null).andExpect(status().isConflict)
        entityManager.flush(); entityManager.clear()
        assertEquals(null, issues.findById(own.id!!).orElse(null)); assertEquals(null, issues.findById(managed.id!!).orElse(null)); assertEquals(true, issues.findById(discussed.id!!).isPresent)
        json(delete(api(project, "issues/1")), authorToken, null).andExpect(status().is4xxClientError)
        json(delete(api(project, "issues/3")), null, null).andExpect(status().isUnauthorized)
    }

    private fun labelsOf(project: Project): Triple<IssueLabel, IssueLabel, IssueLabel> {
        val exclusive = categories.save(IssueLabelCategory(project = project, name = "Type", isExclusive = true))
        val free = categories.save(IssueLabelCategory(project = project, name = "Free", isExclusive = false))
        return Triple(labels.save(IssueLabel(project = project, category = exclusive, name = "a", color = "#fff")), labels.save(IssueLabel(project = project, category = exclusive, name = "b", color = "#fff")), labels.save(IssueLabel(project = project, category = free, name = "c", color = "#fff")))
    }

    @Test
    fun `라벨을 더하고 하나씩 떼며 같은 분류의 중복과 다른 프로젝트의 라벨은 거절한다`() {
        val project = newProject(); val other = newProject(); val (member, token) = joined(project); val i = issue(project, member)
        val (a, b, c) = labelsOf(project)
        val foreign = labels.save(IssueLabel(project = other, category = categories.save(IssueLabelCategory(project = other, name = "o", isExclusive = false)), name = "f", color = "#fff"))
        json(post(api(project, "issues/1/labels")), token, """{"labels":[${a.id}]}""").andExpect(status().isOk)
        json(post(api(project, "issues/1/labels")), token, """{"labels":[${c.id}]}""").andExpect(status().isOk)
        assertEquals(setOf(a.id, c.id), reload(i).labels.map { it.id }.toSet())
        json(post(api(project, "issues/1/labels")), token, """{"labels":[${b.id}]}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/labels")), token, """{"labels":[${foreign.id}]}""").andExpect(status().isBadRequest)
        json(delete(api(project, "issues/1/labels/${a.id}")), token, null).andExpect(status().isOk)
        assertEquals(setOf(c.id), reload(i).labels.map { it.id }.toSet())
        json(delete(api(project, "issues/1/labels/${a.id}")), token, null).andExpect(status().isOk)
        json(post(api(project, "issues/1/labels")), tokenOf(newUser("x")), """{"labels":[${a.id}]}""").andExpect(status().isForbidden)
    }

    @Test
    fun `담당자를 정하고 빼며 변경 이벤트를 남긴다`() {
        val project = newProject(); val (member, token) = joined(project); val i = issue(project, member)
        val assignee = newUser().also { projectUsers.save(ProjectUser(user = it, project = project, role = roles.findById(RoleType.MEMBER.id).get())) }
        json(post(api(project, "issues/1/assignees")), token, """{"assignees":["${assignee.loginId}"]}""").andExpect(status().isOk).andExpect(jsonPath("$.result.assignees[0].loginId").value(assignee.loginId))
        assertEquals(assignee.id, reload(i).assignee?.user?.id)
        json(post(api(project, "issues/1/assignees")), token, """{"assignees":["a","b"]}""").andExpect(status().isBadRequest)
        json(post(api(project, "issues/1/assignees")), token, """{"assignees":["nobody-here"]}""").andExpect(status().isBadRequest)
        json(delete(api(project, "issues/1/assignees")), token, null).andExpect(status().isOk)
        assertNull(reload(i).assignee)
        assertEquals(listOf(EventType.ISSUE_ASSIGNEE_CHANGED, EventType.ISSUE_ASSIGNEE_CHANGED), issueEvents.findByIssueIdOrderByCreatedAscIdAsc(i.id!!).map { it.eventType })
        json(post(api(project, "issues/1/assignees")), tokenOf(newUser("x")), """{"assignees":["${assignee.loginId}"]}""").andExpect(status().isForbidden)
    }
}
