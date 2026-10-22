package dev.yona.web.issue

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.http.MediaType
import dev.yona.domain.comment.IssueComment
import dev.yona.domain.comment.IssueCommentRepository
import dev.yona.domain.issue.IssueSharer
import java.time.LocalDate
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
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
 * 이슈 화면이 쓰는 JSON API를 검증한다: 담당자 후보와 변경, 공유자 후보와 변경, 가중치, 변경 감지, 본문 갱신(체크리스트), 댓글 갱신.
 *
 * 원본: `app/controllers/api/IssueApi.java`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueApiTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var comments: IssueCommentRepository
    @Autowired private lateinit var assignees: AssigneeRepository
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
        val project = projects.save(Project(owner = "ao$n", name = "ap$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 1))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = true, board = true))
        return project
    }

    private fun join(user: User, project: Project): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(RoleType.MEMBER.id).get()))
        return user
    }

    private fun issue(project: Project, author: User? = null, assignee: User? = null, body: String = "본문"): Issue = issues.save(
        Issue(
            project = project, number = 1, title = "제목", body = body, state = State.OPEN, authorId = author?.id, authorLoginId = author?.loginId, authorName = author?.name,
            assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, numOfComments = 0, weight = 0,
            createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
        )
    )

    private fun api(project: Project, path: String) = "/-_-api/v1/owners/${project.owner}/projects/${project.name}/$path"

    private fun json(builder: org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder, session: MockHttpSession?, body: String?) =
        mockMvc.perform(builder.apply { session?.let { session(it) } }.contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).apply { body?.let { content(it) } })

    private fun sha1(text: String) = java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    // --- 담당자 후보 ---

    @Test
    fun `담당자가 있는 이슈의 후보는 나, 작성자, 없음, 담당자 순이다`() {
        val project = newProject()
        val me = join(newUser(), project); val author = join(newUser(), project); val assignee = join(newUser(), project)
        issue(project, author = author, assignee = assignee)
        mockMvc.perform(get(api(project, "issues/1/assignableUsers")).session(login(me)).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].loginId").value(me.loginId)).andExpect(jsonPath("$[0].name").value("Assign to me"))
            .andExpect(jsonPath("$[1].loginId").value(author.loginId)).andExpect(jsonPath("$[1].name").value("Assign to author"))
            .andExpect(jsonPath("$[2].loginId").value("")).andExpect(jsonPath("$[2].name").value("No assignee"))
            .andExpect(jsonPath("$[3].loginId").value(assignee.loginId)).andExpect(jsonPath("$[3].type").value("user"))
    }

    @Test
    fun `담당자가 없으면 나와 작성자가 먼저이다`() {
        val project = newProject()
        val me = join(newUser(), project); val author = join(newUser(), project)
        issue(project, author = author)
        mockMvc.perform(get(api(project, "issues/1/assignableUsers")).session(login(me)).accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("Assign to me")).andExpect(jsonPath("$[1].name").value("Assign to author")).andExpect(jsonPath("$[2].type").value("user"))
        // 내가 작성자이면 작성자 항목이 없다.
        mockMvc.perform(get(api(project, "issues/1/assignableUsers")).session(login(author)).accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("Assign to me")).andExpect(jsonPath("$[1].type").value("user"))
    }

    @Test
    fun `검색어가 있으면 활성 사용자를 찾고 JSON이 아니면 406이다`() {
        val project = newProject()
        val me = join(newUser(), project)
        issue(project, author = me)
        mockMvc.perform(get(api(project, "issues/1/assignableUsers")).param("query", me.loginId!!).session(login(me)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].loginId").value(me.loginId))
        mockMvc.perform(get(api(project, "issues/1/assignableUsers")).session(login(me)).accept(MediaType.TEXT_HTML)).andExpect(status().isNotAcceptable)
    }

    // --- 담당자 변경 ---

    @Test
    fun `담당자를 바꾸고 이벤트를 남긴다`() {
        val project = newProject()
        val member = join(newUser(), project); val target = join(newUser(), project)
        val i = issue(project, author = member)
        json(post(api(project, "issues/1/assignees")), login(member), """{"assignees":["${target.loginId}"]}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.assignee.loginId").value(target.loginId)).andExpect(jsonPath("$.assignee.name").value("${target.name}"))
            .andExpect(jsonPath("$.issue").value("/${project.owner}/${project.name}/issue/1"))
        entityManager.flush(); entityManager.clear()
        assertEquals(target.id, issues.findById(i.id!!).get().assignee?.user?.id)
        val events = entityManager.createQuery("select e from IssueEvent e", IssueEvent::class.java).resultList
        assertEquals(listOf(EventType.ISSUE_ASSIGNEE_CHANGED), events.map { it.eventType }); assertEquals(target.loginId, events.single().newValue)
    }

    @Test
    fun `빈 로그인 아이디는 담당자를 비우고 이름은 없음이다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member, assignee = member)
        json(post(api(project, "issues/1/assignees")), login(member), """{"assignees":[""]}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.assignee.loginId").value("")).andExpect(jsonPath("$.assignee.name").value("None"))
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findById(i.id!!).get().assignee)
    }

    @Test
    fun `담당자 변경의 잘못된 요청`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        val session = login(member)
        json(post(api(project, "issues/1/assignees")), session, """{"assignees":[]}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No assignee"))
        mockMvc.perform(post(api(project, "issues/1/assignees")).session(session).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    @Test
    fun `권한이 없으면 담당자를 바꾸지 않고 이슈 주소만 돌려준다`() {
        val project = newProject()
        val outsider = newUser()
        val i = issue(project)
        json(post(api(project, "issues/1/assignees")), login(outsider), """{"assignees":["${outsider.loginId}"]}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.issue").value("/${project.owner}/${project.name}/issue/1")).andExpect(jsonPath("$.assignee").doesNotExist())
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findById(i.id!!).get().assignee)
    }

    // --- 공유자 ---

    @Test
    fun `사용자를 공유자로 추가하고 삭제한다`() {
        val project = newProject()
        val member = join(newUser(), project); val target = newUser()
        val i = issue(project, author = member)
        val session = login(member)
        json(post(api(project, "issues/1/share")), session, """{"sharer":{"loginId":"${target.loginId}","type":"user"},"action":"add"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.action").value("added")).andExpect(jsonPath("$.sharer").value(target.name))
        entityManager.flush(); entityManager.clear()
        assertEquals(listOf(target.loginId), entityManager.createQuery("select s from IssueSharer s where s.issue.id = :id", IssueSharer::class.java).setParameter("id", i.id).resultList.map { it.loginId })
        json(post(api(project, "issues/1/share")), session, """{"sharer":{"loginId":"${target.loginId}","type":"user"},"action":"delete"}""").andExpect(jsonPath("$.action").value("deleted"))
        entityManager.flush(); entityManager.clear()
        assertEquals(0, entityManager.createQuery("select s from IssueSharer s where s.issue.id = :id", IssueSharer::class.java).setParameter("id", i.id).resultList.size)
    }

    @Test
    fun `공유자 변경은 알림 이벤트를 남기고 같은 사용자를 두 번 추가해도 한 번만 기록한다`() {
        val project = newProject()
        val member = join(newUser(), project); val target = newUser()
        val i = issue(project, author = member)
        val session = login(member)
        repeat(2) { json(post(api(project, "issues/1/share")), session, """{"sharer":{"loginId":"${target.loginId}","type":"user"},"action":"add"}""").andExpect(status().isOk) }
        entityManager.flush(); entityManager.clear()
        assertEquals(1, entityManager.createQuery("select s from IssueSharer s where s.issue.id = :id", IssueSharer::class.java).setParameter("id", i.id).resultList.size)
        val events = entityManager.createQuery("select e from IssueEvent e order by e.id", IssueEvent::class.java).resultList
        assertEquals(listOf(EventType.ISSUE_SHARER_CHANGED, EventType.ISSUE_SHARER_CHANGED), events.map { it.eventType })
        assertEquals("" to target.loginId, events[0].oldValue.orEmpty() to events[0].newValue)
    }

    @Test
    fun `프로젝트를 공유자로 추가하면 구성원이 모두 추가된다`() {
        val project = newProject(); val other = newProject()
        val member = join(newUser(), project); val a = join(newUser(), other); val b = join(newUser(), other)
        val i = issue(project, author = member)
        json(post(api(project, "issues/1/share")), login(member), """{"sharer":{"loginId":"${other.id}","type":"project"},"action":"add"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.sharer").value(other.name))
        entityManager.flush(); entityManager.clear()
        assertEquals(setOf(a.loginId, b.loginId), entityManager.createQuery("select s from IssueSharer s where s.issue.id = :id", IssueSharer::class.java).setParameter("id", i.id).resultList.map { it.loginId }.toSet())
    }

    @Test
    fun `공유자 변경의 오류`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        json(post(api(project, "issues/1/share")), login(newUser()), """{"sharer":{"loginId":"x","type":"user"},"action":"add"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Permission denied"))
        json(post(api(project, "issues/1/share")), login(member), """{"action":"add"}""").andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("No sharer"))
        json(post(api(project, "issues/1/share")), login(member), """{"sharer":{"loginId":"nobody-here","type":"user"},"action":"add"}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `공유자를 로그인 아이디로 찾는다`() {
        val project = newProject()
        val member = join(newUser(), project); val a = newUser(); val b = newUser()
        val i = issue(project, author = member)
        entityManager.persist(IssueSharer(created = LocalDate.of(2020, 1, 2), loginId = a.loginId, user = a, issue = i))
        entityManager.persist(IssueSharer(created = LocalDate.of(2020, 1, 1), loginId = b.loginId, user = b, issue = i))
        entityManager.flush()
        mockMvc.perform(get(api(project, "issues/1/findSharer")).param("query", "${a.loginId},${b.loginId}").session(login(member)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].loginId").value(b.loginId)).andExpect(jsonPath("$[1].loginId").value(a.loginId))
    }

    @Test
    fun `공유 가능한 사용자와 공개 프로젝트를 찾는다`() {
        val project = newProject(); val shared = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        mockMvc.perform(get(api(project, "issues/1/sharableUsers")).param("query", member.loginId!!).session(login(member)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk).andExpect(jsonPath("$[0].loginId").value(member.loginId)).andExpect(jsonPath("$[0].type").value("user"))
        mockMvc.perform(get(api(project, "issues/1/sharableUsers")).param("query", shared.name!!).session(login(member)).accept(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].type").value("project")).andExpect(jsonPath("$[0].name").value("${shared.owner}/${shared.name}")).andExpect(jsonPath("$[0].loginId").value(shared.id))
    }

    // --- 댓글 알림 수신자 미리 보기 ---

    @Test
    fun `댓글 알림을 받을 사용자를 미리 보여 준다`() {
        val project = newProject()
        val author = join(newUser(), project); val commenter = join(newUser(), project); val mentioned = newUser()
        issue(project, author = author)
        json(post(api(project, "issues/1/commentNotiReceivers")), login(commenter), """{"comment":"확인 @${mentioned.loginId}","parentCommentId":""}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.receivers.length()").value(2)).andExpect(jsonPath("$.receivers[0].loginId").value(author.loginId)).andExpect(jsonPath("$.receivers[0].type").value("user"))
            .andExpect(jsonPath("$.receivers[1].loginId").value(mentioned.loginId)).andExpect(jsonPath("$.receivers[0].pureNameOnly").isString)
    }

    @Test
    fun `한 줄 댓글이면 부모 댓글 작성자가 수신자이다`() {
        val project = newProject()
        val author = join(newUser(), project); val parentAuthor = join(newUser(), project); val commenter = join(newUser(), project)
        val i = issue(project, author = author)
        val parent = comments.save(IssueComment(contents = "부모", createdDate = Date(), authorId = parentAuthor.id, authorLoginId = parentAuthor.loginId, authorName = parentAuthor.name, issue = i, projectId = project.id!!))
        json(post(api(project, "issues/1/commentNotiReceivers")), login(commenter), """{"comment":"답","parentCommentId":"${parent.id}"}""")
            .andExpect(jsonPath("$.receivers.length()").value(1)).andExpect(jsonPath("$.receivers[0].loginId").value(parentAuthor.loginId))
    }

    @Test
    fun `수신자 미리 보기의 오류`() {
        val project = newProject()
        issue(project)
        mockMvc.perform(post(api(project, "issues/1/commentNotiReceivers")).session(login(newUser())).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
        json(post(api(project, "issues/9/commentNotiReceivers")), login(newUser()), """{"comment":"x"}""").andExpect(status().isNotFound)
    }

    // --- 번역 ---

    @Test
    fun `번역 API를 설정하지 않았으면 412이다`() {
        mockMvc.perform(post("/-_-api/v1/translation").session(login(newUser())).contentType(MediaType.APPLICATION_JSON).content("""{"owner":"a","projectName":"b","type":"issue","number":1}"""))
            .andExpect(status().isPreconditionFailed).andExpect(content().string("Precondition Failed"))
    }

    // --- 가중치 ---

    @Test
    fun `가중치를 올리고 내린다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member)
        val session = login(member)
        mockMvc.perform(post(api(project, "issues/1/upvoteWeight")).session(session)).andExpect(status().isOk).andExpect(jsonPath("$.weight").value(1))
        mockMvc.perform(post(api(project, "issues/1/upvoteWeight")).session(session)).andExpect(jsonPath("$.weight").value(2))
        mockMvc.perform(post(api(project, "issues/1/downvoteWeight")).session(session)).andExpect(jsonPath("$.weight").value(1))
        entityManager.flush(); entityManager.clear()
        assertEquals(1, issues.findById(i.id!!).get().weight)
        mockMvc.perform(post(api(project, "issues/1/upvoteWeight")).session(login(newUser()))).andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Permission denied"))
    }

    // --- 변경 감지 ---

    @Test
    fun `변경 감지는 본문과 댓글 수의 변화를 알린다`() {
        val project = newProject()
        val member = join(newUser(), project); val writer = join(newUser(), project)
        val i = issue(project, author = member, body = "원문")
        val c = comments.save(IssueComment(contents = "새 댓글", createdDate = Date(), authorId = writer.id, authorLoginId = writer.loginId, authorName = writer.name, issue = i, projectId = project.id!!))
        entityManager.flush(); entityManager.clear()
        json(post(api(project, "issues/1/detectChange")), login(member), """{"issueBodyChecksum":"${sha1("원문")}","numOfComments":0,"lastUpdateDate":0}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.issueBodyChanged").value(false)).andExpect(jsonPath("$.numOfComments").value(1)).andExpect(jsonPath("$.commentAuthorName").value(writer.name))
            .andExpect(jsonPath("$.issueBodyChecksum").value(sha1("원문"))).andExpect(jsonPath("$.issueUpdateDate").value(1_700_000_000_000L)).andExpect(jsonPath("$.result").value("ok"))
        json(post(api(project, "issues/1/detectChange")), login(member), """{"issueBodyChecksum":"x","numOfComments":1}""")
            .andExpect(jsonPath("$.issueBodyChanged").value(true)).andExpect(jsonPath("$.commentAuthorName").doesNotExist())
        assertNotNull(c.id)
    }

    @Test
    fun `변경 감지의 오류`() {
        val project = newProject()
        issue(project)
        json(post(api(project, "issues/1/detectChange")), null, """{}""").andExpect(status().isUnauthorized).andExpect(jsonPath("$.message").value("unauthorized request"))
        mockMvc.perform(post(api(project, "issues/1/detectChange")).session(login(newUser())).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    // --- 본문 갱신 (체크리스트) ---

    @Test
    fun `본문을 갱신한다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member, body = "- [ ] 할 일")
        json(patch(api(project, "issues/1/content")), login(member), """{"content":"- [x] 할 일","original":"- [ ] 할 일"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.body").value("- [x] 할 일")).andExpect(jsonPath("$.number").value(1)).andExpect(jsonPath("$.owner").value(project.owner)).andExpect(jsonPath("$.type").value("ISSUE_POST"))
        entityManager.flush(); entityManager.clear()
        assertEquals("- [x] 할 일", issues.findById(i.id!!).get().body)
    }

    @Test
    fun `다른 사람이 먼저 고쳤으면 409와 저장된 내용을 돌려준다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member, body = "현재")
        json(patch(api(project, "issues/1/content")), login(member), """{"content":"내 것","original":"옛날"}""").andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Already modified by someone.")).andExpect(jsonPath("$.storedContent").value("현재"))
    }

    @Test
    fun `줄 끝 문자와 앞뒤 공백은 같은 본문으로 본다`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member, body = "가\n나")
        json(patch(api(project, "issues/1/content")), login(member), """{"content":"다","original":" 가\r\n나 "}""").andExpect(status().isOk)
    }

    @Test
    fun `본문 갱신의 오류`() {
        val project = newProject()
        val member = join(newUser(), project)
        issue(project, author = member)
        json(patch(api(project, "issues/1/content")), null, """{"content":"a","original":"b"}""").andExpect(status().isUnauthorized)
        json(patch(api(project, "issues/1/content")), login(newUser()), """{"content":"a","original":"본문"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Forbidden request"))
        mockMvc.perform(patch(api(project, "issues/1/content")).session(login(member)).contentType(MediaType.TEXT_PLAIN).content("x")).andExpect(status().isBadRequest)
    }

    // --- 댓글 갱신 ---

    @Test
    fun `댓글을 갱신한다`() {
        val project = newProject()
        val member = join(newUser(), project)
        val i = issue(project, author = member)
        val c = comments.save(IssueComment(contents = "처음", createdDate = Date(), authorId = member.id, authorLoginId = member.loginId, authorName = member.name, issue = i, projectId = project.id!!))
        val session = login(member)
        json(patch("/${project.owner}/${project.name}/issue/1/comments/${c.id}"), session, """{"content":"고침","original":"처음"}""").andExpect(status().isOk)
            .andExpect(jsonPath("$.result.id").value(c.id)).andExpect(jsonPath("$.result.contents").value("고침")).andExpect(jsonPath("$.result.author.loginId").value(member.loginId)).andExpect(jsonPath("$.result.author.id").value(member.id))
        json(put(api(project, "issues/1/comments/${c.id}")), session, """{"content":"또","original":"고침"}""").andExpect(status().isOk).andExpect(jsonPath("$.result.contents").value("또"))
        entityManager.flush(); entityManager.clear()
        assertEquals("또", comments.findById(c.id!!).get().contents)
    }

    @Test
    fun `댓글 갱신의 충돌과 권한`() {
        val project = newProject()
        val member = join(newUser(), project); val other = join(newUser(), project)
        val i = issue(project, author = member)
        val c = comments.save(IssueComment(contents = "현재", createdDate = Date(), authorId = member.id, authorLoginId = member.loginId, authorName = member.name, issue = i, projectId = project.id!!))
        val url = "/${project.owner}/${project.name}/issue/1/comments/${c.id}"
        json(patch(url), login(member), """{"content":"x","original":"옛날"}""").andExpect(status().isConflict).andExpect(jsonPath("$.storedContent").value("현재"))
        json(patch(url), login(newUser()), """{"content":"x","original":"현재"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Forbidden request"))
        json(patch(url), null, """{"content":"x","original":"현재"}""").andExpect(status().isUnauthorized)
        assertNotNull(other.id)
    }
}
