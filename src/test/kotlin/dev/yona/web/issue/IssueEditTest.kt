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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 이슈 수정 폼(`GET issue/:number/editform`)과 수정(`POST issue/:number/edit`)을 검증한다.
 *
 * 원본: `app/controllers/IssueApp.java:662-680, 735-850`, `app/controllers/AbstractPostingApp.java:83-131`, `app/views/issue/edit.scala.html`.
 */
@AutoConfigureMockMvc
@Transactional
class IssueEditTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var projects: ProjectRepository
    @Autowired private lateinit var menuSettings: ProjectMenuSettingRepository
    @Autowired private lateinit var projectUsers: ProjectUserRepository
    @Autowired private lateinit var roles: RoleRepository
    @Autowired private lateinit var issues: IssueRepository
    @Autowired private lateinit var milestones: MilestoneRepository
    @Autowired private lateinit var labels: IssueLabelRepository
    @Autowired private lateinit var categories: IssueLabelCategoryRepository
    @Autowired private lateinit var assignees: AssigneeRepository
    @Autowired private lateinit var attachments: AttachmentRepository
    @Autowired private lateinit var entityManager: EntityManager

    private var sequence = 0

    private fun newUser(name: String? = null): User {
        val n = ++sequence
        val salt = LegacyPasswordHasher.generateSalt()
        return users.save(User(loginId = "ie$n", name = name ?: "ie$n", email = "ie$n@example.com", state = UserState.ACTIVE, passwordSalt = salt, password = LegacyPasswordHasher.hash("1234", salt)))
    }

    private fun login(user: User): MockHttpSession = MockHttpSession().also {
        mockMvc.perform(post("/users/login").session(it).param("loginIdOrEmail", user.loginId!!).param("password", "1234"))
    }

    private fun newProject(scope: ProjectScope = ProjectScope.PUBLIC, milestone: Boolean = true): Project {
        val n = ++sequence
        val project = projects.save(Project(owner = "io$n", name = "ip$n", vcs = "GIT", projectScope = scope, lastIssueNumber = 0))
        menuSettings.save(ProjectMenuSetting(project = project, code = true, issue = true, pullRequest = true, review = true, milestone = milestone, board = true))
        return project
    }

    private fun join(user: User, project: Project, role: RoleType = RoleType.MEMBER): User {
        projectUsers.save(ProjectUser(user = user, project = project, role = roles.findById(role.id).get()))
        return user
    }

    private fun issue(
        project: Project, number: Long = 1, author: User? = null, draft: Boolean = false, state: State = State.OPEN, assignee: User? = null, milestone: Milestone? = null,
        issueLabels: Set<IssueLabel> = emptySet(), dueDate: Date? = null, parent: Issue? = null
    ): Issue {
        project.lastIssueNumber = maxOf(project.lastIssueNumber ?: 0, number); projects.save(project)
        return issues.save(
            Issue(
                project = project, number = number, title = "제목 $number", body = "본문 $number", state = if (draft) State.DRAFT else state, isDraft = draft, authorId = author?.id, authorLoginId = author?.loginId,
                authorName = author?.name, assignee = assignee?.let { assignees.save(Assignee(user = it, project = project)) }, milestone = milestone, labels = issueLabels.toMutableSet(),
                dueDate = dueDate, parent = parent, numOfComments = 0, weight = 0, createdDate = Date(1_700_000_000_000L), updatedDate = Date(1_700_000_000_000L)
            )
        )
    }

    private fun base(project: Project) = "/${project.owner}/${project.name}"

    private fun form(project: Project, number: Long = 1, session: MockHttpSession? = null): Document =
        Jsoup.parse(mockMvc.perform(get("${base(project)}/issue/$number/editform").apply { session?.let { session(it) } }).andExpect(status().isOk).andReturn().response.contentAsString)

    private fun submit(project: Project, session: MockHttpSession, number: Long = 1, vararg params: Pair<String, String>) =
        mockMvc.perform(multipart("${base(project)}/issue/$number/edit").session(session).apply { params.forEach { (k, v) -> param(k, v) } })

    // --- 폼 ---

    @Test
    fun `수정 폼의 머리와 입력란`() {
        val project = newProject()
        val author = join(newUser(), project)
        val due = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("2030-05-06 23:59:59")
        issue(project, author = author, dueDate = due)
        val doc = form(project, session = login(author))
        assertEquals("Edit issue - ${project.owner}/${project.name}", doc.title())
        val form = doc.selectFirst("form#issue-form")!!
        assertEquals("${base(project)}/issue/1/edit", form.attr("action"))
        assertEquals("multipart/form-data", form.attr("enctype"))
        assertEquals("#1", form.selectFirst("dt label strong")!!.text())
        assertEquals("제목 1", form.selectFirst("input#title")!!.attr("value"))
        assertEquals("본문 1", form.selectFirst("textarea[name=body]")!!.text())
        assertEquals("2030-05-06", form.selectFirst("input#issueDueDate")!!.attr("value"))
        assertEquals("false", form.selectFirst("input#isDraft")!!.attr("value")); assertEquals("false", form.selectFirst("input#isPublish")!!.attr("value"))
        assertEquals(author.id.toString(), form.selectFirst("input[name=authorId]")!!.attr("value"))
        assertTrue(form.select("#upload[data-resource-type=ISSUE_POST][data-resource-id]").isNotEmpty())
        assertTrue(doc.select("script").any { "issue.Write" in it.data() && "\"sMode\"" in it.data() && "edit" in it.data() })
    }

    @Test
    fun `상태, 담당자, 마일스톤, 라벨 선택은 현재 값으로 채워진다`() {
        val project = newProject()
        val author = join(newUser(), project); val assignee = join(newUser(), project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        issue(project, author = author, state = State.CLOSED, assignee = assignee, milestone = milestone, issueLabels = setOf(label))
        val form = form(project, session = login(author)).selectFirst("form#issue-form")!!
        assertEquals("closed", form.select("#state li.active").single().attr("data-value").lowercase())
        assertEquals(assignee.loginId, form.selectFirst("input#assignee[name=assigneeLoginId]")!!.attr("value"))
        assertEquals(milestone.id.toString(), form.selectFirst("select#milestoneId option[selected]")!!.attr("value"))
        assertTrue(form.selectFirst("select#labelIds option[value=${label.id}]")!!.hasAttr("selected"))
        assertEquals("false", form.selectFirst("select#labelIds")!!.attr("data-close-on-select"))
        assertTrue(form.select("#subtask, .subtask-wrap.show").isNotEmpty())
    }

    @Test
    fun `작성자에게는 알림 메일 체크가 있고 초안에는 번호 대신 초안 표시와 게시 단추가 있다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = join(newUser(), project)
        issue(project, 1, author = author)
        issue(project, 2, author = author, draft = true)
        assertTrue(form(project, 1, login(author)).select("input#notificationMail").isNotEmpty())
        assertTrue(form(project, 1, login(other)).select("input#notificationMail").isEmpty())
        val draft = form(project, 2, login(author))
        assertEquals("Draft", draft.selectFirst("dt .draft")!!.text())
        assertTrue(draft.select("button#button-draft-publish, button#draft-save-btn").size == 2)
        assertTrue(draft.select("input#notificationMail, button#button-save").isEmpty())
        assertTrue(form(project, 1, login(author)).select("button#button-save").isNotEmpty())
    }

    @Test
    fun `읽기만 할 수 있으면 저장 단추가 없다`() {
        val project = newProject()
        issue(project, author = join(newUser(), project))
        val doc = form(project, session = login(newUser()))
        assertTrue(doc.select("button#button-save, button#button-draft-publish").isEmpty())
        assertTrue(doc.select("#state, input#assignee").isEmpty())
    }

    @Test
    fun `없는 이슈는 404이고 읽을 수 없으면 403이다`() {
        val project = newProject()
        mockMvc.perform(get("${base(project)}/issue/9/editform").session(login(newUser()))).andExpect(status().isNotFound)
        val secret = newProject(ProjectScope.PRIVATE)
        issue(secret)
        mockMvc.perform(get("${base(secret)}/issue/1/editform").session(login(newUser()))).andExpect(status().isForbidden)
    }

    // --- 수정 ---

    @Test
    fun `수정하면 값이 바뀌고 보기 화면으로 돌아온다`() {
        val project = newProject()
        val author = join(newUser(), project); val assignee = join(newUser(), project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = false))
        val label = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        val i = issue(project, author = author)
        submit(
            project, login(author), 1, "title" to "새 제목", "body" to "새 본문", "dueDate" to "2031-02-03", "state" to "CLOSED", "assigneeLoginId" to assignee.loginId!!,
            "milestoneId" to milestone.id.toString(), "labelIds" to label.id.toString(), "isDraft" to "false", "isPublish" to "false"
        ).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        val saved = issues.findById(i.id!!).get()
        assertEquals("새 제목", saved.title); assertEquals("새 본문", saved.body); assertEquals(State.CLOSED, saved.state)
        assertEquals("2031-02-03 23:59:59", SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(saved.dueDate!!))
        assertEquals(assignee.id, saved.assignee?.user?.id); assertEquals(milestone.id, saved.milestone?.id); assertEquals(setOf(label.id), saved.labels.map { it.id }.toSet())
        assertEquals(author.id, saved.updatedByAuthorId)
        assertTrue(saved.history!!.contains("history-made-by"))
    }

    @Test
    fun `비워 보내면 마감일, 담당자, 마일스톤, 라벨이 지워지고 마일스톤을 보내지 않아도 지워진다`() {
        val project = newProject()
        val author = join(newUser(), project)
        val milestone = milestones.save(Milestone(project = project, title = "v1", state = State.OPEN))
        val i = issue(project, author = author, assignee = author, milestone = milestone, dueDate = Date())
        submit(project, login(author), 1, "title" to "t", "body" to "b", "dueDate" to "", "assigneeLoginId" to "").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val saved = issues.findById(i.id!!).get()
        assertNull(saved.dueDate); assertNull(saved.assignee); assertNull(saved.milestone)
    }

    @Test
    fun `제목이 비면 400으로 폼을 오류와 함께 다시 보여 준다`() {
        val project = newProject()
        val author = join(newUser(), project)
        issue(project, author = author)
        val doc = Jsoup.parse(submit(project, login(author), 1, "title" to "", "body" to "b").andExpect(status().isBadRequest).andReturn().response.contentAsString)
        assertEquals("This field is required", doc.selectFirst("div.message div")?.text())
        assertEquals("제목 1", doc.selectFirst("input#title")!!.attr("value"))
    }

    @Test
    fun `수정 권한이 없으면 403이다`() {
        val project = newProject()
        issue(project, author = join(newUser(), project))
        submit(project, login(newUser()), 1, "title" to "t", "body" to "b").andExpect(status().isForbidden)
        mockMvc.perform(multipart("${base(project)}/issue/1/edit").param("title", "t").param("body", "b")).andExpect(status().isForbidden)
    }

    @Test
    fun `본문이 오지 않으면 413 화면이다`() {
        val project = newProject()
        val author = join(newUser(), project)
        issue(project, author = author)
        submit(project, login(author), 1, "title" to "t").andExpect(status().isPayloadTooLarge)
    }

    @Test
    fun `같은 분류의 라벨 둘을 고르면 400이다`() {
        val project = newProject()
        val author = join(newUser(), project)
        val category = categories.save(IssueLabelCategory(project = project, name = "c", isExclusive = true))
        val a = labels.save(IssueLabel(project = project, category = category, name = "a", color = "#fff"))
        val b = labels.save(IssueLabel(project = project, category = category, name = "b", color = "#fff"))
        issue(project, author = author)
        submit(project, login(author), 1, "title" to "t", "body" to "b", "labelIds" to a.id.toString(), "labelIds" to b.id.toString())
            .andExpect(status().isBadRequest).andExpect(content().string("This category does not allow an issue to have two or more labels of the category"))
    }

    @Test
    fun `알림 메일을 고르거나 작성자가 아니면 변경 이벤트가 남는다`() {
        val project = newProject()
        val author = join(newUser(), project); val other = join(newUser(), project)
        val i = issue(project, author = author)
        submit(project, login(author), 1, "title" to "t", "body" to "새 본문", "state" to "CLOSED").andExpect(status().isSeeOther)
        entityManager.flush()
        assertEquals(0, entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id").setParameter("id", i.id).resultList.size)
        submit(project, login(author), 1, "title" to "t", "body" to "새 본문2", "state" to "OPEN", "notificationMail" to "yes").andExpect(status().isSeeOther)
        entityManager.flush()
        val types = entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id", IssueEvent::class.java).setParameter("id", i.id).resultList.map { it.eventType }
        assertTrue(EventType.ISSUE_STATE_CHANGED in types && EventType.ISSUE_BODY_CHANGED in types)
        assertFalse(EventType.ISSUE_ASSIGNEE_CHANGED in types)
        submit(project, login(other), 1, "title" to "t", "body" to "새 본문3", "state" to "OPEN").andExpect(status().isSeeOther)
    }

    @Test
    fun `초안을 게시하면 새 번호의 열린 이슈가 되고 초안으로 저장하면 초안이다`() {
        val project = newProject()
        val author = join(newUser(), project)
        issue(project, 1, author = author)
        val draft = issue(project, 2, author = author, draft = true)
        submit(project, login(author), 2, "title" to "t", "body" to "b", "isDraft" to "true").andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/2"))
        entityManager.flush(); entityManager.clear()
        assertEquals(State.DRAFT, issues.findById(draft.id!!).get().state)
        submit(project, login(author), 2, "title" to "t", "body" to "b", "isPublish" to "true").andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(project)}/issue/3"))
        entityManager.flush(); entityManager.clear()
        val published = issues.findById(draft.id!!).get()
        assertEquals(State.OPEN, published.state); assertEquals(false, published.isDraft); assertEquals(3L, published.number)
    }

    @Test
    fun `부모 이슈를 고르면 하위 이슈가 되고 비우면 풀린다`() {
        val project = newProject()
        val author = join(newUser(), project)
        val parent = issue(project, 1, author = author)
        val child = issue(project, 2, author = author)
        val session = login(author)
        submit(project, session, 2, "title" to "t", "body" to "b", "targetProjectId" to project.id.toString(), "parentIssueId" to parent.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(parent.id, issues.findById(child.id!!).get().parent?.id)
        submit(project, session, 2, "title" to "t", "body" to "b", "targetProjectId" to project.id.toString(), "parentIssueId" to "").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertNull(issues.findById(child.id!!).get().parent)
    }

    @Test
    fun `다른 프로젝트를 지정하면 옮기고 거기로 간다`() {
        val project = newProject(); val target = newProject()
        val user = join(join(newUser(), project), target)
        val i = issue(project, author = user)
        submit(project, login(user), 1, "title" to "t", "body" to "b", "targetProjectId" to target.id.toString()).andExpect(status().isSeeOther).andExpect(redirectedUrl("${base(target)}/issue/1"))
        entityManager.flush(); entityManager.clear()
        assertEquals(target.id, issues.findById(i.id!!).get().project?.id)
    }

    @Test
    fun `초안을 다른 프로젝트로 옮기며 게시하면 이동 이벤트도 남는다`() {
        val project = newProject(); val target = newProject()
        val user = join(join(newUser(), project), target)
        val draft = issue(project, 1, author = user, draft = true)
        // v1.6 IssueApp.preUpdateHook: 이동 알림(addIssueMovedNotification)을 먼저 남기고 그 뒤에 게시 알림을 보낸다
        submit(project, login(user), 1, "title" to "t", "body" to "b", "targetProjectId" to target.id.toString(), "isPublish" to "true").andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        val moved = issues.findById(draft.id!!).get()
        assertEquals(target.id, moved.project?.id); assertEquals(State.OPEN, moved.state)
        val types = entityManager.createQuery("select e from IssueEvent e where e.issue.id = :id", IssueEvent::class.java).setParameter("id", draft.id).resultList.map { it.eventType }
        assertTrue(EventType.ISSUE_MOVED in types, types.toString())
    }

    @Test
    fun `대상 프로젝트가 없거나 만들 수 없으면 거부한다`() {
        val project = newProject(); val secret = newProject(ProjectScope.PRIVATE)
        val user = join(newUser(), project)
        issue(project, author = user)
        submit(project, login(user), 1, "title" to "t", "body" to "b", "targetProjectId" to "999999").andExpect(status().isBadRequest)
        submit(project, login(user), 1, "title" to "t", "body" to "b", "targetProjectId" to secret.id.toString()).andExpect(status().isForbidden)
    }

    @Test
    fun `임시 첨부는 이슈로 옮겨진다`() {
        val project = newProject()
        val author = join(newUser(), project)
        val i = issue(project, author = author)
        val temp = attachments.save(Attachment(name = "a.txt", hash = "h-${System.nanoTime()}", containerType = ResourceType.USER, containerId = author.id!!, mimeType = "text/plain", size = 1L, createdDate = Date(), ownerLoginId = author.loginId))
        submit(project, login(author), 1, "title" to "t", "body" to "b", "temporaryUploadFiles" to temp.id.toString()).andExpect(status().isSeeOther)
        entityManager.flush(); entityManager.clear()
        assertEquals(i.id, attachments.findById(temp.id!!).get().containerId)
    }
}
